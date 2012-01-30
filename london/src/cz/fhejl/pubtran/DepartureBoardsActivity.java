package cz.fhejl.pubtran;

import java.io.InputStream;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import cz.fhejl.pubtran.DeparturesParser.Departures;
import cz.fhejl.pubtran.DeparturesParser.Departures.Line;
import cz.fhejl.pubtran.DeparturesParser.Departures.Platform;

public class DepartureBoardsActivity extends AbstractActivity {

	private static final int DOWNLOAD_INTERVAL = 40 * 1000;
	private static final int UI_UPDATE_INTERVAL = 5 * 1000;

	private long lastDownload = 0;
	private Departures departures;
	private Handler handler = new Handler();
	private LinearLayout llBoards;
	private ProgressBar pbLoading;
	private String lineCode;
	private String stationCode;

	// -----------------------------------------------------------------------------------------------

	private Runnable updateTask = new Runnable() {
		@Override
		public void run() {
			updateDepartureBoards(departures);
			if (System.currentTimeMillis() - lastDownload > DOWNLOAD_INTERVAL) {
				startTask(new DownloadDeparturesTask(lineCode, stationCode));
			}
			handler.postDelayed(this, UI_UPDATE_INTERVAL);
		}
	};

	// -----------------------------------------------------------------------------------------------

	public void departuresDownloaded(Departures departures) {
		lastDownload = System.currentTimeMillis();
		this.departures = departures;
		updateDepartureBoards(departures);
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.departure_boards);

		pbLoading = (ProgressBar) findViewById(R.id.pbLoading);
		llBoards = (LinearLayout) findViewById(R.id.llBoards);

		Intent intent = getIntent();
		int stopId = intent.getIntExtra("stopId", 0);
		String area = intent.getStringExtra("area");
		lineCode = intent.getStringExtra("lineCode");

		Stops stops = Stops.getInstance(this, area);

		TextView tvTitle = (TextView) findViewById(R.id.tvTitle);
		String stationName = stops.names[stopId];
		if (stationName.endsWith(" Underground Station")) stationName =
				stationName.substring(0, stationName.length() - " Underground Station".length());
		tvTitle.setText(stationName);

		TextView tvLine = (TextView) findViewById(R.id.tvLine);
		tvLine.setText(" - " + DeparturesActivity.lineCodeToLineName(lineCode.charAt(0)) + " line");

		if (lineCode.equals("c")) lineCode = "H";
		String extra = stops.extras[stopId];
		stationCode = extra.split(":")[0];

		if (savedInstanceState != null) {
			departures = (Departures) savedInstanceState.getSerializable("departures");
			lastDownload = savedInstanceState.getLong("lastDownload");
		}
	}

	// -----------------------------------------------------------------------------------------------

	public void onPause() {
		super.onPause();
		handler.removeCallbacks(updateTask);
	}

	// -----------------------------------------------------------------------------------------------

	public void onResume() {
		super.onResume();
		updateTask.run();
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onSaveInstanceState(Bundle bundle) {
		bundle.putSerializable("departures", departures);
		bundle.putLong("lastDownload", lastDownload);
	}

	// -----------------------------------------------------------------------------------------------

	private void updateDepartureBoards(Departures departures) {
		if (departures == null) return;

		pbLoading.setVisibility(View.GONE);
		llBoards.removeViews(0, llBoards.getChildCount());
		LayoutInflater inflater = getLayoutInflater();

		TextView tvNoData = (TextView) findViewById(R.id.tvNoData);
		if (departures.platforms.size() == 0) tvNoData.setVisibility(View.VISIBLE);
		else tvNoData.setVisibility(View.GONE);

		for (Platform platform : departures.platforms) {
			LinearLayout board = (LinearLayout) inflater.inflate(R.layout.departure_board, null);
			TextView tvTitle = (TextView) board.findViewById(R.id.tvTitle);
			tvTitle.setText(platform.name);
			LinearLayout lines = (LinearLayout) board.findViewById(R.id.llLines);

			int maxIndex = 3;
			for (int i = 0; i < Math.min(maxIndex, platform.lines.size()); i++) {
				Line line = platform.lines.get(i);

				LinearLayout llLine = (LinearLayout) inflater.inflate(R.layout.departure_line, null);
				((TextView) llLine.findViewById(R.id.tvDestination)).setText(line.destination);

				int minutes = ((int) (line.when - System.currentTimeMillis()) / 1000 + 30) / 60;
				String timeInfo = "";
				if (minutes < 0) {
					maxIndex++;
					continue;
				} else if (minutes == 0) {
					timeInfo = "now";
				} else if (minutes == 1) {
					timeInfo = "1 min";
				} else {
					timeInfo = minutes + " mins";
				}
				((TextView) llLine.findViewById(R.id.tvTime)).setText(timeInfo);

				lines.addView(llLine);
			}

			llBoards.addView(board);
		}
	}

	// -----------------------------------------------------------------------------------------------

	private static class DownloadDeparturesTask extends Task<DepartureBoardsActivity, Void, Object> {
		private String line;
		private String stationCode;

		public DownloadDeparturesTask(String line, String stationCode) {
			this.line = line;
			this.stationCode = stationCode;
		}

		@Override
		protected Object runInBackground() {
			InputStream is = null;
			try {
				is =
						IOUtils.doGetRequestReturnStream("http://cloud.tfl.gov.uk/TrackerNet/PredictionDetailed/"
								+ line + "/" + stationCode);
				return DeparturesParser.parse(is);
			} catch (Exception e) {
				e.printStackTrace();
				return e;
			} finally {
				if (is != null) IOUtils.close(is);
			}
		}

		@Override
		protected void runAfter(Object object) {
			if (object instanceof Departures) {
				activity.departuresDownloaded((Departures)object);
			} else if (object instanceof Exception){
				activity.startTask(new DownloadDeparturesTask(line, stationCode));
			}
		}

	}

}