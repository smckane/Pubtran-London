package cz.fhejl.pubtran;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import cz.fhejl.pubtran.LocationTool.BestLocationListener;

public class DeparturesActivity extends AbstractActivity implements BestLocationListener {

	private EditText edFilter;
	private Location location = null;
	private LocationTool locationTool;
	private Stops stops;
	private StopsAdapter adapter;

	// -----------------------------------------------------------------------------------------------

	public static String lineCodeToLineName(char code) {
		switch (code) {
		case 'B':
			return "Bakerloo";
		case 'C':
			return "Central";
		case 'c':
			return "Circle";
		case 'D':
			return "District";
		case 'H':
			return "Hammersmith & City";
		case 'J':
			return "Jubilee";
		case 'M':
			return "Metropolitan";
		case 'N':
			return "Northern";
		case 'P':
			return "Piccadilly";
		case 'V':
			return "Victoria";
		case 'W':
			return "Waterloo & City";
		default:
			return "";
		}
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onBestLocationUpdated(Location location) {
		if (!locationTool.differsOnlyInTime(this.location, location)) {
			stops.sortDepartures(location);
			adapter.filter(edFilter.getText().toString());
		}
		this.location = location;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.departures);

		locationTool = new LocationTool(this, this);

		stops = Stops.getInstance(this, "London1");
		stops.sortDepartures(null);

		adapter = new StopsAdapter();
		ListView lvDepartures = (ListView) findViewById(R.id.lvDepartures);
		lvDepartures.setAdapter(adapter);
		lvDepartures.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				stopSelected((int) id);
			}
		});
		adapter.filter("");

		edFilter = (EditText) findViewById(R.id.edFilter);
		edFilter.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				adapter.filter(s.toString());
			}
		});
		edFilter.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (adapter.items.size() > 0) stopSelected(adapter.items.get(0));
				return true;
			}
		});
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onPause() {
		super.onPause();
		locationTool.onPause();
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onResume() {
		super.onResume();
		locationTool.onResume();
	}

	// -----------------------------------------------------------------------------------------------

	private void saveToDatabase(final int stopId) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				DatabaseAdapter dbAdapter = new DatabaseAdapter(getApplicationContext()).openAndLock();
				dbAdapter.saveDeparture(stopId, stops);
				dbAdapter.closeAndUnlock();
			}
		}).start();
	}

	// -----------------------------------------------------------------------------------------------

	private void stopAndLineSelected(int stopId, String lineCode) {
		Intent intent = new Intent(getBaseContext(), DepartureBoardsActivity.class);
		intent.putExtra("stopId", stopId);
		intent.putExtra("area", stops.area);
		intent.putExtra("lineCode", lineCode);
		startActivity(intent);
	}

	// -----------------------------------------------------------------------------------------------

	private void stopSelected(final int stopId) {
		saveToDatabase(stopId);

		final String lineCodes = stops.extras[stopId].split(":")[1];
		if (lineCodes.length() == 1) {
			stopAndLineSelected(stopId, lineCodes);
		} else {
			final String[] items = new String[lineCodes.length()];
			for (int i = 0; i < lineCodes.length(); i++) {
				items[i] = lineCodeToLineName(lineCodes.charAt(i)) + " line";
			}

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setItems(items, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					stopAndLineSelected(stopId, lineCodes.charAt(item) + "");
				}
			});
			builder.create().show();
		}
	}

	// -----------------------------------------------------------------------------------------------

	private class StopsAdapter extends BaseAdapter {

		private ArrayList<Integer> items = new ArrayList<Integer>();

		public void filter(String s) {
			ArrayList<Integer> newItems = new ArrayList<Integer>();

			s = Utils.simplify(s.toLowerCase());
			for (int i = 0; i < stops.listDepartures.size(); i++) {
				int index = stops.listDepartures.get(i);
				if (stops.simpleNames[index].startsWith(s)) {
					newItems.add(index);
				}
			}

			s = " " + s;
			for (int i = 0; i < stops.listDepartures.size(); i++) {
				int index = stops.listDepartures.get(i);
				if (stops.simpleNames[index].indexOf(s) != -1 && !newItems.contains(index)) {
					newItems.add(index);
				}
			}

			items = newItems;
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return items.size();
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return items.get(position);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				convertView = getLayoutInflater().inflate(R.layout.departure_station_item, null);

				holder = new ViewHolder();
				holder.text = (TextView) convertView.findViewById(R.id.text);

				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			int index = items.get(position);
			String stopName = stops.names[index];
			if (stopName.endsWith(" Underground Station")) stopName =
					stopName.substring(0, stopName.length() - " Underground Station".length());
			holder.text.setText(stopName);

			return convertView;
		}

		class ViewHolder {
			TextView text;
		}

	}
}
