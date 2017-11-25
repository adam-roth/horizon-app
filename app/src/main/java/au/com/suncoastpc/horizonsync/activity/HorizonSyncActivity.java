package au.com.suncoastpc.horizonsync.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import au.com.suncoastpc.horizonsync.R;
import au.com.suncoastpc.horizonsync.activity.base.EnvironmentAwareActivity;
import au.com.suncoastpc.horizonsync.event.EventHandler;
import au.com.suncoastpc.horizonsync.event.EventManager;
import au.com.suncoastpc.horizonsync.event.EventType;
import au.com.suncoastpc.horizonsync.model.SyncEvent;
import au.com.suncoastpc.horizonsync.utils.Constants;
import au.com.suncoastpc.horizonsync.utils.Environment;
import au.com.suncoastpc.horizonsync.utils.UserPreferences;
import au.com.suncoastpc.horizonsync.utils.comparators.SyncEventStartTimeCompare;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class HorizonSyncActivity extends EnvironmentAwareActivity implements EventHandler, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    private static final int PULSE_DURATION = 1000;     //total time to cycle from low to high or high to low
    private static final double PULSE_EASING = 0.25;    //how much time to 'linger' at high/low values

    private static final int MIN_FLICKER = 100;        //minimum time to wait before adjusting the flicker
    private static final int MAX_FLICKER = 500;        //maximum time to wait before adjusting the flicker
    private static final int MIN_BRIGHTNESS = 0;
    private static final int MAX_BRIGHTNESS = 192;

    private static final long CLOCK_STEP = 25;

    private static final int REQUEST_CHECK_SETTINGS = 1337;
    private static final int REQUEST_CHECK_SCREEN = 1338;

    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;

    //http://is5.mzstatic.com/image/thumb/Purple127/v4/5e/15/cc/5e15cc46-cf7d-7e87-1075-8ac57f1dbd24/source/392x696bb.jpg
    private static final String DEMO_SCRIPT = "[{\"time\":\"0\", \"color\":\"#0000ff\"}, " +
            "{\"time\":\"5000\", \"color\":\"#00ff00\", \"message\":\"Ready!\"}, " +
            "{\"time\":\"10000\", \"color\":\"#ff0000\"}, " +
            "{\"time\":\"15000\", \"color\":\"#ffffff\"}, " +
            "{\"time\":\"20000\", \"message\":\"Lighter\", \"image\":\"http://is5.mzstatic.com/image/thumb/Purple127/v4/5e/15/cc/5e15cc46-cf7d-7e87-1075-8ac57f1dbd24/source/392x696bb.jpg\", \"color\":\"#888888\"}" +
            "]";


    private final Handler scriptHandler = new Handler();
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    //Google Play Services endpoint (for location services, etc.)
    private GoogleApiClient googleApiClient;
    private LocationRequest locationRequest;

    //UI Components
    private TextView textContentView;
    private View backgroundView;
    private ImageView imageView;
    private View overlayView;

    private Button button;

    private double latitude = -90;
    private double longitude = 90;
    private SyncEvent bestEvent;
    private SyncEvent executingEvent;
    private JSONArray executingScript;
    private JSONObject executingAction;
    private int executingActionIndex = -1;
    private long executionStartTime;

    private long clockSkew = 0;     //local clock skew, in ms; ADD this to the current reported time to determine what the real time actually is

    private void stopExecution() {
        button.setVisibility(View.VISIBLE);

        imageView.setVisibility(View.GONE);
        backgroundView.setBackgroundColor(Color.parseColor("#000000"));
        textContentView.setText("");

        executingAction = null;
        executingEvent = null;
        executingScript = null;
        bestEvent = null;

        scanEvents();
    }

    private void startExecution(SyncEvent event, boolean useEventTime) {
        button.setVisibility(View.GONE);

        executingEvent = event;
        executingScript = event.scriptForZone(event.zoneForCoord(latitude, longitude));
        executionStartTime = useEventTime ? event.getEventTime().getTime() : System.currentTimeMillis() + clockSkew;
        executingActionIndex = 0;

        if (executingActionIndex < executingScript.size()) {
            executingAction = (JSONObject) executingScript.get(executingActionIndex);
            applyScriptAction(executingAction);
        }
        else {
            scriptHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopExecution();
                }
            }, event.getDurationMillis());
        }
    }

    private void startNextAction() {
        overlayView.setBackgroundColor(Color.argb(0, 0, 0, 0));

        executingActionIndex++;
        if (executingActionIndex < executingScript.size()) {
            applyScriptAction((JSONObject) executingScript.get(executingActionIndex));
        }
    }

    private CountDownTimer timer = new CountDownTimer(Long.MAX_VALUE, CLOCK_STEP) {
        private long nextFlicker = 0;

        private String pad(long num) {
            String result = num + "";
            return result.length() == 1 ? "0" + result : result;
        }

        private long forwardTolerance() {
            return CLOCK_STEP / 2;
        }

        @Override
        public void onTick(long millisUntilFinished) {
            SyncEvent event = executingEvent;
            long currentTime = System.currentTimeMillis() + clockSkew;


            if (event != null) {
                long showTime = currentTime - executionStartTime;
                if (showTime > event.getDurationMillis() - forwardTolerance()) {
                    //we've reached the end of the executing event; revert to default state and stop executing
                    stopExecution();
                    return;
                }

                //the event is running and we need to continue executing the script!
                JSONObject action = executingAction;
                if (action != null) {
                    long actionTime = showTime - action.getAsNumber("time").longValue();
                    if (actionTime > action.getAsNumber("duration").longValue() - forwardTolerance()) {
                        //the current action is no longer valid
                        nextFlicker = 0;
                        startNextAction();
                        return;
                    }

                    if (action.containsKey("effect")) {
                        String effect = action.getAsString("effect");
                        if ("pulse".equals(effect)) {
                            nextFlicker = 0;
                            if (imageView.getVisibility() == View.GONE) {
                                overlayView.setBackgroundColor(Color.argb(0, 0, 0, 0));
                            }

                            String colorCode = action.containsKey("color") ? action.getAsString("color") : "#000000";
                            int color = Color.parseColor(colorCode);

                            long currentPos = actionTime % PULSE_DURATION;
                            boolean increasing = (actionTime / PULSE_DURATION) % 2 == 0;
                            if (! increasing) {
                                currentPos = (currentPos - PULSE_DURATION) * -1;
                            }

                            double scaleFactor = (double)currentPos / (double)PULSE_DURATION;
                            if (scaleFactor < PULSE_EASING) {
                                scaleFactor = 0;
                            }
                            else if (scaleFactor > 1.0 - PULSE_EASING) {
                                scaleFactor = 1.0;
                            }
                            else {
                                scaleFactor -= PULSE_EASING;
                                scaleFactor /= 1.0 - (PULSE_EASING * 2);
                            }

                            int A = (color >> 24) & 0xff; // or color >>> 24
                            int R = (int)(((color >> 16) & 0xff) * scaleFactor);
                            int G = (int)(((color >>  8) & 0xff) * scaleFactor);
                            int B = (int)(((color      ) & 0xff) * scaleFactor);

                            color = (A & 0xff) << 24 | (R & 0xff) << 16 | (G & 0xff) << 8 | (B & 0xff);
                            backgroundView.setBackgroundColor(color);

                            if (imageView.getVisibility() == View.VISIBLE) {
                                overlayView.setBackgroundColor(Color.argb(255 - (int)(255 * scaleFactor), 0, 0, 0));
                            }
                        }
                        else if (effect.startsWith("flash_")) {
                            nextFlicker = 0;
                            overlayView.setBackgroundColor(Color.argb(0, 0, 0, 0));

                            long flashDuration = Long.parseLong(effect.split("_")[1]);
                            boolean shouldBeOn = (actionTime / flashDuration) % 2 == 0;
                            String colorCode = shouldBeOn ? (action.containsKey("color") ? action.getAsString("color") : "#000000") : "#000000";

                            backgroundView.setBackgroundColor(Color.parseColor(colorCode));
                        }
                        else if ("flicker".equals(effect)) {
                            if (actionTime > nextFlicker) {
                                overlayView.setBackgroundColor(Color.argb(randomBetween(MIN_BRIGHTNESS, MAX_BRIGHTNESS), 0, 0, 0));

                                int interval = randomBetween(MIN_FLICKER, MAX_FLICKER);
                                //if (actionTime + interval > action.getAsNumber("duration").longValue() - 10) {
                                //    nextFlicker = 0;
                                //}
                                //else {
                                nextFlicker = actionTime + interval;
                                //}
                            }
                        }
                    }
                    else {
                        nextFlicker = 0;
                        overlayView.setBackgroundColor(Color.argb(0, 0, 0, 0));
                    }
                }
            }
            else if (bestEvent != null) {
                if (currentTime < bestEvent.getEventTime().getTime() - forwardTolerance()) {
                    long millisToStart = bestEvent.getEventTime().getTime() - currentTime;
                    long hoursToStart = millisToStart / (1000 * 60 * 60);
                    millisToStart -= hoursToStart * (1000 * 60 * 60);
                    long minutesToStart = millisToStart / (1000 * 60);
                    millisToStart -= minutesToStart * (1000 * 60);
                    long secondsToStart = millisToStart / 1000;


                    textContentView.setText(bestEvent.getName() + "\n\n" + hoursToStart + ":" + pad(minutesToStart) + ":" + pad(secondsToStart));
                    button.setText("Run Now!");
                }
                else {
                    //we should start actually executing the event now!
                    startExecution(bestEvent, true);
                }
            }
            else {
                //no real events found nearby; allow the user to manually run the hardcoded demo script
                textContentView.setText("No Events Found");
                button.setText("Run Demo");
            }
        }

        @Override
        public void onFinish() {

        }
    };

    private int randomBetween(int lower, int upper) {
        int range = upper - lower;
        return lower + (int)(Math.random() * range);
    }

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    @Override
    protected Class<? extends Activity> getLoginActivity() {
        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case Constants.REQUEST_LOCATION_PERMISSIONS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (googleApiClient != null) {
                        googleApiClient.disconnect();
                    }

                    googleApiClient = new GoogleApiClient.Builder(this)
                            .addConnectionCallbacks(this)
                            .addOnConnectionFailedListener(this)
                            .addApi(LocationServices.API)
                            .build();
                    googleApiClient.connect();
                }
                else {
                    // permission denied; no location services are available
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        //stop handling events
        EventManager.deregisterAll(this);

        try {
            //disconnect from location services
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
            googleApiClient.disconnect();
        }
        catch (Throwable e) {
            Log.w("shutdown", "Exception when attempting to shutdown LoginActivity", e);
        }

        super.onDestroy();
    }

    @Override
    //FIXME:  implement image download                                                                              [X]
    //FIXME:  implement effects                                                                                     [X]
    //FIXME:  implement reset at end of script                                                                      [X]
    //FIXME:  implement auto-start of script                                                                        [X]
    //FIXME:  implement timer-based rendering path                                                                  [X]
    //FIXME:  video of three devices on one zone, keeping in sync (with each other) to music video                  [X]
    //FIXME:  video of three devices on three zones, displaying independent (but still well-synced) content to same music video     [X]
    //FIXME:  video of three devices on three zones, "digital wave"                                                 [X]
    //FIXME:  slides and narration                                                                                  [good enough]
    //FIXME:  submission document                                                                                   [ditto]
    //FIXME:  source code to github                                                                                 []
    //FIXME:  demo page on terra?                                                                                   [X]
    //FIXME:  ensure that the candidate event is cleared/reset if the user is no longer within its area of influence
    //FIXME:  need to monitor GPS and detect when the user is near an event                                         [X]
    //FIXME:  need to spin up a background thread/service to sync data with the server                              [X]
    //FIXME:  the background sync process should also attempt to measure and track the clock skew on the device     [X]
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Environment.setApplicationContext(this.getApplicationContext());

        //register for events
        EventManager.register(EnumSet.of(EventType.SYNC_COMPLETE, EventType.CLOCK_SKEW_CHANGED), this);

        //location services
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
            googleApiClient.connect();
        }

        setContentView(R.layout.activity_horizon_sync);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);


        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        button = (Button)findViewById(R.id.dummy_button);
        button.setOnTouchListener(mDelayHideTouchListener);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bestEvent != null && bestEvent.isValid() && bestEvent.zoneForCoord(latitude, longitude) != -1) {
                    //executingEvent = bestEvent;
                    //runScript(bestEvent.scriptForZone(bestEvent.zoneForCoord(latitude, longitude)));
                    forceExecuteEvent(bestEvent);
                }
                else {
                    runScript((JSONArray) JSONValue.parse(DEMO_SCRIPT));
                }
            }
        });

        backgroundView = findViewById(R.id.fullscreen_background);
        imageView = (ImageView) findViewById(R.id.fullscreen_image);
        textContentView = (TextView) findViewById(R.id.fullscreen_content);
        overlayView = findViewById(R.id.fullscreen_overlay);

        //check permissions
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (! Settings.System.canWrite(this)) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + this.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityForResult(intent, REQUEST_CHECK_SCREEN);
            }
        }*/

        //see if we're near a known event
        scanEvents();

        //start syncing updates from the server
        Environment.startBackgroundSync();

        timer.start();
    }

    private void scanEvents() {
        //XXX:  probably not correct to do this
        if (executingEvent != null) {
            return;
        }

        //scan all events to see if we're near one
        List<SyncEvent> sortedEvents = new ArrayList<>();
        JSONArray allEvents = UserPreferences.getValueAsJsonArray(UserPreferences.EVENT_DATA_KEY);
        System.out.println("!!!!!!!!!!  SHOULD CHECK:  " + allEvents.size());

        for (Object jsonObj : allEvents) {
            JSONObject json = (JSONObject)jsonObj;
            SyncEvent event = SyncEvent.fromJson(json);
            sortedEvents.add(event);
        }

        Collections.sort(sortedEvents, new SyncEventStartTimeCompare());

        for (SyncEvent event : sortedEvents) {
            System.out.println("!!!!!!!!!!  CHECKING:  " + event.getName() + event.isValid() + event.zoneForCoord(latitude, longitude));

            if (event.isValid() && event.zoneForCoord(latitude, longitude) != -1) {
                bestEvent = event;
                Log.d("exec", "Found a new candidate event:  " + event.toJson() + ", skew=" + clockSkew);
                break;
            }
        }
    }

    private JSONObject sanitizeAction(JSONObject action) {
        JSONObject newAction = new JSONObject();

        for (String key : action.keySet()) {
            if (action.get(key) != null && action.getAsString(key).length() > 0) {
                newAction.put(key, action.get(key));
            }
        }

        if (newAction.containsKey("color") && ! newAction.getAsString("color").startsWith("#")) {
            newAction.put("color", "#" + newAction.getAsString("color"));
        }
        if (newAction.containsKey("effect") && "-1".equals(newAction.getAsString("effect"))) {
            newAction.remove("effect");
        }

        return newAction;
    }

    private void applyScriptAction(JSONObject action) {
        if (action == null) {
            return;
        }

        action = sanitizeAction(action);

        String colorCode = action.containsKey("color") ? action.getAsString("color") : "#000000";
        String imageUrl = action.containsKey("image") ? action.getAsString("image") : null;
        String text = action.containsKey("message") ? action.getAsString("message") : null;

        backgroundView.setBackgroundColor(Color.parseColor(colorCode));

        if (imageUrl != null) {
            String url = action.getAsString("image");
            File imageFile = Environment.getFileForUrl(url);
            if (imageFile != null && imageFile.exists()) {
                try {
                    Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                        imageView.setVisibility(View.VISIBLE);
                    }
                    else {
                        imageView.setVisibility(View.GONE);
                    }
                }
                catch (Throwable e) {
                    imageView.setVisibility(View.GONE);
                }
            }
            else {
                imageView.setVisibility(View.GONE);
            }
        }
        else {
            imageView.setVisibility(View.GONE);
        }

        if (text != null) {
            textContentView.setText(text);
        }
        else {
            textContentView.setText("");
        }

        executingAction = action;
        Log.d("exec", "Executing action:  " + action);
    }

    private void forceExecuteEvent(SyncEvent event) {
        if (event == null) {
            return;
        }
        if (executingEvent != null) {
            return;
        }

        startExecution(event, false);
    }

    @Deprecated
    //XXX:  deprecated; use startExecution and stopExecution instead
    private void runScript(JSONArray script) {
        executionStartTime = System.currentTimeMillis() + clockSkew;
        JSONObject lastAction = null;
        for (Object obj : script) {
            final JSONObject action = (JSONObject)obj;
            scriptHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    applyScriptAction(action);
                }
            }, Long.parseLong(action.get("time").toString()));

            lastAction = action;
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        //mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        //mHideHandler.removeCallbacks(mHidePart2Runnable);
        //mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    @Override
    public void handleEvent(EventType eventType, Object eventData) {
        switch (eventType) {
            case CLOCK_SKEW_CHANGED:
                this.clockSkew = ((Number)eventData).longValue();
                Log.d("sync", "Clock skew updated; skew=" + this.clockSkew);
                break;
            case SYNC_COMPLETE:
                int numEvents = ((Number)eventData).intValue();
                if (numEvents > 0) {
                    this.scanEvents();
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CHECK_SETTINGS && resultCode == RESULT_OK) {
            try {
                // All location settings are satisfied. The client can initialize location requests here.
                LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, HorizonSyncActivity.this);
            }
            catch (SecurityException noPermission) {
                //location unavailable
                noPermission.printStackTrace();
            }
        }
        if (requestCode == REQUEST_CHECK_SCREEN && resultCode == RESULT_OK) {
            //anything to do here?
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        try {
            //grab the user's last known location
            LocationAvailability availability = LocationServices.FusedLocationApi.getLocationAvailability(googleApiClient);
            if (availability != null && availability.isLocationAvailable()) {
                Location location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
                Environment.updateGeolocation(location.getLatitude(), location.getLongitude(), location.getTime());

                this.latitude = location.getLatitude();
                this.longitude = location.getLongitude();

                this.scanEvents();
            }
            else {
                //XXX:  check to see if we have permission to access location details; ask for it if we don't
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    String[] perms = new String[] {Manifest.permission.ACCESS_FINE_LOCATION};
                    ActivityCompat.requestPermissions(this, perms, Constants.REQUEST_LOCATION_PERMISSIONS);

                    return;
                }
            }

            //request notification of future location changes
            locationRequest = new LocationRequest();
            locationRequest.setInterval(5000);                                     //request 1 update every 5 seconds
            locationRequest.setFastestInterval(1000);                               //limit to a maximum of 1 update per second
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);    //request highest possible GPS accuracy

            final PendingResult<LocationSettingsResult> response = LocationServices.SettingsApi.checkLocationSettings(googleApiClient, new LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build());
            response.setResultCallback(new ResultCallback<LocationSettingsResult>() {
                @Override
                public void onResult(LocationSettingsResult result) {
                    Status status = result.getStatus();
                    switch (status.getStatusCode()) {
                        case LocationSettingsStatusCodes.SUCCESS:
                            try {
                                // All location settings are satisfied. The client can initialize location requests here.
                                LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, HorizonSyncActivity.this);
                            }
                            catch (SecurityException noPermission) {
                                //location unavailable
                                noPermission.printStackTrace();
                            }
                            break;
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            // Location settings are not satisfied, but this can be fixed by showing the user a dialog.
                            try {
                                // Show the dialog by calling startResolutionForResult(), and check the result in onActivityResult().
                                status.startResolutionForResult(HorizonSyncActivity.this, REQUEST_CHECK_SETTINGS);
                            } catch (IntentSender.SendIntentException e) {
                                // Ignore the error.
                                e.printStackTrace();
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            //Location settings are not satisfied. However, we have no way to fix the settings so we won't show the dialog.
                            break;
                    }
                }
            });

        }
        catch (SecurityException noPermission) {
            //XXX:  location services have been disabled/permission has been denied by the user
            noPermission.printStackTrace();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Environment.updateGeolocation(location.getLatitude(), location.getLongitude(), location.getTime());
    }

    //@Override
    public void onConnectionSuspended(int i) {
        //XXX:  no-op
    }

    //@Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        //XXX:  no-op
    }
}
