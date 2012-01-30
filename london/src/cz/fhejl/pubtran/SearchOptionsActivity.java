package cz.fhejl.pubtran;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;

public class SearchOptionsActivity extends AbstractActivity {

	private static final int[] ICONS = { R.drawable.bus, R.drawable.options_tube, R.drawable.dlr,
			R.drawable.rail, R.drawable.tram, R.drawable.river };
	private static final String[] TRANSPORT_MODES = { "Bus or Coach", "Tube", "DLR", "Rail", "Tram", "River" };

	private CheckedTextView[] transportModeCheckboxes = new CheckedTextView[TRANSPORT_MODES.length];
	private SearchOptions searchOptions;
	private Spinner spPrefferedMode;
	private Spinner spWalkingSpeed;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.search_options);

		LinearLayout llTransportModes = (LinearLayout) findViewById(R.id.llTransportModes);
		for (int i = 0; i < TRANSPORT_MODES.length; i++) {
			View view = getLayoutInflater().inflate(R.layout.transport_mode_item, null);
			CheckedTextView checkedTextView = (CheckedTextView) view.findViewById(R.id.checkedTextView);
			view.setTag(checkedTextView);
			ImageView imageView = (ImageView) view.findViewById(R.id.image);
			imageView.setImageResource(ICONS[i]);
			checkedTextView.setText(TRANSPORT_MODES[i]);
			checkedTextView.setChecked(false);
			transportModeCheckboxes[i] = checkedTextView;
			view.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					CheckedTextView checkedTextView = (CheckedTextView) v.findViewById(R.id.checkedTextView);
					checkedTextView.toggle();
				}
			});

			llTransportModes.addView(view);

			if (i != TRANSPORT_MODES.length - 1) {
				View divider = new View(this);
				divider.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, 1));
				divider.setBackgroundColor(0xffdddddd);
				llTransportModes.addView(divider);
			}
		}

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
				new String[] { "Fastest", "Fewest changes", "Least walking" });
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spPrefferedMode = (Spinner) findViewById(R.id.spPreferredMode);
		spPrefferedMode.setAdapter(adapter);
		
		adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
				new String[] { "Average", "Fast", "Slow" });
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spWalkingSpeed = (Spinner) findViewById(R.id.spWalkingSpeed);
		spWalkingSpeed.setAdapter(adapter);

		findViewById(R.id.btReset).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				searchOptions.reset(null);
				updateUI();
				finish();
			}
		});
		
		findViewById(R.id.btDone).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});

		searchOptions = new SearchOptions();
		searchOptions.loadFromSharedPreferences(preferences, null);

		updateUI();
	}

	@Override
	public void onPause() {
		super.onPause();
		searchOptions.useBus = transportModeCheckboxes[0].isChecked();
		searchOptions.useTube = transportModeCheckboxes[1].isChecked();
		searchOptions.useDLR = transportModeCheckboxes[2].isChecked();
		searchOptions.useRail = transportModeCheckboxes[3].isChecked();
		searchOptions.useTram = transportModeCheckboxes[4].isChecked();
		searchOptions.useRiver = transportModeCheckboxes[5].isChecked();
		searchOptions.prefferedMode = spPrefferedMode.getSelectedItemPosition();
		searchOptions.walkingSpeed = spWalkingSpeed.getSelectedItemPosition();
		searchOptions.save(preferences);
	}
	
	private void updateUI() {
		if (searchOptions.useBus)
			transportModeCheckboxes[0].setChecked(true);
		if (searchOptions.useTube)
			transportModeCheckboxes[1].setChecked(true);
		if (searchOptions.useDLR)
			transportModeCheckboxes[2].setChecked(true);
		if (searchOptions.useRail)
			transportModeCheckboxes[3].setChecked(true);
		if (searchOptions.useTram)
			transportModeCheckboxes[4].setChecked(true);
		if (searchOptions.useRiver)
			transportModeCheckboxes[5].setChecked(true);

		spPrefferedMode.setSelection(searchOptions.prefferedMode);
		
		spWalkingSpeed.setSelection(searchOptions.walkingSpeed);
	}
}
