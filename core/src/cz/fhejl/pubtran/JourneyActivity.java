package cz.fhejl.pubtran;

import java.util.Calendar;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.LightingColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Time;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Toast;

public class JourneyActivity extends AbstractActivity {
	// When starting this Activity, set this field before sending the Intent.
	// Results are not serialized to the Intent, because it's a bit slow.
	public static AbstractResults sentResults;

	private static final int DIALOG_SHARE = 0;
	private static final int DIALOG_WATCH = 1;
	private static final int[] TIME_PERIODS_IN_MINUTES = { 0, 5, 10, 15, 20, 30, 45, 60, 120 };

	protected AbstractJourney journey;
	protected View journeyView;

	private int journeyIndex;
	private int selectedTimePeriod = 0;
	private int watchMenuItemPosition;
	private AbstractResults results;
	private BroadcastReceiver timeTickReceiver;
	private LinearLayout llMenu;

	// -----------------------------------------------------------------------------------------------

	protected void addCalendarMenuItem() {
		addMenuItem(R.drawable.ic_calendar, new OnClickListener() {
			@Override
			public void onClick(View view) {
				addToCalendar();
			}
		});
	}

	// -----------------------------------------------------------------------------------------------

	protected void addShareMenuItem() {
		addMenuItem(R.drawable.ic_share, new OnClickListener() {
			@Override
			public void onClick(View view) {
				showDialog(DIALOG_SHARE);
			}
		});
	}

	// -----------------------------------------------------------------------------------------------

	protected void addWatchMenuItem() {
		watchMenuItemPosition = llMenu.getChildCount();
		addMenuItem(R.drawable.ic_watch, new OnClickListener() {
			@Override
			public void onClick(View view) {
				if (JourneyNotifs.isJourneyWatched(journey, getApplicationContext())) unWatchJourney();
				else showDialog(DIALOG_WATCH);
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

	protected void addToCalendar() {
		Calendar departure = journey.getDepartureTime();
		Time eventStart = new Time();
		eventStart.set(0, departure.get(Calendar.MINUTE), departure.get(Calendar.HOUR_OF_DAY),
				departure.get(Calendar.DAY_OF_MONTH), departure.get(Calendar.MONTH), departure.get(Calendar.YEAR));

		Calendar arrival = journey.getArrivalTime();
		Time eventEnd = new Time();
		eventEnd.set(0, arrival.get(Calendar.MINUTE), arrival.get(Calendar.HOUR_OF_DAY),
				arrival.get(Calendar.DAY_OF_MONTH), arrival.get(Calendar.MONTH), arrival.get(Calendar.YEAR));

		Intent intent = new Intent(Intent.ACTION_EDIT);
		intent.setType("vnd.android.cursor.item/event");
		intent.putExtra("title", journey.getCalendarEventTitle(this));
		intent.putExtra("description", journey.toHumanReadableString(false));
		intent.putExtra("beginTime", eventStart.toMillis(true));
		intent.putExtra("endTime", eventEnd.toMillis(true));

		try {
			startActivity(intent);
		} catch (ActivityNotFoundException e) {
			e.printStackTrace();
			Toast.makeText(this, R.string.googleCalendarNotInstalled, Toast.LENGTH_LONG).show();
		}
	}

	// -----------------------------------------------------------------------------------------------

	private String[] createTimePeriods() {
		String[] timePeriods = new String[TIME_PERIODS_IN_MINUTES.length];

		for (int i = 0; i < TIME_PERIODS_IN_MINUTES.length; i++) {
			String timePeriod;
			int minutes = TIME_PERIODS_IN_MINUTES[i];

			if (minutes == 0) timePeriod = getString(R.string.now);
			else if (minutes < 60) timePeriod = getString(R.string.xMinutesBeforeDeparture, minutes);
			else if (minutes == 60) timePeriod = getString(R.string.oneHourBeforeDeparture);
			else timePeriod = getString(R.string.xHoursBeforeDeparture, minutes / 60);

			timePeriods[i] = timePeriod;
		}

		return timePeriods;
	}

	// -----------------------------------------------------------------------------------------------

	private Dialog createShareDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		//			builder.setInverseBackgroundForced(true);
		builder.setTitle(R.string.sendJourney);

		//			View view = getLayoutInflater().inflate(R.layout.dialog_share, null);
		//			TextView tvEmail = (TextView) view.findViewById(R.id.tvEmail);
		//			tvEmail.setText(getString(R.string.sendByEmail));
		//			tvEmail.setClickable(true);
		//			tvEmail.setFocusable(true);
		//			tvEmail.setBackgroundResource(android.R.drawable.list_selector_background);
		//			TextView tvSMS = (TextView) view.findViewById(R.id.tvSms);
		//			tvSMS.setText(getString(R.string.sendBySms));
		//			tvSMS.setClickable(true);
		//			tvSMS.setFocusable(true);
		//			tvSMS.setBackgroundResource(android.R.drawable.list_selector_background);
		//			builder.setView(view);

		builder.setItems(new String[] { getString(R.string.sendByEmail), getString(R.string.sendBySms) },
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						switch (which) {
						case 0:
							sendByEmail();
							break;
						case 1:
							sendBySms();
							break;
						}
					}
				});
		builder.setNeutralButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
			}
		});
		return builder.create();
	}

	// -----------------------------------------------------------------------------------------------

	private Dialog createWatchDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		builder.setTitle(R.string.setNotification);
		builder.setInverseBackgroundForced(true);

		View view = getLayoutInflater().inflate(R.layout.dialog_watch_journey, null);
		Spinner spWhenNotify = (Spinner) view.findViewById(R.id.spWhenNotify);
		spWhenNotify.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				selectedTimePeriod = position;
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
		ArrayAdapter<String> adapter =
				new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, createTimePeriods());
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spWhenNotify.setAdapter(adapter);
		builder.setView(view);

		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				long depTime = journey.getDepartureTime().getTimeInMillis();
				int minutes = TIME_PERIODS_IN_MINUTES[selectedTimePeriod];
				long whenNotify = depTime - minutes * 60 * 1000;
				if (minutes == 0) whenNotify = System.currentTimeMillis();
				watchJourney(whenNotify);
			}
		});

		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
			}
		});

		return builder.create();
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.journey_detail);

		if (savedInstanceState == null) {
			results = sentResults;
			sentResults = null;
		} else {
			selectedTimePeriod = savedInstanceState.getInt("selectedTimePeriod");
			results = (AbstractResults) savedInstanceState.getSerializable("results");
		}
		journeyIndex = getIntent().getIntExtra("journeyIndex", -1);
		journey = results.getJourneys().get(journeyIndex);

		ScrollView scrollView = (ScrollView) findViewById(R.id.scollView);
		journeyView = journey.inflateView(this, true);
		scrollView.addView(journeyView);

		// Create timeTickReceiver
		timeTickReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				journey.update(JourneyActivity.this, journeyView);
			}
		};

		// Create menu
		llMenu = (LinearLayout) findViewById(R.id.llMenu);
		onCreateMenu();
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	protected Dialog onCreateDialog(int id) {
		if (id == DIALOG_SHARE) {
			return createShareDialog();
		} else if (id == DIALOG_WATCH) {
			return createWatchDialog();
		}
		return null;
	}

	// -----------------------------------------------------------------------------------------------

	protected void onCreateMenu() {

	}

	// -----------------------------------------------------------------------------------------------

	public void onPause() {
		super.onPause();
		unregisterReceiver(timeTickReceiver);
	}

	// -----------------------------------------------------------------------------------------------

	public void onResume() {
		super.onResume();
		journey.update(this, journeyView);
		registerReceiver(timeTickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
		setWatchIcon(JourneyNotifs.isJourneyWatched(journey, this));
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putSerializable("results", results);
		outState.putSerializable("selectedTimePeriod", selectedTimePeriod);
	}

	// -----------------------------------------------------------------------------------------------

	protected void sendByEmail() {
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.putExtra(Intent.EXTRA_TEXT, journey.toHumanReadableString(false));
		intent.setType("message/rfc822");
		startActivity(Intent.createChooser(intent, getString(R.string.sendByEmail)));
	}

	// -----------------------------------------------------------------------------------------------

	protected void sendBySms() {
		String smsBody = journey.toHumanReadableString(true);
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse("sms:"));
		intent.putExtra("sms_body", smsBody);
		startActivity(intent);
	}

	// -----------------------------------------------------------------------------------------------

	private void setWatchIcon(boolean isJourneyWatched) {
		Bitmap immutableBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_watch);
		Bitmap mutableBitmap = immutableBitmap.copy(Bitmap.Config.ARGB_8888, true);
		immutableBitmap.recycle();
		Drawable d = new BitmapDrawable(mutableBitmap);

		if (isJourneyWatched) {
			int color = getResources().getColor(R.color.themeColor);
			int R = (color & 0x00ff0000) >> 16;
			int G = (color & 0x0000ff00) >> 8;
			int B = (color & 0x000000ff);
			float r = R / 255f;
			float g = G / 255f;
			float b = B / 255f;
			int lightenFactor = 130;
			int lightenR = (int) (r * lightenFactor);
			int lightenG = (int) (g * lightenFactor);
			int lightenB = (int) (b * lightenFactor);
			int lightenColor = (lightenR * 0x10000) + (lightenG * 0x100) + lightenB;
			d.setColorFilter(new LightingColorFilter(color, lightenColor));
		}

		LinearLayout llMenuItem = (LinearLayout) llMenu.getChildAt(watchMenuItemPosition);
		ImageView iv = ((ImageView) llMenuItem.findViewById(R.id.icon));
		iv.setImageDrawable(d);
	}

	// -----------------------------------------------------------------------------------------------

	protected void unWatchJourney() {
		Intent intent = new Intent(this, JourneyNotifsService.class);
		intent.putExtra(JourneyNotifsService.EXTRA_CANCEL, JourneyNotifs.getFileName(this, journey));
		startService(intent);

		setWatchIcon(false);

		Toast.makeText(this, R.string.journeyNotificationCancelled, Toast.LENGTH_SHORT).show();
	}

	// -----------------------------------------------------------------------------------------------

	protected void watchJourney(long whenNotify) {
		JourneyNotifs.watchJourney(this, whenNotify, journey, results, journeyIndex);
		setWatchIcon(true);
	}
}
