package cz.fhejl.pubtran;

import java.io.Serializable;
import java.util.ArrayList;

import android.content.Context;

public class Favourites {

	public static void add(Favourite favourite, Context context) {
		DatabaseAdapter dbAdapter = new DatabaseAdapter(context).openAndLock();
		dbAdapter.favouritesAdd(favourite);
		dbAdapter.closeAndUnlock();
	}

	public static void delete(Favourite favourite, Context context) {
		DatabaseAdapter dbAdapter = new DatabaseAdapter(context).openAndLock();
		dbAdapter.favouritesDelete(favourite);
		dbAdapter.closeAndUnlock();
	}

	public static ArrayList<Favourite> getAll(Context context) {
		ArrayList<Favourite> favourites;
		DatabaseAdapter dbAdapter = new DatabaseAdapter(context).openAndLock();
		favourites = dbAdapter.favouritesGetAll();
		dbAdapter.closeAndUnlock();

		return favourites;
	}

	public static void update(Favourite favourite, Context context) {
		DatabaseAdapter dbAdapter = new DatabaseAdapter(context).openAndLock();
		dbAdapter.favouritesUpdate(favourite);
		dbAdapter.closeAndUnlock();
	}

	@SuppressWarnings("serial")
	public static class Favourite implements Serializable {
		private boolean swapped = false;
		private Integer id;
		private Integer order;
		private String departure;
		private String arrival;
		private String areaClass;

		public Favourite(String departure, String arrival, String areaClass, boolean swapped, int id, int order) {
			this.departure = departure;
			this.arrival = arrival;
			this.areaClass = areaClass;
			this.swapped = swapped;
			this.id = id;
			this.order = order;
		}

		public Favourite(String departure, String arrival, String areaClass) {
			this.departure = departure;
			this.arrival = arrival;
			this.areaClass = areaClass;
		}

		public String getAreaClass() {
			return areaClass;
		}

		public String getArrival() {
			return arrival;
		}

		public String getDeparture() {
			return departure;
		}

		public Integer getId() {
			return id;
		}

		public Integer getOrder() {
			return order;
		}

		public boolean isSwapped() {
			return swapped;
		}

		public void setOrder(Integer order) {
			this.order = order;
		}

		public void swap() {
			swapped = !swapped;
		}
	}

}
