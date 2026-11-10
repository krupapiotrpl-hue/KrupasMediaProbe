package pl.krupapiotr.mediaprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class StationRdsRotatorTest {
    @Test public void alternatesEveryFiveSecondsStartingWithCustomStation() {
        StationRdsRotator r = new StationRdsRotator();
        assertEquals("MOJA STACJA", r.select(100000L,"102.5","MOJA STACJA","Wiadomosci"));
        assertEquals("MOJA STACJA", r.select(104999L,"102.5","MOJA STACJA","Wiadomosci"));
        assertEquals("Wiadomosci", r.select(105000L,"102.5","MOJA STACJA","Wiadomosci"));
        assertEquals("Wiadomosci", r.select(109999L,"102.5","MOJA STACJA","Wiadomosci"));
        assertEquals("MOJA STACJA", r.select(110000L,"102.5","MOJA STACJA","Wiadomosci"));
        assertEquals("Nowy tekst", r.select(116000L,"102.5","MOJA STACJA","Nowy tekst"));
    }

    @Test public void resetsOnTuningOrSourceSwitch() {
        StationRdsRotator r = new StationRdsRotator();
        assertEquals("STACJA 1",r.select(100000L,"100.0","STACJA 1","RDS 1"));
        assertEquals("RDS 1",r.select(105000L,"100.0","STACJA 1","RDS 1"));
        assertEquals("STACJA 2",r.select(106000L,"101.0","STACJA 2","RDS 2"));
        assertEquals("RDS 2",r.select(111000L,"101.0","STACJA 2","RDS 2"));
        r.reset();
        assertEquals("STACJA 2",r.select(112000L,"101.0","STACJA 2","RDS 2"));
    }

    @Test public void retainsSingleTextWhenOneFieldIsMissing() {
        StationRdsRotator r = new StationRdsRotator();
        assertEquals("SAM RDS",r.select(100000L,"98.4","","SAM RDS"));
        assertEquals("TYLKO STACJA",r.select(105000L,"98.4","TYLKO STACJA",""));
        assertEquals("TA SAMA",r.select(109000L,"98.4","TA SAMA","TA SAMA"));
        assertEquals("",r.select(110000L,"98.4","",""));
    }
}
