package cz.fhejl.pubtran;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.view.View;

@SuppressWarnings("serial")
public class SearchOptions extends AbstractSearchOptions {

	public static final int MODE_FASTEST = 0;
	public static final int MODE_FEWEST_CHANGES = 1;
	public static final int MODE_LEAST_WALKING = 2;

	public static final int SPEED_AVERAGE = 0;
	public static final int SPEED_FAST = 1;
	public static final int SPEED_SLOW = 2;

	private static final String PREF_PREFFERED_MODE = "prefferedMode"; // int
	private static final String PREF_USE_BUS = "bus"; // boolean
	private static final String PREF_USE_TUBE = "tube"; // boolean
	private static final String PREF_USE_DLR = "dlr"; // boolean
	private static final String PREF_USE_RAIL = "rail"; // boolean
	private static final String PREF_USE_TRAM = "tram"; // boolean
	private static final String PREF_USE_RIVER = "river"; // boolean
	private static final String PREF_WALKING_SPEED = "walkingSpeed"; // int

	public boolean useBus;
	public boolean useTube;
	public boolean useDLR;
	public boolean useRail;
	public boolean useTram;
	public boolean useRiver;
	public int prefferedMode;
	public int walkingSpeed;

	private boolean dummy1;

	@Override
	public void loadFromSharedPreferences(SharedPreferences preferences, MainActivity activity) {
		useBus = preferences.getBoolean(PREF_USE_BUS, true);
		useTube = preferences.getBoolean(PREF_USE_TUBE, true);
		useTram = preferences.getBoolean(PREF_USE_TRAM, true);
		useDLR = preferences.getBoolean(PREF_USE_DLR, true);
		useRail = preferences.getBoolean(PREF_USE_RAIL, true);
		useRiver = preferences.getBoolean(PREF_USE_RIVER, true);
		prefferedMode = preferences.getInt(PREF_PREFFERED_MODE, MODE_FASTEST);
		walkingSpeed = preferences.getInt(PREF_WALKING_SPEED, SPEED_AVERAGE);

		if (activity != null) {
			boolean modesOfTransportChanged =
					!(useBus == true && useTube == true && useTram == true && useDLR == true && useRail == true && useRiver == true);
			if (modesOfTransportChanged || prefferedMode != MODE_FASTEST || walkingSpeed != SPEED_AVERAGE) activity.btSearchOptions
					.setImageResource(R.drawable.ic_search_options_changed);
			else activity.btSearchOptions.setImageResource(R.drawable.ic_search_options);
		}
	}

	@Override
	public void onPause(SharedPreferences preferences) {
	}

	@Override
	public void onResume() {
	}

	@Override
	public void reset(MainActivity activity) {
		useBus = true;
		useTube = true;
		useDLR = true;
		useRail = true;
		useTram = true;
		useRiver = true;
		prefferedMode = MODE_FASTEST;
		walkingSpeed = SPEED_AVERAGE;
	}

	public void save(SharedPreferences preferences) {
		Editor editor = preferences.edit();

		editor.putBoolean(PREF_USE_BUS, useBus);
		editor.putBoolean(PREF_USE_TUBE, useTube);
		editor.putBoolean(PREF_USE_DLR, useDLR);
		editor.putBoolean(PREF_USE_RAIL, useRail);
		editor.putBoolean(PREF_USE_TRAM, useTram);
		editor.putBoolean(PREF_USE_RIVER, useRiver);

		editor.putInt(PREF_PREFFERED_MODE, prefferedMode);

		editor.putInt(PREF_WALKING_SPEED, walkingSpeed);

		editor.commit();
	}

	@Override
	public void show(MainActivity activity, View button) {
		activity.startActivityForResult(new Intent(activity, SearchOptionsActivity.class),
				MainActivity.REQUEST_SEARCH_OPTIONS);
	}

}
