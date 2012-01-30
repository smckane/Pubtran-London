package cz.fhejl.pubtran;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import cz.fhejl.pubtran.Areas.Area;

@SuppressWarnings("serial")
public class Areas extends ArrayList<Area> {

	private static Areas areas = null;

	// -----------------------------------------------------------------------------------------------

	public static class Map implements Serializable {
		private String id;
		private String name;

		public Map(String id, String name) {
			this.id = id;
			this.name = name;
		}

		public String getId() {
			return id;
		}

		public String getName() {
			return name;
		}
	}

	// -----------------------------------------------------------------------------------------------

	public static class Area implements Serializable {
		public double latitude;
		public double longitude;
		public int radius;
		public Map[] maps;
		public String className;
		public String id;
		public String name;
	}

	// -----------------------------------------------------------------------------------------------

	private Areas(Context context) {
		BufferedReader br = null;
		try {
			InputStream is = context.getResources().openRawResource(R.raw.areas);
			br = new BufferedReader(new InputStreamReader(is));
			StringBuilder sb = new StringBuilder();

			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}

			JSONArray json = new JSONArray(sb.toString());

			for (int i = 0; i < json.length(); i++) {
				Area area = new Area();
				add(area);

				JSONObject jsonArea = (JSONObject) json.get(i);
				area.name = getString(context, (String) jsonArea.get("name"));
				area.className = (String) jsonArea.get("class");
				area.id = (String) jsonArea.get("id");
				area.latitude = (Double) jsonArea.get("lat");
				area.longitude = (Double) jsonArea.get("lon");
				area.radius = (Integer) jsonArea.get("radius");

				JSONArray jsonMaps = (JSONArray) jsonArea.get("maps");
				area.maps = new Map[jsonMaps.length()];
				for (int j = 0; j < jsonMaps.length(); j++) {
					JSONObject jsonMap = (JSONObject) jsonMaps.get(j);
					area.maps[j] = new Map(jsonMap.getString("id"), getString(context, jsonMap.getString("name")));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		} finally {
			if (br != null) IOUtils.close(br);
		}
	}

	// -----------------------------------------------------------------------------------------------

	public Area findByClass(String cls) {
		for (Area area : this) {
			if (cls.equals(area.className)) return area;
		}
		return null;
	}

	// -----------------------------------------------------------------------------------------------

	public Area findById(String id) {
		for (Area area : this) {
			if (id.equals(area.id)) return area;
		}
		return null;
	}

	// -----------------------------------------------------------------------------------------------

	public static Areas getInstance(Context context) {
		if (areas == null) areas = new Areas(context);
		return areas;
	}

	// -----------------------------------------------------------------------------------------------

	private String getString(Context c, String s) {
		if (s.startsWith("@")) {
			int resource = c.getResources().getIdentifier(s.substring(1), "string", "cz.fhejl.pubtran");
			return c.getResources().getString(resource);
		} else {
			return s;
		}
	}

	// -----------------------------------------------------------------------------------------------

	public static void reset() {
		areas = null;
	}

}
