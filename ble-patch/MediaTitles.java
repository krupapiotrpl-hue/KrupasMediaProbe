package pl.krupapiotr.mediaprobe;

import java.util.Locale;

/** Shared title validation for sessions, notifications and the factory UI. */
final class MediaTitles {
    static boolean factory(String pkg) {
        String p = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        return !p.contains("youtube") && (p.contains("music") || p.contains("mp3")
                || p.contains("player") || p.contains("media"))
                && !p.equals("pl.krupapiotr.mediaprobe");
    }

    static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (s.isEmpty() || s.length() > 200) return "";
        String u = s.toUpperCase(Locale.ROOT);
        if (u.matches("T[1-4]")) return "";
        if (s.matches("[-+]?\\d{1,3}:\\d{2}(?::\\d{2})?(?:\\s*/\\s*\\d{1,3}:\\d{2}(?::\\d{2})?)?")) return "";
        if (u.matches("\\d+(?:\\s*/\\s*\\d+)?")) return "";
        if (u.matches("PLAY|PAUSE|NEXT|PREV|PREVIOUS|STOP|MUSIC|MP3|USB|SD|BLUETOOTH|LOCAL MUSIC|MUZYKA|USTAWIENIA|SETTINGS|UNKNOWN|UNKNOWN TITLE|UNKNOWN ARTIST")) return "";
        return s;
    }

    static String first(String... values) {
        for (String value : values) {
            String title = clean(value);
            if (!title.isEmpty()) return title;
        }
        return "";
    }
}
