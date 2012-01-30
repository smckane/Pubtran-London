package cz.fhejl.pubtran;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import android.content.Context;
import android.location.Location;

public class Stops {

	public static final int FROM = 0;
	public static final int TO = 1;
	public static final int DEPARTURES = 2;

	public double[] baseRanksTo;
	public double[] bonusRanksTo;
	public double[] importances;
	public double[] latitudes;
	public double[] longitudes;
	public double[] departureRanks;
	public double[] ranksFrom;
	public ArrayList<Integer> listDepartures;
	public ArrayList<Integer> listFrom;
	public ArrayList<Integer> listTo;
	public String area;
	public String[] extras;
	public String[] names;
	public String[] simpleNames;

	private static Stops instance = null;

	private CzechComparator czechComparator = new CzechComparator();

	// -----------------------------------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	private Stops(Context context, String area) {
		this.area = area;

		loadClass(area);

		ranksFrom = importances.clone();
		departureRanks = importances.clone();
		baseRanksTo = importances.clone();
		bonusRanksTo = new double[names.length];
		Arrays.fill(bonusRanksTo, 0);

		listFrom = new ArrayList<Integer>(names.length);
		for (int i = 0; i < names.length; i++) {
			listFrom.add(i);
		}
		listTo = (ArrayList<Integer>) listFrom.clone();
		listDepartures = new ArrayList<Integer>();
		for (int i = 0; i < names.length; i++) {
			if (extras[i] != null) listDepartures.add(i);
		}

		DatabaseAdapter dbAdapter = new DatabaseAdapter(context).openAndLock();
		dbAdapter.loadStops(this);
		dbAdapter.closeAndUnlock();
	}

	// -----------------------------------------------------------------------------------------------

	public double getDistanceRank(Location location, double latitude, double longitude) {
		double distanceRank = 0.0;
		if (location != null) {
			double meters = computeDistance(location.getLatitude(), location.getLongitude(), latitude,
					longitude);
			double accuracy = location.getAccuracy();
			if (meters < accuracy / 2) meters = accuracy / 2;
			if (meters < 1000.0) {
				distanceRank = (2100 - meters) / 100;
			}
		}
		return distanceRank;
	}

	// -----------------------------------------------------------------------------------------------

	private double computeDistance(double lat1, double lon1, double lat2, double lon2) {
		int R = 6371000;
		double dLat = (lat2 - lat1) / 360 * 2 * Math.PI;
		double dLon = (lon2 - lon1) / 360 * 2 * Math.PI;
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1 / 360 * 2 * Math.PI)
				* Math.cos(lat2 / 360 * 2 * Math.PI) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return R * c;
	}

	// -----------------------------------------------------------------------------------------------

	public int getIndex(String name) {
		int index = Arrays.binarySearch(names, name, czechComparator);
		return index;
	}

	// -----------------------------------------------------------------------------------------------

	public static Stops getInstance(Context context, String area) {
		if (instance == null || !instance.area.equals(area)) {
			instance = new Stops(context, area);
		}
		return instance;
	}

	// -----------------------------------------------------------------------------------------------

	public ArrayList<Integer> getStopIdsInArea(double latitudeMin, double latitudeMax, double longitudeMin,
			double longitudeMax) {
		ArrayList<Integer> stopIds = new ArrayList<Integer>();
		for (int i = 0; i < latitudes.length; i++) {
			if (latitudes[i] >= latitudeMin && latitudes[i] <= latitudeMax && longitudes[i] >= longitudeMin
					&& longitudes[i] <= longitudeMax) {
				stopIds.add(i);
			}
		}
		return stopIds;
	}

	// -----------------------------------------------------------------------------------------------

	private void loadClass(String name) {
		boolean endsWithNum = false;
		char lastChar = name.charAt(name.length() - 1);
		if (lastChar >= '0' && lastChar <= '9') {
			name = name.substring(0, name.length() - 1);
			endsWithNum = true;
		}

		ArrayList<Object> data = new ArrayList<Object>();
		int stopsCount = 0;

		int i = 1;
		while (true) {
			String fullClassName = "cz.fhejl.pubtran.data." + name;
			if (endsWithNum) fullClassName += i;

			try {
				Class<?> dataClass = Class.forName(fullClassName);
				Object obj = dataClass.newInstance();
				stopsCount += ((String[]) dataClass.getField("names").get(obj)).length;
				data.add(obj);
			} catch (Exception e) {
				e.printStackTrace();
				break;
			}

			if (!endsWithNum) break;
			i++;
		}

		loadFields(data, stopsCount);
	}

	// -----------------------------------------------------------------------------------------------

	private void loadFields(ArrayList<Object> data, int stopsCount) {
		extras = new String[stopsCount];
		names = new String[stopsCount];
		simpleNames = new String[stopsCount];
		latitudes = new double[stopsCount];
		longitudes = new double[stopsCount];
		importances = new double[stopsCount];

		int index = 0;

		try {
			for (Object obj : data) {
				String[] extrasField = (String[]) obj.getClass().getField("extras").get(obj);
				System.arraycopy(extrasField, 0, extras, index, extrasField.length);
				String[] namesField = (String[]) obj.getClass().getField("names").get(obj);
				System.arraycopy(namesField, 0, names, index, namesField.length);
				String[] simpleNamesField = (String[]) obj.getClass().getField("simpleNames").get(obj);
				System.arraycopy(simpleNamesField, 0, simpleNames, index, simpleNamesField.length);
				double[] latitudesField = (double[]) obj.getClass().getField("latitudes").get(obj);
				System.arraycopy(latitudesField, 0, latitudes, index, latitudesField.length);
				double[] longitudesField = (double[]) obj.getClass().getField("longitudes").get(obj);
				System.arraycopy(longitudesField, 0, longitudes, index, longitudesField.length);
				double[] importancesField = (double[]) obj.getClass().getField("importances").get(obj);
				System.arraycopy(importancesField, 0, importances, index, importancesField.length);

				index += namesField.length;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// -----------------------------------------------------------------------------------------------

	private int prepareSortDepartures(Location location) {
		int aboveZeroCount = 0;

		for (int i = 0; i < listDepartures.size(); i++) {
			int index = listDepartures.get(i);
			double rank = departureRanks[index]
					+ getDistanceRank(location, latitudes[index], longitudes[index]);
			if (rank > 0.1) {
				listDepartures.set(i, listDepartures.get(aboveZeroCount));
				listDepartures.set(aboveZeroCount, index);
				aboveZeroCount++;
			}

		}

		return aboveZeroCount;
	}

	// -----------------------------------------------------------------------------------------------

	private int prepareSortFrom(Location location) {
		int aboveZeroCount = 0;

		for (int i = 0; i < listFrom.size(); i++) {
			int index = listFrom.get(i);
			double rank = ranksFrom[index] + getDistanceRank(location, latitudes[index], longitudes[index]);
			if (rank > 0.1) {
				listFrom.set(i, listFrom.get(aboveZeroCount));
				listFrom.set(aboveZeroCount, index);
				aboveZeroCount++;
			}

		}

		return aboveZeroCount;
	}

	// -----------------------------------------------------------------------------------------------

	private int prepareSortTo() {
		int aboveZeroCount = 0;

		for (int i = 0; i < listTo.size(); i++) {
			int index = listTo.get(i);
			double rank = baseRanksTo[index] + bonusRanksTo[index];
			if (rank > 0.1) {
				listTo.set(i, listTo.get(aboveZeroCount));
				listTo.set(aboveZeroCount, index);
				aboveZeroCount++;
			} else if (rank < -0.1) {
				listTo.set(i, listTo.get(listTo.size() - 1));
				listTo.set(listTo.size() - 1, index);
			}

		}

		return aboveZeroCount;
	}

	// -----------------------------------------------------------------------------------------------

	public void sortDepartures(final Location location) {
		int nonZeroCount = prepareSortDepartures(location);
		Collections.sort(listDepartures.subList(0, nonZeroCount), new Comparator<Integer>() {

			@Override
			public int compare(Integer id1, Integer id2) {
				double rank1 = departureRanks[id1]
						+ getDistanceRank(location, latitudes[id1], longitudes[id1]);
				double rank2 = departureRanks[id2]
						+ getDistanceRank(location, latitudes[id2], longitudes[id2]);
				if (rank1 > rank2) return -1;
				else if (rank1 < rank2) return 1;
				else return 0;
			}
		});
	}

	// -----------------------------------------------------------------------------------------------

	public void sortFrom(final Location location) {
		int nonZeroCount = prepareSortFrom(location);
		Collections.sort(listFrom.subList(0, nonZeroCount), new Comparator<Integer>() {

			@Override
			public int compare(Integer id1, Integer id2) {
				double rank1 = ranksFrom[id1] + getDistanceRank(location, latitudes[id1], longitudes[id1]);
				double rank2 = ranksFrom[id2] + getDistanceRank(location, latitudes[id2], longitudes[id2]);
				if (rank1 > rank2) return -1;
				else if (rank1 < rank2) return 1;
				else return 0;
			}
		});
	}

	// -----------------------------------------------------------------------------------------------

	public void sortTo() {
		int nonZeroCount = prepareSortTo();
		Collections.sort(listTo.subList(0, nonZeroCount), new Comparator<Integer>() {

			@Override
			public int compare(Integer id1, Integer id2) {
				double rank1 = baseRanksTo[id1] + bonusRanksTo[id1];
				double rank2 = baseRanksTo[id2] + bonusRanksTo[id2];
				if (rank1 > rank2) return -1;
				else if (rank1 < rank2) return 1;
				else return 0;
			}
		});
	}

	// -----------------------------------------------------------------------------------------------

	public double computeBasicRank(int fromSearches, int fromSearchesAll, int toSearches, int toSearchesAll,
			int departures, int departuresAll, long lastFrom, long lastTo, long lastDeparture,
			double importance, int type) {
		double rank = 0.0;

		// fromSearches
		float coeff = 1;
		if (type == TO) coeff = 0.5f;
		else if (type == DEPARTURES) coeff = 0.5f;
		double percent = 0;
		if (fromSearchesAll > 0) percent = (double) fromSearches * 100.0 / fromSearchesAll;
		if (percent > 0.1) rank = (percent / 10.0 + 1.0) * coeff;

		// toSearches
		coeff = 0.5f;
		if (type == TO) coeff = 1;
		else if (type == DEPARTURES) coeff = 0.5f;
		percent = 0;
		if (toSearchesAll > 0) percent = (double) toSearches * 100.0 / toSearchesAll;
		if (percent > 0.1) rank += (percent / 10.0 + 1.0) * coeff;

		// departures
		coeff = 0.1f;
		if (type == DEPARTURES) coeff = 0.5f;
		percent = 0;
		if (departuresAll > 0) percent = (double) departures * 100.0 / departuresAll;
		if (percent > 0.1) rank += (percent / 10.0 + 1.0) * coeff;

		// last use
		long lastUse = Math.max(Math.max(lastFrom, lastTo), lastDeparture);
		long minutes = (System.currentTimeMillis() - lastUse) / (1000 * 60);
		if (minutes <= 60) // 1 hour
		rank += 2;
		else if (minutes <= 60 * 6) // 6 hours
		rank += 1.7;
		else if (minutes <= 60 * 24 * 2) // 2 days
		rank += 1.4;
		else if (minutes <= 60 * 24 * 7) // 1 week
		rank += 1;
		else if (minutes <= 60 * 24 * 30) // 1 month
		rank += 0.6;
		else if (minutes <= 60 * 24 * 365) // 1 year
		rank += 0.3;

		// importance
		rank += importance;

		return rank;
	}

}
