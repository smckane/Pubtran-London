package cz.fhejl.pubtran;


public class CustomJourneyActivity extends JourneyActivity {

	@Override
	protected void onCreateMenu() {
		addWatchMenuItem();
		addShareMenuItem();
		addCalendarMenuItem();
	}

}
