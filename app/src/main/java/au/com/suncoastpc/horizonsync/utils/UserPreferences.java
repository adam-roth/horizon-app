package au.com.suncoastpc.horizonsync.utils;

import android.content.Context;
import android.content.SharedPreferences;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class UserPreferences {
	//global preference key prefixes
	public static final String SYNC_TIMESTAMP_KEY = "sync.updateTime";		    //global sync timestamp
	public static final String EVENT_DATA_KEY = "events.allData";               //all stored event data, JSONArray

	//internal key
	private static final String PREFERENCES_CONTEXT = "horizonsync.event.prefs";

	public static Collection<String> getKeysForUserAndPrefix(String user, String prefix) {
		prefix += user + "/";
		Set<String> result = new HashSet<>();
		SharedPreferences preferences = Environment.getApplicationContext().getSharedPreferences(PREFERENCES_CONTEXT, Context.MODE_PRIVATE);
		for (String key : preferences.getAll().keySet()) {
			if (key.startsWith(prefix)) {
				result.add(key);
			}
		}

		return result;
	}

	public static void clearValue(String key) {
		if (StringUtilities.isEmpty(key)) {
			return;
		}

		SharedPreferences preferences = Environment.getApplicationContext().getSharedPreferences(PREFERENCES_CONTEXT, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preferences.edit();
		editor.remove(key);
		//editor.apply();		//async
		editor.commit();		//synchronous
	}

	public static void setValue(String key, JSONArray value) {
		setValue(key, value.toJSONString());
	}

	public static void setValue(String key, JSONObject value) {
		setValue(key, value.toJSONString());
	}

	public static void setValue(String key, long value) {
		setValue(key, Long.toString(value));
	}

	public static void setValue(String key, int value) {
		setValue(key, Integer.toString(value));
	}

	public static void setValue(String key, String value) {
		SharedPreferences preferences = Environment.getApplicationContext().getSharedPreferences(PREFERENCES_CONTEXT, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString(key, value);
		//editor.apply();		//async
		editor.commit();		//synchronous
	}

	public static String getValue(String key) {
		SharedPreferences preferences = Environment.getApplicationContext().getSharedPreferences(PREFERENCES_CONTEXT, Context.MODE_PRIVATE);
		return preferences.getString(key, null);
	}

	public static long getValueAsLong(String key) {
		try {
			return Long.parseLong(getValue(key));
		}
		catch (Exception ignored) {
			//nothing we can do
		}

		return -1;
	}

	public static int getValueAsInt(String key) {
		try {
			return Integer.parseInt(getValue(key));
		}
		catch (Exception ignored) {
			//nothing we can do
		}

		return -1;
	}

	public static JSONObject getValueAsJson(String key) {
		try {
			JSONObject result = (JSONObject) JSONValue.parse(getValue(key));
			return result == null ? new JSONObject() : result;
		}
		catch (Exception ignored) {
			//nothing we can do
		}

		return new JSONObject();
	}

	public static JSONArray getValueAsJsonArray(String key) {
		try {
			JSONArray result = (JSONArray) JSONValue.parse(getValue(key));
			return result == null ? new JSONArray() : result;
		}
		catch (Exception ignored) {
			//nothing we can do
		}

		return new JSONArray();
	}
}
