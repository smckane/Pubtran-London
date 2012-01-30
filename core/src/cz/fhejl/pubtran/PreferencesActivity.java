package cz.fhejl.pubtran;

import java.util.Locale;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class PreferencesActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	private SharedPreferences preferences;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		preferences = PreferenceManager.getDefaultSharedPreferences(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		preferences.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		preferences.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(Common.PREF_LANGUAGE)) {
			String langCode = sharedPreferences.getString(key, "");
			Locale locale = Locale.getDefault();
			if (!langCode.equals("")) {
				locale = new Locale(langCode);
			}
			Configuration config = getResources().getConfiguration();
			config.locale = locale;
			getResources().updateConfiguration(config, getResources().getDisplayMetrics());

			Toast.makeText(this, R.string.languageChanged, Toast.LENGTH_LONG).show();
		}
	}

}
