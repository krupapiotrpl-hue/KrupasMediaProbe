package pl.krupapiotr.mediaprobe;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class PhoneUiDetectorTest {
    @Test public void activeHfpShowsNumberWithoutLeakingToDiagnostics() {
        PhoneUiDetector.Result r=PhoneUiDetector.detect("com.syu.bt",
                Arrays.asList("Jan","+48 501 234 567","00:31","End call"));
        assertTrue(r.active);assertEquals("+48 501 234 567",r.display);
        assertFalse(r.diagnostic.contains("501 234 567"));
    }
    @Test public void incomingWithoutNumber() {
        PhoneUiDetector.Result r=PhoneUiDetector.detect("com.tw.bt",
                Arrays.asList("Incoming call","Answer call","Reject call"));
        assertTrue(r.active);assertEquals("ROZMOWA",r.display);
    }
    @Test public void doesNotConfuseDialerWithConversation() {
        assertFalse(PhoneUiDetector.detect("com.android.dialer",
                Arrays.asList("Contacts","Call history","00:13")).active);
        assertFalse(PhoneUiDetector.detect("com.google.android.youtube",
                Arrays.asList("End call","00:31")).active);
        assertFalse(PhoneUiDetector.detect("com.android.systemui",
                Arrays.asList("End call")).active);
        assertTrue(PhoneUiDetector.detect("com.android.systemui",
                Arrays.asList("End call","00:31")).active);
    }
    @Test public void unknownVendorPackageNeedsStrongCallEvidence() {
        PhoneUiDetector.Result yes=PhoneUiDetector.detect("com.vendor.proprietary",
                Arrays.asList("End call","+48 501 234 567","00:31"));
        assertTrue(yes.active);assertEquals("+48 501 234 567",yes.display);
        assertFalse(yes.diagnostic.contains("501 234 567"));
        assertFalse(PhoneUiDetector.detect("com.vendor.proprietary",
                Arrays.asList("End call")).active);
        assertFalse(PhoneUiDetector.detect("com.vendor.proprietary",
                Arrays.asList("00:31","+48 501 234 567")).active);
        assertFalse(PhoneUiDetector.detect("pl.krupapiotr.mediaprobe",
                Arrays.asList("End call","+48 501 234 567","00:31")).active);
    }
    @Test public void polishLabel() {
        assertTrue(PhoneUiDetector.detect("com.wits.ksw.bt",
                Arrays.asList("Zakończ połączenie","01:20")).active);
    }
}
