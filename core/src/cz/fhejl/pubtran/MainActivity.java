package cz.fhejl.pubtran;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.TimePicker;
import android.widget.Toast;
import cz.fhejl.pubtran.AbstractProvider.InputEmptyException;
import cz.fhejl.pubtran.AbstractProvider.JourneyFinderException;
import cz.fhejl.pubtran.AbstractProvider.JourneyNotFoundException;
import cz.fhejl.pubtran.AbstractProvider.ParseException;
import cz.fhejl.pubtran.AbstractProvider.StopAmbiguousException;
import cz.fhejl.pubtran.AbstractProvider.UnknownStopException;
import cz.fhejl.pubtran.Areas.Area;
import cz.fhejl.pubtran.Favourites.Favourite;
import cz.fhejl.pubtran.LocationTool.BestLocationListener;

public class MainActivity extends AbstractActivity implements OnTimeSetListener, OnDateSetListener,
		BestLocationListener {

	public static final int REQUEST_PREFERENCES = 0;
	public static final int REQUEST_MAP = 1;
	public static final int REQUEST_SEARCH_OPTIONS = 2;
	public static final int REQUEST_FAVOURITES = 3;

	private static final int DIALOG_DATE = 0;
	private static final int DIALOG_TIME = 1;
	private static final int DIALOG_FINDING_JOURNEY = 2;
	private static final int MAX_AREA_LOCATION_AGE = 2 * 60 * 60 * 1000;
	private static final int MAX_DROPDOWN_ITEMS = 7;
	private static final int RESET_INTERVAL = 10 * 60 * 1000;
	private static final int FROM = 0;
	private static final int TO = 1;

	protected boolean depArrSwitchEnabled = false;
	protected boolean languageSelectionEnabled = false;
	protected ImageButton btSearchOptions;

	private boolean bonusToRanksJustUpdated = false;
	private boolean timeIsDeparture = true;
	private boolean timeManuallyChanged = false;
	private long lastOnPause = -1;
	private AbstractSearchOptions searchOptions;
	private Area area;
	private Areas areas;
	private AreasAdapter areasAdapter = new AreasAdapter();
	private Button btArrowFrom;
	private Button btArrowTo;
	private Button btDate;
	private Button btTime;
	private Calendar calendar;
	private DatePickerDialog datePickerDialog;
	private Drawable arrow;
	private Drawable deleteInput;
	private ImageView ivSwitch;
	private Integer mapMenuItemPosition;
	private LinearLayout llMenu;
	private Location location;
	private LocationTool locationTool;
	private MyAutoComplete acFrom;
	private MyAutoComplete acTo;
	private Stops stops;
	private String fromHint = "";
	private String toHint = "";
	private TextView tvArea;
	private TextView tvSwitch;
	private TimePickerDialog timePickerDialog;
	private View btSwap;

	// -----------------------------------------------------------------------------------------------

	private BroadcastReceiver timeTickReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			boolean timeOrDateDialogOpen =
					(timePickerDialog != null && timePickerDialog.isShowing())
							|| (datePickerDialog != null && datePickerDialog.isShowing());
			if (!timeManuallyChanged && !timeOrDateDialogOpen) {
				// update time if it is behind actual time
				if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
					calendar = Calendar.getInstance();
					updateTimeDateViews();
				}
			}
		}
	};

	// -----------------------------------------------------------------------------------------------

	protected void addFavouritesMenuItem() {
		addMenuItem(R.drawable.ic_star, new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent intent = new Intent(getApplicationContext(), FavouritesActivity.class);
				startActivityForResult(intent, REQUEST_FAVOURITES);
			}
		});
	}

	// -----------------------------------------------------------------------------------------------

	protected void addMapMenuItem() {
		mapMenuItemPosition = llMenu.getChildCount();
		addMenuItem(R.drawable.ic_map, new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR_MR1) {
					Intent intent = new Intent(getBaseContext(), MapActivity.class);
					intent.putExtra("area", area);
					startActivityForResult(intent, REQUEST_MAP);
				} else {
					Toast.makeText(getApplicationContext(), R.string.mapUnavailable, Toast.LENGTH_LONG).show();
				}
			}
		});
	}

	// -----------------------------------------------------------------------------------------------

	protected void addMenuItem(int icon, OnClickListener listener) {
		LinearLayout llMenuItem = (LinearLayout) getLayoutInflater().inflate(R.layout.menu_item, null);
		llMenuItem.setOnClickListener(listener);
		((ImageView) llMenuItem.findViewById(R.id.icon)).setImageResource(icon);

		LinearLayout.LayoutParams params =
				new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 1);

		llMenu.addView(llMenuItem, params);
	}

	// -----------------------------------------------------------------------------------------------

	private void autofill() {
		int stopId = stops.listFrom.get(0);
		double rank =
				stops.ranksFrom[stopId]
						+ stops.getDistanceRank(location, stops.latitudes[stopId], stops.longitudes[stopId]);
		if (rank < 17) fromHint = "";
		else fromHint = stops.names[stops.listFrom.get(0)];
		acFrom.setHint(fromHint);
		if (acFrom.getText().toString().equals("")) {
			if (fromHint.equals("")) {
				toHint = "";
				acTo.setHint(toHint);
			} else {
				fromEntered(); // this can also set toHint
			}
		}
	}

	// -----------------------------------------------------------------------------------------------

	private void findJourney() {
		String from = acFrom.getText().toString();
		if (from.equals("")) from = fromHint;

		String to = acTo.getText().toString();
		if (to.equals("")) to = toHint;

		findJourney(from, to.toString(), area.id);
	}

	// -----------------------------------------------------------------------------------------------

	private void findJourney(String from, String to, String areaId) {
		searchOptions.setBasicOptions(from, to, calendar, timeIsDeparture, areaId);
		startTask(new FindJourneyTask(searchOptions, getProvider(), stops, location));
	}

	// -----------------------------------------------------------------------------------------------

	private void fromEntered() {
		String from = acFrom.getText().toString();
		if (from.equals("")) from = fromHint;
		if (from.equals("")) return;

		DatabaseAdapter dbAdapter = new DatabaseAdapter(this).openAndLock();
		dbAdapter.updateToRanks(from, stops);
		dbAdapter.closeAndUnlock();
		stops.sortTo();
		bonusToRanksJustUpdated = true;

		int stopId = stops.listTo.get(0);
		double rank = stops.baseRanksTo[stopId] + stops.bonusRanksTo[stopId];
		if (rank > 100) toHint = stops.names[stops.listTo.get(0)];
		else toHint = "";
		acTo.setHint(toHint);
	}

	// -----------------------------------------------------------------------------------------------

	public Area getArea() {
		return area;
	}

	// -----------------------------------------------------------------------------------------------

	protected AbstractSearchOptions getSearchOptions(SharedPreferences preferences) {
		return null;
	}

	// -----------------------------------------------------------------------------------------------

	protected AbstractProvider getProvider() {
		return null;
	}

	// -----------------------------------------------------------------------------------------------

	private void hideOrShowSwap() {
		if (acFrom.getText().toString().equals("") && acTo.getText().toString().equals("")) {
			btSwap.setVisibility(View.INVISIBLE);
		} else {
			btSwap.setVisibility(View.VISIBLE);
		}
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_MAP) {
			if (resultCode == MapActivity.RESULT_STOP_FROM) {
				acFrom.setTextNoDropdown(data.getStringExtra("stop_name"));
			} else if (resultCode == MapActivity.RESULT_STOP_TO) {
				acTo.setTextNoDropdown(data.getStringExtra("stop_name"));
			}
		} else if (requestCode == REQUEST_SEARCH_OPTIONS) {
			searchOptions.loadFromSharedPreferences(preferences, this);
		} else if (requestCode == REQUEST_FAVOURITES && resultCode == RESULT_OK) {
			Favourite favourite = (Favourite) data.getSerializableExtra("favourite");

			String from = favourite.getDeparture();
			String to = favourite.getArrival();
			if (data.getBooleanExtra("returnJourney", false)) {
				from = favourite.getArrival();
				to = favourite.getDeparture();
			}

			if (area.className.equals(favourite.getAreaClass())) {
				acFrom.setTextNoDropdown(from);
				acTo.setTextNoDropdown(to);
			}

			findJourney(from, to, Areas.getInstance(this).findByClass(favourite.getAreaClass()).id);
		}
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onBestLocationUpdated(Location newLocation) {
		Location prevLocation = location;
		location = newLocation;

		if (!locationTool.differsOnlyInTime(prevLocation, newLocation)) {
			stops.sortFrom(newLocation);
			autofill();
			setNearestArea();
		}
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onCreate(Bundle savedInstanceState) {
		strictMode();

		super.onCreate(savedInstanceState);

		if (!preferences.contains(Common.PREF_LANGUAGE)) {
			if (languageSelectionEnabled && Locale.getDefault().getLanguage().equals("en")) {
				startActivity(new Intent(this, LanguageActivity.class));
				finish();
			} else {
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
				Editor editor = prefs.edit();
				editor.putString(Common.PREF_LANGUAGE, "");
				editor.commit();
			}
		}

		setContentView(R.layout.main);

		areas = Areas.getInstance(this);
		setUpArea();
		stops = Stops.getInstance(this, area.className);
		calendar = Calendar.getInstance();
		locationTool = new LocationTool(this, this);

		arrow = getResources().getDrawable(R.drawable.ic_show_dropdown);
		deleteInput = getResources().getDrawable(R.drawable.delete_input);

		// Area switcher
		View llAreaSwitcher = findViewById(R.id.llAreaSwitcher);
		if (areas.size() == 1) llAreaSwitcher.setVisibility(View.GONE);
		llAreaSwitcher.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showAreas();
			}
		});
		tvArea = (TextView) findViewById(R.id.tvArea);
		tvArea.setText(area.name);

		// set up "From"
		acFrom = (MyAutoComplete) findViewById(R.id.acFrom);
		acFrom.activity = this;
		acFrom.setAdapter(new StopsAdapter(FROM));
		acFrom.setOnEditorActionListener(new OnEditorActionListener() {

			// this method just returns false, without this actionNext
			// doeasn't work for some reason
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				return false;
			}
		});
		acFrom.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					if (SystemClock.uptimeMillis() - acFrom.lastDismissTime > 80) {
						acFrom.setText(acFrom.getText());
						acFrom.showDropDown();
					}
				}
				return false;
			}
		});
		acFrom.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				fromEntered();
				acFrom.setSelection(0);
				InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(acFrom.getWindowToken(), 0);
			}
		});
		acFrom.addTextChangedListener(new TextWatcher() {

			@Override
			public void afterTextChanged(Editable s) {
				hideOrShowSwap();
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (before == 0 && count == 0) {
					// do nothing
				} else if (!s.toString().equals("")) {
					if (bonusToRanksJustUpdated) {
						bonusToRanksJustUpdated = false;
						Arrays.fill(stops.bonusRanksTo, 0);
						stops.sortTo();
					}
					toHint = "";
					acTo.setHint(toHint);
				} else if (s.toString().equals("") && before > 0) {
					if (bonusToRanksJustUpdated) {
						bonusToRanksJustUpdated = false;
						Arrays.fill(stops.bonusRanksTo, 0);
						stops.sortTo();
					}

					if (fromHint.equals("")) {
						toHint = "";
						acTo.setHint(toHint);
					} else fromEntered();
				}

				if (s.length() == 0) btArrowFrom.setBackgroundDrawable(arrow);
				else btArrowFrom.setBackgroundDrawable(deleteInput);
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
		});

		// swap button
		btSwap = findViewById(R.id.btSwap);
		btSwap.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String from = acFrom.getText().toString();
				String to = acTo.getText().toString();

				if (!from.equals("") || !to.equals("")) {
					if (from.equals("")) from = fromHint;
					if (to.equals("")) to = toHint;
					acFrom.setTextNoDropdown(to);
					acTo.setTextNoDropdown(from);
					fromEntered();
				}
			}
		});

		// set up "To"
		acTo = (MyAutoComplete) findViewById(R.id.acTo);
		acTo.activity = this;
		acTo.setAdapter(new StopsAdapter(TO));
		acTo.setOnEditorActionListener(new OnEditorActionListener() {

			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(acTo.getWindowToken(), 0);
				findJourney();
				return true;
			}
		});
		acTo.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					if (SystemClock.uptimeMillis() - acTo.lastDismissTime > 80) {
						acTo.setText(acTo.getText());
						acTo.showDropDown();
					}
				}
				return false;
			}
		});
		acTo.addTextChangedListener(new TextWatcher() {

			@Override
			public void afterTextChanged(Editable s) {
				hideOrShowSwap();
				if (s.length() == 0) btArrowTo.setBackgroundDrawable(arrow);
				else btArrowTo.setBackgroundDrawable(deleteInput);
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
		});
		acTo.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				acTo.setSelection(0);
				InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(acTo.getWindowToken(), 0);
			}
		});

		// set up down arrow inside "From" edittext
		btArrowFrom = (Button) findViewById(R.id.btArrowFrom);
		btArrowFrom.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					if (SystemClock.uptimeMillis() - acFrom.lastDismissTime > 80
							|| !acFrom.getText().toString().equals("")) {
						acFrom.requestFocus();
						if (!acFrom.getText().toString().equals("")) {
							InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
							imm.showSoftInput(acFrom, 0);
						}
						acFrom.setText("");
					}
				}
				return false;
			}
		});

		// set up down arrow inside "To" edittext
		btArrowTo = (Button) findViewById(R.id.btArrowTo);
		btArrowTo.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					if (SystemClock.uptimeMillis() - acTo.lastDismissTime > 80
							|| !acTo.getText().toString().equals("")) {
						boolean forceAboveCopy = acTo.mPopup.forceDropDownAbove;
						acTo.mPopup.forceDropDownAbove = false;
						acTo.requestFocus();
						if (!acTo.getText().toString().equals("")) {
							InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
							imm.showSoftInput(acTo, 0);
						}
						acTo.setText("");
						acTo.mPopup.forceDropDownAbove = forceAboveCopy;
					}
				}
				return false;
			}
		});

		// determine if dropdown should be above or below "To" textfield
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		if (metrics.heightPixels <= 480) acTo.mPopup.forceDropDownAbove = true;
		else acTo.mPopup.forceDropDownBelow = true;

		// set up departure/arrival time switch
		tvSwitch = (TextView) findViewById(R.id.tvSwitch);
		ivSwitch = (ImageView) findViewById(R.id.ivSwitch);
		if (depArrSwitchEnabled) {
			tvSwitch.setText(R.string.departAt);
			ivSwitch.setVisibility(View.VISIBLE);
			LinearLayout llSwitch = (LinearLayout) findViewById(R.id.llSwitch);
			llSwitch.setOnTouchListener(new OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					if (event.getAction() == MotionEvent.ACTION_DOWN) switchDepArr();
					return true;
				}
			});
			if (savedInstanceState != null && !savedInstanceState.getBoolean("timeIsDeparture")) switchDepArr();
		} else {
			tvSwitch.setPadding(tvSwitch.getPaddingLeft(), 0, 0, 0);
		}

		// set up time
		btTime = (Button) findViewById(R.id.btTime);
		btTime.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				showDialog(DIALOG_TIME);
			}
		});

		// set up date
		btDate = (Button) findViewById(R.id.btDate);
		btDate.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				showDialog(DIALOG_DATE);
			}
		});

		// Search options
		searchOptions = getSearchOptions(preferences);
		btSearchOptions = (ImageButton) findViewById(R.id.btSearchOptions);
		btSearchOptions.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				searchOptions.show(MainActivity.this, btSearchOptions);
			}
		});
		if (searchOptions != null) {
			btSearchOptions.setVisibility(View.VISIBLE);
			searchOptions.loadFromSharedPreferences(preferences, this);
		}

		// Set up "Find Journey" button
		findViewById(R.id.llFindJourney).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				findJourney();
			}
		});

		// Create menu
		llMenu = (LinearLayout) findViewById(R.id.llMenu);
		onCreateMenu();
		updateMapMenuItem();
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_DATE:
			datePickerDialog =
					new DatePickerDialog(this, this, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
							calendar.get(Calendar.DAY_OF_MONTH));
			return datePickerDialog;
		case DIALOG_TIME:
			timePickerDialog =
					new TimePickerDialog(this, this, calendar.get(Calendar.HOUR_OF_DAY),
							calendar.get(Calendar.MINUTE), true);
			return timePickerDialog;
		case DIALOG_FINDING_JOURNEY:
			ProgressDialog progressDialog = new ProgressDialog(this);
			progressDialog.setIndeterminate(true);
			progressDialog.setMessage(getString(R.string.findingJourney));
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					cancelTasksOfThisClass(FindJourneyTask.class);
					try {
						removeDialog(DIALOG_FINDING_JOURNEY);
					} catch (IllegalArgumentException e) {
					}
				}
			});
			return progressDialog;
		}
		throw new AssertionError();
	}

	// -----------------------------------------------------------------------------------------------

	protected void onCreateMenu() {

	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
		timeManuallyChanged = true;
		calendar.set(Calendar.YEAR, year);
		calendar.set(Calendar.MONTH, monthOfYear);
		calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
		updateTimeDateViews();
	}

	// -----------------------------------------------------------------------------------------------

	protected void handleUnknownJourneyFinderException(JourneyFinderException e) {
		e.printStackTrace();
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onPause() {
		super.onPause();
		searchOptions.onPause(preferences);
		locationTool.onPause();
		unregisterReceiver(timeTickReceiver);
		lastOnPause = SystemClock.elapsedRealtime();
	}

	// -----------------------------------------------------------------------------------------------

	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		// super.onRestoreInstanceState() calls setText() on the text fields. This opens the 
		// autocomplete dropdown. To prevent that, I temporalily set threshold to 1000 characters
		acFrom.setThreshold(1000);
		acTo.setThreshold(1000);
		super.onRestoreInstanceState(savedInstanceState);
		acFrom.setThreshold(0);
		acTo.setThreshold(0);

		calendar = (Calendar) savedInstanceState.getSerializable("calendar");

		fromHint = savedInstanceState.getString("fromHint");
		toHint = savedInstanceState.getString("toHint");
		if (depArrSwitchEnabled) {
			if (!savedInstanceState.getBoolean("timeIsDeparture")) switchDepArr();
		}

		lastOnPause = savedInstanceState.getLong("lastOnPause");
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onResume() {
		super.onResume();

		searchOptions.onResume();

		location = null;
		locationTool.onResume();

		setNearestArea();

		// Update time if it is behind current time
		if (calendar.getTimeInMillis() < System.currentTimeMillis()) calendar = Calendar.getInstance();

		// If last onPause was more that RESET_INTERVAL millis ago, reset time and stops
		if (lastOnPause != -1 && (SystemClock.elapsedRealtime() - lastOnPause) > RESET_INTERVAL) {
			calendar = Calendar.getInstance();
			acFrom.setTextNoDropdown("");
			acTo.setTextNoDropdown("");
		}

		updateTimeDateViews();

		// Sort stops
		stops.sortFrom(location);
		stops.sortTo();

		// Set fromHint and toHint
		autofill();

		// Set up updates every minute (for updating location info and time)
		timeManuallyChanged = false;
		registerReceiver(timeTickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

		if (getTasksByClass(FindJourneyTask.class).size() == 0) {
			try {
				dismissDialog(DIALOG_FINDING_JOURNEY);
			} catch (Exception e) {
				// The dialog is not opened
			}
		}
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
		timeManuallyChanged = true;
		calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
		calendar.set(Calendar.MINUTE, minute);
		updateTimeDateViews();
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onSaveInstanceState(Bundle bundle) {
		super.onSaveInstanceState(bundle);
		bundle.putSerializable("calendar", calendar);
		bundle.putString("fromHint", fromHint);
		bundle.putString("toHint", toHint);
		bundle.putBoolean("timeIsDeparture", timeIsDeparture);
		// SystemClock.elapsedRealtime() is used instead of lastOnPause, because onSaveInstanceState(...)
		// can be called before onPause()
		bundle.putLong("lastOnPause", SystemClock.elapsedRealtime());
	}

	// -----------------------------------------------------------------------------------------------

	private void setNearestArea() {
		Location locLocation;
		if (location == null) {
			Location lastNetworkLocation = locationTool.getLastKnownLocation();
			if (lastNetworkLocation != null
					&& (System.currentTimeMillis() - lastNetworkLocation.getTime()) < MAX_AREA_LOCATION_AGE) locLocation =
					lastNetworkLocation;
			else return;
		} else locLocation = location;

		int nearestAreaId = -1;
		double minDistance = 0;
		for (int i = 3; i < areas.size(); i++) {
			double d1 = (locLocation.getLatitude() - areas.get(i).latitude) * 111308.5;
			double d2 = (locLocation.getLongitude() - areas.get(i).longitude) * 71556.2;
			double meters = (Math.sqrt(d1 * d1 + d2 * d2));
			double distance = meters - areas.get(i).radius;
			if (distance < minDistance) {
				nearestAreaId = i;
				minDistance = distance;
			}
		}
		areasAdapter.nearestAreaId = nearestAreaId;
		areasAdapter.notifyDataSetChanged();
	}

	// -----------------------------------------------------------------------------------------------

	private void setUpArea() {
		String area = preferences.getString(Common.PREF_AREA, "pid");
		for (int i = 0; i < areas.size(); i++) {
			if (area.equals(areas.get(i).id)) {
				this.area = areas.get(i);
				return;
			}
		}
		for (int i = 0; i < areas.size(); i++) {
			if (area.equals(areas.get(i).className)) {
				this.area = areas.get(i);
				return;
			}
		}
		this.area = areas.get(0);
	}

	// -----------------------------------------------------------------------------------------------

	public void showAreas() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setAdapter(areasAdapter, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int position) {
				String prevAreaClass = area.className;
				int areaId = position;
				if (areasAdapter.nearestAreaId != -1) {
					if (position == 0) areaId = areasAdapter.nearestAreaId;
					else areaId = position - 1;
				}
				tvArea.setText(areas.get(areaId).name);
				area = areas.get(areaId);
				updateMapMenuItem();

				if (!prevAreaClass.equals("CR1") || !areas.get(areaId).className.equals("CR1")) {
					stops = Stops.getInstance(getApplicationContext(), areas.get(areaId).className);
					stops.sortFrom(location);
					stops.sortTo();
					acFrom.setTextNoDropdown("");
					acTo.setTextNoDropdown("");
					fromHint = "";
					acFrom.setHint(fromHint);
					toHint = "";
					acTo.setHint(toHint);
					autofill();

					searchOptions.reset(MainActivity.this);
				}

				// save
				Editor editor = preferences.edit();
				editor.putString(Common.PREF_AREA, area.id);
				editor.commit();
			}
		});
		builder.create().show();
	}

	// -----------------------------------------------------------------------------------------------

	private void switchDepArr() {
		timeIsDeparture = !timeIsDeparture;
		if (timeIsDeparture) {
			tvSwitch.setText(getText(R.string.departAt));
			ivSwitch.setImageDrawable(getResources().getDrawable(R.drawable.switch_left));
		} else {
			tvSwitch.setText(getText(R.string.arriveAt));
			ivSwitch.setImageDrawable(getResources().getDrawable(R.drawable.switch_right));
		}
	}

	// -----------------------------------------------------------------------------------------------

	private void strictMode() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && Common.DEVELOPER_MODE) {
			new Runnable() {
				@Override
				public void run() {
					StrictMode.enableDefaults();
				}
			}.run();
		}
	}

	// -----------------------------------------------------------------------------------------------

	private void updateMapMenuItem() {
		boolean enabled = area.maps.length > 0;
		if (mapMenuItemPosition != null) {
			Drawable d = getResources().getDrawable(R.drawable.ic_map);
			d.setAlpha(enabled ? 255 : 128);

			LinearLayout llMenuItem = (LinearLayout) llMenu.getChildAt(mapMenuItemPosition);
			llMenuItem.setClickable(enabled ? true : false);
			ImageView iv = ((ImageView) llMenuItem.findViewById(R.id.icon));
			iv.setImageDrawable(d);
		}
	}

	// -----------------------------------------------------------------------------------------------

	private void updateTimeDateViews() {
		// Time
		btTime.setText(Utils.formatTime(calendar));
		if (timePickerDialog != null) timePickerDialog.updateTime(calendar.get(Calendar.HOUR_OF_DAY),
				calendar.get(Calendar.MINUTE));

		// Date
		btDate.setText(Utils.formatDate(calendar, this));
		if (datePickerDialog != null) datePickerDialog.updateDate(calendar.get(Calendar.YEAR),
				calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
	}

	// -----------------------------------------------------------------------------------------------

	private class AreasAdapter extends BaseAdapter {
		public int nearestAreaId = -1;

		@Override
		public int getCount() {
			int returnedCount = areas.size();
			if (nearestAreaId != -1) returnedCount++;

			return returnedCount;
		}

		@Override
		public Object getItem(int position) {
			return position;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				convertView = getLayoutInflater().inflate(android.R.layout.select_dialog_item, null);

				holder = new ViewHolder();
				holder.text = (TextView) convertView.findViewById(android.R.id.text1);

				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			int areaId = position - 1;
			if (nearestAreaId == -1) areaId = position;
			if (position == 0 && nearestAreaId != -1) {
				String areaName = areas.get(nearestAreaId).name;
				holder.text
						.setText(areaName + " " + getString(R.string.myLocation), TextView.BufferType.SPANNABLE);
				holder.text.setTextColor(0xFF2562A8);
				Spannable spannable = (Spannable) holder.text.getText();
				spannable.setSpan(new ForegroundColorSpan(0xffaaaaaa), areaName.length(), spannable.length(),
						Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
				holder.text.setText(spannable);
			} else {
				holder.text.setText(areas.get(areaId).name);
				if (areas.get(areaId).className.equals("CR1") || areas.get(areaId).className.equals("Letadla")) holder.text
						.setTextColor(0xFF008000);
				else holder.text.setTextColor(0xFF000000);
			}

			return convertView;
		}

		class ViewHolder {
			TextView text;
		}

	}

	// -----------------------------------------------------------------------------------------------

	private static class FindJourneyTask extends Task<MainActivity, Void, Object> {

		private AbstractProvider provider;
		private AbstractSearchOptions options;
		private Context context;
		private Location location;
		private Stops stops;

		// -------------------------------------------------------------------------------------------

		public FindJourneyTask(AbstractSearchOptions searchOptions, AbstractProvider provider, Stops stops,
				Location location) {
			this.options = searchOptions;
			this.provider = provider;
			this.stops = stops;
			this.location = location;
		}

		// -------------------------------------------------------------------------------------------

		@Override
		protected void runBefore() {
			activity.showDialog(DIALOG_FINDING_JOURNEY);
			context = activity.getApplicationContext();
		}

		// -------------------------------------------------------------------------------------------

		@Override
		protected Object runInBackground() {
			try {
				DatabaseAdapter dbAdapter = new DatabaseAdapter(context).openAndLock();
				dbAdapter.saveSearch(options.getFrom(), options.getTo(), stops);
				dbAdapter.closeAndUnlock();

				stops.sortFrom(location);
				stops.sortTo();

				return provider.findJourneys(options);
			} catch (IOException e) {
				return e;
			} catch (JourneyFinderException e) {
				return e;
			}
		}

		// -------------------------------------------------------------------------------------------

		@Override
		protected void runAfter(Object result) {
			activity.removeDialog(DIALOG_FINDING_JOURNEY);

			if (result instanceof JourneyFinderException) {
				if (result instanceof StopAmbiguousException) {
					Toast.makeText(context, R.string.stopAmbiguous, Toast.LENGTH_LONG).show();
				} else if (result instanceof InputEmptyException) {
					Toast.makeText(context, R.string.loadingJourneyInputEmpty, Toast.LENGTH_LONG).show();
				} else if (result instanceof JourneyNotFoundException) {
					Toast.makeText(context, R.string.journeyNotFound, Toast.LENGTH_LONG).show();
				} else if (result instanceof ParseException) {
					Toast.makeText(context, R.string.parseError, Toast.LENGTH_LONG).show();
				} else if (result instanceof UnknownStopException) {
					Toast.makeText(context, R.string.stopDoesntExist, Toast.LENGTH_LONG).show();
				} else {
					activity.handleUnknownJourneyFinderException((JourneyFinderException) result);
				}
			} else if (result instanceof IOException) {
				IOException e = (IOException) result;
				e.printStackTrace();
				Toast.makeText(context, R.string.loadingJourneyIOException, Toast.LENGTH_LONG).show();
			} else {
				AbstractResults results = (AbstractResults) result;

				Intent intent = Utils.getIntent(context, "RESULTS_ACTIVITY");
				intent.putExtra("results", results);
				activity.startActivity(intent);
			}

		}
	}

	// -----------------------------------------------------------------------------------------------

	private class StopsAdapter extends BaseAdapter implements Filterable {

		private int fromOrTo;
		private ArrayList<String> filteredStops = new ArrayList<String>();
		private LayoutInflater inflater;

		// -------------------------------------------------------------------------------------------

		@Override
		public Filter getFilter() {
			return new Filter() {

				@SuppressWarnings("unchecked")
				@Override
				protected void publishResults(CharSequence constraint, FilterResults results) {
					if (results.values != null) filteredStops = (ArrayList<String>) results.values;
					notifyDataSetChanged();
				}

				@Override
				protected FilterResults performFiltering(CharSequence constraint) {
					if (constraint == null) constraint = "";
					String str = constraint.toString();
					str = str.toLowerCase(Locale.getDefault());
					str = Utils.simplify(str);

					ArrayList<String> locFilteredStops = new ArrayList<String>();
					ArrayList<Integer> stopsList = stops.listFrom;
					if (fromOrTo == TO) stopsList = stops.listTo;
					for (int i = 0; i < stopsList.size(); i++) {
						int id = stopsList.get(i);
						if (stops.simpleNames[id].startsWith(str)) {
							locFilteredStops.add(stops.names[id]);
							if (locFilteredStops.size() == MAX_DROPDOWN_ITEMS) break;
						}
					}
					if (locFilteredStops.size() < MAX_DROPDOWN_ITEMS) {
						str = " " + str;
						for (int i = 0; i < stopsList.size(); i++) {
							int id = stopsList.get(i);
							if (stops.simpleNames[id].indexOf(str) != -1) {
								locFilteredStops.add(stops.names[id]);
								if (locFilteredStops.size() == MAX_DROPDOWN_ITEMS) break;
							}
						}
					}

					FilterResults filterResults = new FilterResults();
					filterResults.count = locFilteredStops.size();
					filterResults.values = locFilteredStops;

					return filterResults;
				}
			};
		}

		// -------------------------------------------------------------------------------------------

		@Override
		public int getCount() {
			return filteredStops.size();
		}

		// -------------------------------------------------------------------------------------------

		@Override
		public Object getItem(int position) {
			return filteredStops.get(position);
		}

		// -------------------------------------------------------------------------------------------

		@Override
		public long getItemId(int position) {
			return position;
		}

		// -------------------------------------------------------------------------------------------

		public StopsAdapter(int fromOrTo) {
			super();
			this.fromOrTo = fromOrTo;
			inflater = getLayoutInflater();
		}

		// -------------------------------------------------------------------------------------------

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = inflater.inflate(R.layout.autocomplete_item, null);
			TextView tvAutocompleteItem = (TextView) view.findViewById(R.id.tvAutocompleteItem);
			tvAutocompleteItem.setText(filteredStops.get(position));

			return view;
		}

	}

}
