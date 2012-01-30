package cz.fhejl.pubtran;

import java.util.ArrayList;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.OverlayItem;

import cz.fhejl.pubtran.Areas.Area;

public class GoogleMapView extends MapView {

	private static final int MIN_ZOOM_TO_SHOW_PINS = 15;

	private GeoPoint popupGeoPoint;
	private Handler handler = new Handler();
	private MapActivity activity;
	private MyLocationOverlay myLocationOverlay;
	private Stops stops;
	private StopsOverlay stopsOverlay;

	// -----------------------------------------------------------------------------------------------

	public GoogleMapView(MapActivity activity, String apiKey, Area area) {
		super(activity, apiKey);
		this.activity = activity;
		stops = Stops.getInstance(getContext(), area.className);
	}

	// -----------------------------------------------------------------------------------------------

	private Drawable boundPin(Drawable pin) {
		int width = pin.getIntrinsicWidth();
		int height = pin.getIntrinsicHeight();
		int left = -width * 11 / 38;
		int top = -height * 43 / 48;
		pin.setBounds(left, top, left + width, top + height);
		return pin;
	}

	// -----------------------------------------------------------------------------------------------

	private GeoPoint getGeoPoint(double latitude, double longitude) {
		return (new GeoPoint((int) (latitude * 1000000.0), (int) (longitude * 1000000.0)));
	}

	// -----------------------------------------------------------------------------------------------

	public void pause() {
		myLocationOverlay.disableMyLocation();
	}

	// -----------------------------------------------------------------------------------------------

	public void resume() {
		myLocationOverlay.enableMyLocation();
	}

	// -----------------------------------------------------------------------------------------------

	public void setZoom(int zoomLevel) {
		setClickable(true);

		getController().setZoom(zoomLevel);

		myLocationOverlay = new MyLocationOverlay(getContext(), this);
		myLocationOverlay.runOnFirstFix(new Runnable() {
			@Override
			public void run() {
				getController().animateTo(myLocationOverlay.getMyLocation());
			}
		});
		getOverlays().add(myLocationOverlay);

		stopsOverlay = new StopsOverlay(getResources().getDrawable(R.drawable.pin));
		getOverlays().add(stopsOverlay);
	}

	// -----------------------------------------------------------------------------------------------

	public void zoomIn() {
		getController().zoomIn();
	}

	// -----------------------------------------------------------------------------------------------

	public void zoomOut() {
		getController().zoomOut();
	}

	// -----------------------------------------------------------------------------------------------

	private class StopsOverlay extends ItemizedOverlay<OverlayItem> implements OnGestureListener,
			OnDoubleTapListener, Runnable {

		private double rectLeft;
		private double rectRight;
		private double rectTop;
		private double rectBottom;
		private ArrayList<OverlayItem> items = new ArrayList<OverlayItem>();
		private GestureDetector gestureDetector;
		private Thread thread;

		public StopsOverlay(Drawable defaultMarker) {
			super(boundPin(defaultMarker));

			handler.post(new Runnable() {
				@Override
				public void run() {
					gestureDetector = new GestureDetector(StopsOverlay.this);
					gestureDetector.setOnDoubleTapListener(StopsOverlay.this);
				}
			});

			double latitudeSpan = (double) getLatitudeSpan() / 1000000.0;
			double longitudeSpan = (double) getLongitudeSpan() / 1000000.0;
			rectLeft = (double) getMapCenter().getLongitudeE6() / 1000000.0 - 1.5 * longitudeSpan;
			rectRight = (double) getMapCenter().getLongitudeE6() / 1000000.0 + 1.5 * longitudeSpan;
			rectTop = (double) getMapCenter().getLatitudeE6() / 1000000.0 + 1.5 * latitudeSpan;
			rectBottom = (double) getMapCenter().getLatitudeE6() / 1000000.0 - 1.5 * latitudeSpan;

			ArrayList<Integer> stopIds = stops.getStopIdsInArea(rectBottom, rectTop, rectLeft, rectRight);
			for (int i = 0; i < stopIds.size(); i++) {
				int index = stopIds.get(i);
				double lat = stops.latitudes[index];
				double lon = stops.longitudes[index];
				OverlayItem item = new OverlayItem(getGeoPoint(lat, lon), stops.names[index], "");
				items.add(item);
			}
			populate();
		}

		@Override
		protected OverlayItem createItem(int i) {
			return items.get(i);
		}

		@Override
		public void draw(android.graphics.Canvas canvas, MapView mapView, boolean shadow) {
			activity.setZoomInEnabled(getZoomLevel() != getMaxZoomLevel());
			activity.setZoomOutEnabled(getZoomLevel() != 1);
			if (!shadow && mapView.getZoomLevel() >= MIN_ZOOM_TO_SHOW_PINS) {
				super.draw(canvas, mapView, shadow);

				if (activity.isPopupVisible()) {
					activity.updatePopup(getProjection().toPixels(popupGeoPoint, null));
				}

				double latitudeSpanHalf = (double) mapView.getLatitudeSpan() / 1000000.0 * 0.5;
				double longitudeSpanHalf = (double) mapView.getLongitudeSpan() / 1000000.0 * 0.5;
				double viewportLeft =
						(double) mapView.getMapCenter().getLongitudeE6() / 1000000.0 - longitudeSpanHalf;
				double viewportRight =
						(double) mapView.getMapCenter().getLongitudeE6() / 1000000.0 + longitudeSpanHalf;
				double viewportTop =
						(double) mapView.getMapCenter().getLatitudeE6() / 1000000.0 + latitudeSpanHalf;
				double viewportBottom =
						(double) mapView.getMapCenter().getLatitudeE6() / 1000000.0 - latitudeSpanHalf;
				if (rectLeft > viewportLeft || rectRight < viewportRight || rectTop < viewportTop
						|| rectBottom > viewportBottom) {
					if (thread == null || !thread.isAlive()) {
						thread = new Thread(this);
						thread.start();
					}
				}
			}
		}

		@Override
		public boolean onTouchEvent(MotionEvent event, MapView mapView) {
			if (gestureDetector == null) return false;
			else return gestureDetector.onTouchEvent(event);
		}

		@Override
		protected boolean onTap(int i) {
			if (activity.isPopupVisible()) {
				activity.hidePopup();
			} else if (getZoomLevel() >= MIN_ZOOM_TO_SHOW_PINS) {
				popupGeoPoint = getItem(i).getPoint();
				activity.showPopup(getItem(i).getTitle(), getProjection().toPixels(popupGeoPoint, null));
			}
			return true;
		}

		@Override
		public int size() {
			return items.size();
		}

		@Override
		public boolean onDown(MotionEvent e) {
			return false;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			return false;
		}

		@Override
		public void onLongPress(MotionEvent e) {
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
			return false;
		}

		@Override
		public void onShowPress(MotionEvent e) {
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			activity.hidePopup();
			return false;
		}

		@Override
		public boolean onDoubleTap(MotionEvent e) {
			getController().zoomInFixing((int) e.getX(), (int) e.getY());
			return true;
		}

		@Override
		public boolean onDoubleTapEvent(MotionEvent e) {
			return false;
		}

		@Override
		public boolean onSingleTapConfirmed(MotionEvent e) {
			return false;
		}

		@Override
		public void run() {
			final StopsOverlay newStopsOverlay = new StopsOverlay(getResources().getDrawable(R.drawable.pin));
			handler.post(new Runnable() {

				@Override
				public void run() {
					getOverlays().add(newStopsOverlay);
					getOverlays().remove(stopsOverlay);
					stopsOverlay = newStopsOverlay;
					invalidate();
				}
			});
		}
	}

}
