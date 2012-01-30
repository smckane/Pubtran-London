package cz.fhejl.pubtran;

import java.io.Serializable;
import java.util.Calendar;

import android.content.SharedPreferences;
import android.view.View;

@SuppressWarnings("serial")
public abstract class AbstractSearchOptions implements Serializable {

	private boolean timeIsDeparture;
	private Calendar when;
	private String from;
	private String to;
	private String areaIdentifier;
	
	public abstract void loadFromSharedPreferences(SharedPreferences preferences, MainActivity activity);

	public abstract void onPause(SharedPreferences preferences);

	public abstract void onResume();

	public abstract void reset(MainActivity activity);

	public abstract void show(MainActivity activity, View button);

	public String getFrom() {
		return from;
	}
	
	public String getTo() {
		return to;
	}
	
	public Calendar getWhen() {
		return when;
	}
	
	public String getAreaIdentifier() {
		return areaIdentifier;
	}
	
	public boolean isTimeDeparture() {
		return timeIsDeparture;
	}
	
	public void setBasicOptions(String from, String to, Calendar when, boolean timeIsDeparture,
			String areaIdentifier) {
		this.from = from;
		this.to = to;
		this.when = when;
		this.timeIsDeparture = timeIsDeparture;
		this.areaIdentifier = areaIdentifier;
	}

}
