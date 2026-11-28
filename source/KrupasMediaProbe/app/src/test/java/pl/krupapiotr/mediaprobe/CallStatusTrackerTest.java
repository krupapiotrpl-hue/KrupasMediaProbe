package pl.krupapiotr.mediaprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** Regression tests for K706 phone overlay and missing OEM hang-up events. */
public class CallStatusTrackerTest {
    @Test public void staysIdleWithoutAnyCallEvidence() {
        CallStatusTracker c = new CallStatusTracker();
        assertFalse(c.update(1000, false, 0));
        assertFalse(c.update(120000, false, 0));
    }

    @Test public void shortLossOfBluetoothEvidenceDoesNotFlicker() {
        CallStatusTracker c = new CallStatusTracker();
        assertTrue(c.update(1000, true, 0));
        assertTrue(c.update(5000, false, 0));
        assertTrue(c.update(11999, false, 0));
        assertEquals(1000, c.lastEvidenceAt());
    }

    @Test public void staleCallClearsEvenIfNoOemHangupBroadcastArrives() {
        CallStatusTracker c = new CallStatusTracker();
        assertTrue(c.update(1000, true, 0));
        assertTrue(c.update(12999, false, 0));
        assertFalse(c.update(13000, false, 0));
        assertTrue(c.reason().contains("WYGASZONO"));
        assertFalse(c.update(800000, false, 0));
    }

    @Test public void freshSignalRestartsGracePeriod() {
        CallStatusTracker c = new CallStatusTracker();
        assertTrue(c.update(1000, true, 0));
        assertTrue(c.update(5000, false, 0));
        assertTrue(c.update(6000, true, 0));
        assertTrue(c.update(17999, false, 0));
        assertFalse(c.update(18000, false, 0));
        assertTrue(c.update(18500, true, 0));
    }

    @Test public void explicitHangupWinsOverCachedPositiveSignal() {
        CallStatusTracker c = new CallStatusTracker();
        assertTrue(c.update(1000, true, 0));
        assertFalse(c.update(6000, true, 6000));
        assertFalse(c.update(7000, true, 6000));
        assertFalse(c.update(8000, false, 6000));
        assertTrue(c.update(9000, true, 6000));
    }

    @Test public void endEventOlderThanCurrentCallIsIgnored() {
        CallStatusTracker c = new CallStatusTracker();
        assertTrue(c.update(50000, true, 10000));
        assertTrue(c.update(51000, true, 10000));
    }

    @Test public void dialingSurvivesMissingUiButExpiresAtFiniteLimit() {
        CallStatusTracker c = new CallStatusTracker();
        assertTrue(c.update(1000, true, 0, CallDisplayPolicy.DIALING));
        assertTrue(c.update(21000, false, 0, CallDisplayPolicy.CONNECTED));
        assertEquals(CallDisplayPolicy.DIALING, c.phase());
        assertTrue(c.update(40999, false, 0, CallDisplayPolicy.CONNECTED));
        assertFalse(c.update(41000, false, 0, CallDisplayPolicy.CONNECTED));
    }

    @Test public void ringingEndsImmediatelyOnExplicitHangup() {
        CallStatusTracker c = new CallStatusTracker();
        assertTrue(c.update(1000, true, 0, CallDisplayPolicy.RINGING));
        assertFalse(c.update(2000, true, 2000, CallDisplayPolicy.RINGING));
        assertFalse(c.update(3000, true, 2000, CallDisplayPolicy.RINGING));
    }

    @Test public void connectedCallRetainsOriginalTwelveSecondLimit() {
        CallStatusTracker c = new CallStatusTracker();
        assertTrue(c.update(1000, true, 0, CallDisplayPolicy.DIALING));
        assertTrue(c.update(5000, true, 0, CallDisplayPolicy.CONNECTED));
        assertTrue(c.update(16999, false, 0, CallDisplayPolicy.CONNECTED));
        assertFalse(c.update(17000, false, 0, CallDisplayPolicy.CONNECTED));
    }

    @Test public void staleHangupCannotEndNextCall() {
        CallStatusTracker c = new CallStatusTracker();
        assertTrue(c.update(1000, true, 0));
        assertFalse(c.update(2000, false, 2000));
        assertTrue(c.update(4000, true, 2000));
        assertTrue(c.update(6000, false, 2000));
    }
}
