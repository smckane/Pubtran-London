package cz.fhejl.pubtran;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.content.Context;

@SuppressWarnings("serial")
public class TubeStatus implements Serializable {

	public static final int STATUS_GOOD_SERVICE = 0;
	public static final int STATUS_MINOR_DELAYS = 1;
	public static final int STATUS_SEVERE_DELAYS = 2;
	public static final int STATUS_PLANNED_CLOSURE = 3;
	public static final int STATUS_PART_CLOSURE = 4;
	public static final int STATUS_CLOSED = 5;
	public static final int STATUS_UNKNOWN = 6;
	public static final int STATUS_SUSPENDED = 7;

	public ArrayList<Line> lines = new ArrayList<TubeStatus.Line>();

	private static final int[] COLORS = { 0xffae6118, 0xffe41f1f, 0xfff8d42d, 0xff00a575, 0xffe899a8,
			0xff8f989e, 0xff893267, 0xff000000, 0xff0450a1, 0xff009fe0, 0xff70c3ce, 0xff00bbb4, 0xfff86c00 };
	private static final String FILENAME = "tube_status.dat";
	private static final String HOSTNAME = "http://m.tfl.gov.uk";
	private static final String URL = "http://m.tfl.gov.uk/mt/www.tfl.gov.uk/tfl/livetravelnews/realtime/"
			+ "tube/default.html?un_jtt_v_lines=yes";
	private static final String[] NAMES = { "Bakerloo", "Central", "Circle", "District",
			"Hammersmith & City", "Jubilee", "Metropolitan", "Northern", "Piccadilly", "Victoria",
			"Waterloo & City", "DLR", "Overground" };

	// -----------------------------------------------------------------------------------------------

	public TubeStatus() {
		for (int i = 0; i < NAMES.length; i++) {
			Line line = new Line();
			line.name = NAMES[i];
			line.color = COLORS[i];
			lines.add(line);
		}
	}

	// -----------------------------------------------------------------------------------------------

	public static void downloadDetails(final Line line, final String url, ArrayList<Thread> threads) {
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					String html = IOUtils.doGetRequest(url);
					line.details = parseDetails(html);
				} catch (Exception e) {
					e.printStackTrace();
					line.details = "";
				}
			}
		});
		threads.add(thread);
		thread.start();
	}

	// -----------------------------------------------------------------------------------------------

	public static TubeStatus downloadTubeStatus(Context context) throws Exception {
		TubeStatus tubeStatus = new TubeStatus();
		ArrayList<Thread> threads = new ArrayList<Thread>();

		String html = IOUtils.doGetRequest(URL);
		Document doc = Jsoup.parse(html);

		Elements rows = doc.select("div.un_table_width_problems");
		if (rows.size() == 0) throw new Exception("No div.un_table_width_problems elements found.");
		for (Element row : rows) {
			String name = row.select("div.ltn-name > b").first().text().trim();
			Line line = findLineByName(tubeStatus.lines, name);

			Element problems = row.select("div.status, div.delay_problems, div.delay_problems_white").first();
			String status = problems.text().trim();
			line.status = status;
			line.statusCode = parseStatusCode(status);

			Element link = problems.select("a").first();
			if (link != null) downloadDetails(line, HOSTNAME + link.attr("href").trim(), threads);
		}

		for (Thread thread : threads)
			thread.join();

		tubeStatus.save(context);

		return tubeStatus;
	}

	// -----------------------------------------------------------------------------------------------

	private static Line findLineByName(ArrayList<Line> lines, String name) {
		for (Line line : lines) {
			if (line.name.equals(name)) return line;
			if (line.name.charAt(0) == 'H' && name.charAt(0) == 'H') return line;
			if (line.name.charAt(0) == 'W' && name.charAt(0) == 'W') return line;
		}
		return null;
	}

	// -----------------------------------------------------------------------------------------------

	public static TubeStatus getTubeStatus(Context context) {
		try {
			FileInputStream fis = context.openFileInput(FILENAME);
			ObjectInputStream ois = new ObjectInputStream(fis);
			TubeStatus tubeStatus = (TubeStatus) ois.readObject();
			fis.close();
			return tubeStatus;
		} catch (Exception e) {
			return new TubeStatus();
		}
	}

	// -----------------------------------------------------------------------------------------------

	private static String parseDetails(String html) {
		Document doc = Jsoup.parse(html);
		StringBuilder message = new StringBuilder();

		for (Element element : doc.select("div.message > div")) {
			message.append(element.text()).append("\n");
		}

		return message.toString();
	}

	// -----------------------------------------------------------------------------------------------

	private static int parseStatusCode(String s) {
		if (s.startsWith("Good")) return STATUS_GOOD_SERVICE;
		else if (s.startsWith("Minor")) return STATUS_MINOR_DELAYS;
		else if (s.startsWith("Severe")) return STATUS_SEVERE_DELAYS;
		else if (s.startsWith("Part")) return STATUS_PART_CLOSURE;
		else if (s.startsWith("Planned")) return STATUS_PLANNED_CLOSURE;
		else if (s.startsWith("Closed")) return STATUS_CLOSED;
		else if (s.startsWith("Suspended")) return STATUS_SUSPENDED;
		else return STATUS_UNKNOWN;
	}

	// -----------------------------------------------------------------------------------------------

	public void save(Context context) throws IOException {
		FileOutputStream fos = context.openFileOutput(FILENAME, Context.MODE_PRIVATE);
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(this);
		fos.close();
	}

	// -----------------------------------------------------------------------------------------------

	public static class Line implements Serializable {
		public int color;
		public int statusCode = STATUS_UNKNOWN;
		public String details;
		public String name;
		public String status = "Unknown status";
	}
}
