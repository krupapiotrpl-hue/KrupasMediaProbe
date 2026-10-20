package pl.krupapiotr.mediaprobe;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
public class UiAndBleTest {
    static void eq(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        eq("Letni deszcz", UiMediaTitle.choose(Arrays.asList(
            new UiMediaTitle.Field("", "T1", false, false),
            new UiMediaTitle.Field("", "00:12 / 03:45", false, false),
            new UiMediaTitle.Field("", "Letni deszcz", false, false))));
        eq("Utwór", UiMediaTitle.choose(Arrays.asList(
            new UiMediaTitle.Field("artist", "Wykonawca", false, false),
            new UiMediaTitle.Field("song", "Utwór", false, false),
            new UiMediaTitle.Field("title", "Inny utwór", true, false))));
        eq("", UiMediaTitle.choose(Arrays.asList(
            new UiMediaTitle.Field("", "PLAY", false, true),
            new UiMediaTitle.Field("title", "Lista utworów", true, false))));
        eq("Radio ZET", UiMediaTitle.choose(Arrays.asList(new UiMediaTitle.Field("station_name", "Radio ZET", false, false))));
        eq("Nieznana stacja", UiMediaTitle.radio(Arrays.asList(new UiMediaTitle.Field("rds_ps", "Nieznana stacja", false, false))));
        eq("", UiMediaTitle.radio(Arrays.asList(new UiMediaTitle.Field("rds_ps", "FM 107.0", false, false))));
        eq("Radio ZET | EMO - Letni deszcz | 14:30", UiMediaTitle.radio(Arrays.asList(
            new UiMediaTitle.Field("rds_ps", "Radio ZET", false, false),
            new UiMediaTitle.Field("radio_text", "EMO - Letni deszcz", false, false),
            new UiMediaTitle.Field("rds_clock", "14:30", false, false),
            new UiMediaTitle.Field("android_clock", "15:00", false, false))));
        eq("VOX FM", UiMediaTitle.radio(Arrays.asList(
            new UiMediaTitle.Field("", "95,6 MHz", false, false),
            new UiMediaTitle.Field("", "VOX FM", false, false))));
        eq("", UiMediaTitle.radio(Arrays.asList(
            new UiMediaTitle.Field("", "95,6 MHz", false, false),
            new UiMediaTitle.Field("", "VOX FM", false, false),
            new UiMediaTitle.Field("", "RMF FM", false, false))));
        eq("", UiMediaTitle.radio(Arrays.asList(
            new UiMediaTitle.Field("", "95,6 MHz", false, false),
            new UiMediaTitle.Field("preset", "VOX FM", false, true))));
        eq("14:30", RdsText.join("14:30", "14:30"));
        eq("", MediaTitles.clean("14:30"));
        String title = "Łódź 🎵 Letni deszcz official video";
        for (int limit : new int[]{20, 23, 50, 514}) {
            String fitted = BlePayload.fit(title, limit);
            if (fitted.getBytes(StandardCharsets.UTF_8).length > limit || !title.startsWith(fitted)
                || fitted.indexOf('\uFFFD') >= 0) throw new AssertionError("UTF-8 payload");
        }
        eq(title, BlePayload.fit(title, 514));
        eq(" ", BlePayload.fit(" ", 20));
        System.out.println("UI and BLE: vendor fields, playlists, controls and UTF-8 payload passed");
    }
}
