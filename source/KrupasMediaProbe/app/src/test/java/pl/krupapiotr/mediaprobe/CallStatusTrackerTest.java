package pl.krupapiotr.mediaprobe;
import org.junit.Test;
import static org.junit.Assert.*;
public class CallStatusTrackerTest {
  @Test public void uiDropoutDoesNotClearPhone() {
    CallStatusTracker s=new CallStatusTracker();
    assertFalse(s.update(1000,false,0));
    assertTrue(s.update(2000,true,0));
    for(long ms=3500;ms<120000;ms+=1500)assertTrue(s.update(ms,false,0));
  }
  @Test public void explicitHangupClearsPhone() {
    CallStatusTracker s=new CallStatusTracker();
    assertTrue(s.update(2000,true,0));
    assertTrue(s.update(3500,false,0));
    assertFalse(s.update(5000,false,4900));
  }
  @Test public void staleHangupCannotEndNextCall() {
    CallStatusTracker s=new CallStatusTracker();
    assertTrue(s.update(1000,true,0));
    assertFalse(s.update(2000,false,2000));
    assertTrue(s.update(4000,true,2000));
    assertTrue(s.update(6000,false,2000));
  }
}
