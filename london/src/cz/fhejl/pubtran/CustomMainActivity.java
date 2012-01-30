package cz.fhejl.pubtran;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;

public class CustomMainActivity extends MainActivity {

	private static int DIALOG_FISRT_START_INFO = 101;

	{
		depArrSwitchEnabled = true;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		showFirstStartMessage();
	}

	@Override
	protected AbstractSearchOptions getSearchOptions(SharedPreferences preferences) {
		return new SearchOptions();
	}

	@Override
	protected AbstractProvider getProvider() {
		return new Provider();
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		if (id == DIALOG_FISRT_START_INFO) {
			String message =
					"1. Sorry for the map related problems I made some changes that may or may not fix them.\n\n"
							+ "2. This app does not plan journeys by itself, the data are provided by TFL."
							+ " So if you find some inaccuracies or errors, it's probably not Pubtran's fault. "
							+ "The results should be exactly the same as on tfl.gov.uk.";

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Please read this");
			builder.setMessage(message);
			builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
				}
			});

			return builder.create();
		} else {
			return super.onCreateDialog(id);
		}
	}

	@Override
	protected void onCreateMenu() {
		addMapMenuItem();

		addMenuItem(R.drawable.ic_disruptions, new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				startActivity(new Intent(getBaseContext(), TubeStatusActivity.class));
			}
		});

		addMenuItem(R.drawable.ic_departures, new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				startActivity(new Intent(getBaseContext(), DeparturesActivity.class));
			}
		});

		addFavouritesMenuItem();

		addMenuItem(R.drawable.ic_settings, new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				startActivityForResult(new Intent(CustomMainActivity.this, PreferencesActivity.class),
						REQUEST_PREFERENCES);
			}
		});
	}

	private void showFirstStartMessage() {
		String FIRST_START = "87936220976";

		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		if (!preferences.getBoolean(FIRST_START, true)) return;

		showDialog(DIALOG_FISRT_START_INFO);

		Editor editor = preferences.edit();
		editor.putBoolean(FIRST_START, false);
		editor.commit();
	}
}
