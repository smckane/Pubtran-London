package cz.fhejl.pubtran;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;

public class OfflineMapSource {

	public static int READY = 0;
	public static int NOT_READY = 1;

	private boolean threadsShouldStop;
	private float minZoomMdpi;
	private int bitmapCacheSize;
	private int mapHeight;
	private int mapWidth;
	private int maxHardZoom;
	private int state = NOT_READY;
	private ArrayList<Tile> tilesToDraw = new ArrayList<OfflineMapSource.Tile>();
	private Bitmap emptyTileBitmap;
	private BitmapLoaderThread bitmapLoaderThread;
	private File mapsDir;
	private Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
	private Pool<IntWrapper> intWrapperPool = new Pool<IntWrapper>(IntWrapper.class);
	private Pool<Rect> rectPool = new Pool<Rect>(Rect.class);
	private String mapId;
	private TileCache tileCache = new TileCache();
	private OfflineMapView mapView;

	// -----------------------------------------------------------------------------------------------

	public OfflineMapSource(OfflineMapView mapView) {
		this.mapView = mapView;

		Context context = mapView.getContext();
		emptyTileBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.empty_tile);
		mapsDir = IOUtils.getMapsDirectory(context);

		int heapSize = (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024));
		if (heapSize < 24) bitmapCacheSize = 40; // 16 MB
		else if (heapSize < 32) bitmapCacheSize = 80; // 24 MB
		else bitmapCacheSize = 120; // 32 MB and more

		// Create .nomedia file which will "hide" the directory from photo
		// gallery apps
		File nomedia = new File(mapsDir, ".nomedia");
		nomedia.getParentFile().mkdirs();
		try {
			nomedia.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Rect borrowRect(int left, int top, int right, int bottom) {
		Rect rect = rectPool.borrow();
		rect.set(left, top, right, bottom);
		return rect;
	}

	// -----------------------------------------------------------------------------------------------

	public void drawTiles(Canvas canvas, Point center, float zoom, int canvasWidth, int canvasHeight) {
		int hardZoom;
		if (zoom < 2) hardZoom = 0;
		else if (zoom < 4) hardZoom = 1;
		else if (zoom < 8) hardZoom = 2;
		else if (zoom < 16) hardZoom = 3;
		else if (zoom < 32) hardZoom = 4;
		else if (zoom < 64) hardZoom = 5;
		else hardZoom = 6;

		int granulanity = (int) Math.pow(2, hardZoom);
		int tileSize = 256 * granulanity;
		int scaledTileSize = (int) (256 / (zoom / granulanity));
		float adjustedWidth = canvasWidth * zoom;
		float adjustedHeight = canvasHeight * zoom;
		int topLeftTileX = (int) ((center.x - adjustedWidth / 2) / tileSize);
		if (topLeftTileX < 0) topLeftTileX = 0;
		int topLeftTileY = (int) ((center.y - adjustedHeight / 2) / tileSize);
		if (topLeftTileY < 0) topLeftTileY = 0;
		int bottomRightTileX = (int) ((center.x + adjustedWidth / 2) / tileSize);
		if (bottomRightTileX > (mapWidth - 1) / tileSize) bottomRightTileX = (mapWidth - 1) / tileSize;
		int bottomRightTileY = (int) ((center.y + adjustedHeight / 2) / tileSize);
		if (bottomRightTileY > (mapHeight - 1) / tileSize) bottomRightTileY = (mapHeight - 1) / tileSize;
		int offsetX = (int) ((topLeftTileX * tileSize - (center.x - adjustedWidth / 2)) / zoom);
		int offsetY = (int) ((topLeftTileY * tileSize - (center.y - adjustedHeight / 2)) / zoom);

		bitmapLoaderThread.resetPriorities();

		for (int x = topLeftTileX; x <= bottomRightTileX; x++) {
			for (int y = topLeftTileY; y <= bottomRightTileY; y++) {
				int positionX = (x - topLeftTileX) * scaledTileSize + offsetX;
				int positionY = (y - topLeftTileY) * scaledTileSize + offsetY;

				Tile tile = tileCache.getTile(x, y, hardZoom, true, 2);
				if (tile.getBitmap() == null) {
					Tile tile1 = tileCache.getTile(x * 2, y * 2, hardZoom - 1, false);
					Tile tile2 = tileCache.getTile(x * 2 + 1, y * 2, hardZoom - 1, false);
					Tile tile3 = tileCache.getTile(x * 2, y * 2 + 1, hardZoom - 1, false);
					Tile tile4 = tileCache.getTile(x * 2 + 1, y * 2 + 1, hardZoom - 1, false);
					if (tile1.getBitmap() != null && tile2.getBitmap() != null && tile3.getBitmap() != null
							& tile4.getBitmap() != null) {
						Rect src = borrowRect(0, 0, 256, 256);
						Rect dst =
								borrowRect(positionX, positionY, positionX + scaledTileSize / 2, positionY
										+ scaledTileSize / 2);
						tile1.setDrawInfo(src, dst);
						tilesToDraw.add(tile1);

						src = borrowRect(0, 0, 256, 256);
						dst =
								borrowRect(positionX + scaledTileSize / 2, positionY, positionX + scaledTileSize,
										positionY + scaledTileSize / 2);
						tile2.setDrawInfo(src, dst);
						tilesToDraw.add(tile2);

						src = borrowRect(0, 0, 256, 256);
						dst =
								borrowRect(positionX, positionY + scaledTileSize / 2, positionX + scaledTileSize
										/ 2, positionY + scaledTileSize);
						tile3.setDrawInfo(src, dst);
						tilesToDraw.add(tile3);

						src = borrowRect(0, 0, 256, 256);
						dst =
								borrowRect(positionX + scaledTileSize / 2, positionY + scaledTileSize / 2,
										positionX + scaledTileSize, positionY + scaledTileSize);
						tile4.setDrawInfo(src, dst);
						tilesToDraw.add(tile4);

						continue;
					}

					tile = tileCache.getTile(x / 2, y / 2, hardZoom + 1, false);
					if (tile.getBitmap() != null) {
						int x2 = (x % 2);
						int y2 = (y % 2);
						Rect src = borrowRect(x2 * 128, y2 * 128, (x2 + 1) * 128, (y2 + 1) * 128);
						Rect dst =
								borrowRect(positionX, positionY, positionX + scaledTileSize, positionY
										+ scaledTileSize);
						tile.setDrawInfo(src, dst);
						if (!tilesToDraw.contains(tile)) tilesToDraw.add(tile);

						continue;
					}

					tile = tileCache.getTile(x / 4, y / 4, hardZoom + 2, false);
					if (tile.getBitmap() != null) {
						int x2 = (x % 4);
						int y2 = (y % 4);
						Rect src = borrowRect(x2 * 64, y2 * 64, (x2 + 1) * 64, (y2 + 1) * 64);
						Rect dst =
								borrowRect(positionX, positionY, positionX + scaledTileSize, positionY
										+ scaledTileSize);
						tile.setDrawInfo(src, dst);
						if (!tilesToDraw.contains(tile)) tilesToDraw.add(tile);

						continue;
					}

					tile = tileCache.getTile(x / 8, y / 8, hardZoom + 3, false);
					if (tile.getBitmap() != null) {
						int x2 = (x % 8);
						int y2 = (y % 8);
						Rect src = borrowRect(x2 * 32, y2 * 32, (x2 + 1) * 32, (y2 + 1) * 32);
						Rect dst =
								borrowRect(positionX, positionY, positionX + scaledTileSize, positionY
										+ scaledTileSize);
						tile.setDrawInfo(src, dst);
						if (!tilesToDraw.contains(tile)) tilesToDraw.add(tile);

						continue;
					}
				}

				Rect src = borrowRect(0, 0, 256, 256);
				Rect dst =
						borrowRect(positionX, positionY, positionX + scaledTileSize, positionY + scaledTileSize);
				tile.setDrawInfo(src, dst);
				tilesToDraw.add(tile);
			}
		}

		for (int x = topLeftTileX - 1; x <= bottomRightTileX + 1; x++) {
			for (int y = topLeftTileY - 1; y <= bottomRightTileY + 1; y++) {
				if (x == topLeftTileX - 1 || x == bottomRightTileX + 1 || y == topLeftTileY - 1
						|| y == bottomRightTileY + 1) tileCache.getTile(x / 2, y / 2, hardZoom + 1, true, 3);
			}
		}

		for (int x = topLeftTileX - 4; x <= bottomRightTileX + 4; x++) {
			for (int y = topLeftTileY - 4; y <= bottomRightTileY + 4; y++) {
				if (hardZoom == maxHardZoom) tileCache.getTile(x, y, hardZoom, true, 1);
				else if (hardZoom == maxHardZoom - 1) tileCache.getTile(x / 2, y / 2, hardZoom + 1, true, 1);
				else tileCache.getTile(x / 4, y / 4, hardZoom + 2, true, 1);
			}
		}

		for (int i = 0; i < tilesToDraw.size(); i++) {
			tilesToDraw.get(i).draw(canvas);
		}
		tilesToDraw.clear();
	}

	// -----------------------------------------------------------------------------------------------

	public int getMapHeight() {
		return mapHeight;
	}

	// -----------------------------------------------------------------------------------------------

	public int getMapWidth() {
		return mapWidth;
	}

	// -----------------------------------------------------------------------------------------------

	public float getMinZoomMdpi() {
		return minZoomMdpi;
	}

	// -----------------------------------------------------------------------------------------------

	public int getState() {
		return state;
	}

	// -----------------------------------------------------------------------------------------------

	private int getTileKey(int x, int y, int hardZoom) {
		return x + y * 1000 + hardZoom * 1000000;
	}

	// -----------------------------------------------------------------------------------------------

	private boolean loadMapInfo() {
		File file = new File(mapsDir.getAbsolutePath() + "/" + mapId + "/mapInfo.json");
		String fileContent = null;
		try {
			fileContent = IOUtils.readFile(file);
		} catch (IOException e) {
			return false;
		}

		try {
			JSONObject json = new JSONObject(fileContent);
			mapWidth = json.getInt("width");
			mapHeight = json.getInt("height");
			maxHardZoom = json.getInt("maxHardZoom");
			minZoomMdpi = (float) json.getDouble("minZoomMdpi");
		} catch (JSONException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	// -----------------------------------------------------------------------------------------------

	public void destroy() {
		state = NOT_READY;
		stopThreads();
		tileCache.reset();
	}

	// -----------------------------------------------------------------------------------------------

	public boolean setMap(String mapId) {
		state = NOT_READY;
		stopThreads();
		this.mapId = mapId;
		tileCache.reset();
		startBitmapLoaderThread();
		boolean infoLoaded = loadMapInfo();
		if (infoLoaded) {
			state = READY;
			return true;
		} else {
			return false;
		}
	}

	// -----------------------------------------------------------------------------------------------

	public void stopThreads() {
		boolean retry = true;
		threadsShouldStop = true;
		while (retry) {
			try {
				if (bitmapLoaderThread != null) {
					bitmapLoaderThread.interrupt();
					bitmapLoaderThread.join();
				}
				retry = false;
			} catch (InterruptedException e) {
			}
		}
	}

	// -----------------------------------------------------------------------------------------------

	private void startBitmapLoaderThread() {
		threadsShouldStop = false;
		bitmapLoaderThread = new BitmapLoaderThread();
		bitmapLoaderThread.start();
	}

	// -----------------------------------------------------------------------------------------------

	public class Tile {
		private boolean isValid;
		private int hardZoom;
		private int bitmapRequestPriority;
		private int x;
		private int y;
		private ArrayList<Rect> srcRects = new ArrayList<Rect>();
		private ArrayList<Rect> dstRects = new ArrayList<Rect>();
		private Bitmap bitmap;
		private String mapId;

		public Tile(int x, int y, int hardZoom) {
			this.x = x;
			this.y = y;
			this.hardZoom = hardZoom;
			this.mapId = OfflineMapSource.this.mapId;

			int tileSize = 256 * (int) Math.pow(2, hardZoom);
			isValid =
					hardZoom >= 0 && hardZoom <= maxHardZoom && x >= 0 && x <= (mapWidth - 1) / tileSize && y >= 0
							&& y <= (mapHeight - 1) / tileSize;
		}

		public synchronized void draw(Canvas canvas) {
			Bitmap bitmap = this.bitmap == null ? emptyTileBitmap : this.bitmap;
			for (int i = 0; i < srcRects.size(); i++) {
				canvas.drawBitmap(bitmap, srcRects.get(i), dstRects.get(i), bitmapPaint);
				rectPool.giveBack(srcRects.get(i));
				rectPool.giveBack(dstRects.get(i));
			}
			srcRects.clear();
			dstRects.clear();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			} else if (o instanceof Tile) {
				Tile other = (Tile) o;
				return (other.hardZoom == hardZoom && other.x == x && other.y == y);
			}
			return false;
		}

		public synchronized Bitmap getBitmap() {
			return bitmap;
		}

		public File getBitmapFile() {
			return new File(mapsDir.getAbsolutePath() + "/" + mapId + "/" + hardZoom + "/" + x + "/" + y + ".png");
		}

		public int getHardZoom() {
			return hardZoom;
		}

		public String getMapId() {
			return mapId;
		}

		public int getPriority() {
			return bitmapRequestPriority;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		@Override
		public int hashCode() {
			return getTileKey(x, y, hardZoom);
		}

		public synchronized void removeBitmap() {
			if (bitmap != null) {
				bitmap.recycle();
				bitmap = null;
			}
		}

		public synchronized void setBitmap(Bitmap bitmap) {
			removeBitmap();
			this.bitmap = bitmap;
		}

		public void setDrawInfo(Rect src, Rect dst) {
			srcRects.add(src);
			dstRects.add(dst);
		}
	}

	// -----------------------------------------------------------------------------------------------

	private class TileCache {
		private int loadBitmapCounter = 0;
		private ArrayList<Tile> tilesWithBitmaps = new ArrayList<Tile>();
		private HashMap<IntWrapper, Tile> tiles = new HashMap<IntWrapper, OfflineMapSource.Tile>();

		public synchronized void bitmapLoaded(Tile tile, Bitmap bitmap) {
			tile.setBitmap(bitmap);
			if (tilesWithBitmaps.contains(tile)) return;

			tilesWithBitmaps.add(0, tile);

			if (tilesWithBitmaps.size() == bitmapCacheSize + 1) {
				Tile last = tilesWithBitmaps.get(tilesWithBitmaps.size() - 1);
				last.removeBitmap();
				tilesWithBitmaps.remove(last);
			}

			loadBitmapCounter++;
			if (bitmapLoaderThread.queueIsEmpty() || loadBitmapCounter % 3 == 0) {
				mapView.requestRedraw();
			}
		}

		public synchronized Tile getTile(int x, int y, int hardZoom, boolean loadBitmapIfNull) {
			return getTile(x, y, hardZoom, loadBitmapIfNull, 0);
		}

		public synchronized Tile getTile(int x, int y, int hardZoom, boolean loadBitmapIfNull, int priority) {
			Tile tile = null;
			IntWrapper key = intWrapperPool.borrow();
			key.setValue(getTileKey(x, y, hardZoom));
			if (tiles.containsKey(key)) {
				tile = tiles.get(key);
				intWrapperPool.giveBack(key);
			} else {
				tile = new Tile(x, y, hardZoom);
				tiles.put(key, tile);
				intWrapperPool.giveBack(new IntWrapper());
			}

			if (tile.getBitmap() == null) {
				if (loadBitmapIfNull && tile.isValid) {
					tile.bitmapRequestPriority = priority;
					bitmapLoaderThread.requestBitmap(tile);
				}
			} else {
				tilesWithBitmaps.remove(tile);
				tilesWithBitmaps.add(0, tile);
			}

			return tile;
		}

		public synchronized void reset() {
			loadBitmapCounter = 0;
			for (Tile tile : tilesWithBitmaps) {
				tile.removeBitmap();
			}
			tilesWithBitmaps.clear();
			tiles.clear();
		}
	}

	// -----------------------------------------------------------------------------------------------

	private class BitmapLoaderThread extends Thread {

		private ArrayList<Tile> requestedBitmaps = new ArrayList<Tile>();
		private Set<Tile> missingBitmaps = new HashSet<Tile>();
		private Tile currentTile = null;

		// -------------------------------------------------------------------------------------------

		public synchronized void requestBitmap(Tile tile) {
			if (tile.equals(currentTile)) return;
			if (missingBitmaps.contains(tile)) {
			} else {
				if (requestedBitmaps.contains(tile)) requestedBitmaps.remove(tile);
				int index = requestedBitmaps.size();
				for (int i = 0; i < requestedBitmaps.size(); i++) {
					if (requestedBitmaps.get(i).bitmapRequestPriority <= tile.bitmapRequestPriority) {
						index = i;
						break;
					}
				}
				requestedBitmaps.add(index, tile);
				interrupt();
			}
		}

		// -------------------------------------------------------------------------------------------

		public synchronized void resetPriorities() {
			for (int i = 0; i < requestedBitmaps.size(); i++) {
				requestedBitmaps.get(i).bitmapRequestPriority = 0;
			}
		}

		// -------------------------------------------------------------------------------------------

		@Override
		public void run() {
			byte[] buffer = new byte[16400];
			while (true) {
				synchronized (this) {
					currentTile = null;
					while (currentTile == null) {
						if (threadsShouldStop) return;
						if (requestedBitmaps.size() > 0) {
							currentTile = requestedBitmaps.get(0);
							requestedBitmaps.remove(0);
						} else {
							try {
								wait();
							} catch (InterruptedException e) {
							}
						}
					}
				}

				File file = currentTile.getBitmapFile();
				if (file.exists()) {
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inPreferredConfig = Config.RGB_565;
					options.inTempStorage = buffer;
					Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
					if (bitmap == null) {
						Common.logd(currentTile.getBitmapFile().getAbsolutePath());
					} else {
						tileCache.bitmapLoaded(currentTile, bitmap);
					}
				} else {
					synchronized (this) {
						missingBitmaps.add(currentTile);
					}
				}
			}
		}

		// -------------------------------------------------------------------------------------------

		public synchronized boolean queueIsEmpty() {
			return currentTile == null && requestedBitmaps.size() == 0;
		}

	}

	// -----------------------------------------------------------------------------------------------

	private static class IntWrapper {
		private int value;

		public IntWrapper() {
		}

		public void setValue(int value) {
			this.value = value;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof IntWrapper)) return false;
			return value == ((IntWrapper) o).value;
		}

		@Override
		public int hashCode() {
			return value;
		}
	}

	// -----------------------------------------------------------------------------------------------

	public static class Pool<T> {

		private ArrayList<T> objects;
		private Class<T> cls;

		public Pool(Class<T> cls) {
			this.cls = cls;
			this.objects = new ArrayList<T>();
		}

		public T borrow() {
			if (objects.size() == 0) {
				try {
					objects.add(cls.newInstance());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return objects.remove(objects.size() - 1);
		}

		public void giveBack(T object) {
			this.objects.add(object);
		}
	}

}
