package cz.fhejl.pubtran;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import cz.fhejl.pubtran.TubeStatus.Line;

public class TubeStatusActivity extends AbstractActivity {

	private static final int DIALOG_DETAILS = 0;
	private static final int DIALOG_ERROR = 1;
	private static final int UPDATE_INTERVAL = 6 * 60 * 1000;

	private Line selectedLine;
	private LineStatusAdapter adapter;
	private SharedPreferences preferences;
	private TextView tvInfo;
	private TubeStatus tubeStatus;

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tube_status);

		if (savedInstanceState != null) selectedLine = (Line) savedInstanceState.getSerializable("selectedLine");

		preferences = PreferenceManager.getDefaultSharedPreferences(this);

		tvInfo = (TextView) findViewById(R.id.tvInfo);

		tubeStatus = TubeStatus.getTubeStatus(this);

		adapter = new LineStatusAdapter();
		ListView lvTubeStatus = (ListView) findViewById(R.id.lvTubeStatus);
		lvTubeStatus.setAdapter(adapter);
		lvTubeStatus.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Line line = tubeStatus.lines.get(position);
				if (line.details != null) {
					selectedLine = line;
					showDialog(DIALOG_DETAILS);
				}
			}
		});
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public Dialog onCreateDialog(int id) {
		if (id == DIALOG_DETAILS) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(selectedLine.name).setMessage(selectedLine.details);
			builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
				}
			});
			Dialog dialog = builder.create();
			dialog.setOnDismissListener(new OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog) {
					removeDialog(DIALOG_DETAILS);
				}
			});
			return dialog;
		} else if (id == DIALOG_ERROR) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Update unsuccessful").setMessage("Some error occured during update. Retry?");
			builder.setPositiveButton("Retry", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					if (getTasksByClass(DownloadStatusTask.class).size() == 0) {
						startTask(new DownloadStatusTask());
					}
				}
			});
			builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					finish();
				}
			});

			return builder.create();
		} else {
			return null;
		}
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onResume() {
		super.onResume();
		long sinceLastUpdate = System.currentTimeMillis() - preferences.getLong(Common.PREF_STATUS_LAST_UPDATE, 0);
		if (sinceLastUpdate > UPDATE_INTERVAL) {
			if (getTasksByClass(DownloadStatusTask.class).size() == 0) {
				startTask(new DownloadStatusTask());
			}
		}
		setUpdateInfo(sinceLastUpdate, getTasksByClass(DownloadStatusTask.class).size() != 0);
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onSaveInstanceState(Bundle bundle) {
		bundle.putSerializable("selectedLine", selectedLine);
	}

	// -----------------------------------------------------------------------------------------------

	public void tubeStatusDownloaded(TubeStatus tubeStatus) {
		this.tubeStatus = tubeStatus;
		adapter.notifyDataSetChanged();
		setUpdateInfo(0, false);
	}

	// -----------------------------------------------------------------------------------------------

	private void setUpdateInfo(long millisSinceLastUpdate, boolean loading) {
		ProgressBar pbUpdating = (ProgressBar) findViewById(R.id.pbUpdating);
		if (loading) {
			pbUpdating.setVisibility(View.VISIBLE);
			tvInfo.setText("Updating...");
		} else {
			pbUpdating.setVisibility(View.GONE);
			int minutes = (int) (millisSinceLastUpdate / 1000 / 60);
			if (minutes == 0) tvInfo.setText("Just updated");
			else if (minutes == 1) tvInfo.setText(minutes + " min ago");
			else if (minutes <= 59) tvInfo.setText(minutes + " mins ago");
		}
	}

	// -----------------------------------------------------------------------------------------------

	private static class DownloadStatusTask extends Task<TubeStatusActivity, Void, Object> {

		private Context applicationContext;

		@Override
		public void attachActivity(AbstractActivity activity) {
			super.attachActivity(activity);
			applicationContext = activity.getApplicationContext();
		}

		@Override
		protected Object runInBackground() {
			try {
				TubeStatus tubeStatus = TubeStatus.downloadTubeStatus(applicationContext);

				Editor editor = PreferenceManager.getDefaultSharedPreferences(applicationContext).edit();
				editor.putLong(Common.PREF_STATUS_LAST_UPDATE, System.currentTimeMillis());
				editor.commit();

				return tubeStatus;
			} catch (Exception e) {
				e.printStackTrace();
				return e;
			}
		}

		protected void runAfter(Object object) {
			if (object instanceof Exception) {
				activity.showDialog(DIALOG_ERROR);
			} else {
				activity.tubeStatusDownloaded((TubeStatus) object);
			}
		}

	}

	// -----------------------------------------------------------------------------------------------

	private class LineStatusAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return tubeStatus.lines.size();
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				convertView = getLayoutInflater().inflate(R.layout.line_status_item, null);

				holder = new ViewHolder();
				holder.name = (TextView) convertView.findViewById(R.id.tvName);
				holder.status = (TextView) convertView.findViewById(R.id.tvStatus);
				holder.colorBar = (View) convertView.findViewById(R.id.colorBar);
				holder.icon = (View) convertView.findViewById(R.id.icon);

				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			Line line = TubeStatusActivity.this.tubeStatus.lines.get(position);
			holder.name.setText(line.name);
			holder.status.setText(line.status);
			holder.colorBar.setBackgroundColor(line.color);

			int image = 0;
			switch (line.statusCode) {
			case TubeStatus.STATUS_CLOSED:
				image = R.drawable.ic_closed;
				break;
			case TubeStatus.STATUS_GOOD_SERVICE:
				image = R.drawable.ic_good_service;
				break;
			case TubeStatus.STATUS_MINOR_DELAYS:
				image = R.drawable.ic_minor_delays;
				break;
			case TubeStatus.STATUS_PART_CLOSURE:
				image = R.drawable.ic_part_closure;
				break;
			case TubeStatus.STATUS_PLANNED_CLOSURE:
				image = R.drawable.ic_closed;
				break;
			case TubeStatus.STATUS_SEVERE_DELAYS:
				image = R.drawable.ic_severe_delays;
				break;
			case TubeStatus.STATUS_SUSPENDED:
				image = R.drawable.ic_closed;
				break;
			}
			if (image == 0) holder.icon.setBackgroundColor(0xffffffff);
			else holder.icon.setBackgroundResource(image);

			return convertView;
		}

		class ViewHolder {
			TextView name;
			TextView status;
			View colorBar;
			View icon;
		}

	}
}
