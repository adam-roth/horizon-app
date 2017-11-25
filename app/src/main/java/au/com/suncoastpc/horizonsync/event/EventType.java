package au.com.suncoastpc.horizonsync.event;

public enum EventType {
	DOWNLOAD_STARTED,			//a background file download has started
	DOWNLOAD_PROGRESS,			//data has been received while downloading a file
	DOWNLOAD_COMPLETED,			//a background file download has completed successfully
	DOWNLOAD_FAILED,			//a background file download has failed
	SYNC_REQUESTED,				//an immediate content sync has been requested (likely meaning that the app UI is blocked until the sync completes)
	SYNC_STARTED,				//a new sync request is pending and will be dispatched to the server (so that the app can track/display what's currently syncing)
	SYNC_UPDATE,				//dispatched incrementally while sync content is being parsed/loaded (so that if the app UI is blocked, it can show an approximate progress indicator)
	SYNC_COMPLETE,				//new data synced and loaded into DB
	CLOCK_SKEW_CHANGED,         //clock skew has been measured and updated
}
