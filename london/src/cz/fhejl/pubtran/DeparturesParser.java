package cz.fhejl.pubtran;

import java.io.InputStream;
import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import cz.fhejl.pubtran.DeparturesParser.Departures.Line;
import cz.fhejl.pubtran.DeparturesParser.Departures.Platform;

public class DeparturesParser {

	private static long parseTime(String s) {
		SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy H:mm:ss", Locale.US);
		sdf.setTimeZone(TimeZone.getTimeZone("Europe/London"));
		Date date = null;
		try {
			date = sdf.parse(s);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return date.getTime();
	}

	public static Departures parse(InputStream is) throws Exception {
		Departures departures = new Departures();

		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
		Element root = document.getDocumentElement();

		Element whenCreatedElem = (Element) root.getElementsByTagName("WhenCreated").item(0);
		long whenCreated = parseTime(Utils.getTextContent(whenCreatedElem));

		NodeList platformElems = root.getElementsByTagName("P");
		for (int i = 0; i < platformElems.getLength(); i++) {
			Element platformElem = (Element) platformElems.item(i);

			Platform platform = new Platform();
			platform.name = platformElem.getAttribute("N");
			departures.platforms.add(platform);

			NodeList trainElems = platformElem.getElementsByTagName("T");
			for (int j = 0; j < trainElems.getLength(); j++) {
				Element trainElem = (Element) trainElems.item(j);

				Line line = new Line();
				line.destination = trainElem.getAttribute("Destination");
				line.when = whenCreated + 1000 * new Integer(trainElem.getAttribute("SecondsTo"));
				platform.lines.add(line);
			}
		}
		return departures;
	}

	@SuppressWarnings("serial")
	public static class Departures implements Serializable {
		public ArrayList<Platform> platforms = new ArrayList<Platform>();

		public static class Platform implements Serializable {
			public ArrayList<Line> lines = new ArrayList<Line>();
			public String name;
		}

		public static class Line implements Serializable {
			public long when;
			public String destination;
		}
	}

}
