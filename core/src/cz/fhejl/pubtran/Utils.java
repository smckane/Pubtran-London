package cz.fhejl.pubtran;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.DOMException;
import org.w3c.dom.Node;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

public class Utils {

	private static Pattern timeRegex = Pattern.compile("(\\d+):(\\d+)");

	// -----------------------------------------------------------------------------------------------

	public static String formatDate(Calendar cal, Context context) {
		cal = (Calendar) cal.clone();
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);

		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);

		int days = (int) ((cal.getTimeInMillis() - today.getTimeInMillis()) / 1000 / 60 / 60 / 24);

		Resources res = context.getResources();

		if (days == 0) {
			return res.getString(R.string.today);
		} else if (days == 1) {
			return res.getString(R.string.tomorrow);
		} else if (days >= 2 && days <= 6) {
			switch (cal.get(Calendar.DAY_OF_WEEK)) {
			case Calendar.MONDAY:
				return res.getString(R.string.monday);
			case Calendar.TUESDAY:
				return res.getString(R.string.tuesday);
			case Calendar.WEDNESDAY:
				return res.getString(R.string.wednesday);
			case Calendar.THURSDAY:
				return res.getString(R.string.thursday);
			case Calendar.FRIDAY:
				return res.getString(R.string.friday);
			case Calendar.SATURDAY:
				return res.getString(R.string.saturday);
			case Calendar.SUNDAY:
				return res.getString(R.string.sunday);
			default:
				return null;
			}
		} else {
			return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(cal.getTime());
		}
	}

	// -----------------------------------------------------------------------------------------------

	public static String formatTime(Calendar calendar) {
		Locale locale = Locale.getDefault();

		// Android uses AM/PM for "en_GB" locale, using "cs" locale is a hack to avoid AM/PM.
		if (locale.toString().equals("en_GB")) locale = new Locale("cs");

		return DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(calendar.getTime());
	}

	// -----------------------------------------------------------------------------------------------

	public static Intent getIntent(Context context, String action) {
		return new Intent(context.getPackageName() + "." + action);
	}

	// -----------------------------------------------------------------------------------------------

	public static String getTextContent(Node node) throws DOMException {
		String textContent = "";

		if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
			textContent = node.getNodeValue();
		} else {
			Node child = node.getFirstChild();
			if (child != null) {
				Node sibling = child.getNextSibling();
				if (sibling != null) {
					StringBuffer sb = new StringBuffer();
					getTextContent(node, sb);
					textContent = sb.toString();
				} else {
					if (child.getNodeType() == Node.TEXT_NODE) {
						textContent = child.getNodeValue();
					} else {
						textContent = getTextContent(child);
					}
				}
			}
		}

		return textContent;
	}

	// -----------------------------------------------------------------------------------------------

	private static void getTextContent(Node node, StringBuffer sb) throws DOMException {
		Node child = node.getFirstChild();
		while (child != null) {
			if (child.getNodeType() == Node.TEXT_NODE) {
				sb.append(child.getNodeValue());
			} else {
				getTextContent(child, sb);
			}
			child = child.getNextSibling();
		}
	}

	// -----------------------------------------------------------------------------------------------

	public static int[] parseTime(String time) {
		Matcher m = timeRegex.matcher(time);
		m.find();
		int hours = Integer.parseInt(m.group(1));
		int minutes = Integer.parseInt(m.group(2));
		return new int[] { minutes, hours };
	}

	// -----------------------------------------------------------------------------------------------

	public static String removeDiacritics(String s) {
		s = s.replace('ě', 'e');
		s = s.replace('š', 's');
		s = s.replace('č', 'c');
		s = s.replace('ř', 'r');
		s = s.replace('ž', 'z');
		s = s.replace('ý', 'y');
		s = s.replace('á', 'a');
		s = s.replace('í', 'i');
		s = s.replace('é', 'e');
		s = s.replace('ť', 't');
		s = s.replace('ú', 'u');
		s = s.replace('ů', 'u');
		s = s.replace('ň', 'n');
		s = s.replace('ď', 'd');
		s = s.replace('ó', 'o');
		s = s.replace('ö', 'o');
		s = s.replace('ü', 'u');
		s = s.replace('Ě', 'E');
		s = s.replace('Š', 'S');
		s = s.replace('Č', 'C');
		s = s.replace('Ř', 'R');
		s = s.replace('Ž', 'Z');
		s = s.replace('Ý', 'Y');
		s = s.replace('Á', 'A');
		s = s.replace('Í', 'I');
		s = s.replace('É', 'E');
		s = s.replace('Ť', 'T');
		s = s.replace('Ú', 'U');
		s = s.replace('Ň', 'N');
		s = s.replace('Ď', 'D');
		s = s.replace('Ó', 'O');
		return s;
	}

	// -----------------------------------------------------------------------------------------------

	public static String simplify(String s) {
		s = removeDiacritics(s);
		s = s.replace(',', ' ');
		s = s.replace('.', ' ');
		s = s.replace('(', ' ');
		s = s.replace(')', ' ');
		s = s.replace('-', ' ');
		s = s.replace("  ", " ");
		s = s.replace("  ", " ");
		return s;
	}

}
