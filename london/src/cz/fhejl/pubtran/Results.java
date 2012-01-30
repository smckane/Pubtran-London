package cz.fhejl.pubtran;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import cz.fhejl.pubtran.AbstractProvider.JourneyFinderException;

@SuppressWarnings("serial")
public class Results extends AbstractResults {

	private ArrayList<Journey> journeys;

	// -----------------------------------------------------------------------------------------------

	public Results(SearchOptions options, ArrayList<Journey> journeys, Calendar when, boolean timeIsDeparture) {
		super(options);
		this.journeys = journeys;

		if (timeIsDeparture) {
			// Set start and end indices, so that the first displayed
			// route is not already expired
			for (int i = 0; i < journeys.size(); i++) {
				startIndex = endIndex = i;
				// Find out if this train / bus / ... has not left
				// already
				long departure = journeys.get(i).getDepartureTime().getTimeInMillis();
				if (departure - when.getTimeInMillis() > -120 * 1000) break;
			}
		}

		if (startIndex > 0) loadPrevEnabled = true;
		if (endIndex < journeys.size() - 1) loadNextEnabled = true;
	}

	// -----------------------------------------------------------------------------------------------

	public List<Journey> getJourneys() {
		return journeys;
	}

	// -----------------------------------------------------------------------------------------------

	public List<Journey> getVisibleJourneys() {
		return journeys.subList(startIndex, endIndex);
	}

	// -----------------------------------------------------------------------------------------------

	public int getCachedJourneysCount(int nextOrPrev) {
		if (nextOrPrev == ResultsActivity.NEXT) return journeys.size() - endIndex;
		else return startIndex;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public boolean isLoadNextEnabled() {
		return loadNextEnabled;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public boolean isLoadPrevEnabled() {
		return loadPrevEnabled;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public Journey loadNext() throws IOException, JourneyFinderException {
		if (endIndex < journeys.size()) {
			endIndex++;
			if (endIndex == journeys.size()) loadNextEnabled = false;
			return journeys.get(endIndex - 1);
		} else {
			return null;
		}
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public Journey loadPrev() throws IOException, JourneyFinderException {
		if (startIndex > 0) {
			startIndex--;
			if (startIndex == 0) loadPrevEnabled = false;
			return journeys.get(startIndex);
		} else {
			return null;
		}
	}

}
