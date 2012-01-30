package cz.fhejl.pubtran;

import android.os.AsyncTask;

/**
 * This is similar to AsyncTask but better. Usage: 1) Use only in
 * AbstractActivity based activities 2) Start with
 * AbstractActivity.startTask(Task) 3) detachActivity() should null all
 * references to the Activity Context. The default implementation nulls the
 * activity field. 4) AbstractActivity.getTasksByClass(...) returns unfinished
 * Tasks. This is useful e.g. after Activity restart due to screen orientation
 * change.
 */
public abstract class Task<A extends AbstractActivity, Progress, Result> extends AsyncTask<Void, Progress, Result> {
	protected A activity;
	private int id;
	
	protected abstract Result runInBackground();

	@SuppressWarnings("unchecked")
	public void attachActivity(AbstractActivity activity) {
		this.activity = (A) activity;
	}

	public void detachActivity() {
		activity = null;
	}
	
	public int getId() {
		return id;
	}

	@Override
	protected void onPreExecute() {
		runBefore();
	}

	@Override
	protected Result doInBackground(Void... nothing) {
		return runInBackground();
	}

	@Override
	protected void onPostExecute(Result result) {
		if (activity != null) {
			runAfter(result);
			activity.onTaskCompleted(this);
		}
	}

	protected void runBefore() {
	}

	protected void runAfter(Result result) {
	}
	
	public void setId(int id) {
		this.id = id;
	}
}
