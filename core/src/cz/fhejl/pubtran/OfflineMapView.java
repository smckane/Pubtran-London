package cz.fhejl.pubtran;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

public class OfflineMapView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

	private static final float SCROLL_FRICTION = 5000f;

	private boolean dimensionsKnown = false;
	private boolean redrawIsRequested;
	private boolean screenTouched = false;
	private boolean threadShouldStop = false;
	private float biggest_zoom;
	private float smallest_zoom;
	private Animation animation = null;
	private Float zoom;
	private GestureDetector gestureDetector;
	private OfflineMapSource dataSource;
	private Point center;
	private PinchTracker pinchTracker = new PinchTracker();
	private MapActivity activity;
	private ScrollTracker scrollTracker = new ScrollTracker();
	private SurfaceHolder surfaceHolder;
	private Thread thread;

	// -----------------------------------------------------------------------------------------------

	public OfflineMapView(MapActivity activity) {
		super(activity);
		this.activity = activity;

		dataSource = new OfflineMapSource(this);

		GestureListener gestureListener = new GestureListener();
		gestureDetector = new GestureDetector(gestureListener);
		gestureDetector.setOnDoubleTapListener(gestureListener);

		surfaceHolder = getHolder();
		surfaceHolder.addCallback(this);
	}

	// -----------------------------------------------------------------------------------------------

	private void adjustMapPosition() {
		int horizontal = (int) ((float) getWidth() / 2 * zoom);
		int vertical = (int) ((float) getHeight() / 2 * zoom);

		if (center.x < horizontal) center.x = horizontal;
		else if (center.x > dataSource.getMapWidth() - horizontal) center.x =
				dataSource.getMapWidth() - horizontal;
		if (center.y < vertical) center.y = vertical;
		else if (center.y > dataSource.getMapHeight() - vertical) center.y = dataSource.getMapHeight() - vertical;
	}

	// -----------------------------------------------------------------------------------------------

	private void calculateMaxAndMinZoom() {
		biggest_zoom =
				Math.min(dataSource.getMapHeight() / (float) getHeight(), dataSource.getMapWidth()
						/ (float) getWidth());
		DisplayMetrics metrics = new DisplayMetrics();
		((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(
				metrics);
		smallest_zoom = dataSource.getMinZoomMdpi() / metrics.density;
	}

	// -----------------------------------------------------------------------------------------------

	public void destroy() {
		dataSource.destroy();
	}

	// -----------------------------------------------------------------------------------------------

	private Point getMapCoord(PointF pointOnSreen) {
		return new Point((int) (center.x + (pointOnSreen.x - getWidth() / 2f) * zoom),
				(int) (center.y + (pointOnSreen.y - getHeight() / 2f) * zoom));
	}

	// -----------------------------------------------------------------------------------------------

	public boolean onTouchEvent(MotionEvent e) {
		if (!dimensionsKnown) return true;

		if (dataSource.getState() != OfflineMapSource.READY) return true;

		if (e.getPointerCount() == 1) gestureDetector.onTouchEvent(e);

		switch (e.getAction() & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN: {
			screenTouched = true;
			thread.interrupt();
			if (animation instanceof KineticScrollAnimation) animation = null;
			scrollTracker.start(e.getX(), e.getY());
			break;
		}

		case MotionEvent.ACTION_MOVE: {
			if (e.getPointerCount() == 1) {
				scrollTracker.update(e.getX(), e.getY());
			} else if (e.getPointerCount() == 2) {
				pinchTracker.update(e.getX(0), e.getY(0), e.getX(1), e.getY(1));
			}
			break;
		}

		case MotionEvent.ACTION_UP: {
			screenTouched = false;
			break;
		}

		case MotionEvent.ACTION_POINTER_DOWN: {
			if (e.getPointerCount() == 2) {
				pinchTracker.start(e.getX(0), e.getY(0), e.getX(1), e.getY(1));
			}
			break;
		}

		case MotionEvent.ACTION_POINTER_UP: {
			int upPointerIndex =
					(e.getAction() & MotionEvent.ACTION_POINTER_ID_MASK) >> MotionEvent.ACTION_POINTER_ID_SHIFT;
			if (e.getPointerCount() == 2) {
				int index = 1 - upPointerIndex;
				scrollTracker.start(e.getX(index), e.getY(index));
			}
			break;
		}
		}

		return true;
	}

	// -----------------------------------------------------------------------------------------------

	public void requestRedraw() {
		redrawIsRequested = true;
		if (thread != null) thread.interrupt();
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void run() {
		int c = 0;
		long t = System.currentTimeMillis();
		while (!threadShouldStop) {
			Canvas canvas = null;
			boolean redrawSuccessful = false;
			try {
				canvas = surfaceHolder.lockCanvas(null);

				if (animation != null) {
					long time = System.currentTimeMillis();
					boolean isFinished = animation.update(time);
					if (isFinished) animation = null;
				}

				canvas.drawRGB(255, 255, 255);
				if (dataSource.getState() == OfflineMapSource.READY) dataSource.drawTiles(canvas, center, zoom,
						getWidth(), getHeight());
				redrawSuccessful = true;
			} finally {
				if (canvas != null) {
					surfaceHolder.unlockCanvasAndPost(canvas);
				}
			}
			if (!redrawSuccessful) redrawIsRequested = true;

			c++;
			long d = System.currentTimeMillis() - t;
			if (d > 500) {
				//Common.logd("FPS = " + (int) ((1000.0 / d) * c));
				c = 0;
				t = System.currentTimeMillis();
			}

			if (!redrawIsRequested && animation == null && screenTouched == false) {
				synchronized (this) {
					try {
						wait();
					} catch (InterruptedException e) {
					}
				}
			}

			redrawIsRequested = false;
		}
	}

	// -----------------------------------------------------------------------------------------------

	public boolean setMap(String mapId) {
		boolean success = dataSource.setMap(mapId);
		if (success) {
			center = new Point(dataSource.getMapWidth() / 2, dataSource.getMapHeight() / 2);
			activity.setZoomInEnabled(true);
			activity.setZoomOutEnabled(false);
			if (dimensionsKnown) {
				calculateMaxAndMinZoom();
				zoom = biggest_zoom;
			}
		}

		requestRedraw();
		return success;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if (dataSource.getState() == OfflineMapSource.READY) calculateMaxAndMinZoom();
		if (!dimensionsKnown) {
			dimensionsKnown = true;
			if (zoom == null) zoom = biggest_zoom;
		}
		requestRedraw();
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		threadShouldStop = false;
		thread = new Thread(this);
		thread.start();
		thread.setPriority(10);
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		boolean retry = true;
		threadShouldStop = true;
		thread.interrupt();
		while (retry) {
			try {
				thread.join();
				retry = false;
			} catch (InterruptedException e) {
			}
		}
	}

	// -----------------------------------------------------------------------------------------------

	private void updateMapCenter(Point mapCoord, PointF correspondingScreenPoint) {
		center.x = (int) (mapCoord.x - correspondingScreenPoint.x * zoom + (getWidth() / 2) * zoom);
		center.y = (int) (mapCoord.y - correspondingScreenPoint.y * zoom + (getHeight() / 2) * zoom);
	}

	// -----------------------------------------------------------------------------------------------

	public void zoomIn() {
		if (!dimensionsKnown) return;
		zoomIn(new PointF(getWidth() / 2, getHeight() / 2));
	}

	// -----------------------------------------------------------------------------------------------

	public void zoomIn(PointF focalPoint) {
		if (!dimensionsKnown) return;
		ZoomAnimation animation = new ZoomAnimation(System.currentTimeMillis(), 200, 2, focalPoint, true);
		this.animation = animation;
		thread.interrupt();
	}

	// -----------------------------------------------------------------------------------------------

	public void zoomOut() {
		if (!dimensionsKnown) return;
		ZoomAnimation animation =
				new ZoomAnimation(System.currentTimeMillis(), 200, 2, new PointF(getWidth() / 2, getHeight() / 2),
						false);
		this.animation = animation;
		thread.interrupt();
	}

	// -----------------------------------------------------------------------------------------------

	private class PinchTracker {

		private Point middleMapCoord;
		private PointF startMiddle;
		private float startDistance;
		private float startZoom;

		private float getDistance(PointF a, PointF b) {
			return (float) Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
		}

		public void start(float x1, float y1, float x2, float y2) {
			startMiddle = new PointF(x1 + (x2 - x1) / 2, y1 + (y2 - y1) / 2);
			startDistance = getDistance(new PointF(x1, y1), new PointF(x2, y2));
			startZoom = zoom;
			middleMapCoord = getMapCoord(startMiddle);
		}

		public void update(float x1, float y1, float x2, float y2) {
			float newZoom = startZoom / (getDistance(new PointF(x1, y1), new PointF(x2, y2)) / startDistance);
			if (zoom == smallest_zoom && newZoom > zoom) activity.setZoomInEnabled(true);
			if (zoom == biggest_zoom && newZoom < zoom) activity.setZoomOutEnabled(true);
			zoom = newZoom;

			if (zoom <= smallest_zoom) {
				zoom = smallest_zoom;
				activity.setZoomInEnabled(false);
			}
			if (zoom >= biggest_zoom) {
				zoom = biggest_zoom;
				activity.setZoomOutEnabled(false);
			}

			// PointF middle = new PointF(x1 + (x2 - x1) / 2, y1 + (y2 - y1) / 2);
			updateMapCenter(middleMapCoord, startMiddle);

			adjustMapPosition();
		}

	}

	// -----------------------------------------------------------------------------------------------

	private class ScrollTracker {

		private PointF startTouch;
		private Point startCenter;

		public void start(float x, float y) {
			startTouch = new PointF(x, y);
			startCenter = new Point(center);
		}

		public void update(float x, float y) {
			center.x = (int) (startCenter.x - (x - startTouch.x) * zoom);
			center.y = (int) (startCenter.y - (y - startTouch.y) * zoom);

			int horizontal = (int) ((float) getWidth() / 2 * zoom);
			int vertical = (int) ((float) getHeight() / 2 * zoom);

			if (center.x < horizontal) center.x = horizontal;
			else if (center.x > dataSource.getMapWidth() - horizontal) center.x =
					dataSource.getMapWidth() - horizontal;
			if (center.y < vertical) center.y = vertical;
			else if (center.y > dataSource.getMapHeight() - vertical) center.y =
					dataSource.getMapHeight() - vertical;
		}

	}

	// -----------------------------------------------------------------------------------------------

	private abstract class Animation {
		protected int duration;
		protected long startTime;

		public Animation(long startTime) {
			this.startTime = startTime;
		}

		public Animation(long startTime, int duration) {
			this.startTime = startTime;
			this.duration = duration;
		}

		public abstract boolean update(long time);
	}

	// -----------------------------------------------------------------------------------------------

	private class ZoomAnimation extends Animation {
		private boolean zoomIn;
		private float initialZoom;
		private float zoomMultiplier;
		private Point mapCoord;
		private PointF focalPoint;

		public ZoomAnimation(long startTime, int duration, float zoomMultiplier, PointF focalPoint, boolean zoomIn) {
			super(startTime, duration);
			this.zoomIn = zoomIn;
			this.zoomMultiplier = zoomMultiplier;
			initialZoom = zoom;
			this.focalPoint = focalPoint;
			mapCoord = new Point(getMapCoord(focalPoint));

			if (zoomIn) activity.setZoomOutEnabled(true);
			else activity.setZoomInEnabled(true);
		}

		public boolean update(long time) {
			double a = Math.pow(zoomMultiplier, 1 / ((float) duration / (time - startTime)));
			boolean isFinished = false;
			if (zoomIn) {
				zoom = (float) (initialZoom / a);
				if (zoom <= smallest_zoom) {
					zoom = smallest_zoom;
					activity.setZoomInEnabled(false);
					isFinished = true;
				} else if (zoom <= initialZoom / zoomMultiplier) {
					zoom = initialZoom / zoomMultiplier;
					isFinished = true;
				}
			} else {
				zoom = (float) (initialZoom * a);
				if (zoom >= biggest_zoom) {
					zoom = biggest_zoom;
					activity.setZoomOutEnabled(false);
					isFinished = true;
				} else if (zoom >= initialZoom * zoomMultiplier) {
					zoom = initialZoom * zoomMultiplier;
					isFinished = true;
				}
			}
			updateMapCenter(mapCoord, focalPoint);
			adjustMapPosition();
			return isFinished;
		}
	}

	// -----------------------------------------------------------------------------------------------

	private class KineticScrollAnimation extends Animation {
		private double startSpeed;
		private double xCoeff;
		private double yCoeff;
		private Point startCenter;

		public KineticScrollAnimation(long startTime, float velocityX, float velocityY) {
			super(startTime);
			startCenter = new Point(center);
			startSpeed = Math.sqrt(velocityX * velocityX + velocityY * velocityY);
			xCoeff = velocityX / startSpeed;
			yCoeff = velocityY / startSpeed;
		}

		@Override
		public boolean update(long time) {
			double secondsSinceStart = (time - startTime) / 1000.0;
			double speed = startSpeed - SCROLL_FRICTION * secondsSinceStart;
			if (speed <= 0) return true;
			double distance = (startSpeed + speed) / 2 * secondsSinceStart;
			double xDelta = xCoeff * distance;
			double yDelta = yCoeff * distance;
			center.x = (int) (startCenter.x - xDelta * zoom);
			center.y = (int) (startCenter.y - yDelta * zoom);

			int horizontal = (int) ((float) getWidth() / 2 * zoom);
			int vertical = (int) ((float) getHeight() / 2 * zoom);
			if (center.x < horizontal) center.x = horizontal;
			else if (center.x > dataSource.getMapWidth() - horizontal) center.x =
					dataSource.getMapWidth() - horizontal;
			if (center.y < vertical) center.y = vertical;
			else if (center.y > dataSource.getMapHeight() - vertical) center.y =
					dataSource.getMapHeight() - vertical;

			return false;
		}
	}

	// -----------------------------------------------------------------------------------------------

	private class GestureListener extends SimpleOnGestureListener {

		@Override
		public boolean onDoubleTap(MotionEvent e) {
			zoomIn(new PointF(e.getX(), e.getY()));
			return false;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			KineticScrollAnimation animation =
					new KineticScrollAnimation(System.currentTimeMillis(), velocityX, velocityY);
			OfflineMapView.this.animation = animation;
			thread.interrupt();
			return false;
		}

	}
}
