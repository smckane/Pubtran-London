package cz.fhejl.pubtran;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import cz.fhejl.pubtran.AbstractProvider.JourneyFinderException;
import cz.fhejl.pubtran.AbstractProvider.JourneyNotFoundException;
import cz.fhejl.pubtran.Favourites.Favourite;

public class ResultsActivity extends AbstractActivity implements OnScrollListener {

	public static final int NEXT = 0;
	public static final int PREV = 1;

	protected static final int DIALOG_OPTIONS = 0;

	private static final int ITEM_ID_LOAD_NEXT = -1;
	private static final int ITEM_ID_LOAD_PREV = -2;

	protected ResultsAdapter adapter;

	private AbstractResults results;
	private BroadcastReceiver timeTickReceiver;
	private LinearLayout llBottomBar;
	private ListView listView;

	// -----------------------------------------------------------------------------------------------

	protected void cancelJourneyWatch() {
		Intent intent = new Intent(this, JourneyNotifsService.class);
		intent.putExtra("cancel", getIntent().getStringExtra("watchedJourneyFileName"));
		startService(intent);
		llBottomBar.setVisibility(View.GONE);

		if (getIntent().hasExtra("watchedJourney")) {
			AbstractJourney journey = (AbstractJourney) getIntent().getSerializableExtra("watchedJourney");
			View view = adapter.findView(journey);
			if (view != null) view.setBackgroundColor(Color.TRANSPARENT);
		}
	}

	// -----------------------------------------------------------------------------------------------

	protected Dialog createJourneyMenu() {
		return null;
	}

	// -----------------------------------------------------------------------------------------------

	private boolean isLoadingPrev() {
		for (Task<? extends AbstractActivity, ?, ?> task : getTasksByClass(LoadMoreTask.class)) {
			LoadMoreTask loadMoreTask = (LoadMoreTask) task;
			if (loadMoreTask.nextOrPrev == PREV) return true;
		}
		return false;
	}

	// -----------------------------------------------------------------------------------------------

	private boolean isLoadingNext() {
		for (Task<? extends AbstractActivity, ?, ?> task : getTasksByClass(LoadMoreTask.class)) {
			LoadMoreTask loadMoreTask = (LoadMoreTask) task;
			if (loadMoreTask.nextOrPrev == NEXT) return true;
		}
		return false;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.results);

		preferences = PreferenceManager.getDefaultSharedPreferences(this);

		results = (AbstractResults) getNonConfigurationData("results");
		if (results == null) {
			if (savedInstanceState != null) {
				results = (AbstractResults) savedInstanceState.getSerializable("results");
			} else {
				results = (AbstractResults) getIntent().getSerializableExtra("results");
			}
		}

		setUpListView();

		// bottom bar
		llBottomBar = (LinearLayout) findViewById(R.id.llBottomBar);
		Button btCancelWatch = (Button) findViewById(R.id.btCancelWatch);
		btCancelWatch.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				cancelJourneyWatch();
			}
		});

		// create timeTickReceiver
		timeTickReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				updateJourneys();
			}
		};
	}

	// -----------------------------------------------------------------------------------------------

	protected Dialog onCreateDialog(int id) {
		return null;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add("Přidat do oblíbených").setIcon(R.drawable.ic_menu_star);
		return true;
	}

	// -----------------------------------------------------------------------------------------------

	protected void onJourneyClick(AbstractJourney journey) {
		JourneyActivity.sentResults = results;

		Intent intent = Utils.getIntent(this, "JOURNEY_ACTIVITY");
		intent.putExtra("journeyIndex", results.getJourneys().indexOf(journey));
		startActivity(intent);
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	protected void onNewIntent(Intent intent) {
		finish();
		startActivity(intent);
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		AbstractSearchOptions options = results.getOptions();
		Areas areas = Areas.getInstance(this);
		String areaClass = areas.findById(options.getAreaIdentifier()).className;
		Favourite favourite = new Favourite(options.getFrom(), options.getTo(), areaClass);
		Favourites.add(favourite, this);
		Toast.makeText(this, "Přidáno do oblíbených", Toast.LENGTH_SHORT).show();
		return true;
	}

	// -----------------------------------------------------------------------------------------------

	public void onPause() {
		super.onPause();
		unregisterReceiver(timeTickReceiver);
		SavedResults.saveResults(this, results);
	}

	// -----------------------------------------------------------------------------------------------

	public void onResume() {
		super.onResume();
		updateJourneys();
		registerReceiver(timeTickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

		if (getIntent().hasExtra("watchedJourney")) {
			AbstractJourney journey = (AbstractJourney) getIntent().getSerializableExtra("watchedJourney");
			View view = adapter.findView(journey);
			if (JourneyNotifs.isJourneyWatched(journey, this)) {
				llBottomBar.setVisibility(View.VISIBLE);
				if (view != null) view.setBackgroundResource(R.drawable.bg_highlighted_journey_selector);
			} else {
				llBottomBar.setVisibility(View.GONE);
				if (view != null) view.setBackgroundColor(Color.TRANSPARENT);
			}
		}
	}

	// -----------------------------------------------------------------------------------------------

	public void onSaveInstanceState(Bundle bundle) {
		super.onSaveInstanceState(bundle);
		bundle.putSerializable("results", results);
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	protected void onSaveNonConfigurationData(HashMap<String, Object> nonConfigurationData) {
		nonConfigurationData.put("results", results);
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		results.setFirstVisibleItem(firstVisibleItem);
		if (firstVisibleItem + visibleItemCount >= totalItemCount && !isLoadingNext()) {
			if (adapter.loadNextState != LoadMoreState.ERROR) tryLoadNext();
		}
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
	}

	// -----------------------------------------------------------------------------------------------

	private void setUpListView() {
		adapter = new ResultsAdapter();

		listView = (ListView) findViewById(R.id.lvResults);
		listView.setAdapter(adapter);

		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (id == ITEM_ID_LOAD_PREV) {
					tryLoadPrev();
				} else if (id == ITEM_ID_LOAD_NEXT) {
					tryLoadNext();
				} else {
					onJourneyClick((AbstractJourney) adapter.journeys.get((int) adapter.getItemId(position)));
				}
			}
		});

		listView.setSelection(results.getFirstVisibleItem());

		listView.setOnScrollListener(this);
	}

	// -----------------------------------------------------------------------------------------------

	private void tryLoadPrev() {
		if (results.isLoadPrevEnabled() && !isLoadingPrev()) {
			startTask(new LoadMoreTask(PREV));
		}
	}

	// -----------------------------------------------------------------------------------------------

	private void tryLoadNext() {
		if (results.isLoadNextEnabled() && !isLoadingNext()) {
			startTask(new LoadMoreTask(NEXT));
		}
	}

	// -----------------------------------------------------------------------------------------------

	private void updateJourneys() {
		for (int i = 0; i < adapter.views.size(); i++) {
			if (adapter.getItemId(i) >= 0) adapter.journeys.get((int) adapter.getItemId(i)).update(this,
					adapter.views.get(i));
		}
	}

	// -----------------------------------------------------------------------------------------------

	protected class ResultsAdapter extends BaseAdapter {

		protected LoadMoreState loadPrevState = LoadMoreState.ENABLED;
		protected LoadMoreState loadNextState = LoadMoreState.ENABLED;
		protected ArrayList<View> views = new ArrayList<View>();
		protected ArrayList<AbstractJourney> journeys = new ArrayList<AbstractJourney>();
		protected LayoutInflater inflater = getLayoutInflater();

		// -------------------------------------------------------------------------------------------

		public ResultsAdapter() {
			views.add(inflater.inflate(R.layout.load_journey_item, null));
			setLoadMoreItem(PREV, LoadMoreState.ENABLED);
			views.add(inflater.inflate(R.layout.load_journey_item, null));
			setLoadMoreItem(NEXT, LoadMoreState.ENABLED);

			for (AbstractJourney journey : results.getVisibleJourneys()) {
				addJourney(journey, false);
			}

			if (!results.isLoadNextEnabled()) setLoadMoreItem(NEXT, LoadMoreState.DISABLED);
			if (!results.isLoadPrevEnabled()) setLoadMoreItem(PREV, LoadMoreState.DISABLED);
		}

		// -------------------------------------------------------------------------------------------

		public void addJourney(AbstractJourney journey, boolean atBeginning) {
			View view = journey.inflateView(ResultsActivity.this, false);

			if (atBeginning) {
				int index = isPrevDisabled() ? 0 : 1;
				views.add(index, view);
				journeys.add(0, journey);
			} else {
				int index = isNextDisabled() ? views.size() : views.size() - 1;
				views.add(index, view);
				journeys.add(journey);
			}
		}

		// -------------------------------------------------------------------------------------------

		@Override
		public int getCount() {
			return views.size();
		}

		// -------------------------------------------------------------------------------------------

		@Override
		public Object getItem(int position) {
			return null;
		}

		// -------------------------------------------------------------------------------------------

		@Override
		public long getItemId(int position) {
			boolean isLast = position == views.size() - 1;
			if (!isPrevDisabled()) position--;
			if (position == -1) return ITEM_ID_LOAD_PREV;
			else if (isLast && !isNextDisabled()) return ITEM_ID_LOAD_NEXT;
			else return position;
		}

		// -------------------------------------------------------------------------------------------

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			return views.get(position);
		}

		// -------------------------------------------------------------------------------------------

		public View findView(AbstractJourney journey) {
			for (int i = 0; i < journeys.size(); i++) {
				if (journeys.get(i).hashCode() == journey.hashCode()) {
					return views.get(isPrevDisabled() ? i : i + 1);
				}
			}
			return null;
		}

		// -------------------------------------------------------------------------------------------

		private boolean isPrevDisabled() {
			return loadPrevState == LoadMoreState.DISABLED;
		}

		// -------------------------------------------------------------------------------------------

		private boolean isNextDisabled() {
			return loadNextState == LoadMoreState.DISABLED;
		}

		// -------------------------------------------------------------------------------------------

		public void setLoadMoreItem(int nextOrPrev, LoadMoreState state) {
			if (state == LoadMoreState.DISABLED) {
				if (nextOrPrev == NEXT && !isNextDisabled()) {
					loadNextState = LoadMoreState.DISABLED;
					views.remove(views.size() - 1);
				} else if (nextOrPrev == PREV && !isPrevDisabled()) {
					loadPrevState = LoadMoreState.DISABLED;
					views.remove(0);
				}
				notifyDataSetChanged();
			} else {
				if (nextOrPrev == NEXT) loadNextState = state;
				else if (nextOrPrev == PREV) loadPrevState = state;

				int index = nextOrPrev == PREV ? 0 : views.size() - 1;
				View item = views.get(index);

				TextView tvLoadJourney = (TextView) item.findViewById(R.id.tvLoadJourney);
				if (nextOrPrev == PREV) tvLoadJourney.setText(R.string.loadPrevJourney);
				else if (nextOrPrev == NEXT) tvLoadJourney.setText(R.string.loadNextJourney);

				LinearLayout llLoading = (LinearLayout) item.findViewById(R.id.llLoading);
				LinearLayout llClickToLoad = (LinearLayout) item.findViewById(R.id.llClickToLoad);
				LinearLayout llError = (LinearLayout) item.findViewById(R.id.llError);

				llLoading.setVisibility(View.GONE);
				llClickToLoad.setVisibility(View.GONE);
				llError.setVisibility(View.GONE);

				if (state == LoadMoreState.ENABLED) llClickToLoad.setVisibility(View.VISIBLE);
				else if (state == LoadMoreState.LOADING) llLoading.setVisibility(View.VISIBLE);
				else if (state == LoadMoreState.ERROR) llError.setVisibility(View.VISIBLE);
			}
		}
	}

	// -----------------------------------------------------------------------------------------------

	private static class LoadMoreTask extends Task<ResultsActivity, Void, Object> {

		private int nextOrPrev;
		private AbstractResults results;

		// -------------------------------------------------------------------------------------------

		public LoadMoreTask(int nextOrPrev) {
			this.nextOrPrev = nextOrPrev;
		}

		// -------------------------------------------------------------------------------------------

		@Override
		public void attachActivity(AbstractActivity activity) {
			super.attachActivity(activity);
			if (results == null) results = this.activity.results;
			this.activity.adapter.setLoadMoreItem(nextOrPrev, LoadMoreState.LOADING);
		}

		// -------------------------------------------------------------------------------------------

		@Override
		protected Object runInBackground() {
			try {
				if (nextOrPrev == NEXT) return results.loadNext();
				else return results.loadPrev();
			} catch (IOException e) {
				return e;
			} catch (JourneyFinderException e) {
				return e;
			}
		}

		// -------------------------------------------------------------------------------------------

		@Override
		protected void runAfter(Object result) {
			ResultsAdapter adapter = activity.adapter;
			if (nextOrPrev == PREV) adapter.setLoadMoreItem(PREV, LoadMoreState.ENABLED);

			if (result instanceof IOException) {
				IOException e = (IOException) result;
				e.printStackTrace();
				adapter.setLoadMoreItem(nextOrPrev, LoadMoreState.ERROR);
			} else if (result instanceof JourneyFinderException) {
				if (result instanceof JourneyNotFoundException == false) {
					adapter.setLoadMoreItem(nextOrPrev, LoadMoreState.ERROR);
				}
			} else {
				AbstractJourney journey = (AbstractJourney) result;
				adapter.addJourney(journey, nextOrPrev == PREV);

				if (nextOrPrev == NEXT) {
					int max = 3;
					while (results.getCachedJourneysCount(NEXT) > 0 && max > 0) {
						max--;
						try {
							journey = results.loadNext();
						} catch (Exception e) {
							assert false;
						}
						adapter.addJourney(journey, nextOrPrev == PREV);
					}
				}

				adapter.notifyDataSetChanged();
			}

			if (!results.isLoadNextEnabled()) adapter.setLoadMoreItem(NEXT, LoadMoreState.DISABLED);
			if (!results.isLoadPrevEnabled()) adapter.setLoadMoreItem(PREV, LoadMoreState.DISABLED);
		}
	}

	// -----------------------------------------------------------------------------------------------

	private enum LoadMoreState {
		ENABLED, DISABLED, LOADING, ERROR
	}
}