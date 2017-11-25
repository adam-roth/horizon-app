package au.com.suncoastpc.horizonsync.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class Constants {
	public static final boolean USE_DEV = false;
	public static final boolean USE_QA = false;

	public static final int FORCE_ZONE = -1;

	public static final String SUCCESS_STATUS = "success";
	public static final String ERROR_STATUS = "error";

	public static final int REQUEST_LOCATION_PERMISSIONS = 1700;
	
	public static DateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");
	public static DateFormat DEFAULT_DATE_FORMAT_WITH_TIME = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss a");
}
