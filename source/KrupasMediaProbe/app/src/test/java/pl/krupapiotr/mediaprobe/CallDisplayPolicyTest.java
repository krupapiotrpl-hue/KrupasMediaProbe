package pl.krupapiotr.mediaprobe;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class CallDisplayPolicyTest {
    @Test public void hfpCallPhases() {
        assertEquals(CallDisplayPolicy.DIALING,CallDisplayPolicy.fromHfpState(2));
        assertEquals(CallDisplayPolicy.DIALING,CallDisplayPolicy.fromHfpState(3));
        assertEquals(CallDisplayPolicy.RINGING,CallDisplayPolicy.fromHfpState(4));
        assertEquals(CallDisplayPolicy.RINGING,CallDisplayPolicy.fromHfpState(5));
        assertEquals(CallDisplayPolicy.CONNECTED,CallDisplayPolicy.fromHfpState(0));
        assertEquals(CallDisplayPolicy.CONNECTED,CallDisplayPolicy.fromHfpState(1));
        assertEquals("",CallDisplayPolicy.fromHfpState(7));
    }

    @Test public void uiAndNotificationPhases() {
        assertEquals(CallDisplayPolicy.DIALING,CallDisplayPolicy.fromUi(true,false));
        assertEquals(CallDisplayPolicy.RINGING,CallDisplayPolicy.fromUi(false,true));
        assertEquals(CallDisplayPolicy.CONNECTED,CallDisplayPolicy.fromUi(false,false));
        assertEquals(CallDisplayPolicy.DIALING,
                CallDisplayPolicy.fromNotification("Wybieranie","Piotr",""));
        assertEquals(CallDisplayPolicy.RINGING,
                CallDisplayPolicy.fromNotification("Incoming call","+48 501 234 567",""));
        assertEquals(CallDisplayPolicy.CONNECTED,
                CallDisplayPolicy.fromNotification("Anna","00:31",""));
    }

    @Test public void numberIsShownOnlyAfterAnswer() {
        String number="+48 501 234 567";
        assertEquals("WYBIERANIE",CallDisplayPolicy.display(CallDisplayPolicy.DIALING,number,false));
        assertEquals("POŁĄCZENIE",CallDisplayPolicy.display(CallDisplayPolicy.RINGING,number,false));
        assertEquals(number,CallDisplayPolicy.display(CallDisplayPolicy.CONNECTED,number,false));
        assertEquals("ROZMOWA",CallDisplayPolicy.display(CallDisplayPolicy.CONNECTED,"",false));
        assertEquals("ROZMOWA",CallDisplayPolicy.display(CallDisplayPolicy.CONNECTED,"Anna",false));
        assertEquals("TEST TELEFONU",CallDisplayPolicy.display(CallDisplayPolicy.CONNECTED,number,true));
    }

    @Test public void strongHfpSignalWinsOverStaleOutgoingUi() {
        assertEquals(CallDisplayPolicy.CONNECTED,CallDisplayPolicy.select(true,
                CallDisplayPolicy.CONNECTED,true,CallDisplayPolicy.DIALING,false,false,""));
        assertEquals(CallDisplayPolicy.DIALING,CallDisplayPolicy.select(false,
                "",true,CallDisplayPolicy.DIALING,false,false,""));
        assertEquals(CallDisplayPolicy.RINGING,CallDisplayPolicy.select(false,
                "",false,"",true,false,""));
        assertEquals(CallDisplayPolicy.CONNECTED,CallDisplayPolicy.select(false,
                "",false,"",false,false,""));
    }

    @Test public void k706OutgoingUiStillTriggersPhone() {
        PhoneUiDetector.Result dialing=PhoneUiDetector.detect("com.android.systemui",
                Arrays.asList("wybiera...","Piotr K"));
        assertTrue(dialing.active);
        assertEquals(CallDisplayPolicy.DIALING,dialing.phase);

        PhoneUiDetector.Result answered=PhoneUiDetector.detect("com.syu.bt",
                Arrays.asList("End call","00:31","+48 501 234 567"));
        assertTrue(answered.active);
        assertEquals(CallDisplayPolicy.CONNECTED,answered.phase);
        assertEquals("+48 501 234 567",
                CallDisplayPolicy.display(answered.phase,answered.display,false));
    }
}
