package cz.fhejl.pubtran;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import cz.fhejl.pubtran.AbstractProvider.JourneyFinderException;

/**
 * Subclasses should not have any shallow or deep references to Activity
 * Context. For example no Views or Drawables.
 */
@SuppressWarnings("serial")
public abstract class AbstractResults implements Serializable {

	protected boolean loadNextEnabled = true;
	protected boolean loadPrevEnabled = true;
	protected int startIndex = 0;
	protected int endIndex = 0;
	private int firstVisibleItem = 0;
	private long whenCreated = System.currentTimeMillis();
	private AbstractSearchOptions options;

	// -----------------------------------------------------------------------------------------------

	public AbstractResults(AbstractSearchOptions options) {
		this.options = options;
	}

	// -----------------------------------------------------------------------------------------------

	public int getFirstVisibleItem() {
		return firstVisibleItem;
	}

	// -----------------------------------------------------------------------------------------------

	public abstract int getCachedJourneysCount(int nextOrPrev);

	// -----------------------------------------------------------------------------------------------

	public AbstractSearchOptions getOptions() {
		return options;
	}

	// -----------------------------------------------------------------------------------------------

	public abstract List<? extends AbstractJourney> getJourneys();

	// -----------------------------------------------------------------------------------------------

	public abstract List<? extends AbstractJourney> getVisibleJourneys();

	// -----------------------------------------------------------------------------------------------

	public abstract boolean isLoadNextEnabled();

	// -----------------------------------------------------------------------------------------------

	public abstract boolean isLoadPrevEnabled();

	// -----------------------------------------------------------------------------------------------

	public abstract AbstractJourney loadNext() throws IOException, JourneyFinderException;

	// -----------------------------------------------------------------------------------------------

	public abstract AbstractJourney loadPrev() throws IOException, JourneyFinderException;

	// -----------------------------------------------------------------------------------------------

	public void setJourneyIndexes(int start, int end) {
		if (start > startIndex) loadPrevEnabled = true;
		if (end < endIndex) loadNextEnabled = true;
		startIndex = start;
		endIndex = end;
	}

	// -----------------------------------------------------------------------------------------------

	public void setFirstVisibleItem(int firstVisibleItem) {
		this.firstVisibleItem = firstVisibleItem;
	}

	// -----------------------------------------------------------------------------------------------

	public long whenCreated() {
		return whenCreated;
	}

}
