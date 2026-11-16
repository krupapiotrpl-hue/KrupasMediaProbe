package pl.krupapiotr.mediaprobe;
import org.junit.Test;
import static org.junit.Assert.*;
public class RdsSeparatorTest {
  @Test public void hideDanglingSeparatorsButKeepRealText() {
    assertEquals("",SourceRouter.cleanRds("---"));
    assertEquals("",SourceRouter.cleanRds("- - -"));
    assertEquals("Radio Lublin",SourceRouter.cleanRds("Radio Lublin ---"));
    assertEquals("Radio Lublin",SourceRouter.cleanRds("Radio Lublin -"));
    assertEquals("Artist - Title",SourceRouter.cleanRds("Artist - Title"));
  }
}
