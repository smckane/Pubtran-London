package cz.fhejl.pubtran;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;

/**
 * Watched journeys are saved in files, in this format: int (hashcode), long
 * (when notify), AbstractJourney, AbstractResults
 */
public class JourneyNotifs {

	private static final String FILENAME_START = "watched_journey_";

	// -----------------------------------------------------------------------------------------------
	
	public static synchronized String getFileName(Context context, AbstractJourney journey) {
		String fileName = null;
		for (String f : getFileNames(context)) {
			FileInputStream fis = null;
			try {
				fis = context.openFileInput(f);
				int hashCode = new ObjectInputStream(fis).readInt();
				if (hashCode == journey.hashCode()) fileName = f;
				fis.close();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				IOUtils.close(fis);
			}
		}

		return fileName;
	}
	
	// -----------------------------------------------------------------------------------------------

	public static synchronized ArrayList<String> getFileNames(Context context) {
		ArrayList<String> files = new ArrayList<String>();
		for (String name : context.fileList()) {
			if (name.startsWith(FILENAME_START)) files.add(name);
		}

		return files;
	}

	// -----------------------------------------------------------------------------------------------

	public static synchronized boolean isJourneyWatched(AbstractJourney journey, Context context) {
		boolean isWatched = false;
		for (String fileName : getFileNames(context)) {
			FileInputStream fis = null;
			try {
				fis = context.openFileInput(fileName);
				int hashCode = new ObjectInputStream(fis).readInt();
				if (hashCode == journey.hashCode()) isWatched = true;
				fis.close();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				IOUtils.close(fis);
			}
		}

		return isWatched;
	}

	// -----------------------------------------------------------------------------------------------

	public static synchronized void remove(Context context, String fileName) {
		context.deleteFile(fileName);
	}

	// -----------------------------------------------------------------------------------------------

	public synchronized static void watchJourney(Context context, long whenNotify, AbstractJourney journey,
			AbstractResults results, int journeyIndex) {
		int startIndex = results.startIndex;
		int endIndex = results.endIndex;
		boolean loadPrevEnabled = results.loadPrevEnabled;
		boolean loadNextEnabled = results.loadNextEnabled;
		results.setJourneyIndexes(journeyIndex, journeyIndex + 1);

		FileOutputStream fos = null;
		try {
			String fileName = FILENAME_START + System.currentTimeMillis();
			fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeInt(journey.hashCode());
			oos.writeLong(whenNotify);
			oos.writeObject(journey);
			oos.writeObject(results);
			fos.close();

			context.sendBroadcast(new Intent(context, JourneyNotifsReceiver.class));
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			IOUtils.close(fos);
		}

		results.startIndex = startIndex;
		results.endIndex = endIndex;
		results.loadPrevEnabled = loadPrevEnabled;
		results.loadNextEnabled = loadNextEnabled;
	}

}
