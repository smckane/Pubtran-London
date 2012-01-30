package cz.fhejl.pubtran;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.TextView;
import cz.fhejl.pubtran.Favourites.Favourite;

public class FavouritesActivity extends AbstractActivity {

	private static final int DIALOG_FAVOURITE = 0;
	private static final int REQUEST_RESULTS = 0;

	private int selectedFavourite;
	private ArrayList<Favourite> favourites;
	private Handler handler = new Handler();
	private LinearLayout llHistory;
	private LinearLayout llFavourites;

	private void addFavouriteItem(final Favourite favourite) {
		final LinearLayout item = (LinearLayout) getLayoutInflater().inflate(R.layout.favourite_item, null);
		((TextView) item.findViewById(R.id.tvFrom)).setText(favourite.getDeparture());
		((TextView) item.findViewById(R.id.tvTo)).setText(favourite.getArrival());

		item.getChildAt(0).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onFavouriteSelected(favourite, false);
			}
		});

		item.findViewById(R.id.showPopup).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				for (int i = 0; i < llFavourites.getChildCount(); i++) {
					if (v == llFavourites.getChildAt(i).findViewById(R.id.showPopup)) {
						selectedFavourite = i;
						break;
					}
				}

				showDialog(DIALOG_FAVOURITE);
			}
		});

		llFavourites.addView(item);
	}

	private void addHistoryItem(AbstractSearchOptions options, final String fileName) {
		LinearLayout item = (LinearLayout) getLayoutInflater().inflate(R.layout.history_item, null);
		((TextView) item.findViewById(R.id.tvFrom)).setText(options.getFrom());
		((TextView) item.findViewById(R.id.tvTo)).setText(options.getTo());
		String searchTime = formatTime(System.currentTimeMillis() - SavedResults.getTime(fileName));
		((TextView) item.findViewById(R.id.tvSearchTime)).setText(searchTime);

		item.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = Utils.getIntent(getBaseContext(), "RESULTS_ACTIVITY");
				intent.putExtra("results", SavedResults.loadResults(getApplicationContext(), fileName));
				startActivityForResult(intent, REQUEST_RESULTS);
			}
		});

		llHistory.addView(item);

		View divider = getLayoutInflater().inflate(R.layout.favourites_divider, null);
		llHistory.addView(divider);
	}

	private void findReturnJourney() {
		Favourite favourite = favourites.get(selectedFavourite);
		onFavouriteSelected(favourite, true);
	}

	private String formatTime(long milliseconds) {
		int minutes = (int) (milliseconds / 1000 / 60);
		if (minutes < 60) return getString(R.string.xMinutesAgo, minutes);

		int hours = minutes / 60;
		if (hours == 1) return getString(R.string.oneHourAgo);
		else if (hours < 24) return getString(R.string.xHoursAgo, hours);

		int days = hours / 24;
		if (days == 1) return getString(R.string.oneDayAgo);
		else return getString(R.string.xDaysAgo, days);
	}

	private void loadFavourites() {
		favourites = Favourites.getAll(getApplicationContext());

		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Favourite favourite : favourites) {
					addFavouriteItem(favourite);
				}
			}
		});

		handler.post(new Runnable() {
			@Override
			public void run() {
				if (llFavourites.getChildCount() == 0) {
					findViewById(R.id.favouritesEmpty).setVisibility(View.VISIBLE);
				}
			}
		});
	}

	private void loadHistory() {
		ArrayList<String> fileNames = SavedResults.getFileNames(getApplicationContext());
		for (final String fileName : fileNames) {
			final AbstractSearchOptions options =
					SavedResults.loadSearchOptions(getApplicationContext(), fileName);
			if (options == null) break;
			handler.post(new Runnable() {
				@Override
				public void run() {
					addHistoryItem(options, fileName);
				}
			});
		}

		handler.post(new Runnable() {
			@Override
			public void run() {
				if (llHistory.getChildCount() == 0) {
					findViewById(R.id.historyEmpty).setVisibility(View.VISIBLE);
				}
			}
		});
	}

	private void moveUp() {
		if (selectedFavourite == 0) return;

		Favourite favourite = favourites.get(selectedFavourite);

		// Update UI
		View v = llFavourites.getChildAt(selectedFavourite);
		llFavourites.removeViewAt(selectedFavourite);
		llFavourites.addView(v, selectedFavourite - 1);

		// Update ArrayList
		favourites.get(selectedFavourite).setOrder(selectedFavourite - 1);
		favourites.get(selectedFavourite - 1).setOrder(selectedFavourite);
		favourites.set(selectedFavourite, favourites.get(selectedFavourite - 1));
		favourites.set(selectedFavourite - 1, favourite);

		// Update database
		Favourites.update(favourites.get(selectedFavourite), this);
		Favourites.update(favourites.get(selectedFavourite - 1), this);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		finish();
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.favourites);

		llFavourites = (LinearLayout) findViewById(R.id.llFavourites);
		llHistory = (LinearLayout) findViewById(R.id.llHistory);

		if (icicle != null) selectedFavourite = icicle.getInt("selectedFavourite");

		new Thread() {
			@Override
			public void run() {
				loadFavourites();
				loadHistory();
			}
		}.start();
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setItems(new String[] { "Najít zpáteční spojení", "Posunout nahoru", "Smazat" },
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						switch (which) {
						case 0:
							findReturnJourney();
							break;
						case 1:
							moveUp();
							break;
						case 2:
							remove();
							break;
						}

					}
				});

		return builder.create();
	}

	private void onFavouriteSelected(Favourite favourite, boolean returnJourney) {
		Intent data = new Intent();
		data.putExtra("favourite", favourite);
		data.putExtra("returnJourney", returnJourney);
		setResult(RESULT_OK, data);
		finish();
	}

	@Override
	protected void onSaveInstanceState(Bundle bundle) {
		bundle.putInt("selectedFavourite", selectedFavourite);
		super.onSaveInstanceState(bundle);
	}

	private void remove() {
		Favourite favourite = favourites.get(selectedFavourite);

		// Update ArrayList
		favourites.remove(selectedFavourite);
		for (int i = selectedFavourite; i < favourites.size(); i++) {
			int newOrder = favourites.get(i).getOrder() - 1;
			favourites.get(i).setOrder(newOrder);
		}

		// Update UI
		llFavourites.removeViewAt(selectedFavourite);
		if (favourites.size() == 0) findViewById(R.id.favouritesEmpty).setVisibility(View.VISIBLE);

		// Update database
		Favourites.delete(favourite, this);
		for (int i = selectedFavourite; i < favourites.size(); i++) {
			Favourites.update(favourites.get(i), this);
		}
	}

}
