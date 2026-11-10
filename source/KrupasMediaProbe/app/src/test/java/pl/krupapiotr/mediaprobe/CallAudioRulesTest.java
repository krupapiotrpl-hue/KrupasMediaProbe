package pl.krupapiotr.mediaprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class CallAudioRulesTest {
    @Test public void inCallMode(){assertTrue(CallAudioRules.likelyCall(2,false,false,false));}
    @Test public void communicationWithSco(){assertTrue(CallAudioRules.likelyCall(3,true,false,false));}
    @Test public void communicationWithVoice(){assertTrue(CallAudioRules.likelyCall(3,false,true,false));}
    @Test public void communicationWithPhoneUi(){assertTrue(CallAudioRules.likelyCall(3,false,false,true));}
    @Test public void scoAndPhoneUi(){assertTrue(CallAudioRules.likelyCall(0,true,false,true));}
    @Test public void unrelatedAudioDoesNotLookLikeCall(){
        assertFalse(CallAudioRules.likelyCall(0,true,false,false));
        assertFalse(CallAudioRules.likelyCall(0,false,true,false));
        assertFalse(CallAudioRules.likelyCall(3,false,false,false));
        assertFalse(CallAudioRules.likelyCall(-1,false,false,true));
    }
}
