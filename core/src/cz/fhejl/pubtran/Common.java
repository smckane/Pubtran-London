package cz.fhejl.pubtran;

import android.util.Log;

public class Common {

	public static final boolean DEVELOPER_MODE = true;

	public static final String PREF_AREA = "area"; // String
	public static final String PREF_GPS_ENABLED = "enableGps"; // boolean
	public static final String PREF_LANGUAGE = "language"; // String
	public static final String PREF_MAP_ID = "mapId"; // String
	public static final String PREF_MAP_ZOOM_CONTROLS = "mapZoomControls"; // boolean
	public static final String PREF_STATUS_LAST_UPDATE = "statusLastUpdate"; // long
	public static final String PREF_WATCHED_JOURNEY = "watchedJourneyHash"; // int
	public static final String TAG = "Pubtran";

	public static void logd(String message) {
		Log.d(TAG, message);
	}

	public static void logd(int message) {
		Log.d(TAG, "" + message);
	}

	public static void logd(long message) {
		Log.d(TAG, "" + message);
	}

	public static void loge(String message) {
		Log.e(TAG, message);
	}

}
