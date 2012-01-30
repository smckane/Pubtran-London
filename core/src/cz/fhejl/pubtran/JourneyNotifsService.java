package cz.fhejl.pubtran;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Random;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public class JourneyNotifsService extends Service {

	// TODO PendingIntent flag, run in background, better architecture

	public static String EXTRA_CANCEL = "cancel"; // value is notification filename to cancel

	private static final int MINUTE = 60 * 1000;

	private int highestNotificationId = 0;
	private HashMap<String, Integer> notificationIds = new HashMap<String, Integer>();
	private HashMap<String, JourneyNotification> notifications = new HashMap<String, JourneyNotification>();
	private NotificationManager notificationManager;

	private String getTitle(AbstractJourney journey) {
		String title;
		long departureTime = journey.getDepartureTime().getTimeInMillis();
		long minutesToDeparture = (departureTime - System.currentTimeMillis() + MINUTE) / MINUTE;
		if (departureTime - System.currentTimeMillis() < -MINUTE) minutesToDeparture = -1;
		if (minutesToDeparture > 60) {
			title = getString(R.string.departsAt, Utils.formatTime(journey.getDepartureTime()));
		} else if (minutesToDeparture >= 0) {
			title = getString(R.string.departsInXMins, minutesToDeparture);
		} else {
			title = getString(R.string.arrivesAt, Utils.formatTime(journey.getArrivalTime()));
		}
		return title;
	}

	public void handleCommand(Intent intent) {
		if (intent.hasExtra(EXTRA_CANCEL)) {
			String fileName = intent.getStringExtra(EXTRA_CANCEL);

			if (notifications.containsKey(fileName)) {
				notificationManager.cancel(notificationIds.get(fileName));
				notificationIds.remove(fileName);
				notifications.remove(fileName);
			}
			JourneyNotifs.remove(this, fileName);
		} else {
			updateNotifications();
		}

		if (notifications.size() == 0) stopSelf();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	}

	@Override
	public void onDestroy() {
		notificationManager.cancelAll();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		handleCommand(intent);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		handleCommand(intent);
		return START_REDELIVER_INTENT;
	}

	private void updateNotification(String fileName, ObjectInputStream ois) throws Exception {
		if (!notifications.containsKey(fileName)) {
			AbstractJourney journey = (AbstractJourney) ois.readObject();
			AbstractResults results = (AbstractResults) ois.readObject();

			Intent notificationIntent = Utils.getIntent(this, "RESULTS_ACTIVITY");
			notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			notificationIntent.putExtra("results", results);
			notificationIntent.putExtra("watchedJourney", journey);
			notificationIntent.putExtra("watchedJourneyFileName", fileName);
			int requestCode = new Random().nextInt();
			PendingIntent pendingIntent = PendingIntent.getActivity(this, requestCode, notificationIntent, 0);

			Notification notification = new Notification(R.drawable.ic_stat_watch, getTitle(journey), 0);
			notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;

			notifications.put(fileName, new JourneyNotification(notification, pendingIntent, journey));

			highestNotificationId++;
			notificationIds.put(fileName, highestNotificationId);
		}

		JourneyNotification jn = notifications.get(fileName);
		Notification notification = jn.getNotification();
		PendingIntent intent = jn.getIntent();
		AbstractJourney journey = jn.getJourney();

		String text = journey.getNotificationContent();

		notification.setLatestEventInfo(this, getTitle(journey), text, intent);
		notificationManager.notify(notificationIds.get(fileName), notification);
	}

	private void updateNotifications() {
		Long alarmTime = null;
		for (String fileName : JourneyNotifs.getFileNames(this)) {
			FileInputStream fis = null;
			try {
				fis = openFileInput(fileName);
				ObjectInputStream ois = new ObjectInputStream(fis);
				ois.readInt();
				long whenNotify = ois.readLong();
				if (whenNotify < System.currentTimeMillis()) {
					updateNotification(fileName, ois);
					long departureTime =
							notifications.get(fileName).getJourney().getDepartureTime().getTimeInMillis();
					if (departureTime + MINUTE > System.currentTimeMillis()) {
						int minutes = (int) ((departureTime + MINUTE - System.currentTimeMillis()) / 1000 / 60);
						if (minutes > 61) minutes = 61;
						long newAlarmTime = departureTime + MINUTE - minutes * MINUTE;
						if (alarmTime == null || newAlarmTime < alarmTime) alarmTime = newAlarmTime;
					}
				}
				fis.close();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				IOUtils.close(fis);
			}
		}

		if (alarmTime != null) {
			Intent broadcastIntent = new Intent(this, JourneyNotifsReceiver.class);
			PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 1, broadcastIntent, 0);

			AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
			alarmManager.set(AlarmManager.RTC, alarmTime, pendingIntent);
		}
	}

	private static class JourneyNotification {
		private AbstractJourney journey;
		private PendingIntent intent;
		private Notification notification;

		public JourneyNotification(Notification notification, PendingIntent intent, AbstractJourney journey) {
			this.notification = notification;
			this.intent = intent;
			this.journey = journey;
		}

		public AbstractJourney getJourney() {
			return journey;
		}

		public PendingIntent getIntent() {
			return intent;
		}

		public Notification getNotification() {
			return notification;
		}
	}
}
