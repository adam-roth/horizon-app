package au.com.suncoastpc.horizonsync.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import au.com.suncoastpc.horizonsync.net.ServerApi;
import au.com.suncoastpc.horizonsync.sync.BackgroundSyncManager;

public class Environment {
	private static final int SOCKET_CONNECT_TIMEOUT = 5000;

	public static final String NO_EMAIL_AVAILABLE = "noemail@nodomain.tld";
	
	private static Context appContext;														//the Android application context

	private static double lastKnownLatitude = Double.MAX_VALUE;
	private static double lastKnownLongitude = Double.MAX_VALUE;
	private static long lastGeolocationTimestamp = -1;

	//XXX:  should we use a 'Service' for these (instead or additionally)?
	private static final BackgroundSyncManager syncManager = new BackgroundSyncManager();				//handles background content sync
	
	public static void startBackgroundSync() {
		syncManager.startBackgroundSync();
		if (! syncManager.isSyncEnabled()) {
			syncManager.enableSync();
		}
	}

	public static void disableSync() {
		syncManager.disableSync();
	}

	public static void enableSync() {
		syncManager.enableSync();
	}

	public static boolean isSyncEnabled() {
		return syncManager.isSyncEnabled();
	}

	public static synchronized void updateGeolocation(double lat, double lng) {
		updateGeolocation(lat, lng, new Date().getTime());
	}

	public static synchronized void updateGeolocation(double lat, double lng, long timestamp) {
		lastKnownLatitude = lat;
		lastKnownLongitude = lng;
		lastGeolocationTimestamp = timestamp;
	}

	public static File getPhotosDirectory() {
		File path = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES);
		if (path.exists() || path.mkdirs()) {
			return path;
		}

		return null;
	}

	public static File getPrivateFilesDirectory() {
		if (getApplicationContext() == null) {
			return null;
		}

		return getApplicationContext().getFilesDir();
	}

	public static File getFileForUrl(String url) {
		if (StringUtilities.isEmpty(url)) {
			return null;
		}

		String[] parts = url.split("\\/");
		String filename = parts[parts.length - 1];
		filename.replaceAll("[^a-zA-Z0-9\\.]", "_");

		return new File(getPrivateFilesDirectory(), filename + "_" + url.hashCode());
	}

	public static String randomId() {
		return UUID.randomUUID().toString();
	}
	
	public static Context getApplicationContext() {
		return appContext;
	}
	
	public static void setApplicationContext(Context context) {
		if (appContext == null) {
			appContext = context;
		}
	}
	
	public static boolean isDebugMode() {
		return Constants.USE_DEV;
	}
	
	public static boolean isMainThread() {
		return Looper.getMainLooper().getThread() == Thread.currentThread();
	}
	
	public static boolean isNetworkAvailable() {
		if (appContext == null) {
			//can't actually check until we have a valid application context
			return false;
		}
		
		ConnectivityManager connectivityManager = (ConnectivityManager)appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
  
		return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}
	
	public static boolean isServerAvailable() {
		try (Socket socket = new Socket()) {
	        socket.connect(new InetSocketAddress(ServerApi.getServerHostName(), ServerApi.getServerPort()), SOCKET_CONNECT_TIMEOUT);
	        return true;
	    } catch (IOException e) {
	        return false; // Either timeout or unreachable or failed DNS lookup.
	    }
	}
	
	public static boolean runOnMainThread(Runnable runnable) {
		if (runnable == null) {
			return false;
		}
		
		Handler mainHandler = new Handler(Looper.getMainLooper());
		return mainHandler.post(runnable);
	}

	public static String formatDate(long timestamp) {
		return formatDate(new Date(timestamp));
	}

	public static String formatDateUtc(long timestamp) {
		return formatDate(utcDateToLocalDate(new Date(timestamp)));
	}

	public static String formatDate(Date date) {
		Context context = getApplicationContext();
		if (context == null) {
			//no system format available, use the hardcoded default
			return Constants.DEFAULT_DATE_FORMAT.format(date);
		}

		//use the system format
		return android.text.format.DateFormat.getDateFormat(context).format(date);
	}

	public static String formatDateWithTime(long timestamp) {
		return formatDateWithTime(new Date(timestamp));
	}

	public static String formatDateWithTimeUtc(long timestamp) {
		return formatDateWithTime(utcDateToLocalDate(new Date(timestamp)));
	}

	public static String formatDateWithTime(Date date) {
		Context context = getApplicationContext();
		if (context == null) {
			//no system format available, use the hardcoded default
			return Constants.DEFAULT_DATE_FORMAT_WITH_TIME.format(date);
		}

		//use the system format
		return android.text.format.DateFormat.getDateFormat(context).format(date) + " " + android.text.format.DateFormat.getTimeFormat(context).format(date);
	}

	public static boolean isAndroidEmulator() {
		return Build.FINGERPRINT.startsWith("generic")
				|| Build.FINGERPRINT.startsWith("unknown")
				|| Build.MODEL.contains("google_sdk")
				|| Build.MODEL.contains("Emulator")
				|| Build.MODEL.contains("Android SDK built for x86")
				|| Build.MANUFACTURER.contains("Genymotion")
				|| (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
				|| "google_sdk".equals(Build.PRODUCT);
	}

	private static Date utcDateToLocalDate(Date utcDate) {
		TimeZone localTimezone = TimeZone.getDefault();
		int offsetMillis = localTimezone.getOffset(utcDate.getTime());

		return new Date(utcDate.getTime() - offsetMillis);
	}
}
