package cz.fhejl.pubtran;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;

/**
 * Provides: 1) Handling of Task-s. 2) Subclasses can have access to
 * SharedPreferences via preferences field. 3) Sets custom locale according to
 * preferences
 */
public class AbstractActivity extends Activity {

	protected SharedPreferences preferences;

	private int highestTaskId = 0;
	private MyHashMap tasks;

	// -----------------------------------------------------------------------------------------------

	@SuppressWarnings("serial")
	private static class MyHashMap
			extends
			HashMap<Class<? extends Task<? extends AbstractActivity, ?, ?>>, ArrayList<Task<? extends AbstractActivity, ?, ?>>> {
	}

	// ----------------------------------------------------------------------------------------------

	protected void cancelTasksOfThisClass(Class<? extends Task<? extends AbstractActivity, ?, ?>> cls) {
		for (Task<?, ?, ?> task : tasks.get(cls)) {
			if (!task.isCancelled()) task.cancel(true);
		}
		tasks.get(cls);
	}

	// -----------------------------------------------------------------------------------------------

	protected Object getNonConfigurationData(String key) {
		@SuppressWarnings("unchecked")
		HashMap<String, Object> map = (HashMap<String, Object>) getLastNonConfigurationInstance();
		return map == null ? null : map.get(key);
	}

	// -----------------------------------------------------------------------------------------------

	public ArrayList<Task<? extends AbstractActivity, ?, ?>> getTasksByClass(
			Class<? extends Task<? extends AbstractActivity, ?, ?>> cls) {
		if (tasks.get(cls) == null) {
			tasks.put(cls, new ArrayList<Task<? extends AbstractActivity, ?, ?>>());
		}
		return tasks.get(cls);
	}

	// -----------------------------------------------------------------------------------------------

	public Task<? extends AbstractActivity, ?, ?> getTaskById(int id) {
		for (ArrayList<Task<? extends AbstractActivity, ?, ?>> list : tasks.values()) {
			for (Task<?, ?, ?> task : list) {
				if (task.getId() == id) return task;
			}
		}

		return null;
	}

	// -----------------------------------------------------------------------------------------------

	public static void handleCustomLocale(SharedPreferences preferences, Resources resources) {
		if (!preferences.contains(Common.PREF_LANGUAGE)) return;
		
		String langCode = preferences.getString(Common.PREF_LANGUAGE, "");
		if (!langCode.equals("")) {
			Locale locale = new Locale(langCode);
			Configuration config = resources.getConfiguration();
			config.locale = locale;
			resources.updateConfiguration(config, resources.getDisplayMetrics());
			Locale.setDefault(locale);
		}
	}

	// -----------------------------------------------------------------------------------------------

	// Should be called immediatelly after onCreate ends, fixes weird bug -
	// on Nexus One, locale is reseted to system settings about 100ms after
	// onCreate ends
	public static void localeWorkaround(final SharedPreferences preferences, final Resources resources) {
		Timer timer = new Timer();
		class SetLocaleTask extends TimerTask {
			@Override
			public void run() {
				handleCustomLocale(preferences, resources);
			}
		}

		timer.schedule(new SetLocaleTask(), 200);
		timer.schedule(new SetLocaleTask(), 500);
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	protected void onDestroy() {
		super.onDestroy();
		for (ArrayList<Task<? extends AbstractActivity, ?, ?>> list : tasks.values()) {
			for (Task<?, ?, ?> task : list) {
				task.detachActivity();
			}
		}
	}

	// -----------------------------------------------------------------------------------------------

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		preferences = PreferenceManager.getDefaultSharedPreferences(this);

		handleCustomLocale(preferences, getResources());

		tasks = (MyHashMap) getNonConfigurationData("tasks");
		if (tasks == null) tasks = new MyHashMap();
	}

	// -----------------------------------------------------------------------------------------------

	protected void onSaveNonConfigurationData(HashMap<String, Object> nonConfigurationData) {
	}

	// -----------------------------------------------------------------------------------------------

	protected void onPostCreate(Bundle savedInstanceState) {
		for (ArrayList<Task<? extends AbstractActivity, ?, ?>> list : tasks.values()) {
			for (Task<?, ?, ?> task : list) {
				task.attachActivity(this);
			}
		}

		localeWorkaround(preferences, getResources());

		super.onPostCreate(savedInstanceState);
	}

	// -----------------------------------------------------------------------------------------------

	@Override
	public Object onRetainNonConfigurationInstance() {
		HashMap<String, Object> nonConfigurationInstance = new HashMap<String, Object>();
		nonConfigurationInstance.put("tasks", tasks);
		onSaveNonConfigurationData(nonConfigurationInstance);
		return nonConfigurationInstance;
	}

	// -----------------------------------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	public void onTaskCompleted(Task<? extends AbstractActivity, ?, ?> task) {
		getTasksByClass((Class<? extends Task<AbstractActivity, ?, ?>>) task.getClass()).remove(task);
	}

	// -----------------------------------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	protected int startTask(Task<? extends AbstractActivity, ?, ?> task) {
		getTasksByClass((Class<? extends Task<AbstractActivity, ?, ?>>) task.getClass()).add(task);
		task.setId(highestTaskId);
		highestTaskId++;
		task.attachActivity(this);
		task.execute();
		return task.getId();
	}

}
