package cz.fhejl.pubtran;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class LanguageActivity extends AbstractActivity {

	private String[] languages;
	private String[] languageCodes;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.language);

		languages = getResources().getStringArray(R.array.languages);
		languages[0] = "English";
		languageCodes = getResources().getStringArray(R.array.languageCodes);

		ArrayList<HashMap<String, String>> data = new ArrayList<HashMap<String, String>>();
		for (int i = 0; i < languages.length; i++) {
			HashMap<String, String> map = new HashMap<String, String>();
			map.put("language", languages[i]);
			data.add(map);
		}

		SimpleAdapter adapter = new SimpleAdapter(this, data, R.layout.language_item,
				new String[] { "language" }, new int[] { R.id.tvLanguage });
		ListView lvLanguages = (ListView) findViewById(R.id.lvLanguages);
		lvLanguages.setAdapter(adapter);
		lvLanguages.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Editor editor = preferences.edit();
				editor.putString(Common.PREF_LANGUAGE, languageCodes[position]);
				editor.commit();
				
				Areas.reset();

				startActivity(Utils.getIntent(LanguageActivity.this, "MAIN_ACTIVITY"));
				finish();
			}
		});
	}
}
