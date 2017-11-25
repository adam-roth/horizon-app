package au.com.suncoastpc.horizonsync.model;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import au.com.suncoastpc.horizonsync.utils.Constants;
import au.com.suncoastpc.horizonsync.utils.Environment;

/**
 * Created by aroth on 11/25/2017.
 */

public class SyncEvent {
    private static final int EARTH_RADIUS = 6371;
    private static final Set<String> PENDING_DOWNLOADS = Collections.synchronizedSet(new HashSet<String>());

    private String name;
    private String shape;

    private JSONArray scriptsJson;

    private double latitude;
    private double longitude;

    private int numZones;
    private int radiusMeters;
    private int startDegrees;
    private int stopDegrees;

    private Date createTime;
    private Date eventTime;
    private Date endTime;

    private JSONObject sourceData;

    private SyncEvent() {
        throw new RuntimeException("Default constructor not allowed!");
    }

    private SyncEvent(JSONObject data) {
        this.name = data.getAsString("name");
        this.shape = data.getAsString("shape");

        this.latitude = data.getAsNumber("latitude").doubleValue();
        this.longitude = data.getAsNumber("longitude").doubleValue();

        this.numZones = data.getAsNumber("numZones").intValue();
        this.radiusMeters = data.getAsNumber("radiusMeters").intValue();
        this.startDegrees = data.getAsNumber("startDegrees").intValue();
        this.stopDegrees = data.getAsNumber("stopDegrees").intValue();

        this.createTime = new Date(data.getAsNumber("created").longValue());
        this.eventTime = new Date(data.getAsNumber("start").longValue());

        this.scriptsJson = (JSONArray)data.get("zoneScripts");

        long endTime = this.eventTime.getTime();
        for (Object scriptObj : scriptsJson) {
            JSONObject zoneScript = (JSONObject)scriptObj;
            JSONArray zoneEvents = (JSONArray) zoneScript.get("events");
            for (Object eventObj : zoneEvents) {
                JSONObject event = (JSONObject)eventObj;
                long eventEndTime = eventTime.getTime() + event.getAsNumber("time").longValue() + event.getAsNumber("duration").longValue();
                if (eventEndTime > endTime) {
                    endTime = eventEndTime;
                }

                if (event.containsKey("image")) {
                    String url = event.getAsString("image");
                    File imageFile = Environment.getFileForUrl(url);
                    if (imageFile != null && ! imageFile.exists()) {
                        //XXX:  we need to download the image
                        new DownloadImageTask(imageFile).execute(url);
                    }
                }
            }
        }
        this.endTime = new Date(endTime);

        this.sourceData = data;
    }

    public String getName() {
        return name;
    }

    public String getShape() {
        return shape;
    }

    public JSONArray getScriptsJson() {
        return scriptsJson;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getNumZones() {
        return numZones;
    }

    public int getRadiusMeters() {
        return radiusMeters;
    }

    public int getStartDegrees() {
        return startDegrees;
    }

    public int getStopDegrees() {
        return stopDegrees;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public Date getEventTime() {
        return eventTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public long getDurationMillis() {
        return getEndTime().getTime() - getEventTime().getTime();
    }

    public int zoneForCoord(double lat, double lng) {
        double distance = metersBetween(this.getLatitude(), lat, this.getLongitude(), lng, 0, 0);

        //XXX:  for demo purposes, force the device onto a particular zone if we've been configured with one
        if (Constants.FORCE_ZONE >= 0 && Constants.FORCE_ZONE < this.getScriptsJson().size()) {
            return Constants.FORCE_ZONE;
        }

        //HACK:  this isn't the real way to do it; we need to properly evaluate the audience geometry against the location to determine the actual zone
        if (distance < this.getRadiusMeters() /*|| distance > 7000000*/) {      //BIGGER HACK:  GPS not working on legacy device; need to always treat it as valid for demo purposes
            //XXX:  edit here to force a particular zone to run
            return 0;
        }

        //FIXME:  implement; see which event zone the provided coord falls within, if any
        return -1;
    }

    public JSONArray scriptForZone(int zone) {
        if (zone < 0 || zone >= this.getScriptsJson().size()) {
            return new JSONArray();
        }

        JSONObject scriptMeta = (JSONObject) this.getScriptsJson().get(zone);
        return (JSONArray) scriptMeta.get("events");
    }

    /**
     * Calculate distance between two points in latitude and longitude taking
     * into account height difference. If you are not interested in height
     * difference pass 0.0. Uses Haversine method as its base.
     *
     * lat1, lon1 Start point lat2, lon2 End point el1 Start altitude in meters
     * el2 End altitude in meters
     * @returns Distance in Meters
     *
     * see:  https://stackoverflow.com/questions/3694380/calculating-distance-between-two-points-using-latitude-longitude-what-am-i-doi
     */
    public static double metersBetween(double lat1, double lat2, double lon1,
                                  double lon2, double el1, double el2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS * c * 1000; // convert to meters

        double height = el1 - el2;

        distance = Math.pow(distance, 2) + Math.pow(height, 2);

        return Math.sqrt(distance);
    }

    public boolean isValid() {
        return System.currentTimeMillis() < this.getEventTime().getTime();
    }

    public JSONObject toJson() {
        return sourceData;
    }

    public static SyncEvent fromJson(JSONObject json) {
        try {
            return new SyncEvent(json);
        }
        catch (Throwable e) {
            Log.e("event.parse", "Failed to parse event JSON data!", e);
        }
        return null;
    }

    private static class DownloadImageTask extends AsyncTask<String, Void, File> {
        File outfile;

        public DownloadImageTask(File outfile) {
            this.outfile = outfile;
        }

        protected File doInBackground(String... urls) {
            String url = urls[0];
            if (PENDING_DOWNLOADS.contains(url)) {
                //do not attempt to repeat an in-flight download
                return null;
            }

            PENDING_DOWNLOADS.add(url);
            try /*(OutputStream out = new FileOutputStream(outfile); InputStream in = new java.net.URL(url).openStream())*/ {
                OutputStream out = new FileOutputStream(outfile);
                InputStream in = new java.net.URL(url).openStream();

                int read = 0;
                byte[] buffer = new byte[1024];
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }

                in.close();
                out.close();
            }
            catch (Exception e) {
                Log.e("download", "File download failed for url=" + url, e);
            }

            PENDING_DOWNLOADS.remove(url);
            return outfile;
        }

        protected void onPostExecute(File result) {
            //bmImage.setImageBitmap(result);
            //bmImage.setVisibility(View.VISIBLE);
        }
    }
}
