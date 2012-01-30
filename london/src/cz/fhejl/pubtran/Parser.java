package cz.fhejl.pubtran;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.GregorianCalendar;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.OSRef;
import cz.fhejl.pubtran.Journey.JourneyPart;
import cz.fhejl.pubtran.Journey.TransportMode;

public class Parser {

	public static ArrayList<Journey> parseJourneys(InputStream is) throws Exception {
		ArrayList<Journey> journeys = new ArrayList<Journey>();
		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
		Element root = document.getDocumentElement();
		NodeList itdRouteElems = root.getElementsByTagName("itdRoute");
		for (int i = 0; i < itdRouteElems.getLength(); i++) {
			ArrayList<Integer> disruptionHashCodes = new ArrayList<Integer>();
			Element itdRoute = (Element) itdRouteElems.item(i);
			ArrayList<JourneyPart> journeyParts = new ArrayList<JourneyPart>();
			NodeList itdPartialRouteElems = itdRoute.getElementsByTagName("itdPartialRoute");
			for (int j = 0; j < itdPartialRouteElems.getLength(); j++) {
				JourneyPart journeyPart = new JourneyPart();

				// dep and arr stops
				Element itdPartialRoute = (Element) itdPartialRouteElems.item(j);
				parseDepArrStops(itdPartialRoute.getElementsByTagName("itdPoint"), journeyPart);

				// transport modes
				journeyPart.transportModes = new ArrayList<TransportMode>();
				Element itdFrequencyInfo =
						(Element) itdPartialRoute.getElementsByTagName("itdFrequencyInfo").item(0);
				if (itdFrequencyInfo != null) {
					parseFrequencyInfo(itdFrequencyInfo, journeyPart);
				} else {
					journeyPart.transportModes.add(parseTransportMode((Element) itdPartialRoute
							.getElementsByTagName("itdMeansOfTransport").item(0)));
				}

				// disruption info
				NodeList infoLinkElems = itdPartialRoute.getElementsByTagName("infoLink");
				parseDisruptionInfo(infoLinkElems, journeyPart, disruptionHashCodes);

				journeyPart.simplifyStopNames();
				journeyParts.add(journeyPart);
			}

			Journey journey = new Journey(journeyParts);
			journeys.add(journey);
			System.out.println(journey.toString());
		}

		return journeys;
	}

	private static void parseDisruptionInfo(NodeList infoLinkElems, JourneyPart journeyPart,
			ArrayList<Integer> disruptionHashCodes) {
		journeyPart.disruptionInfos = new ArrayList<DisruptionInfo>();

		for (int i = 0; i < infoLinkElems.getLength(); i++) {
			Element infoLinkElem = (Element) infoLinkElems.item(i);

			int appearance = 0;
			String lastUpdate = "";
			NodeList paramElems = infoLinkElem.getElementsByTagName("param");
			for (int j = 0; j < paramElems.getLength(); j++) {
				Element paramElem = (Element) paramElems.item(j);
				String name = Utils.getTextContent(paramElem.getElementsByTagName("name").item(0));
				if (name.equals("lastModificationTime")) {
					lastUpdate = Utils.getTextContent(paramElem.getElementsByTagName("value").item(0));
				} else if (name.equals("appearance")) {
					appearance =
							Integer.parseInt(Utils.getTextContent(paramElem.getElementsByTagName("value").item(0)));
				}
			}

			int priority = DisruptionInfo.PRIORITY_NORMAL;
			if (appearance == 1) priority = DisruptionInfo.PRIORITY_HIGH;
			Element infoLinkTextElem = (Element) infoLinkElem.getElementsByTagName("infoLinkText").item(0);
			String info = Utils.getTextContent(infoLinkTextElem);

			if (!disruptionHashCodes.contains(info.hashCode())) {
				journeyPart.disruptionInfos.add(new DisruptionInfo(info, lastUpdate, priority));
				disruptionHashCodes.add(info.hashCode());
			}
		}
	}

	private static void parseDepArrStops(NodeList itdPointElems, JourneyPart journeyPart) {
		for (int i = 0; i < itdPointElems.getLength(); i++) {
			Element itdPoint = (Element) itdPointElems.item(i);
			String name = itdPoint.getAttribute("name");
			GregorianCalendar calendar =
					parseTimeDate((Element) itdPoint.getElementsByTagName("itdDateTime").item(0));

			Double lat = null;
			Double lon = null;
			if (!itdPoint.getAttribute("x").equals("")) {
				int x = Integer.parseInt(itdPoint.getAttribute("x"));
				int y = 1000000 - Integer.parseInt(itdPoint.getAttribute("y"));
				LatLng latLon = new OSRef(x, y).toLatLng();
				latLon.toWGS84();
				lat = latLon.getLat();
				lon = latLon.getLng();
			}

			if (itdPoint.getAttribute("usage").equals("departure")) {
				journeyPart.depStop = name;
				journeyPart.depTime = calendar;
				journeyPart.depLat = lat;
				journeyPart.depLon = lon;
			} else if (itdPoint.getAttribute("usage").equals("arrival")) {
				journeyPart.arrStop = name;
				journeyPart.arrTime = calendar;
				journeyPart.arrLat = lat;
				journeyPart.arrLon = lon;
			}
		}
	}

	private static void parseFrequencyInfo(Element itdFrequencyInfo, JourneyPart journeyPart) {
		String minTimeGap = itdFrequencyInfo.getAttribute("minTimeGap");
		String maxTimeGap = itdFrequencyInfo.getAttribute("maxTimeGap");
		if (!minTimeGap.equals("0")) {
			if (minTimeGap.equals(maxTimeGap)) journeyPart.frequency = minTimeGap;
			else journeyPart.frequency = minTimeGap + " - " + maxTimeGap;
		}

		NodeList itdMeansOfTransportElems = itdFrequencyInfo.getElementsByTagName("itdMeansOfTransport");

		for (int i = 0; i < itdMeansOfTransportElems.getLength(); i++) {
			Element itdMeansOfTransport = (Element) itdMeansOfTransportElems.item(i);
			TransportMode transportMode = parseTransportMode(itdMeansOfTransport);

			Element nextSibling = (Element) itdMeansOfTransport.getNextSibling();
			if (nextSibling != null && nextSibling.getTagName().equals("depPlatform")) {
				transportMode.depPlatform = nextSibling.getFirstChild().getNodeValue();
			}

			boolean duplicateFound = false;
			for (int j = 0; j < journeyPart.transportModes.size(); j++) {
				TransportMode tm = journeyPart.transportModes.get(j);
				if (tm.symbol.equals(transportMode.symbol)) {
					duplicateFound = true;
					tm.destinations.add(transportMode.destinations.get(0));
					break;
				}
			}
			if (!duplicateFound) journeyPart.transportModes.add(transportMode);
		}
	}

	private static TransportMode parseTransportMode(Element itdMeansOfTransport) {
		int type = parseType(itdMeansOfTransport.getAttribute("type"));
		String name = itdMeansOfTransport.getAttribute("name");
		String shortname = itdMeansOfTransport.getAttribute("shortname");
		String symbol = itdMeansOfTransport.getAttribute("symbol");
		String destination = itdMeansOfTransport.getAttribute("destination");
		if (destination.equals("")) destination = null;

		return new TransportMode(type, name, shortname, symbol, destination);
	}

	private static int parseType(String type) {
		Common.logd(type);
		switch (Integer.parseInt(type)) {
		case 1:
			return TransportMode.TUBE;
		case 2:
			return TransportMode.DLR;
		case 3:
			return TransportMode.BUS;
		case 4:
			return TransportMode.TRAM;
		case 6:
			return TransportMode.RAIL;
		case 10:
			return TransportMode.RIVER;
		case 12:
			return TransportMode.BUS;
		case 99:
			return TransportMode.WALK;
		case 100:
			return TransportMode.WALK;
		}
		return -1;
	}

	private static GregorianCalendar parseTimeDate(Element itdDateTime) {
		if (itdDateTime == null) return null;

		Element itdDate = (Element) itdDateTime.getElementsByTagName("itdDate").item(0);
		int year = Integer.parseInt(itdDate.getAttribute("year"));
		int month = Integer.parseInt(itdDate.getAttribute("month")) - 1;
		int day = Integer.parseInt(itdDate.getAttribute("day"));

		Element itdTime = (Element) itdDateTime.getElementsByTagName("itdTime").item(0);
		int hour = Integer.parseInt(itdTime.getAttribute("hour"));
		int minute = Integer.parseInt(itdTime.getAttribute("minute"));

		return new GregorianCalendar(year, month, day, hour, minute);
	}
}