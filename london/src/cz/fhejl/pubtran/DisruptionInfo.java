package cz.fhejl.pubtran;

import java.io.Serializable;

@SuppressWarnings("serial")
public class DisruptionInfo implements Serializable {
	public static int PRIORITY_NORMAL = 0;
	public static int PRIORITY_HIGH = 1;

	public int priority;
	public String info;
	public String lastUpdate;

	public DisruptionInfo(String info, String lastUpdate, int priority) {
		this.info = info;
		this.lastUpdate = lastUpdate;
		this.priority = priority;
	}
}
