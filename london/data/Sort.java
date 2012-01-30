import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.Collator;
import java.text.RuleBasedCollator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.*;

public class Sort {

	private static final String RULES = "< ' ' < A,a;Á,á;À,à;Â,â;Ä,ä;Ą,ą < B,b < C,c;Ç,ç < Č,č < D,d;Ď,ď < E,e;É,é;È,è;Ê,ê;Ě,ě"
			+ "< F,f < G,g < H,h < CH,Ch,cH,ch < I,i;Í,í < J,j < K,k < L,l;Ľ,ľ;Ł,ł < M,m < N,n;Ň,ň"
			+ "< O,o;Ó,ó;Ô,ô;Ö,ö < P,p < Q,q < R,r;Ŕ,ŕ < Ř,ř < S,s < Š,š < T,t;Ť,ť"
			+ "< U,u;Ú,ú;Ů,ů;Ü,ü < V,v < W,w < X,x < Y,y;Ý,ý < Z,z;Ż,ż < Ž,ž"
			+ "< 0 < 1 < 2 < 3 < 4 < 5 < 6 < 7 < 8 < 9"
			+ "< '.' < ',' < ';' < '?' < '¿' < '!' < '¡' < ':' < '\"' < '\'' < '«' < '»'";

	public static void main(String[] args) {
		Pattern regex = Pattern.compile(",\\W");
		File dir = new File(".");
		File[] list = dir.listFiles();
		for (File dir2 : list) {
			if (dir2.isDirectory()) {
				try {
					File f = new File(dir2, "stops.txt");
					FileInputStream fis = new FileInputStream(f);
					BufferedReader br = new BufferedReader(new InputStreamReader(fis));

					ArrayList<String> stops = new ArrayList<String>();

					String line;
					while ((line = br.readLine()) != null) {
						String stop = line.trim();
						//Matcher m = regex.matcher(stop);
						//if(m.find()) System.out.println(stop);
						if(stops.contains(stop) || stop.equals(""))
							System.out.println("Duplicitni zastavka: " + stop);
						else
							stops.add(line.trim());
					}

					br.close();
					fis.close();

					String[] stopsArray = new String[stops.size()];
					stops.toArray(stopsArray);
					try{
						final RuleBasedCollator rbc = new RuleBasedCollator(RULES);
						rbc.setStrength(Collator.IDENTICAL);
						Arrays.sort(stopsArray, new Comparator<String>() {
							@Override
							public int compare(String object1, String object2) {
								return rbc.compare(object1, object2);
							}
						});
					}catch(Exception e){
						System.out.println("ParseException");
					}

					FileOutputStream fos = new FileOutputStream(f);
					BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
					for (String stop : stopsArray) {
						bw.write(stop + "\n");
					}
					bw.flush();
					bw.close();
					fos.close();
				} catch (IOException e) {

				}
			}
		}
	}

}
