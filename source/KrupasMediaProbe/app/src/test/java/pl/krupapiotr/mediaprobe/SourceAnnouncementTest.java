package pl.krupapiotr.mediaprobe;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceAnnouncementTest {
    @Test public void dabToFmSelectsFmEvenWithoutRds() {
        SourceAnnouncement a = new SourceAnnouncement();
        assertEquals(Arrays.asList("CLEAR:ALL", "SOURCE:DAB"), a.commands("DAB", 100));
        assertEquals(Arrays.asList("CLEAR:ALL", "SOURCE:FM"), a.commands("FM", 200));
    }
    @Test public void changingMusicSourceClearsPriorTitle() {
        SourceAnnouncement a = new SourceAnnouncement();
        a.commands("MP3", 0);
        assertEquals(Arrays.asList("CLEAR:ALL", "SOURCE:YT"), a.commands("YT", 1));
        assertEquals(Arrays.asList("CLEAR:ALL", "SOURCE:OFF"), a.commands("OFF", 2));
    }
    @Test public void stableSourceDoesNotClearOrResetScroll() {
        SourceAnnouncement a = new SourceAnnouncement();
        a.commands("FM", 50);
        assertEquals(Collections.emptyList(), a.commands("FM", 500));
        assertEquals(Arrays.asList("SOURCE:FM"), a.commands("FM", 10050));
    }
    @Test public void reconnectAndCallEndResendFullSourceBoundary() {
        SourceAnnouncement a = new SourceAnnouncement();
        a.commands("FM", 100);
        a.reset();
        assertEquals(Arrays.asList("CLEAR:ALL", "SOURCE:FM"), a.commands("FM", 200));
    }
}
