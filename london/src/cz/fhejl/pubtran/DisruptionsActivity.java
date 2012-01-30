package cz.fhejl.pubtran;

import java.util.ArrayList;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.widget.LinearLayout;
import android.widget.TextView;

public class DisruptionsActivity extends AbstractActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.disruptions);

		@SuppressWarnings("unchecked")
		ArrayList<DisruptionInfo> disruptions = (ArrayList<DisruptionInfo>) getIntent().getSerializableExtra(
				"disruptions");
		LinearLayout llDisruptions = (LinearLayout) findViewById(R.id.llDisruptions);
		for (DisruptionInfo disruptionInfo : disruptions) {
			SpannableString text = new SpannableString(disruptionInfo.info);
			if (disruptionInfo.info.indexOf(":") >= 0) {
				text.setSpan(new StyleSpan(Typeface.BOLD), 0, disruptionInfo.info.indexOf(":") + 1,
						Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
			}
			
			LinearLayout llDisruption = (LinearLayout) getLayoutInflater().inflate(R.layout.disruption, null);
			TextView tvInfo = (TextView) llDisruption.findViewById(R.id.tvInfo);
			tvInfo.setText(text);
			if (disruptionInfo.priority == DisruptionInfo.PRIORITY_HIGH)
				tvInfo.setTextColor(0xff990000);
			TextView tvLastUpdated = (TextView) llDisruption.findViewById(R.id.tvLastUpdated);
			tvLastUpdated.setText("Last updated: " + disruptionInfo.lastUpdate);
			llDisruptions.addView(llDisruption);
		}
	}
}
