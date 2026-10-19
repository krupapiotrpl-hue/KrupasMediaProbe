package pl.krupapiotr.mediaprobe;
public class StableTitleTest {
    static void eq(String a, String b) { if (!a.equals(b)) throw new AssertionError(a + " != " + b); }
    public static void main(String[] args) {
        StableTitle stable = new StableTitle();
        eq("Hotel California", stable.update("mp3", "Hotel California", 0));
        eq("Hotel California", stable.update("mp3", "Eagles", 100));
        eq("Hotel California", stable.update("mp3", "Hotel California", 200));
        eq("Hotel California", stable.update("mp3", "Next song", 1000));
        eq("Next song", stable.update("mp3", "Next song", 1600));
        eq("Next song", stable.update("mp3", "", 2000));
        eq("", stable.update("mp3", "", 4100));
        eq("New", stable.update("mp3", "New", 5000));
        eq("", stable.update("youtube", "", 5100));
        for (String placeholder : new String[]{"none", "None", "null", "undefined", "unknown", "N/A", "<unknown>"}) {
            eq("", MediaTitles.clean(placeholder));
            eq("", RdsText.join(placeholder));
        }
        eq("14:30", RdsText.join("none", "14:30"));
        eq("", UiMediaTitle.radio(java.util.Arrays.asList(
            new UiMediaTitle.Field("tv_preset_station_name", "RMF", false, false))));
        System.out.println("StableTitle: redraw jitter, source switch, missing expiry, placeholders and presets passed");
    }
}
