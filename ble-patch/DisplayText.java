package pl.krupapiotr.mediaprobe;

import java.util.Locale;

/** Printable BLE source markers. */
final class DisplayText {
    static String format(String pkg, String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String title = value.trim();
        String source = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        if (source.contains("radio") || source.contains(".fm")) return "R | " + title;
        if (source.contains("youtube")) return "> | " + title;
        if (MediaTitles.factory(pkg)) return "M | " + title;
        return title;
    }
}
