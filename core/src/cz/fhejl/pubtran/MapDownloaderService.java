package cz.fhejl.pubtran;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import cz.fhejl.pubtran.Areas.Area;
import cz.fhejl.pubtran.IOUtils.ProgressListener;
import cz.fhejl.pubtran.MapDownloaderService.Progress.State;

public class MapDownloaderService extends Service implements Runnable {

	public static boolean isRunning = false;

	private static final int NOTIFICATION_PROGRESS = 0;
	private static final int NOTIFICATION_ERROR = 1;

	private boolean cancelRequest = false;
	private Area area;
	private Progress cancelProgress;
	private Progress progress = new Progress();
	private File mapsDir;
	private Notification progressNotification;
	private NotificationManager notificationManager;
	private Thread thread;

	// -----------------------------------------------------------------------------------------------

	private void cancel() {
		cancelProgress = new Progress();
		cancelRequest = true;
		updateProgress();
	}

	// -----------------------------------------------------------------------------------------------

	public static void cancelErrorNotification(Context context) {
		NotificationManager notificationManager =
				(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(NOTIFICATION_ERROR);
	}

	// -----------------------------------------------------------------------------------------------

	public static String getBroadcastIntentAction(Context c) {
		return c.getPackageName() + ".MAP_DOWNLOADER_SERVICE_BROADCAST";
	}

	// -----------------------------------------------------------------------------------------------

	private PendingIntent getContentIntent(boolean error) {
		Intent notificationIntent = new Intent(getApplicationContext(), MapActivity.class);
		notificationIntent.putExtra("area", area);
		notificationIntent.putExtra("error", error);
		notificationIntent.putExtra("mapId", progress.getCurrent());
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		return PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	// -----------------------------------------------------------------------------------------------

	private String getMapArchiveUrl(String mapId) {
		return "http://dl.dropbox.com/u/6875183/pubtran/maps/" + getPackageName() + "/" + mapId + ".zip";
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onCreate() {
		isRunning = true;
		mapsDir = IOUtils.getMapsDirectory(this);
		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		progressNotification =
				new Notification(android.R.drawable.stat_sys_download, "", System.currentTimeMillis());
		progressNotification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void onDestroy() {
		isRunning = false;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		area = (Area) intent.getSerializableExtra("area");
		if (intent.getBooleanExtra("cancel", false)) {
			cancel();
		} else {
			String mapId = intent.getStringExtra("mapId");
			Progress progress = cancelRequest ? cancelProgress : this.progress;
			progress.addMap(mapId);
			updateProgress();
			if (thread == null) {
				thread = new Thread(this);
				thread.start();
			}
		}

		return START_REDELIVER_INTENT;
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public void run() {
		for (int i = 0; i < progress.maps.size(); i++) {
			if (cancelRequest) break;
			String mapId = progress.getCurrent();

			progress.setState(State.STARTING);
			updateProgress();

			String url = getMapArchiveUrl(mapId);
			File archive = new File(mapsDir, mapId + ".zip");
			boolean success = IOUtils.downloadFile(url, archive, new ProgressListener() {
				@Override
				public void updateProgress(int completed, int total) {
					progress.setState(State.DOWNLOADING);
					progress.setBytes(completed, total);
					MapDownloaderService.this.updateProgress();
				}

				@Override
				public boolean isCancelRequested() {
					return cancelRequest;
				}
			});

			if (!success) {
				progress.setState(State.ERROR);
				break;
			} else {
				progress.setState(State.EXTRACTING);
				progress.setBytes(0, 1);
				updateProgress();
				boolean unzipSuccess = IOUtils.unzip(archive, mapsDir, new ProgressListener() {
					@Override
					public void updateProgress(int completed, int total) {
						progress.setBytes(completed, total);
						MapDownloaderService.this.updateProgress();
					}

					@Override
					public boolean isCancelRequested() {
						return false;
					}
				});

				if (!unzipSuccess) {
					progress.setState(State.ERROR);
					break;
				}
			}
			archive.delete();
			progress.setCurrentFinished();
		}

		updateProgress();

		if (cancelRequest && cancelProgress.maps.size() > 0) {
			cancelRequest = false;
			progress = cancelProgress;
			thread = new Thread(this);
			thread.start();
		} else {
			thread = null;
			stopSelf();
		}
	}

	// -----------------------------------------------------------------------------------------------

	private void updateNotification(Progress progress) {
		if (progress.getState() == State.DOING_NOTHING) {
			notificationManager.cancel(NOTIFICATION_PROGRESS);
		} else if (progress.getState() == State.ERROR) {
			notificationManager.cancel(NOTIFICATION_PROGRESS);

			if (MapActivity.isResumed) return;

			Notification errorNotification =
					new Notification(android.R.drawable.stat_notify_error, getString(R.string.mapErrorTitle),
							System.currentTimeMillis());
			errorNotification.flags |= Notification.FLAG_AUTO_CANCEL;
			errorNotification.setLatestEventInfo(getApplicationContext(), getString(R.string.mapErrorTitle),
					":-(", getContentIntent(true));

			notificationManager.notify(NOTIFICATION_ERROR, errorNotification);
		} else {
			progressNotification.setLatestEventInfo(getApplicationContext(), progress.getTitle(this, true),
					progress.getDescription(this), getContentIntent(false));
			notificationManager.notify(NOTIFICATION_PROGRESS, progressNotification);
		}
	}

	// -----------------------------------------------------------------------------------------------

	public void updateProgress() {
		Progress progress = cancelRequest ? cancelProgress : this.progress;

		updateNotification(progress);

		Intent intent = new Intent(getBroadcastIntentAction(this));
		intent.putExtra("progress", progress);
		sendStickyBroadcast(intent);
	}

	// -----------------------------------------------------------------------------------------------

	@SuppressWarnings("serial")
	public static class Progress implements Serializable {
		public static enum State {
			STARTING, DOWNLOADING, EXTRACTING, ERROR, DOING_NOTHING
		}

		private int completedBytes;
		private int currentMap = 0;
		private int totalBytes;
		private ArrayList<String> maps = new ArrayList<String>();
		private State state = State.DOING_NOTHING;

		public void addMap(String mapId) {
			if (!maps.contains(mapId)) {
				maps.add(mapId);
			}
		}

		public String getCurrent() {
			return maps.get(currentMap);
		}

		public String getDescription(Context c) {
			String s = c.getString(R.string.mapXoutOfY, currentMap + 1, maps.size());

			if (state == State.STARTING) {
				return s;
			} else if (state == State.DOWNLOADING) {
				return s + c.getString(R.string.downloadProgressInfo, getPercent(), getSizeInMB());
			} else if (state == State.EXTRACTING) {
				return s;
			} else if (state == State.ERROR) {
				return "";
			} else {
				return "";
			}
		}

		public int getPercent() {
			return (int) (completedBytes * 100L / totalBytes);
		}

		public float getSizeInMB() {
			return totalBytes * 10 / (1024 * 1024) / 10f;
		}

		public State getState() {
			return state;
		}

		public String getTitle(Context c, boolean isNotification) {
			if (state == State.STARTING) {
				return c.getString(R.string.startingDownload);
			} else if (state == State.DOWNLOADING) {
				return c.getString(R.string.downloading);
			} else if (state == State.EXTRACTING) {
				return c.getString(R.string.installing);
			} else {
				return "";
			}
		}

		public boolean isFinished(String mapId) {
			return maps.subList(0, currentMap).contains(mapId);
		}

		public boolean isNotYetFinished(String mapId) {
			if (state == State.ERROR) return false;
			return maps.subList(currentMap, maps.size()).contains(mapId);
		}

		public void setBytes(int completed, int total) {
			completedBytes = completed;
			totalBytes = total;
		}

		public void setCurrentFinished() {
			currentMap++;
			state = State.DOING_NOTHING;
		}

		public void setState(State state) {
			this.state = state;
		}
	}

}
