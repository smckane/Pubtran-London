package cz.fhejl.pubtran;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.os.Environment;

public class IOUtils {

	private static final int PROGRESS_UPDATE_INTERVAL = 700;

	// -----------------------------------------------------------------------------------------------

	public static void close(Closeable c) {
		if (c == null) return;
		try {
			c.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// -----------------------------------------------------------------------------------------------

	public static String doGetRequest(String url) throws IOException {
		InputStream is = doGetRequestReturnStream(url);
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line;
		StringBuilder reply = new StringBuilder();
		while ((line = br.readLine()) != null) {
			reply.append(line + '\n');
		}
		is.close();
		br.close();

		return reply.toString();
	}

	// -----------------------------------------------------------------------------------------------

	public static InputStream doGetRequestReturnStream(String url) throws IOException {
		HttpClient httpClient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(url);
		InputStream is = httpClient.execute(httpGet).getEntity().getContent();

		return is;
	}

	// -----------------------------------------------------------------------------------------------

	public static String doPostRequest(String url, List<NameValuePair> nameValuePairs) throws IOException {
		InputStream is = doPostRequestGetStream(url, nameValuePairs);
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		StringBuilder reply = new StringBuilder();
		String line = null;
		while ((line = br.readLine()) != null) {
			reply.append(line + '\n');
		}
		is.close();
		br.close();

		return reply.toString();
	}

	// -----------------------------------------------------------------------------------------------

	public static InputStream doPostRequestGetStream(String url, List<NameValuePair> nameValuePairs)
			throws IOException {
		HttpClient httpClient = new DefaultHttpClient();
		HttpPost postRequest = new HttpPost(url);

		String userAgent =
				"Mozilla/5.0 (X11; U; Linux i686; cs-CZ; rv:1.9.2.10) Gecko/20100915 Ubuntu/10.04 (lucid) Firefox/3.6.10";
		postRequest.setHeader("User-Agent", userAgent);
		String accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
		postRequest.setHeader("Accept", accept);
		String acceptLanguage = "cs,en-us;q=0.7,en;q=0.3";
		postRequest.setHeader("Accept-Language", acceptLanguage);
		String acceptEncoding = "gzip,deflate";
		postRequest.setHeader("Accept-Encoding", acceptEncoding);
		String acceptCharset = "ISO-8859-2,utf-8;q=0.7,*;q=0.7";
		postRequest.setHeader("Accept-Charset", acceptCharset);
		String keepAlive = "115";
		postRequest.setHeader("Keep-Alive", keepAlive);
		String connection = "keep-alive";
		postRequest.setHeader("Connection", connection);
		String contentType = "application/x-www-form-urlencoded";
		postRequest.setHeader("Content-Type", contentType);

		postRequest.setEntity(new UrlEncodedFormEntity(nameValuePairs, "UTF-8"));

		HttpResponse response = httpClient.execute(postRequest);
		InputStream is = response.getEntity().getContent();
		Header contentEncoding = response.getFirstHeader("Content-Encoding");
		if (contentEncoding != null && contentEncoding.getValue().equalsIgnoreCase("gzip")) {
			is = new GZIPInputStream(is);
		}

		return is;
	}

	// -----------------------------------------------------------------------------------------------

	public static boolean downloadFile(String url, File file, ProgressListener l) {
		FileOutputStream fos = null;
		InputStream is = null;
		try {
			HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
			connection.connect();

			int completed = 0;
			int total = connection.getContentLength();
			l.updateProgress(completed, total);

			file.getParentFile().mkdirs();
			fos = new FileOutputStream(file);
			is = connection.getInputStream();

			long lastUpdate = System.currentTimeMillis();
			byte[] buffer = new byte[8192];
			int len = 0;
			while ((len = is.read(buffer)) > 0) {
				if (l.isCancelRequested()) return false;

				fos.write(buffer, 0, len);
				completed += len;

				if ((System.currentTimeMillis() - lastUpdate) > PROGRESS_UPDATE_INTERVAL) {
					l.updateProgress(completed, total);
					lastUpdate = System.currentTimeMillis();
				}
			}
			l.updateProgress(completed, total);

			return true;
		} catch (IOException e) {
			if (file.exists()) file.delete();

			return false;
		} finally {
			close(is);
			flush(fos);
			close(fos);
		}
	}

	// -----------------------------------------------------------------------------------------------

	private static void flush(Flushable f) {
		if (f == null) return;
		try {
			f.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// -----------------------------------------------------------------------------------------------

	public static File getMapsDirectory(Context context) {
		File root = Environment.getExternalStorageDirectory();
		File dir = new File(root.getAbsolutePath() + "/Android/data/" + context.getPackageName() + "/files/maps");
		dir.mkdirs();

		return dir;
	}

	// -----------------------------------------------------------------------------------------------

	public static InputStream httpPostGetStream(String url, String body, Header... headers) throws IOException {
		HttpClient httpClient = new DefaultHttpClient();
		
		HttpPost postRequest = new HttpPost(url);
		postRequest.setEntity(new StringEntity(body, "UTF-8"));
		for (Header header : headers) {
			postRequest.setHeader(header);
		}

		HttpResponse response = httpClient.execute(postRequest);
		InputStream is = response.getEntity().getContent();
		
		return is;
	}

	// -----------------------------------------------------------------------------------------------

	public static String readFile(File file) throws IOException {
		FileInputStream stream = new FileInputStream(file);
		try {
			Reader reader = new BufferedReader(new InputStreamReader(stream));
			StringBuilder builder = new StringBuilder();
			char[] buffer = new char[8192];
			int read;
			while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
				builder.append(buffer, 0, read);
			}
			return builder.toString();
		} finally {
			stream.close();
		}
	}

	// -----------------------------------------------------------------------------------------------

	public static boolean unzip(File archive, File dir, ProgressListener l) {
		final int BUFFER = 2048;
		try {
			int completed = 0;
			int total = 0;

			ZipFile zipFile = new ZipFile(archive);
			ArrayList<? extends ZipEntry> entries = Collections.list(zipFile.entries());
			for (ZipEntry entry : entries) {
				total += entry.getSize();
			}

			if (l != null) l.updateProgress(completed, total);

			long lastUpdate = System.currentTimeMillis();
			BufferedOutputStream bos = null;
			for (ZipEntry entry : entries) {
				File file = new File(dir, entry.getName());
				if (entry.isDirectory()) {
					file.mkdir();
				} else {
					InputStream is = zipFile.getInputStream(entry);
					FileOutputStream fos = new FileOutputStream(file);
					bos = new BufferedOutputStream(fos, BUFFER);

					int count;
					byte data[] = new byte[BUFFER];
					while ((count = is.read(data, 0, BUFFER)) != -1) {
						bos.write(data, 0, count);
						completed += count;

						if (l != null && (System.currentTimeMillis() - lastUpdate) > PROGRESS_UPDATE_INTERVAL) {
							l.updateProgress(completed, total);
							lastUpdate = System.currentTimeMillis();
						}
					}
					bos.flush();
					bos.close();
					is.close();
				}
			}

			if (l != null) l.updateProgress(completed, total);

			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	// -----------------------------------------------------------------------------------------------

	public static interface ProgressListener {
		public boolean isCancelRequested();

		public void updateProgress(int completed, int total);
	}

}
