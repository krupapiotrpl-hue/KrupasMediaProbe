package pl.krupapiotr.mediaprobe;

public class RadioReaderTest {
    static void eq(String a, String b) { if (!a.equals(b)) throw new AssertionError(a + " != " + b); }
    public static void main(String[] args) {
        eq("R | SK", DisplayText.format("com.android.fmradio.ext", "SK"));
        eq("R | Radio ZET", DisplayText.format("com.navimods.radio", "Radio ZET"));
        eq("R | R Lublin", DisplayText.format("com.navimods.radio", RdsText.join("R Lublin", "-")));
        eq("", DisplayText.format("com.android.fmradio.ext", ""));
        eq("M | Hotel California", DisplayText.format("com.qf.MusicPlayer", "Hotel California"));
        eq("M | Track 1", DisplayText.format("com.ts.music", "Track 1"));
        eq("> | Radio Ga Ga", DisplayText.format("com.google.android.youtube", "Radio Ga Ga"));
        eq("Unknown title", DisplayText.format("com.unknown.app", "Unknown title"));
        eq("R | 14:30", DisplayText.format("com.navimods.radio", RdsText.join("14:30")));
        eq("SK", UiMediaTitle.radio(java.util.Arrays.asList(
            new UiMediaTitle.Field("com.android.fmradio.ext:id/tv_ps", "SK", false, true))));
        RadioRdsCache cache = new RadioRdsCache();
        cache.observe("radio", "SK", 1000);
        eq("SK", cache.read("radio", 3500));
        eq("", cache.read("radio", 3501));
        eq("", cache.read("youtube", 1001));
        cache.observe("radio", "ZET", 4000);
        eq("ZET", cache.read("radio", 4001));
        cache.clear(); eq("", cache.read("radio", 4002));
        System.out.println("Radio reader: R, >, M BLE prefixes and source isolation passed");
    }
}
