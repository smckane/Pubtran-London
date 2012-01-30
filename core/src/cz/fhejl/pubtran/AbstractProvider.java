package cz.fhejl.pubtran;

import java.io.IOException;

public abstract class AbstractProvider {

	@SuppressWarnings("serial")
	public static class JourneyFinderException extends Exception {
	}
	
	// -----------------------------------------------------------------------------------------------
	
	@SuppressWarnings("serial")
	public static class UnknownStopException extends JourneyFinderException {
	}
	
	// -----------------------------------------------------------------------------------------------
	
	@SuppressWarnings("serial")
	public static class StopAmbiguousException extends JourneyFinderException {
	}
	
	// -----------------------------------------------------------------------------------------------
	
	@SuppressWarnings("serial")
	public static class InputEmptyException extends JourneyFinderException {
	}
	
	// -----------------------------------------------------------------------------------------------
	
	@SuppressWarnings("serial")
	public static class ParseException extends JourneyFinderException {
	}
	
	// -----------------------------------------------------------------------------------------------
	
	@SuppressWarnings("serial")
	public static class JourneyNotFoundException extends JourneyFinderException {
	}


	// -----------------------------------------------------------------------------------------------

	public abstract AbstractResults findJourneys(AbstractSearchOptions options)
			throws IOException, JourneyFinderException;

}
