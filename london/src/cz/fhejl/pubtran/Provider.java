package cz.fhejl.pubtran;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;

public class Provider extends AbstractProvider {

	private static String HOSTNAME = "http://journeyplanner.tfl.gov.uk/";

	// -----------------------------------------------------------------------------------------------

	@Override
	public Results findJourneys(AbstractSearchOptions abstractOptions) throws IOException, JourneyFinderException {
		SearchOptions options = (SearchOptions) abstractOptions;

		String typeOrigin = isPostCode(options.getFrom().trim()) ? "locator" : "stop";
		String typeDestination = isPostCode(options.getTo().trim()) ? "locator" : "stop";
		String from = URLEncoder.encode(options.getFrom(), "UTF-8");
		String to = URLEncoder.encode(options.getTo(), "UTF-8");

		Calendar when = options.getWhen();
		String hour = "" + when.get(Calendar.HOUR_OF_DAY);
		String minute = "" + when.get(Calendar.MINUTE);
		String day = "" + when.get(Calendar.DAY_OF_MONTH);
		String yearMonth = "" + when.get(Calendar.YEAR);
		int month = when.get(Calendar.MONTH) + 1;
		if (month < 10) yearMonth += "0";
		yearMonth += month;

		String routeType = null;
		switch (options.prefferedMode) {
		case SearchOptions.MODE_FASTEST:
			routeType = "LEASTTIME";
			break;
		case SearchOptions.MODE_FEWEST_CHANGES:
			routeType = "LEASTINTERCHANGE";
			break;
		case SearchOptions.MODE_LEAST_WALKING:
			routeType = "LEASTWALKING";
			break;
		}

		StringBuilder url = new StringBuilder(HOSTNAME + "user/XML_TRIP_REQUEST2?language=en");
		url.append("&ptOptionsActive=1&itOptionsActive=1&imparedOptionsActive=1");
		url.append("&ptAdvancedOptions=1&advOptActive_2=1&advOpt_2=1");
		url.append("&name_origin=").append(from);
		url.append("&type_origin=" + typeOrigin);
		url.append("&place_origin=");
		url.append("&name_destination=").append(to);
		url.append("&type_destination=" + typeDestination);
		url.append("&place_destination=");
		url.append("&itdDateDay=").append(day);
		url.append("&itdDateYearMonth=").append(yearMonth);
		url.append("&itdTimeHour=").append(hour);
		url.append("&itdTimeMinute=").append(minute);
		if (options.isTimeDeparture()) {
			url.append("&itdTripDateTimeDepArr=dep");
			url.append("&calcNumberOfTrips=7");
		} else {
			url.append("&itdTripDateTimeDepArr=arr");
		}
		url.append("&routeType=").append(routeType);

		url.append("&includedMeans=checkbox");
		url.append("&inclMOT_11=1");
		if (options.useRail) url.append("&inclMOT_0=on");
		if (options.useDLR) url.append("&inclMOT_1=on");
		if (options.useTube) url.append("&inclMOT_2=on");
		if (options.useTram) url.append("&inclMOT_4=on");
		if (options.useBus) url.append("&inclMOT_5=on&inclMOT_7=on");
		if (options.useRiver) url.append("&inclMOT_9=on");
		
		String changeSpeed = null;
		switch (options.walkingSpeed) {
		case SearchOptions.SPEED_AVERAGE:
			changeSpeed = "normal";
			break;
		case SearchOptions.SPEED_FAST:
			changeSpeed = "fast";
			break;
		case SearchOptions.SPEED_SLOW:
			changeSpeed = "slow";
			break;
		}
		url.append("&changeSpeed=").append(changeSpeed);

		InputStream is = IOUtils.doGetRequestReturnStream(url.toString());
		try {
			ArrayList<Journey> journeys = Parser.parseJourneys(is);
			if (journeys.size() == 0) throw new ParseException();
			return new Results(options, journeys, when, options.isTimeDeparture());
		} catch (JourneyFinderException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			throw new ParseException();
		}
	}

	// -----------------------------------------------------------------------------------------------

	private static boolean isPostCode(String s) {
		s = s.toUpperCase();
		if (s.matches("[A-Z]{1,2}[0-9R][0-9A-Z]? ?[0-9][ABD-HJLNP-UW-Z]{2}")) return true;
		else return false;
	}

}
