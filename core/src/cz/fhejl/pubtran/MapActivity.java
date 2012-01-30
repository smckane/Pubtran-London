package cz.fhejl.pubtran;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import cz.fhejl.pubtran.Areas.Area;
import cz.fhejl.pubtran.MapDownloaderService.Progress;
import cz.fhejl.pubtran.MapDownloaderService.Progress.State;

public class MapActivity extends com.google.android.maps.MapActivity {

	public static final int RESULT_STOP_FROM = RESULT_FIRST_USER + 0;
	public static final int RESULT_STOP_TO = RESULT_FIRST_USER + 1;

	public static boolean isResumed = false;

	private static final int DIALOG_ERROR = 0;
	private static final int MENU_TOGGLE_ZOOM = 0;
	private static final String DEBUG_MAP_KEY = "0Wjw6Dv9VwUXyydeLMGVBwvfRcg6GBnva_fSJpA"; // Debug key
	private static final String RELEASE_MAP_KEY = "0Wjw6Dv9VwUV9sRY_GfA-EMh6vdu25reLWQaWmw"; // Release key

	private boolean zoomVisible;
	private Area area;
	private Button btDownloadMap;
	private Button btZoomIn;
	private Button btZoomOut;
	private GoogleMapView googleMapView;
	private Handler handler = new Handler();
	private LinearLayout llMapContainer;
	private LinearLayout llNoMap;
	private LinearLayout llProgress;
	private MenuItem toggleZoomMenuItem;
	private OfflineMapView offlineMapView;
	private Progress lastProgress = new Progress();
	private ProgressBar progressBar;
	private SharedPreferences preferences;
	private Spinner spWhichMap;
	private String popupTitle;
	private String selectedMapId = "";
	private TextView tvProgress;
	private TextView tvProgressTitle;
	private View popup;

	// -----------------------------------------------------------------------------------------------

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			lastProgress = getProgressExtra(intent);
			if (lastProgress == null) lastProgress = new Progress();

			// If there is an ongoing download and the process gets killed, the last sticky broadcast
			// state is DOWNLOADING which is incorrect - so send new broadcast
			if (lastProgress.getState() != State.DOING_NOTHING && lastProgress.getState() != State.ERROR
					&& !MapDownloaderService.isRunning) {
				sendDefaultBroadcast();
			}

			updateProgress(lastProgress);
		}
	};

	// -----------------------------------------------------------------------------------------------

	private void addGoogleMapView() {
		String key = Common.DEVELOPER_MODE ? DEBUG_MAP_KEY : RELEASE_MAP_KEY;
		googleMapView = new GoogleMapView(this, key, area);
		googleMapView.setZoom(17);

		llMapContainer.addView(googleMapView);
	}

	// -----------------------------------------------------------------------------------------------

	private int getMapIndex(String mapId) {
		for (int i = 0; i < area.maps.length; i++) {
			if (area.maps[i].getId().equals(mapId)) return i;
		}
		return -1;
	}

	// -----------------------------------------------------------------------------------------------

	private Progress getProgressExtra(Intent i) {
		Progress p = null;
		try {
			p = (Progress) i.getSerializableExtra("progress");
		} catch (Exception e) {
			// Can't deserialize progress, probably because of newer version of Progress
		}
		return p;
	}

	// -----------------------------------------------------------------------------------------------

	private Intent getServiceIntent(String mapId, boolean cancel) {
		Intent intent = new Intent(getApplicationContext(), MapDownloaderService.class);
		intent.putExtra("mapId", mapId);
		intent.putExtra("cancel", cancel);
		intent.putExtra("area", area);
		return intent;
	}

	// -----------------------------------------------------------------------------------------------

	public void hidePopup() {
		popup.setVisibility(View.INVISIBLE);
	}

	// -----------------------------------------------------------------------------------------------

	public boolean isPopupVisible() {
		return popup.getVisibility() == View.VISIBLE;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		preferences = PreferenceManager.getDefaultSharedPreferences(this);
		AbstractActivity.handleCustomLocale(preferences, getResources());
		setContentView(R.layout.map);

		// Init fields
		area = (Area) getIntent().getSerializableExtra("area");
		llMapContainer = (LinearLayout) findViewById(R.id.llMapContainer);
		llNoMap = (LinearLayout) findViewById(R.id.llNoMap);
		llProgress = (LinearLayout) findViewById(R.id.llProgress);
		tvProgress = (TextView) findViewById(R.id.tvProgress);
		tvProgressTitle = (TextView) findViewById(R.id.tvProgressTitle);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);

		// Map selector
		spWhichMap = (Spinner) findViewById(R.id.spWhichMap);

		String[] mapNames = new String[area.maps.length];
		for (int i = 0; i < mapNames.length; i++) {
			mapNames[i] = area.maps[i].getName();
		}

		ArrayAdapter<String> adapter =
				new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, mapNames);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spWhichMap.setAdapter(adapter);

		Drawable d = getResources().getDrawable(android.R.drawable.btn_dropdown);
		d.setAlpha(200);
		spWhichMap.setBackgroundDrawable(d);

		selectMapFromIntent(getIntent());

		spWhichMap.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View view, int position, long id) {
				selectMap(area.maps[position].getId());
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});

		// Popup
		popup = findViewById(R.id.popup);
		findViewById(R.id.btSetAsDeparture).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent data = new Intent();
				int resultCode = 0;
				data.putExtra("stop_name", popupTitle);
				resultCode = RESULT_STOP_FROM;
				setResult(resultCode, data);
				finish();
			}
		});
		findViewById(R.id.btSetAsArrival).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent data = new Intent();
				int resultCode = 0;
				data.putExtra("stop_name", popupTitle);
				resultCode = RESULT_STOP_TO;
				setResult(resultCode, data);
				finish();
			}
		});

		// GoogleMapView
		addGoogleMapView();

		// Zoom buttons
		btZoomIn = (Button) findViewById(R.id.btZoomIn);
		btZoomIn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				if (googleMapView != null) googleMapView.zoomIn();
				if (offlineMapView != null) offlineMapView.zoomIn();
			}
		});

		btZoomOut = (Button) findViewById(R.id.btZoomOut);
		btZoomOut.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				if (googleMapView != null) googleMapView.zoomOut();
				if (offlineMapView != null) offlineMapView.zoomOut();
			}
		});

		setZoomVisibility();

		// Download map button
		btDownloadMap = (Button) findViewById(R.id.btDownloadMap);
		btDownloadMap.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				startService(getServiceIntent(selectedMapId, false));
			}
		});

		// Cancel download button
		Button btCancel = (Button) findViewById(R.id.btCancel);
		d = getResources().getDrawable(android.R.drawable.btn_default_small);
		d.setAlpha(210);
		btCancel.setBackgroundDrawable(d);
		btCancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				startService(getServiceIntent(null, true));
			}
		});

		// If the last sticky broadcast is error, don't show error dialog - rewrite the broadcast
		Intent i = registerReceiver(null, new IntentFilter(MapDownloaderService.getBroadcastIntentAction(this)));
		if (i != null && savedInstanceState == null) {
			if (!getIntent().getBooleanExtra("error", false)) {
				Progress p = getProgressExtra(i);
				if (p != null && p.getState() == State.ERROR) {
					sendDefaultBroadcast();
				}
			}
		}

		AbstractActivity.localeWorkaround(preferences, getResources());
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	protected Dialog onCreateDialog(int id) {
		if (id == DIALOG_ERROR) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(getString(R.string.mapErrorTitle)).setMessage(R.string.mapError);
			builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
				}
			});

			return builder.create();
		} else {
			return null;
		}
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		int text = zoomVisible ? R.string.mapHideZoom : R.string.mapShowZoom;
		toggleZoomMenuItem = menu.add(Menu.NONE, MENU_TOGGLE_ZOOM, 0, text);
		toggleZoomMenuItem.setIcon(android.R.drawable.ic_menu_zoom);
		return true;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (offlineMapView != null) offlineMapView.destroy();
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onNewIntent(Intent intent) {
		selectMapFromIntent(intent);
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == MENU_TOGGLE_ZOOM) {
			Editor editor = preferences.edit();
			editor.putBoolean(Common.PREF_MAP_ZOOM_CONTROLS, !zoomVisible);
			editor.commit();
			setZoomVisibility();
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	protected void onPause() {
		super.onPause();
		isResumed = false;
		unregisterReceiver(receiver);
		googleMapView.pause();
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	protected void onResume() {
		super.onResume();
		isResumed = true;
		registerReceiver(receiver, new IntentFilter(MapDownloaderService.getBroadcastIntentAction(this)));
		if (selectedMapId.equals("google")) googleMapView.resume();
	}

	// -----------------------------------------------------------------------------------------------

	private void selectMap(String id) {
		hidePopup();

		selectedMapId = id;
		if (id.equals("google")) {
			llNoMap.setVisibility(View.GONE);

			if (offlineMapView != null) {
				llMapContainer.removeView(offlineMapView);
				offlineMapView.destroy();
				offlineMapView = null;
			}

			googleMapView.setVisibility(View.VISIBLE);
			googleMapView.resume();
		} else {
			googleMapView.pause();
			googleMapView.setVisibility(View.GONE);

			if (offlineMapView == null) {
				offlineMapView = new OfflineMapView(this);
				llMapContainer.addView(offlineMapView);
			}

			if (offlineMapView.setMap(id)) {
				llNoMap.setVisibility(View.GONE);
			} else {
				llNoMap.setVisibility(View.VISIBLE);
				setDownloadButtonEnabled(true);
				if (lastProgress.isNotYetFinished(id)) {
					setDownloadButtonEnabled(false);
				}
			}
		}

	}

	// -----------------------------------------------------------------------------------------------

	private void selectMapFromIntent(Intent intent) {
		String mapId = intent.getStringExtra("mapId");
		if (mapId != null) {
			int index = getMapIndex(mapId);
			if (index != -1) spWhichMap.setSelection(index);
		}
	}

	// -----------------------------------------------------------------------------------------------

	private void sendDefaultBroadcast() {
		Intent i = new Intent(MapDownloaderService.getBroadcastIntentAction(getApplicationContext()));
		i.putExtra("progress", new Progress());
		sendStickyBroadcast(i);
	}

	// -----------------------------------------------------------------------------------------------

	private void setDownloadButtonEnabled(boolean enabled) {
		btDownloadMap.setEnabled(enabled);
		btDownloadMap.setText(enabled ? getString(R.string.downloadMap) : getString(R.string.downloadMapDisabled));
	}

	// -----------------------------------------------------------------------------------------------

	public void setZoomInEnabled(final boolean enabled) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				btZoomIn.setEnabled(enabled);
			}
		});
	}

	// -----------------------------------------------------------------------------------------------

	public void setZoomOutEnabled(final boolean enabled) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				btZoomOut.setEnabled(enabled);
			}
		});
	}

	// -----------------------------------------------------------------------------------------------

	private void setZoomVisibility() {
		LinearLayout llZoom = (LinearLayout) findViewById(R.id.llZoom);
		zoomVisible = preferences.getBoolean(Common.PREF_MAP_ZOOM_CONTROLS, true);
		llZoom.setVisibility(zoomVisible ? View.VISIBLE : View.GONE);
		if (toggleZoomMenuItem != null) {
			toggleZoomMenuItem.setTitle(zoomVisible ? R.string.mapHideZoom : R.string.mapShowZoom);
		}
	}

	// -----------------------------------------------------------------------------------------------

	public void showPopup(String title, Point point) {
		popupTitle = title;
		((TextView) popup.findViewById(R.id.tvStopName)).setText(title);
		updatePopup(point);
		popup.setVisibility(View.VISIBLE);
	}

	// -----------------------------------------------------------------------------------------------

	public void updatePopup(Point point) {
		RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) popup.getLayoutParams();
		int yOffset = 0;
		yOffset = -43;
		params.setMargins(point.x - popup.getWidth() / 2, point.y - popup.getHeight() + yOffset, -1000, -1000);
		popup.setLayoutParams(params);
	}

	// -----------------------------------------------------------------------------------------------

	private void updateProgress(Progress progress) {
		if (progress.getState() == State.STARTING) {
			llProgress.setVisibility(View.VISIBLE);
			tvProgressTitle.setText(progress.getTitle(this, false));
			tvProgress.setText(progress.getDescription(this));
			progressBar.setIndeterminate(true);
		} else if (progress.getState() == State.DOWNLOADING) {
			llProgress.setVisibility(View.VISIBLE);
			tvProgressTitle.setText(progress.getTitle(this, false));
			tvProgress.setText(progress.getDescription(this));
			progressBar.setIndeterminate(false);
			progressBar.setProgress(progress.getPercent());
		} else if (progress.getState() == State.EXTRACTING) {
			llProgress.setVisibility(View.VISIBLE);
			tvProgressTitle.setText(progress.getTitle(this, false));
			tvProgress.setText(progress.getDescription(this));
			progressBar.setIndeterminate(false);
			progressBar.setProgress(progress.getPercent());
		} else if (progress.getState() == State.ERROR) {
			llProgress.setVisibility(View.GONE);
			MapDownloaderService.cancelErrorNotification(this);
			showDialog(DIALOG_ERROR);
			sendDefaultBroadcast();
		} else if (progress.getState() == State.DOING_NOTHING) {
			llProgress.setVisibility(View.GONE);
		}

		progressBar.invalidate();

		if (progress.isNotYetFinished(selectedMapId)) {
			setDownloadButtonEnabled(false);
		} else {
			setDownloadButtonEnabled(true);
			if (llNoMap.getVisibility() == View.VISIBLE && progress.isFinished(selectedMapId)) {
				selectMap(selectedMapId);
			}
		}
	}

}
