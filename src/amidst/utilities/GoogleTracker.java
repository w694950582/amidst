package amidst.utilities;

import amidst.AmidstMetaData;
import amidst.documentation.ThreadSafe;
import amidst.mojangapi.world.WorldSeed;

import com.boxysystems.jgoogleanalytics.FocusPoint;
import com.boxysystems.jgoogleanalytics.JGoogleAnalyticsTracker;

@ThreadSafe
public class GoogleTracker {
	private static final String APP_NAME = "AMIDST";
	private static final String TRACKING_CODE = "UA-27092717-1";

	private final JGoogleAnalyticsTracker tracker = createTracker();

	private JGoogleAnalyticsTracker createTracker() {
		return new JGoogleAnalyticsTracker(APP_NAME,
				AmidstMetaData.getFullVersionString(), TRACKING_CODE);
	}

	public void trackApplicationRunning() {
		track("Run");
	}

	public void trackSeed(WorldSeed seed) {
		if (seed.hasTrackingMessage()) {
			track(seed.getTrackingMessage());
		}
	}

	private synchronized void track(String name) {
		tracker.trackAsynchronously(new FocusPoint(name));
	}
}
