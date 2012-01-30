package cz.fhejl.pubtran;

import java.text.Collator;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.Comparator;

public class CzechComparator implements Comparator<String> {

	private static final String RULES = "< ' ' < A,a;Á,á;À,à;Â,â;Ä,ä;Ą,ą < B,b < C,c;Ç,ç < Č,č < D,d;Ď,ď < E,e;É,é;È,è;Ê,ê;Ě,ě"
			+ "< F,f < G,g < H,h < CH,Ch,cH,ch < I,i;Í,í < J,j < K,k < L,l;Ľ,ľ;Ł,ł < M,m < N,n;Ň,ň"
			+ "< O,o;Ó,ó;Ô,ô;Ö,ö < P,p < Q,q < R,r;Ŕ,ŕ < Ř,ř < S,s < Š,š < T,t;Ť,ť"
			+ "< U,u;Ú,ú;Ů,ů;Ü,ü < V,v < W,w < X,x < Y,y;Ý,ý < Z,z;Ż,ż < Ž,ž"
			+ "< 0 < 1 < 2 < 3 < 4 < 5 < 6 < 7 < 8 < 9"
			+ "< '.' < ',' < ';' < '?' < '¿' < '!' < '¡' < ':' < '\"' < '\'' < '«' < '»'";

	private RuleBasedCollator comparator;

	public CzechComparator() {
		try {
			comparator = new RuleBasedCollator(RULES);
			comparator.setStrength(Collator.IDENTICAL);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	public int compare(String s1, String s2) {
		return comparator.compare(s1, s2);
	}
}
