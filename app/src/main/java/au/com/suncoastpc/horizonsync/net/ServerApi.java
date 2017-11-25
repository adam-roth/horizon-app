package au.com.suncoastpc.horizonsync.net;

import android.util.Log;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import au.com.suncoastpc.horizonsync.utils.Constants;
import au.com.suncoastpc.horizonsync.utils.Environment;
import au.com.suncoastpc.horizonsync.utils.StringUtilities;

public class ServerApi {
	//Hackfest data gateway
	private static final String QA_SERVER = "http://192.168.1.9:8080";                                  //laptop
	private static final String DEV_SERVER = "http://192.168.1.127:8080";                               //desktop
	private static final String PRODUCTION_SERVER = "http://terra.suncoastpc.com.au:8181";              //production

	private static final int CONNECT_TIMEOUT = 1000 * 30;			//30 second connect timeout
	private static final int REQUEST_TIMEOUT = 1000 * 60 * 4;		//4 minutes request timeout

	private static final String NETWORK_DISALLOWED_ON_MAIN_THREAD = "Application Error:  Network requests cannot be performed on the main/UI thread!";
	private static final String NETWORK_UNAVAILABLE = "Network Error:  Internet access is currently unavailable, please try again later.";

	private static final CookieManager COOKIE_MANAGER = new CookieManager();
	static {
		//make sure that we keep any cookies that the server might send (such as JSESSIONID)
		COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
		CookieHandler.setDefault(COOKIE_MANAGER);

	}

	private static long requestId = 0;

	//XXX:  executes synchronously on the calling thread; immediately returns an 'error' response if called from the main thread
	public static JSONObject getUpdates(long updatesTimestamp) {
		JSONObject error = checkNetwork();
		if (error != null) {
			return error;
		}

		//okay to proceed
		try {
			JSONObject latency = ping();
			if (Constants.SUCCESS_STATUS.equals(latency.get("status"))) {
				long clientLatency = latency.getAsNumber("result").longValue();

				//timestamp=0&clientTime=0&clientLatency=0
				String url = getServerAddress() + ApiMethod.GET_UPDATES.getPath() + "?timestamp=" + updatesTimestamp + "&clientLatency=" + clientLatency + "&clientTime=" + System.currentTimeMillis();

				Log.d("net", "Requesting data from the server:  url=" + url );
				JSONObject result = getJsonFromServer(url);
				Log.d("net", "Got response:  " + result);

				return result;
			}

			return errorResponse("Unable to determine client latency!");
		}
		catch (Exception e) {
			//something bad happened
			if (Environment.isDebugMode()) {
				e.printStackTrace();
			}
			return errorResponse("Unexpected network error:  " + e.getMessage());
		}
	}

	//XXX:  executes synchronously on the calling thread; immediately returns an 'error' response if called from the main thread
	private static JSONObject ping() {
		//check for basic validity of the request
		JSONObject error = checkNetwork();
		if (error != null) {
			return error;
		}

		//okay to proceed
		try {
			////format=json&apiKey=e4335a64660e40b1826ab61296bb0a26&params=where=1%3D1&f=pjson&context=Society/Society_SCRC/MapServer/17
			long start = System.currentTimeMillis();
			String url = getServerAddress() + ApiMethod.PING.getPath();
			Log.d("net", "Requesting data from the server:  url=" + url );

			String result = getFromServer(url);
			long stop = System.currentTimeMillis();
			long roundTripTime = stop - start;

			Log.d("net", "Got response:  ping=" + roundTripTime);

			JSONObject response = new JSONObject();
			response.put("status", Constants.SUCCESS_STATUS);
			response.put("result", roundTripTime / 2);

			return response;
		}
		catch (Exception e) {
			//something bad happened
			if (Environment.isDebugMode()) {
				e.printStackTrace();
			}
			return errorResponse("Unexpected network error:  " + e.getMessage());
		}
	}

	//XXX:  executes synchronously on the calling thread; immediately returns an 'error' response if called from the main thread
	public static JSONObject loadData(String context, String queryParams) {
		//check for basic validity of the request
		JSONObject error = checkNetwork();
		if (error != null) {
			return error;
		}
		
		//okay to proceed
		try {
			////format=json&apiKey=e4335a64660e40b1826ab61296bb0a26&params=where=1%3D1&f=pjson&context=Society/Society_SCRC/MapServer/17
			String url = getServerAddress() + ApiMethod.LOAD_DATA.getPath();
			String params = "format=json&apiKey=" + getApiKey()
					+ "&context=" + StringUtilities.encodeUriComponent(context)
					+ "&params=" + StringUtilities.encodeUriComponent(queryParams);

			Log.d("net", "Requesting data from the server:  url=" + url + ", params=" + params);
			JSONObject result = postToServer(url, params);
			Log.d("net", "Got response:  " + result.toJSONString());

			return result;
		}
		catch (Exception e) {
			//something bad happened
			if (Environment.isDebugMode()) {
				e.printStackTrace();
			}
			return errorResponse("Unexpected network error:  " + e.getMessage());
		}
	}

	private static String getApiKey() {
		return null;
	}

	public static JSONObject loadData(ApiContext context, String queryParams) {
		return loadData(context.getName(), queryParams);
	}
	
	//XXX:  always executes asynchronously in a new thread; passes the result back via a callback on the nominated handler
	public static long loadData(final String context, final String queryParams, final ApiResponseDelegate handler) {
		final long result = nextRequestId();
		new Thread() {
			@Override
			public void run() {
				JSONObject response = loadData(context, queryParams);
				handler.handleResponse(result, ApiMethod.LOAD_DATA, response);
			}
		}.start();
		
		return result;		//return this to the caller in case they want to track individual requests
	}

	public static long loadData(final ApiContext context, final String queryParams, final ApiResponseDelegate handler) {
		return loadData(context.getName(), queryParams, handler);
	}

	//XXX:  executes synchronously on the calling thread; immediately returns an 'error' response if called from the main thread
	public static JSONObject loadAggregateData(List<String> contexts, String queryParams) {
		//check for basic validity of the request
		JSONObject error = checkNetwork();
		if (error != null) {
			return error;
		}

		//okay to proceed
		try {
			Collections.sort(contexts);
			JSONArray contextsJson = new JSONArray();
			contextsJson.addAll(contexts);

			JSONObject mergedParams = new JSONObject();
			mergedParams.put("datasets", contextsJson);
			mergedParams.put("args", queryParams);

			//format=json&apiKey=<key>&context=Aggregator&params={"datasets":[<SCC Dataset>,<SCC Dataset>, ...],"args":<query params for ArcGIS>}
			String url = getServerAddress() + ApiMethod.LOAD_DATA.getPath();
			String params = "format=json&apiKey=" + getApiKey()
					+ "&context=" + StringUtilities.encodeUriComponent("Aggregator")
					+ "&params=" + StringUtilities.encodeUriComponent(mergedParams.toJSONString());

			Log.d("net", "Requesting data from the server:  " + params);
			return postToServer(url, params);
		}
		catch (Exception e) {
			//something bad happened
			if (Environment.isDebugMode()) {
				e.printStackTrace();
			}
			return errorResponse("Unexpected network error:  " + e.getMessage());
		}
	}

	public static JSONObject loadAggregateData(Collection<ApiContext> contexts, String queryParams) {
		List<String> strings = new ArrayList<>();
		for (ApiContext context : contexts) {
			strings.add(context.getName());
		}

		return loadAggregateData(strings, queryParams);
	}

	//XXX:  always executes asynchronously in a new thread; passes the result back via a callback on the nominated handler
	public static long loadAggregateData(final List<String> contexts, final String queryParams, final ApiResponseDelegate handler) {
		final long result = nextRequestId();
		new Thread() {
			@Override
			public void run() {
				JSONObject response = loadAggregateData(contexts, queryParams);
				handler.handleResponse(result, ApiMethod.LOAD_DATA, response);
			}
		}.start();

		return result;		//return this to the caller in case they want to track individual requests
	}

	public static long loadAggregateData(final Collection<ApiContext> contexts, final String queryParams, final ApiResponseDelegate handler) {
		List<String> strings = new ArrayList<>();
		for (ApiContext context : contexts) {
			strings.add(context.getName());
		}

		return loadAggregateData(strings, queryParams, handler);
	}

	//utils
	public static String getServerAddress() {
		if (Constants.USE_DEV) {
			return DEV_SERVER;
		}
		if (Constants.USE_QA) {
			return QA_SERVER;
		}
		return PRODUCTION_SERVER;
	}

	public static String getServerHostName() {
		return getServerAddress().split("\\:\\/\\/")[1].split("\\:")[0];
	}
	
	public static int getServerPort() {
		if (Constants.USE_DEV) {
			//for dev servers we expect the port to be explicitly specified as part of the server address
			String nameAndPort = getServerAddress().split("\\:\\/\\/")[1];
			if (nameAndPort.contains(":")) {
				return Integer.parseInt(nameAndPort.split("\\:")[1]);
			}
		}
		
		//for QA and production, we use the default port associated with the protocol
		return getServerAddress().startsWith("https") ? 443 : 80;
	}

	private static JSONObject postToServer(String url, String params) throws Exception {
		return postToServer(url, params, null);
	}

	private static JSONObject postToServer(String url, String params, Map<String, String> extraHeaders) throws Exception {
		InputStream in = null;
		HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
		try {
			byte[] paramBytes = params.getBytes();

			//send as a POST request as the parameter length may be quite long
			configurePostConnection(connection);

			connection.setDoOutput( true );
			connection.setRequestMethod( "POST" );
			connection.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded");
			connection.setRequestProperty( "charset", "utf-8");
			connection.setRequestProperty( "Content-Length", Integer.toString( paramBytes.length ));
			if (extraHeaders != null) {
				for (String key : extraHeaders.keySet()) {
					connection.setRequestProperty(key, extraHeaders.get(key));
				}
			}

			//write the params to the request body
			try( DataOutputStream wr = new DataOutputStream(connection.getOutputStream()) ) {
				wr.write(paramBytes);
			}

			//read response
			in = connection.getInputStream();
			return (JSONObject) JSONValue.parse(in);
		}
		finally {
			connection.disconnect();
			if (in != null) {
				in.close();
			}
		}
	}

	private static JSONObject getJsonFromServer(String url) throws Exception {
		return getJsonFromServer(url, null);
	}

	private static JSONObject getJsonFromServer(String url, Map<String, String> extraHeaders) throws Exception {
		InputStream in = null;
		HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
		try {
			//send as a GET request as the parameter length may be quite long
			configureGetConnection(connection);
			connection.setRequestMethod( "GET" );
			if (extraHeaders != null) {
				for (String key : extraHeaders.keySet()) {
					connection.setRequestProperty(key, extraHeaders.get(key));
				}
			}

			//read response
			in = connection.getInputStream();
			return (JSONObject) JSONValue.parse(in);
		}
		finally {
			connection.disconnect();
			if (in != null) {
				in.close();
			}
		}
	}

	private static String getFromServer(String url) throws Exception {
		InputStream in = null;
		HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
		try {
			//send as a GET request as the parameter length may be quite long
			configureGetConnection(connection);
			connection.setRequestMethod( "GET" );

			//read response
			in = connection.getInputStream();

			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			StringBuilder total = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				total.append(line).append('\n');
			}

			return total.toString();
		}
		finally {
			connection.disconnect();
			if (in != null) {
				in.close();
			}
		}
	}
	
	private static void configurePostConnection(HttpURLConnection connection) {
		connection.setConnectTimeout(CONNECT_TIMEOUT);
		connection.setReadTimeout(REQUEST_TIMEOUT);
		connection.setUseCaches(false);
	}

	private static void configureGetConnection(HttpURLConnection connection) {
		connection.setConnectTimeout(CONNECT_TIMEOUT);
		connection.setReadTimeout(REQUEST_TIMEOUT);
		connection.setUseCaches(true);
	}
	
	private static JSONObject errorResponse(String message) {
		JSONObject result = new JSONObject();
		result.put("status", Constants.ERROR_STATUS);
		result.put("message", message);
		
		return result;
	}
	
	private static synchronized long nextRequestId() {
		requestId++;
		return requestId;
	}
	
	private static JSONArray collectionToJson(Collection<Long> ids) {
		JSONArray result = new JSONArray();
		
		if (ids != null) {
			for (long id : ids) {
				result.add(id);
			}
		}
		
		return result;
	}
	
	private static JSONObject checkNetwork() {
		if (Environment.isMainThread()) {
			return errorResponse(NETWORK_DISALLOWED_ON_MAIN_THREAD);
		}
		if (! Environment.isNetworkAvailable()) {
			return errorResponse(NETWORK_UNAVAILABLE);
		}
		
		//XXX:  can't perform this check here, as it may take several seconds to complete if the server is unresponsive
		//if (! Environment.isServerAvailable()) {
		//	return errorResponse(SERVER_UNAVAILABLE);
		//}

		return null;
	}
}
