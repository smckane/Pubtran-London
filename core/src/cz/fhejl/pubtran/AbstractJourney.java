package cz.fhejl.pubtran;

import java.io.Serializable;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.content.Context;
import android.view.View;

@SuppressWarnings("serial")
public abstract class AbstractJourney implements Serializable {

	public int durationInMinutes;
	public GregorianCalendar date;

	// -----------------------------------------------------------------------------------------------

	public abstract Calendar getArrivalTime();

	// -----------------------------------------------------------------------------------------------

	public abstract String getCalendarEventTitle(Context context);

	// -----------------------------------------------------------------------------------------------

	public abstract Calendar getDepartureTime();

	// -----------------------------------------------------------------------------------------------

	public abstract String getNotificationContent();

	// -----------------------------------------------------------------------------------------------

	public abstract View inflateView(AbstractActivity activity, boolean detailed);

	// -----------------------------------------------------------------------------------------------

	public abstract String toHumanReadableString(boolean withoutDiacritics);

	// -----------------------------------------------------------------------------------------------

	public abstract void update(AbstractActivity activity, View journeyView);

}