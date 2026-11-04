package pl.krupapiotr.mediaprobe;
import org.junit.Test;
import static org.junit.Assert.*;
public class SourceRouterTest {
    @Test public void rejectsPlaceholderWithInvisibleCharactersAndPrefix() {
        for(String s:new String[]{"TODO","todo"," TODO ","T\u200bODO","TODO\u00a0","R| TODO","RDS:TODO"}) {
            assertTrue(s,SourceRouter.isPlaceholder(s));
            assertEquals("",SourceRouter.clean(s));
        }
    }
    @Test public void doesNotSuppressRealSongsOrArtists() {
        assertEquals("TOTO",SourceRouter.clean("TOTO"));
        assertEquals("Todo de ti",SourceRouter.clean("Todo de ti"));
    }
    @Test public void preservesStationAndTrackMetadata() {
        assertEquals("RMF FM - Africa - TOTO",SourceRouter.radioDisplay("RMF FM","Africa","RMF FM","TOTO"));
        assertEquals("Africa - TOTO",SourceRouter.radioDisplay("TODO","Africa","TOTO",""));
        assertEquals("",SourceRouter.radioDisplay("FM 101.1","NavRadio+","TODO",""));
    }
    @Test public void recognizesNavRadioAsRadio() {
        assertTrue(SourceRouter.isRadioPackage("com.navimods.radio"));
        assertFalse(SourceRouter.isRadioPackage("com.android.music"));
    }
}
