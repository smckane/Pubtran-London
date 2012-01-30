package cz.fhejl.pubtran;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.content.Context;

public class SavedResults {

	private static final int MAX_SAVED_RESULTS = 7;
	private static final String FILENAME_START = "saved_results_";

	private static void deleteOld(Context context) {
		ArrayList<String> files = getFileNames(context);
		for (int i = files.size() - 1; i >= MAX_SAVED_RESULTS; i--) {
			context.deleteFile(files.get(i));
			files.remove(i);
		}
	}

	public static ArrayList<String> getFileNames(Context context) {
		ArrayList<String> files = new ArrayList<String>();
		for (String name : context.fileList()) {
			if (name.startsWith(FILENAME_START)) files.add(name);
		}

		Collections.sort(files, new Comparator<String>() {
			@Override
			public int compare(String s1, String s2) {
				long t1 = getTime(s1);
				long t2 = getTime(s2);
				
				// Can't just use "return t2 - t1;" because of long to int conversion problems
				if (t2 > t1) return 1;
				else if (t2 < t1) return -1;
				else return 0;
			}
		});

		return files;
	}

	public static long getTime(String fileName) {
		return new Long(fileName.substring(FILENAME_START.length()));
	}

	public static AbstractSearchOptions loadSearchOptions(Context context, String fileName) {
		AbstractSearchOptions options = null;
		FileInputStream fis = null;
		try {
			fis = context.openFileInput(fileName);
			ObjectInputStream ois = new ObjectInputStream(fis);
			options = (AbstractSearchOptions) ois.readObject();
			fis.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} finally {
			IOUtils.close(fis);
		}

		return options;
	}

	public static AbstractResults loadResults(Context context, String fileName) {
		AbstractResults results = null;
		FileInputStream fis = null;
		try {
			fis = context.openFileInput(fileName);
			ObjectInputStream ois = new ObjectInputStream(fis);
			ois.readObject();
			results = (AbstractResults) ois.readObject();
			fis.close();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			IOUtils.close(fis);
		}

		return results;
	}

	public static void saveResults(final Context context, final AbstractResults results) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				FileOutputStream fos = null;
				try {
					fos = context.openFileOutput(FILENAME_START + results.whenCreated(), Context.MODE_PRIVATE);
					ObjectOutputStream oos = new ObjectOutputStream(fos);
					oos.writeObject(results.getOptions());
					oos.writeObject(results);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					IOUtils.close(fos);
				}
				deleteOld(context);
			}
		}).start();
	}

}
