package pl.krupapiotr.mediaprobe;
import org.junit.Test;
import static org.junit.Assert.*;

public class CallNotificationPolicyTest {
    @Test public void namedCall() {
        assertTrue(CallNotificationPolicy.qualifies("call",true,false,"Anna Kowalska","Połączenie"));
        assertEquals("Anna Kowalska",CallNotificationPolicy.display("Anna Kowalska","Połączenie",""));
    }
    @Test public void numberPriority() {
        assertEquals("+48 501 234 567",CallNotificationPolicy.display(
                "Anna Kowalska","+48 501 234 567",""));
    }
    @Test public void noFalsePositiveFromMissedOrMedia() {
        assertFalse(CallNotificationPolicy.qualifies("call",false,false,"Jan","Połączenie"));
        assertFalse(CallNotificationPolicy.qualifies("call",true,false,"Missed call","Jan"));
        assertFalse(CallNotificationPolicy.qualifies("transport",true,true,"Jan","Połączenie"));
    }
    @Test public void noFakeCaller() {
        assertEquals("ROZMOWA",CallNotificationPolicy.display("Incoming call","Answer call",""));
    }
}
