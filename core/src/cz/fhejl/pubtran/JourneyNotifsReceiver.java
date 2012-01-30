package cz.fhejl.pubtran;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class JourneyNotifsReceiver extends BroadcastReceiver {

	private Long getNotifyTime(String fileName, Context context) {
		Long whenNotify = null;
		FileInputStream fis = null;
		try {
			fis = context.openFileInput(fileName);
			ObjectInputStream ois = new ObjectInputStream(fis);
			ois.readInt();
			whenNotify = ois.readLong();
			fis.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			IOUtils.close(fis);
		}

		return whenNotify;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		boolean atLeastOneActive = false;
		Long alarmTime = null;
		for (String fileName : JourneyNotifs.getFileNames(context)) {
			long whenNotify = getNotifyTime(fileName, context);
			if (whenNotify > System.currentTimeMillis()) {
				if (alarmTime == null || alarmTime > whenNotify) alarmTime = whenNotify;
			} else {
				atLeastOneActive = true;
			}
		}
		
		if (alarmTime != null) {
			AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
			Intent broadcastIntent = new Intent(context, JourneyNotifsReceiver.class);
			PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, broadcastIntent, 0);
			alarmManager.set(AlarmManager.RTC, alarmTime, pendingIntent);
		}

		if (atLeastOneActive) {
			Intent serviceIntent = new Intent(context, JourneyNotifsService.class);
			context.startService(serviceIntent);
		}
	}

}
