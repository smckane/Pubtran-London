package cz.fhejl.pubtran;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class LocationTool implements LocationListener {

	private static final int MAX_AGE = 10 * 60 * 1000;
	private static final int TWO_MINUTES = 1000 * 60 * 2;

	private boolean enabled = false;
	private BestLocationListener listener;
	private Context context;
	private Location currentBestLocation = null;
	private LocationManager locationManager;

	public LocationTool(final Context context, BestLocationListener listener) {
		this.context = context;
		this.listener = listener;

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
			class DeviceCompatible {
				public boolean testIfCompatible() {
					PackageManager pm = context.getPackageManager();
					if (!pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) return false;
					if (!pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK)) return false;
					return true;
				}
			}
			
			if(new DeviceCompatible().testIfCompatible() == false) return;
		}

		enabled = true;
		locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
	}

	public Location getLastKnownLocation() {
		if (!enabled) return null;
		Location lastGpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		Location lastNetworkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		if (lastGpsLocation == null) return lastNetworkLocation;
		if (lastNetworkLocation == null) return lastGpsLocation;
		if (lastGpsLocation.getTime() > lastNetworkLocation.getTime()) return lastGpsLocation;
		else return lastNetworkLocation;
	}

	private boolean isBetterLocation(Location location, Location currentBestLocation) {
		if (location == null) return false;
		if (isTooOld(location)) return false;
		if (currentBestLocation == null) return true;

		long timeDelta = location.getTime() - currentBestLocation.getTime();
		boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
		boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
		boolean isNewer = timeDelta > 0;

		if (isSignificantlyNewer) return true;
		else if (isSignificantlyOlder) return false;

		int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
		boolean isLessAccurate = accuracyDelta > 0;
		boolean isMoreAccurate = accuracyDelta < 0;
		boolean isSignificantlyLessAccurate = accuracyDelta > 400;

		boolean isFromSameProvider = isSameProvider(location.getProvider(), currentBestLocation.getProvider());

		if (isMoreAccurate) return true;
		else if (isNewer && !isLessAccurate) return true;
		else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) return true;
		else return false;
	}

	public boolean differsOnlyInTime(Location location1, Location location2) {
		if (location1 == null || location2 == null) return false;
		if (location1.getLatitude() == location2.getLatitude()
				&& location1.getLongitude() == location2.getLongitude()
				&& location1.getAccuracy() == location2.getAccuracy()) return true;
		else return false;
	}

	private boolean isSameProvider(String provider1, String provider2) {
		if (provider1 == null) return provider2 == null;
		else return provider1.equals(provider2);
	}

	public boolean isTooOld(Location location) {
		if (System.currentTimeMillis() - location.getTime() > MAX_AGE) return true;
		else return false;
	}

	public void onPause() {
		if (!enabled) return;
		locationManager.removeUpdates(this);
	}

	public void onResume() {
		Common.logd("location.onresume ");
		if (!enabled) return;

		currentBestLocation = null;
		boolean bestLocationUpdated = false;

		Location lastGpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		if (isBetterLocation(lastGpsLocation, currentBestLocation)) {
			Common.logd("isBetterLocation(lastGpsLocation, currentBestLocation)");
			currentBestLocation = lastGpsLocation;
			bestLocationUpdated = true;
		}

		Location lastNetworkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		if (isBetterLocation(lastNetworkLocation, currentBestLocation)) {
			Common.logd("isBetterLocation(lastNetworkLocation, currentBestLocation)");
			currentBestLocation = lastNetworkLocation;
			bestLocationUpdated = true;
		}

		if (bestLocationUpdated) listener.onBestLocationUpdated(currentBestLocation);

		boolean gpsEnabled =
				PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Common.PREF_GPS_ENABLED, false);
		if (gpsEnabled) locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500, 25, this);
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 500, 100, this);
		Common.logd("location.onresume end");
	}

	@Override
	public void onLocationChanged(Location location) {
		Common.logd("onLocationChanged");
		Common.logd(location.getAccuracy() + " " + location.getTime());
		if (isBetterLocation(location, currentBestLocation)) {
			currentBestLocation = location;
			listener.onBestLocationUpdated(currentBestLocation);
		}
		Common.logd("onLocationChanged end");
	}

	@Override
	public void onProviderDisabled(String provider) {
	}

	@Override
	public void onProviderEnabled(String provider) {
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	public static interface BestLocationListener {
		public void onBestLocationUpdated(Location location);
	}

}
