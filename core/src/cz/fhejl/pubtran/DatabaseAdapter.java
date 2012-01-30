package cz.fhejl.pubtran;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import cz.fhejl.pubtran.Favourites.Favourite;

public class DatabaseAdapter {

	private static final int DATABASE_VERSION = 9;

	private static final String AREAS_AREA = "area";
	private static final String AREAS_SEARCHES_COUNT = "count";
	private static final String AREAS_DEPARTURES_COUNT = "departures_count";
	private static final String FAVS_ID = "id";
	private static final String FAVS_AREA = "area";
	private static final String FAVS_ARRIVAL = "arrival";
	private static final String FAVS_DEPARTURE = "departure";
	private static final String FAVS_SWAPPED = "swapped";
	private static final String FAVS_ORDER = "position";
	private static final String SEARCHES_AREA = "area";
	private static final String SEARCHES_COUNT = "count";
	private static final String SEARCHES_ID = "id";
	private static final String SEARCHES_FROM = "from_stop";
	private static final String SEARCHES_TO = "to_stop";
	private static final String STOPS_AREA = "area";
	private static final String STOPS_DEPARTURES = "departures";
	private static final String STOPS_FROM_SEARCHES = "from_searches";
	private static final String STOPS_LAST_FROM = "last_from";
	private static final String STOPS_LAST_TO = "last_to";
	private static final String STOPS_LAST_DEPARTURE = "last_departure";
	private static final String STOPS_STOP = "stop_name";
	private static final String STOPS_TO_SEARCHES = "to_searches";

	private static final String CREATE_AREAS_TABLE = "CREATE TABLE areas (id INTEGER PRIMARY KEY AUTOINCREMENT, "
			+ "area TEXT NOT NULL, count INTEGER NOT NULL, departures_count INTEGER NOT NULL);";

	private static final String CREATE_FAVS_TABLE = "CREATE TABLE favs (id INTEGER PRIMARY KEY AUTOINCREMENT, "
			+ "area TEXT NOT NULL, departure TEXT NOT NULL, arrival TEXT NOT NULL, position INETEGR NOT NULL, "
			+ "swapped INETEGR NOT NULL);";

	private static final String CREATE_SEARCHES_TABLE = "create table searches (id INTEGER PRIMARY KEY "
			+ "AUTOINCREMENT, from_stop TEXT NOT NULL, to_stop TEXT NOT NULL, count INTEGER NOT NULL, area TEXT"
			+ " NOT NULL);";

	private static final String CREATE_STOPS_TABLE = "CREATE TABLE stops (id INTEGER PRIMARY KEY AUTOINCREMENT"
			+ ", stop_name TEXT NOT NULL, area TEXT NOT NULL, from_searches INTEGER NOT NULL, to_searches INTEGER"
			+ " NOT NULL, departures INTEGER NOT NULL, last_from INTEGER NOT NULL, last_to INTEGER NOT NULL,"
			+ " last_departure INTEGER NOT NULL);";

	private static final String DATABASE_NAME = "pubtran";

	private static final String TABLE_AREAS = "areas";
	private static final String TABLE_FAVS = "favs";
	private static final String TABLE_SEARCHES = "searches";
	private static final String TABLE_STOPS = "stops";

	private static Semaphore lock = new Semaphore(1, true);

	private final Context context;
	private DatabaseHelper dbHelper;
	private SQLiteDatabase db;

	// -----------------------------------------------------------------------------------------------

	public void favouritesAdd(Favourite favourite) {
		Cursor cursor = db.rawQuery("SELECT Count(*) FROM " + TABLE_FAVS, null);
		cursor.moveToNext();
		int tableRows = cursor.getInt(0);
		cursor.close();

		ContentValues values = new ContentValues();
		values.put(FAVS_AREA, favourite.getAreaClass());
		values.put(FAVS_ARRIVAL, favourite.getArrival());
		values.put(FAVS_DEPARTURE, favourite.getDeparture());
		values.put(FAVS_ORDER, tableRows);
		values.put(FAVS_SWAPPED, favourite.isSwapped() ? 1 : 0);
		db.insert(TABLE_FAVS, "", values);
	}

	// -----------------------------------------------------------------------------------------------

	public ArrayList<Favourite> favouritesGetAll() {
		ArrayList<Favourite> favourites = new ArrayList<Favourite>();

		String[] columns =
				new String[] { FAVS_AREA, FAVS_ARRIVAL, FAVS_DEPARTURE, FAVS_ORDER, FAVS_SWAPPED, FAVS_ID };
		Cursor cursor = db.query(TABLE_FAVS, columns, null, null, null, null, FAVS_ORDER);

		int columnArea = cursor.getColumnIndex(FAVS_AREA);
		int columnArrival = cursor.getColumnIndex(FAVS_ARRIVAL);
		int columnDeparture = cursor.getColumnIndex(FAVS_DEPARTURE);
		int columnOrder = cursor.getColumnIndex(FAVS_ORDER);
		int columnSwapped = cursor.getColumnIndex(FAVS_SWAPPED);
		int columnId = cursor.getColumnIndex(FAVS_ID);
		while (cursor.moveToNext()) {
			String area = cursor.getString(columnArea);
			String arrival = cursor.getString(columnArrival);
			String departure = cursor.getString(columnDeparture);
			int order = cursor.getInt(columnOrder);
			boolean swapped = cursor.getInt(columnSwapped) == 1 ? true : false;
			int id = cursor.getInt(columnId);

			Favourite favourite = new Favourite(departure, arrival, area, swapped, id, order);
			favourites.add(favourite);
		}

		cursor.close();

		return favourites;
	}

	// -----------------------------------------------------------------------------------------------

	public void favouritesDelete(Favourite favourite) {
		db.delete(TABLE_FAVS, FAVS_ID + "=" + favourite.getId(), null);
	}

	// -----------------------------------------------------------------------------------------------

	public void favouritesUpdate(Favourite favourite) {
		ContentValues values = new ContentValues();
		values.put(FAVS_ORDER, favourite.getOrder());
		db.update(TABLE_FAVS, values, FAVS_ID + "=" + favourite.getId(), null);
	}

	// -----------------------------------------------------------------------------------------------

	public void closeAndUnlock() {
		dbHelper.close();
		lock.release();
	}

	// -----------------------------------------------------------------------------------------------

	public DatabaseAdapter(Context context) {
		this.context = context;
	}

	// -----------------------------------------------------------------------------------------------

	private int[] getCounts(String area) {
		Cursor cursor =
				db.query(TABLE_AREAS, new String[] { AREAS_SEARCHES_COUNT, AREAS_DEPARTURES_COUNT }, AREAS_AREA
						+ "= ?", new String[] { area }, null, null, null);
		int searchesCount = 0;
		int departuresCount = 0;
		if (cursor.moveToFirst()) {
			searchesCount = cursor.getInt(cursor.getColumnIndex(AREAS_SEARCHES_COUNT));
			departuresCount = cursor.getInt(cursor.getColumnIndex(AREAS_DEPARTURES_COUNT));
		}
		cursor.close();

		return new int[] { searchesCount, departuresCount };
	}

	// -----------------------------------------------------------------------------------------------

	public void loadStops(Stops stops) {
		int[] counts = getCounts(stops.area);
		int searchesCount = counts[0];
		int departuresCount = counts[1];

		Cursor cursor =
				db.query(TABLE_STOPS, new String[] { STOPS_STOP, STOPS_FROM_SEARCHES, STOPS_TO_SEARCHES,
						STOPS_DEPARTURES, STOPS_LAST_FROM, STOPS_LAST_TO, STOPS_LAST_DEPARTURE }, STOPS_AREA
						+ "= ?", new String[] { stops.area }, null, null, null);

		int columnStop = cursor.getColumnIndex(STOPS_STOP);
		int columnFromSearches = cursor.getColumnIndex(STOPS_FROM_SEARCHES);
		int columnToSearches = cursor.getColumnIndex(STOPS_TO_SEARCHES);
		int columnDepartures = cursor.getColumnIndex(STOPS_DEPARTURES);
		int columnLastFrom = cursor.getColumnIndex(STOPS_LAST_FROM);
		int columnLastTo = cursor.getColumnIndex(STOPS_LAST_TO);
		int columnLastDeparture = cursor.getColumnIndex(STOPS_LAST_DEPARTURE);

		while (cursor.moveToNext()) {
			String stop = cursor.getString(columnStop);
			int fromSearches = cursor.getInt(columnFromSearches);
			int departures = cursor.getInt(columnDepartures);
			int toSearches = cursor.getInt(columnToSearches);
			long lastFrom = cursor.getLong(columnLastFrom);
			long lastTo = cursor.getLong(columnLastTo);
			long lastDeparture = cursor.getLong(columnLastDeparture);

			int index = stops.getIndex(stop);
			if (index < 0) {
				removeStop(stop, stops.area, (fromSearches + toSearches) / 2, departures);
			} else {
				stops.ranksFrom[index] =
						stops.computeBasicRank(fromSearches, searchesCount, toSearches, searchesCount, departures,
								departuresCount, lastFrom, lastTo, lastDeparture, stops.importances[index],
								Stops.FROM);
				stops.baseRanksTo[index] =
						stops.computeBasicRank(fromSearches, searchesCount, toSearches, searchesCount, departures,
								departuresCount, lastFrom, lastTo, lastDeparture, stops.importances[index],
								Stops.TO);
				stops.departureRanks[index] =
						stops.computeBasicRank(fromSearches, searchesCount, toSearches, searchesCount, departures,
								departuresCount, lastFrom, lastTo, lastDeparture, stops.importances[index],
								Stops.DEPARTURES);
			}
		}

		cursor.close();
	}

	// -----------------------------------------------------------------------------------------------

	public DatabaseAdapter openAndLock() {
		lock.acquireUninterruptibly();
		dbHelper = new DatabaseHelper(context);
		db = dbHelper.getWritableDatabase();
		return this;
	}

	// -----------------------------------------------------------------------------------------------

	private void removeStop(String stop, String area, int searchesCount, int departuresCount) {
		db.delete(TABLE_STOPS, STOPS_STOP + "= ?", new String[] { stop });
		db.delete(TABLE_SEARCHES, SEARCHES_FROM + "= ? OR " + SEARCHES_TO + "= ?", new String[] { stop, stop });
		modifyCounts(area, -searchesCount, -departuresCount);
	}

	// -----------------------------------------------------------------------------------------------

	public void saveDeparture(int stopId, Stops stops) {
		long time = System.currentTimeMillis();
		String stopName = stops.names[stopId];

		// save to stops table
		Cursor cursor =
				db.query(TABLE_STOPS, new String[] { STOPS_DEPARTURES }, STOPS_STOP + " = ? AND " + STOPS_AREA
						+ " = ?", new String[] { stopName, stops.area }, null, null, null);
		if (cursor.moveToFirst()) {
			int departures = cursor.getInt(cursor.getColumnIndex(STOPS_DEPARTURES));

			ContentValues values = new ContentValues();
			values.put(STOPS_DEPARTURES, departures + 1);
			values.put(STOPS_LAST_DEPARTURE, time);
			db.update(TABLE_STOPS, values, STOPS_STOP + "= ? AND " + STOPS_AREA + " = ?", new String[] { stopName,
					stops.area });
		} else {
			ContentValues values = new ContentValues();
			values.put(STOPS_STOP, stopName);
			values.put(STOPS_AREA, stops.area);
			values.put(STOPS_FROM_SEARCHES, 0);
			values.put(STOPS_TO_SEARCHES, 0);
			values.put(STOPS_DEPARTURES, 1);
			values.put(STOPS_LAST_FROM, 0);
			values.put(STOPS_LAST_TO, 0);
			values.put(STOPS_LAST_DEPARTURE, time);
			db.insert(TABLE_STOPS, "", values);
		}
		cursor.close();

		// update all searches count
		modifyCounts(stops.area, 0, 1);

		// update ranks
		loadStops(stops);
	}

	// -----------------------------------------------------------------------------------------------

	public void saveSearch(String from, String to, Stops stops) {
		long time = System.currentTimeMillis();

		if (from.equals(to)) return;

		boolean foundFrom = false;
		boolean foundTo = false;

		// find out if from an to are valid stops
		for (int i = 0; i < stops.names.length; i++) {
			if (!foundFrom && stops.names[i].equals(from)) {
				foundFrom = true;
			}
			if (!foundTo && stops.names[i].equals(to)) {
				foundTo = true;
			}
		}

		// save to DB
		if (foundFrom && foundTo) {

			// first save from stop to TABLE_STOPS
			Cursor cursor =
					db.query(TABLE_STOPS, new String[] { STOPS_FROM_SEARCHES }, STOPS_STOP + " = ? AND "
							+ STOPS_AREA + " = ?", new String[] { from, stops.area }, null, null, null);
			if (cursor.moveToFirst()) {
				int fromSearches = cursor.getInt(cursor.getColumnIndex(STOPS_FROM_SEARCHES));

				ContentValues values = new ContentValues();
				values.put(STOPS_FROM_SEARCHES, fromSearches + 1);
				values.put(STOPS_LAST_FROM, time);
				db.update(TABLE_STOPS, values, STOPS_STOP + "= ? AND " + STOPS_AREA + " = ?", new String[] { from,
						stops.area });
			} else {
				ContentValues values = new ContentValues();
				values.put(STOPS_STOP, from);
				values.put(STOPS_AREA, stops.area);
				values.put(STOPS_FROM_SEARCHES, 1);
				values.put(STOPS_TO_SEARCHES, 0);
				values.put(STOPS_DEPARTURES, 0);
				values.put(STOPS_LAST_FROM, time);
				values.put(STOPS_LAST_TO, 0);
				values.put(STOPS_LAST_DEPARTURE, 0);
				db.insert(TABLE_STOPS, "", values);
			}
			cursor.close();

			// and now to stop
			cursor =
					db.query(TABLE_STOPS, new String[] { STOPS_TO_SEARCHES }, STOPS_STOP + " = ? AND "
							+ STOPS_AREA + " = ?", new String[] { to, stops.area }, null, null, null);
			if (cursor.moveToFirst()) {
				int toSearches = cursor.getInt(cursor.getColumnIndex(STOPS_TO_SEARCHES));

				ContentValues values = new ContentValues();
				values.put(STOPS_TO_SEARCHES, toSearches + 1);
				values.put(STOPS_LAST_TO, time);
				db.update(TABLE_STOPS, values, STOPS_STOP + "= ? AND " + STOPS_AREA + " = ?", new String[] { to,
						stops.area });

			} else {
				ContentValues values = new ContentValues();
				values.put(STOPS_STOP, to);
				values.put(STOPS_AREA, stops.area);
				values.put(STOPS_FROM_SEARCHES, 0);
				values.put(STOPS_TO_SEARCHES, 1);
				values.put(STOPS_DEPARTURES, 0);
				values.put(STOPS_LAST_FROM, 0);
				values.put(STOPS_LAST_TO, time);
				values.put(STOPS_LAST_DEPARTURE, 0);
				db.insert(TABLE_STOPS, "", values);
			}
			cursor.close();

			// update all searches count
			modifyCounts(stops.area, 1, 0);

			// update stop ranks
			loadStops(stops);

			// save to TABLE_SEARCHES
			cursor =
					db.query(TABLE_SEARCHES, new String[] { SEARCHES_ID, SEARCHES_COUNT }, SEARCHES_FROM
							+ "= ? AND " + SEARCHES_TO + "= ? AND " + SEARCHES_AREA + "= ?", new String[] { from,
							to, stops.area }, null, null, null);
			if (cursor.getCount() == 0) {
				ContentValues values = new ContentValues();
				values.put(SEARCHES_FROM, from);
				values.put(SEARCHES_TO, to);
				values.put(SEARCHES_AREA, stops.area);
				values.put(SEARCHES_COUNT, 1);

				db.insert(TABLE_SEARCHES, null, values);
			} else if (cursor.getCount() == 1) {
				cursor.moveToFirst();
				long id = cursor.getLong(cursor.getColumnIndex(SEARCHES_ID));
				int count = cursor.getInt(cursor.getColumnIndex(SEARCHES_COUNT));
				ContentValues values = new ContentValues();
				values.put(SEARCHES_COUNT, count + 1);

				db.update(TABLE_SEARCHES, values, SEARCHES_ID + "=" + id, null);
			}
			cursor.close();
		}
	}

	// -----------------------------------------------------------------------------------------------

	private void modifyCounts(String area, int searchesCountChange, int departuresCountChange) {
		int[] counts = getCounts(area);
		int newSearchesCount = counts[0] + searchesCountChange;
		int newDeparturesCount = counts[1] + departuresCountChange;

		db.delete(TABLE_AREAS, AREAS_AREA + " = ?", new String[] { area });
		ContentValues values = new ContentValues();
		values.put(AREAS_AREA, area);
		values.put(AREAS_SEARCHES_COUNT, newSearchesCount);
		values.put(AREAS_DEPARTURES_COUNT, newDeparturesCount);
		db.insert(TABLE_AREAS, "", values);
	}

	// -----------------------------------------------------------------------------------------------

	public void updateToRanks(String from, Stops stops) {
		// reset all bonus to ranks
		Arrays.fill(stops.bonusRanksTo, 0);

		int index = stops.getIndex(from);
		if (index >= 0) stops.bonusRanksTo[index] = -1000;

		Cursor cursor =
				db.query(TABLE_SEARCHES, new String[] { SEARCHES_COUNT, SEARCHES_TO }, SEARCHES_FROM + "= ? AND "
						+ SEARCHES_AREA + "= ?", new String[] { from, stops.area }, null, null, null);
		int columnCount = cursor.getColumnIndex(SEARCHES_COUNT);
		int columnTo = cursor.getColumnIndex(SEARCHES_TO);

		while (cursor.moveToNext()) {
			String to = cursor.getString(columnTo);
			int count = cursor.getInt(columnCount);

			index = stops.getIndex(to);
			if (index >= 0) {
				stops.bonusRanksTo[index] += 100 + count * 10;
			}
		}
		cursor.close();
	}

	// -----------------------------------------------------------------------------------------------

	private static class DatabaseHelper extends SQLiteOpenHelper {

		public DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		// -------------------------------------------------------------------------------------------

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(CREATE_STOPS_TABLE);
			db.execSQL(CREATE_SEARCHES_TABLE);
			db.execSQL(CREATE_AREAS_TABLE);
			db.execSQL(CREATE_FAVS_TABLE);
		}

		// -------------------------------------------------------------------------------------------

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if (oldVersion == newVersion) return;

			if (oldVersion <= 6) {
				db.execSQL("drop table if exists areas;");
				db.execSQL("drop table if exists stops;");
				db.execSQL("drop table if exists searches;");
				onCreate(db);
			} else if (oldVersion == 7) {
				db.execSQL("ALTER TABLE stops ADD COLUMN departures INTEGER NOT NULL DEFAULT 0;");
				db.execSQL("ALTER TABLE stops ADD COLUMN last_departure INTEGER NOT NULL DEFAULT 0;");
				db.execSQL("ALTER TABLE areas ADD COLUMN departures_count INTEGER NOT NULL DEFAULT 0;");
				onUpgrade(db, 8, newVersion);
			} else if (oldVersion == 8) {
				db.execSQL(CREATE_FAVS_TABLE);
				onUpgrade(db, 9, newVersion);
			}
		}
	}
}
