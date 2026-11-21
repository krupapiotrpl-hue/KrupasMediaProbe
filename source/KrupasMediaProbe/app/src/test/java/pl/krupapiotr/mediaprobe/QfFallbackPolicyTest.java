package pl.krupapiotr.mediaprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public final class QfFallbackPolicyTest {
    @Test public void btConnectedShowsFallback() {
        assertTrue(QfFallbackPolicy.shouldShow(true,true,false,false));
    }
    @Test public void btDisconnectedClearsFallback() {
        assertFalse(QfFallbackPolicy.shouldShow(false,true,false,false));
    }
    @Test public void realCallTakesPriority() {
        assertFalse(QfFallbackPolicy.shouldShow(true,true,true,false));
    }
    @Test public void manualTestTakesPriority() {
        assertFalse(QfFallbackPolicy.shouldShow(true,true,false,true));
    }
    @Test public void canDisableFallback() {
        assertFalse(QfFallbackPolicy.shouldShow(true,false,false,false));
    }
}
