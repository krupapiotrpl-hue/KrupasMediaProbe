package pl.krupapiotr.mediaprobe;

public class RdsFormatTest {
    private static void eq(String a, String b) {
        if (!a.equals(b)) throw new AssertionError(a + " != " + b);
    }
    public static void main(String[] args) {
        eq("R Lublin", RdsText.join("R Lublin", "-", "|", "—", "...", " / ", "none"));
        eq("Radio ZET | 14:30", RdsText.join("Radio ZET", "RADIO ZET", "-", "14:30"));
        eq("AC/DC | Love - Hate", RdsText.join("AC/DC", "Love - Hate"));
        eq("SK", RdsText.join("SK", ""));
        eq("", RdsText.join(null, "", "-", "|", "–", "---", "null"));
        eq("Łódź | 14:30", UiMediaTitle.radio(java.util.Arrays.asList(
            new UiMediaTitle.Field("rds_ps", "Łódź", false, false),
            new UiMediaTitle.Field("radio_text", "-", false, false),
            new UiMediaTitle.Field("rds_clock", "14:30", false, false),
            new UiMediaTitle.Field("tv_preset_station_name", "RMF", false, false))));
        System.out.println("RDS format: punctuation placeholders, duplicate names, clocks and genuine titles passed");
    }
}
