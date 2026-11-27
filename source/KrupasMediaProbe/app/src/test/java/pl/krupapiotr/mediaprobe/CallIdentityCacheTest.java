package pl.krupapiotr.mediaprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class CallIdentityCacheTest {
    @Test public void holdsNumberAndNameWhenFreshUiSamplesDisappear() {
        CallIdentityCache cache = new CallIdentityCache();
        CallIdentityCache.Snapshot first=cache.update("+48 501 234 567","Córka");
        assertEquals("+48 501 234 567",first.number);
        assertEquals("Córka",first.name);
        CallIdentityCache.Snapshot later=cache.update("","");
        assertEquals("+48 501 234 567",later.number);
        assertEquals("Córka",later.name);
        assertTrue(later.numberCached);
        assertTrue(later.nameCached);
    }

    @Test public void clearsOldCallIdentityBeforeNextCall() {
        CallIdentityCache cache = new CallIdentityCache();
        cache.update("501234567","Córka");
        cache.clear();
        assertEquals("",cache.update("","").name);
        assertEquals("",cache.update("","").number);
        assertEquals("ROZMOWA",CallDisplayPolicy.display(
                CallDisplayPolicy.CONNECTED,cache.update("","").number,"",false));
    }

    @Test public void unrelatedNumberDoesNotReplaceConfirmedCaller() {
        CallIdentityCache cache = new CallIdentityCache();
        cache.update("501234567","Córka");
        CallIdentityCache.Snapshot second=cache.update("555222111","");
        assertEquals("501234567",second.number);
        assertEquals("Córka",second.name);
    }

    @Test public void newNameOverridesSavedNumberOnDisplay() {
        CallIdentityCache cache = new CallIdentityCache();
        cache.update("501234567","");
        CallIdentityCache.Snapshot result=cache.update("","Córka");
        assertEquals("Córka",CallDisplayPolicy.display(
                CallDisplayPolicy.CONNECTED,result.number,result.name,false));
    }

    @Test public void ignoresGenericPhoneLabels() {
        CallIdentityCache cache = new CallIdentityCache();
        assertEquals("",cache.update("","Rozmowa").name);
    }
}
