package pl.krupapiotr.mediaprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaRoutePolicyTest {
    @Test public void ongoingDabNotificationCannotOverrideFreshVisibleFm() {
        assertFalse(MediaRoutePolicy.preferDab(true, true, "DAB NOTIFICATION", true, true));
        assertFalse(MediaRoutePolicy.preferDab(true, true, "DAB UI", true, true));
    }
    @Test public void backgroundDabStillWorksWithoutVisibleFm() {
        assertTrue(MediaRoutePolicy.preferDab(true, true, "DAB NOTIFICATION", true, false));
        assertTrue(MediaRoutePolicy.preferDab(true, true, "DAB NOTIFICATION", false, true));
    }
    @Test public void actualPlayingSessionKeepsExistingPriority() {
        assertTrue(MediaRoutePolicy.preferDab(true, true, "PLAYING", true, true));
        assertFalse(MediaRoutePolicy.preferDab(true, false, "PLAYING", true, true));
        assertFalse(MediaRoutePolicy.preferDab(false, true, "PLAYING", true, true));
    }
}
