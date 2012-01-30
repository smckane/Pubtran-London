package cz.fhejl.pubtran;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.TextAppearanceSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

@SuppressWarnings("serial")
public class Journey extends AbstractJourney {

	public static final int BUS = 0;
	public static final int TRAM = 1;
	public static final int TRAIN = 2;
	public static final int METRO_A = 3;
	public static final int METRO_B = 4;
	public static final int METRO_C = 5;
	public static final int TROLLEY = 6;
	public static final int UNKNOWN = 7;
	public static final int BOAT = 8;
	public static final int ROPEWAY = 9;
	public static final int PLANE = 10;

	private int cachedHashCode = 0;
	private ArrayList<JourneyPart> parts;

	// -----------------------------------------------------------------------------------------------

	public Journey(ArrayList<JourneyPart> parts) {
		this.parts = parts;
	}

	// -----------------------------------------------------------------------------------------------

	private String abbreviateTrainCompany(String s) {
		String company;
		String[] parts = s.split(" ", 3);
		if (parts.length == 3) company = parts[2];
		else company = parts[0];
		if (company.equals("National Express East Anglia")) return "Natinal Express E.A.";
		else return company;
	}

	// -----------------------------------------------------------------------------------------------

	private String formatTime(Context context, int hours, int minutes) {
		StringBuilder sb = new StringBuilder(10);
		if (hours > 0) sb.append(hours + " " + context.getString(R.string.hours) + " ");
		sb.append(minutes + " " + context.getString(R.string.minutes));
		return sb.toString();
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public Calendar getArrivalTime() {
		return parts.get(parts.size() - 1).arrTime;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public String getCalendarEventTitle(Context context) {
		return context.getString(R.string.calendarEventTitle, parts.get(0).depStop,
				parts.get(parts.size() - 1).arrStop);
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public Calendar getDepartureTime() {
		return parts.get(0).depTime;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public String getNotificationContent() {
		String startTime = Utils.formatTime(parts.get(0).depTime);
		String endTime = Utils.formatTime(parts.get(parts.size() - 1).arrTime);
		JourneyPart dep = parts.get(0);
		JourneyPart arr = parts.get(parts.size() - 1);
		return startTime + " " + dep.depStop + " ⇨ " + endTime + " " + arr.arrStop;
	}

	// -----------------------------------------------------------------------------------------------

	// if c2 is later than c1, returns positive value
	private int getMinuteDiff(Calendar c1, Calendar c2) {
		return (int) ((c2.getTimeInMillis() - c1.getTimeInMillis()) / (1000 * 60));
	}

	// -----------------------------------------------------------------------------------------------

	public int hashCode() {
		if (cachedHashCode == 0) {
			for (int i = 0; i < parts.size(); i++) {
				cachedHashCode += parts.get(i).depStop.hashCode();
				cachedHashCode += parts.get(i).depTime.getTime().getTime();
				cachedHashCode += parts.get(i).arrStop.hashCode();
				cachedHashCode += parts.get(i).arrTime.getTime().getTime();
			}
		}

		return cachedHashCode;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public View inflateView(AbstractActivity activity, boolean detailed) {
		LayoutInflater inflater = activity.getLayoutInflater();

		View journeyView = inflater.inflate(R.layout.journey, null);
		update(activity, journeyView);
		final Context context = inflater.getContext();
		int durationMinutes = getMinuteDiff(parts.get(0).depTime, parts.get(parts.size() - 1).arrTime);
		((TextView) journeyView.findViewById(R.id.tvDuration)).setText(context.getString(R.string.duration,
				formatTime(context, durationMinutes / 60, durationMinutes % 60)));

		LinearLayout llJourneyParts = (LinearLayout) journeyView.findViewById(R.id.llJourneyParts);
		for (int i = 0; i < parts.size(); i++) {
			final JourneyPart part = parts.get(i);
			int transportType = part.transportModes.get(0).type;

			LinearLayout llJourneyPart = (LinearLayout) inflater.inflate(R.layout.journey_part, null);

			TextView tvJourneyFromStop = (TextView) llJourneyPart.findViewById(R.id.tvJourneyFromStop);
			String depStop = part.depStop;
			String platform = part.transportModes.get(0).depPlatform;
			if (transportType == TransportMode.BUS && platform != null) {
				tvJourneyFromStop.setEllipsize(TextUtils.TruncateAt.MIDDLE);
				depStop += " (" + platform + ")";
				tvJourneyFromStop.setText(depStop, TextView.BufferType.SPANNABLE);
				Spannable spannable = (Spannable) tvJourneyFromStop.getText();
				int start = spannable.length() - 1 - platform.length();
				int end = start + platform.length();
				spannable.setSpan(new ForegroundColorSpan(0xff0077CC), start, end,
						Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
				tvJourneyFromStop.setText(spannable);
			} else {
				tvJourneyFromStop.setText(depStop);
			}

			TextView tvJourneyFromTime = (TextView) llJourneyPart.findViewById(R.id.tvJourneyFromTime);
			tvJourneyFromTime.setText(Utils.formatTime(part.depTime));

			TextView tvJourneyToStop = (TextView) llJourneyPart.findViewById(R.id.tvJourneyToStop);
			tvJourneyToStop.setText(part.arrStop);

			TextView tvJourneyToTime = (TextView) llJourneyPart.findViewById(R.id.tvJourneyToTime);
			tvJourneyToTime.setText(Utils.formatTime(part.arrTime));

			boolean highPriorityDisruption = false;
			for (DisruptionInfo disruptionInfo : part.disruptionInfos) {
				if (disruptionInfo.priority == DisruptionInfo.PRIORITY_HIGH) {
					highPriorityDisruption = true;
					break;
				}
			}

			if (detailed) {
				if (part.disruptionInfos.size() > 0) {
					Button btDisruption = (Button) llJourneyPart.findViewById(R.id.btDisruption);
					btDisruption.setVisibility(View.VISIBLE);
					if (highPriorityDisruption) btDisruption.setTextColor(0xff990000);
					btDisruption.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							Intent intent = new Intent(context, DisruptionsActivity.class);
							intent.putExtra("disruptions", part.disruptionInfos);
							context.startActivity(intent);
						}
					});
				}

				if (transportType == TransportMode.WALK && part.arrLat != null) {
					Button btDirections = (Button) llJourneyPart.findViewById(R.id.btDirections);
					btDirections.setVisibility(View.VISIBLE);
					btDirections.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							showWalkingDirections(part, context);
						}
					});
				}

				durationMinutes = getMinuteDiff(part.depTime, part.arrTime);
				String duration = "Duration " + formatTime(context, durationMinutes / 60, durationMinutes % 60);
				TextView tvDuration = (TextView) llJourneyPart.findViewById(R.id.tvDuration);
				tvDuration.setVisibility(View.VISIBLE);
				tvDuration.setText(duration);
			}

			// transport modes
			ArrayList<Integer> images = new ArrayList<Integer>();
			ArrayList<SpannableStringBuilder> texts = new ArrayList<SpannableStringBuilder>();

			for (int j = 0; j < part.transportModes.size(); j++) {
				TransportMode transportMode = part.transportModes.get(j);

				// image
				if (transportType == TransportMode.TUBE) {
					images.add(TransportMode.getTubeImage(transportMode.symbol));
				} else if (transportType == TransportMode.RAIL && transportMode.name.startsWith("LO ")) {
					images.add(R.drawable.overground);
				} else if (j == 0) {
					images.add(TransportMode.getTypeImage(part.transportModes.get(0).type));
				} else {
					images.add(null);
				}

				// name
				if (transportType == TransportMode.BUS) {
					if (detailed) {
						texts.add(new SpannableStringBuilder(transportMode.symbol));
					} else {
						SpannableStringBuilder text = (j == 0 ? new SpannableStringBuilder() : texts.get(0));
						int startIndex = text.length();
						int orStart = -1;
						int orEnd = -1;
						if (j > 0 && j == part.transportModes.size() - 1) {
							orStart = text.length() + 1;
							orEnd = orStart + 2;
							text.append(" or ");
						} else if (j > 0) {
							orStart = text.length();
							orEnd = orStart + 1;
							text.append(", ");
						}
						text.append(transportMode.symbol);

						text.setSpan(new TextAppearanceSpan(context, R.style.londonTransportName), startIndex,
								text.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
						if (orStart != -1) {
							text.setSpan(new TextAppearanceSpan(context, R.style.londonTransportNameOr), orStart,
									orEnd, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
						}

						if (texts.size() == 0) texts.add(text);
					}
				} else if (transportType == TransportMode.TUBE || transportType == TransportMode.DLR
						|| transportType == TransportMode.WALK) {
					texts.add(new SpannableStringBuilder());
				} else if (transportType == TransportMode.TRAM) {
					texts.add(new SpannableStringBuilder(transportMode.symbol));
				} else if (transportType == TransportMode.RAIL && transportMode.name.startsWith("LO ")) {
					texts.add(new SpannableStringBuilder());
				} else if (transportType == TransportMode.RAIL) {
					texts.add(new SpannableStringBuilder(abbreviateTrainCompany(transportMode.name)));
				} else {
					texts.add(new SpannableStringBuilder(transportMode.name));
				}

				SpannableStringBuilder text = texts.get(texts.size() - 1);
				if (text.length() > 0) {
					if (!(transportType == TransportMode.BUS && !detailed)) {
						text.setSpan(new TextAppearanceSpan(context, R.style.londonTransportName), 0,
								text.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
						text.append(" ");
					}
				}

				// destinations
				if (transportType != TransportMode.WALK && !(transportType == TransportMode.BUS && !detailed)) {
					int start = text.length();
					text.append("→ ");
					ArrayList<Integer> orIndexes = new ArrayList<Integer>();
					for (int k = 0; k < transportMode.destinations.size(); k++) {
						if (k > 0) {
							orIndexes.add(text.length() + 1);
							text.append(" or ");
						}
						text.append(transportMode.destinations.get(k));
					}

					text.setSpan(new TextAppearanceSpan(context, R.style.londonTransportDestination), start,
							text.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
					for (int index : orIndexes) {
						text.setSpan(new ForegroundColorSpan(0xffaaaaaa), index, index + 2,
								Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
					}
				}
			}

			LinearLayout llTransportModes = (LinearLayout) llJourneyPart.findViewById(R.id.llTransportModes);
			for (int j = 0; j < texts.size(); j++) {
				LinearLayout llTransportMode =
						(LinearLayout) inflater.inflate(R.layout.journey_part_transport, null);
				ImageView ivIcon = (ImageView) llTransportMode.findViewById(R.id.ivIcon);
				if (images.get(j) != null) ivIcon.setImageResource(images.get(j));
				else {
					ivIcon.setVisibility(View.GONE);
					llTransportMode.findViewById(R.id.tvOr).setVisibility(View.VISIBLE);
				}
				TextView tvLine = (TextView) llTransportMode.findViewById(R.id.tvLine);
				tvLine.setText(texts.get(j));
				if (detailed) tvLine.setSingleLine(false);

				if (j == 0 && highPriorityDisruption) llTransportMode.findViewById(R.id.ivDisruption)
						.setVisibility(View.VISIBLE);

				llTransportModes.addView(llTransportMode);
			}

			// frequency
			if (part.frequency != null) {
				TextView tvFrequency = (TextView) llJourneyPart.findViewById(R.id.tvFrequency);
				tvFrequency.setVisibility(View.VISIBLE);
				tvFrequency.setText("Buses every " + part.frequency + " mins");
				if (i != 0) tvJourneyFromTime.setVisibility(View.INVISIBLE);
				if (i != parts.size() - 1) tvJourneyToTime.setVisibility(View.INVISIBLE);
			}

			if (i == 0) llJourneyPart.findViewById(R.id.tvDivider).setVisibility(View.GONE);

			llJourneyParts.addView(llJourneyPart);
		}

		return journeyView;

	}

	// -----------------------------------------------------------------------------------------------

	private void showWalkingDirections(JourneyPart part, Context context) {
		String start = part.depLat + "," + part.depLon;
		String end = part.arrLat + "," + part.arrLon;
		Uri uri = Uri.parse("http://maps.google.com/maps?saddr=" + start + "&daddr=" + end + "&dirflg=w");
		Intent intent = new Intent(android.content.Intent.ACTION_VIEW, uri);
		context.startActivity(intent);
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public String toHumanReadableString(boolean withoutDiacritics) {
		Common.logd(Locale.getDefault().toString());
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			JourneyPart part = parts.get(i);

			if (part.transportModes.get(0).type == TransportMode.WALK) {
				sb.append("Walk from ").append(part.depStop).append(" to ").append(part.arrStop).append(".");
			} else {
				sb.append(Utils.formatTime(part.depTime)).append(" From ").append(part.depStop + " take");

				for (int j = 0; j < part.transportModes.size(); j++) {
					TransportMode mode = part.transportModes.get(j);
					if (j > 0) sb.append(" or");
					String name = mode.name;
					if (mode.type == TransportMode.RAIL) name = abbreviateTrainCompany(name);
					else if (mode.type == TransportMode.TUBE) name = "the " + mode.shortname + " line";
					sb.append(" " + name);
					for (int k = 0; k < mode.destinations.size(); k++) {
						if (k > 0) sb.append(" or");
						sb.append(" towards ").append(mode.destinations.get(k));
					}
				}

				sb.append(".\nGet off at ").append(part.arrStop).append(".");
			}

			if (i != parts.size() - 1) sb.append("\n\n");
		}

		String returnedString = sb.toString();
		if (withoutDiacritics) returnedString = Utils.removeDiacritics(returnedString);

		return returnedString;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("--------------------------------------------\n");
		for (JourneyPart part : parts) {
			sb.append(part.toString() + "\n");
		}

		return sb.toString();
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void update(AbstractActivity activity, View journeyView) {
		Calendar now = Calendar.getInstance();
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);
		int minuteDifference = getMinuteDiff(now, parts.get(0).depTime);

		Context context = journeyView.getContext();
		TextView tvLeavesIn = (TextView) journeyView.findViewById(R.id.tvLeavesIn);
		if (minuteDifference < 0) {
			tvLeavesIn.setText("");
		} else if (minuteDifference < 6 * 60) {
			tvLeavesIn.setText(context.getString(R.string.leavesIn,
					formatTime(context, (int) minuteDifference / 60, (int) minuteDifference % 60)));
		} else {
			tvLeavesIn.setText(Utils.formatDate(parts.get(0).depTime, context));
		}
	}

	// -----------------------------------------------------------------------------------------------

	protected static class TransportMode implements Serializable {
		public static int WALK = 0;
		public static int TUBE = 1;
		public static int BUS = 2;
		public static int COACH = 3;
		public static int DLR = 4;
		public static int RIVER = 5;
		public static int TRAM = 6;
		public static int RAIL = 7;

		public int type;
		public String depPlatform;
		public String name;
		public String shortname;
		public String symbol;
		public ArrayList<String> destinations = new ArrayList<String>();

		public TransportMode(int type, String name, String shortname, String symbol, String destination) {
			this.type = type;
			this.name = name;
			this.shortname = shortname;
			this.symbol = symbol;
			if (destination != null) this.destinations.add(destination);
		}

		public static int getTypeImage(int type) {
			if (type == WALK) return R.drawable.walk;
			else if (type == BUS) return R.drawable.bus;
			else if (type == COACH) return R.drawable.river;
			else if (type == DLR) return R.drawable.dlr;
			else if (type == RIVER) return R.drawable.river;
			else if (type == TRAM) return R.drawable.tram;
			else if (type == RAIL) return R.drawable.rail;
			return R.drawable.unknown;
		}

		public static int getTubeImage(String symbol) {
			if (symbol.startsWith("B")) return R.drawable.tube_bak;
			else if (symbol.startsWith("CEN")) return R.drawable.tube_cen;
			else if (symbol.startsWith("CIR")) return R.drawable.tube_cir;
			else if (symbol.startsWith("D")) return R.drawable.tube_dis;
			else if (symbol.startsWith("H")) return R.drawable.tube_ham;
			else if (symbol.startsWith("J")) return R.drawable.tube_jub;
			else if (symbol.startsWith("M")) return R.drawable.tube_met;
			else if (symbol.startsWith("N")) return R.drawable.tube_nor;
			else if (symbol.startsWith("P")) return R.drawable.tube_pic;
			else if (symbol.startsWith("V")) return R.drawable.tube_vic;
			else if (symbol.startsWith("W")) return R.drawable.tube_wat;
			return R.drawable.unknown;
		}

		@Override
		public String toString() {
			String from = "";
			if (depPlatform != null) from = " (stop " + depPlatform + ")";
			return symbol + from + "\n";
		}
	}

	// -----------------------------------------------------------------------------------------------

	protected static class JourneyPart implements Serializable {
		public ArrayList<DisruptionInfo> disruptionInfos;
		public ArrayList<TransportMode> transportModes;
		public Double arrLat;
		public Double arrLon;
		public Double depLat;
		public Double depLon;
		public GregorianCalendar arrTime;
		public GregorianCalendar depTime;
		public String arrStop;
		public String depStop;
		public String frequency; // e.g. "3 - 7" or "5" (in minutes)

		private String simplifyStopName(String stop) {
			if (stop.matches(".* \\([A-Z0-9]+\\)$")) {
				return stop.replaceFirst(" \\([A-Z0-9]+\\)$", "");
			} else if (stop.endsWith(" Underground Station")) {
				stop = stop.substring(0, stop.length() - 20);
				if (stop.endsWith("Line)")) stop = stop.substring(0, stop.lastIndexOf(" ("));
				return stop;
			} else if (stop.endsWith(" Rail Station")) {
				return stop.substring(0, stop.length() - 13);
			} else if (stop.endsWith(" DLR Station")) {
				return stop.substring(0, stop.length() - 12);
			} else if (stop.endsWith(" Bus Station")) {
				return stop.substring(0, stop.length() - 12);
			} else if (stop.endsWith(" Station")) {
				return stop.substring(0, stop.length() - 8);
			} else if (stop.endsWith(" London Tramlink Stop")) {
				return stop.substring(0, stop.length() - 21);
			} else {
				return stop;
			}
		}

		public void simplifyStopNames() {
			if (transportModes.get(0).type == TransportMode.WALK) return;
			depStop = simplifyStopName(depStop);
			arrStop = simplifyStopName(arrStop);
			for (TransportMode transportMode : transportModes) {
				for (int i = 0; i < transportMode.destinations.size(); i++) {
					transportMode.destinations.set(i, simplifyStopName(transportMode.destinations.get(i)));
				}
			}
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();

			String depTimeStr = depTime.get(Calendar.HOUR_OF_DAY) + ":" + depTime.get(Calendar.MINUTE);
			String arrTimeStr = arrTime.get(Calendar.HOUR_OF_DAY) + ":" + arrTime.get(Calendar.MINUTE);
			sb.append(depStop + " " + depTimeStr + "\n");
			sb.append(arrStop + " " + arrTimeStr + "\n");

			for (TransportMode transportMode : transportModes) {
				sb.append(transportMode.toString());
			}

			return sb.toString();
		}
	}

}
