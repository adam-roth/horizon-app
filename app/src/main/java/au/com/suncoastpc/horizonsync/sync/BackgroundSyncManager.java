package au.com.suncoastpc.horizonsync.sync;

import android.util.Log;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import au.com.suncoastpc.horizonsync.event.EventManager;
import au.com.suncoastpc.horizonsync.event.EventType;
import au.com.suncoastpc.horizonsync.model.SyncEvent;
import au.com.suncoastpc.horizonsync.net.ServerApi;
import au.com.suncoastpc.horizonsync.utils.Constants;
import au.com.suncoastpc.horizonsync.utils.Environment;
import au.com.suncoastpc.horizonsync.utils.UserPreferences;

/**
 * Created by aroth on 2/8/2017.
 */
public class BackgroundSyncManager {
    private static final DateFormat SYNC_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private static final long WAKE_INTERVAL = 1000 * 60 * 5;

    private final SyncThread syncThread;

    public BackgroundSyncManager() {
        syncThread = new SyncThread();
    }

    public void startBackgroundSync() {
        synchronized(syncThread) {
            if (! syncThread.isAlive()) {
                syncThread.start();
            }
        }
    }

    public void disableSync() {
        synchronized(syncThread) {
            syncThread.setShouldRun(false);
        }
    }

    public void enableSync() {
        synchronized(syncThread) {
            syncThread.setShouldRun(true);
            syncThread.notify();
        }
    }

    public boolean isSyncEnabled() {
        return syncThread.shouldRun;
    }

    private static class SyncThread extends Thread {
        private boolean shouldRun = false;
        private boolean isRunning = false;

        public boolean isShouldRun() {
            return shouldRun;
        }

        public void setShouldRun(boolean shouldRun) {
            this.shouldRun = shouldRun;
        }

        public boolean isRunning() {
            return isRunning;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    while (!this.isShouldRun()) {
                        synchronized (this) {
                            wait();
                        }
                    }

                    String key = SYNC_DATE_FORMAT.format(new Date());

                    if (Environment.isNetworkAvailable() && Environment.isServerAvailable()) {
                        isRunning = true;

                        //request the latest events updates
                        long syncTimestamp = UserPreferences.getValueAsLong(UserPreferences.SYNC_TIMESTAMP_KEY);
                        if (syncTimestamp < 0) {
                            syncTimestamp = 0;
                        }

                        JSONObject response = ServerApi.getUpdates(syncTimestamp);
                        Log.d("net", "Received sync response:  " + response);

                        int numProcessed = 0;
                        if (Constants.SUCCESS_STATUS.equals(response.getAsString("status")) && response.containsKey("result")) {
                            //have new data to parse
                            JSONObject clockSkewAndUpdates = (JSONObject)response.get("result");
                            if (clockSkewAndUpdates.containsKey("timeOffset")) {
                                EventManager.post(EventType.CLOCK_SKEW_CHANGED, clockSkewAndUpdates.getAsNumber("timeOffset").longValue());
                            }

                            JSONArray knownEvents = UserPreferences.getValueAsJsonArray(UserPreferences.EVENT_DATA_KEY);
                            JSONArray updates = (JSONArray) clockSkewAndUpdates.get("updates");
                            for (Object updateObj : updates) {
                                JSONObject update = (JSONObject)updateObj;
                                SyncEvent parsedEvent = SyncEvent.fromJson(update);
                                if (parsedEvent != null) {
                                    if (update.getAsNumber("created").longValue() > syncTimestamp) {
                                        syncTimestamp = update.getAsNumber("created").longValue();
                                    }
                                    knownEvents.add(update);
                                    numProcessed++;
                                }
                            }

                            if (numProcessed > 0) {
                                UserPreferences.setValue(UserPreferences.SYNC_TIMESTAMP_KEY, syncTimestamp);
                                UserPreferences.setValue(UserPreferences.EVENT_DATA_KEY, knownEvents);
                            }

                            //dispatch an event to indicate that we're no longer syncing data
                            EventManager.post(EventType.SYNC_COMPLETE, numProcessed);

                        }
                        else {
                            //XXX:  API request failed; anything we should do here?
                        }

                        isRunning = false;
                    }
                    else {
                        Log.w("sync", "Unable to synchronize with the server; clock skew will not be compensated for!!!");
                    }


                    //wait before syncing again
                    synchronized (this) {
                        wait(WAKE_INTERVAL);
                    }

                } catch (Throwable e) {
                    //nothing we can do at this point
                    isRunning = false;
                    e.printStackTrace();
                }
            }
        }

    }
}
