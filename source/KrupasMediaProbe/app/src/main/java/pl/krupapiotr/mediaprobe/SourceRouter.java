package pl.krupapiotr.mediaprobe;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalization and routing of MP3 and OEM FM radio, independently of BLE transport. */
final class SourceRouter {
    private static final Pattern FREQ = Pattern.compile("(?<![0-9])(?:8[7-9]|9[0-9]|10[0-8])(?:[.,][0-9]{1,2})?(?![0-9])");
    private SourceRouter() {}

    static String clean(String v) {
        if (v == null) return "";
        String s = v.replaceAll("[\\p{Cf}\\p{Z}\\s]+", " ").trim();
        if (s.equalsIgnoreCase("unknown") || s.equalsIgnoreCase("<unknown>") ||
                s.equalsIgnoreCase("null") || s.equalsIgnoreCase("none") ||
                isPlaceholder(s) || s.equalsIgnoreCase("unknown title")) return "";
        return s.length() > 90 ? s.substring(0, 90) : s;
    }

    static boolean isPlaceholder(String value) {
        if (value == null) return false;
        String s = value.replaceAll("[\\p{Cf}\\p{Z}\\s]", "").trim();
        s = s.replaceFirst("(?i)^(?:R[|]|RDS:|TITLE:)", "");
        return s.equalsIgnoreCase("TODO");
    }

    // A frequency alone or a player name is not radio text. Other metadata may
    // legitimately contain digits, FM, or short station names (e.g. RMF FM).
    static String radioDisplay(String title, String artist, String display, String subtitle) {
        java.util.LinkedHashSet<String> parts = new java.util.LinkedHashSet<>();
        for (String value : new String[]{title, artist, display, subtitle}) {
            String t = clean(value);
            if (t.isEmpty() || isPlaceholder(t)) continue;
            String u = t.toUpperCase(Locale.ROOT);
            if (u.equals("RADIO") || u.equals("NAVRADIO") || u.equals("NAVRADIO+") ||
                    u.equals("NAVRADIO PLUS") || u.equals("FM")) continue;
            if (t.matches("(?i)^(?:FM\\s*)?[0-9]+(?:[.,][0-9]+)?(?:\\s*MHz)?$")) continue;
            boolean duplicate = false;
            for (String part : parts) if (part.equalsIgnoreCase(t)) duplicate = true;
            if (!duplicate) parts.add(t);
        }
        return clean(androidFreeJoin(parts));
    }

    private static String androidFreeJoin(java.util.Set<String> parts) {
        StringBuilder out = new StringBuilder();
        for (String s : parts) { if (out.length() > 0) out.append(" - "); out.append(s); }
        return out.toString();
    }

    static String frequency(String source) {
        String s = clean(source);
        Matcher m = FREQ.matcher(s);
        while (m.find()) {
            String raw = m.group().replace(',', '.');
            try {
                double value = Double.parseDouble(raw);
                if (value >= 87.5 && value <= 108.0) return String.format(Locale.US, "%.1f", value);
            } catch (Exception ignored) {}
        }
        return "";
    }

    // DAB-Z is a separate digital source, not analog FM/RDS.
    static boolean isDabPackage(String pkg) {
        return pkg != null && (pkg.equalsIgnoreCase("com.zoulou.dab") ||
                pkg.toLowerCase(Locale.ROOT).contains("dabz"));
    }

    static String dabDisplay(String... values) {
        java.util.LinkedHashSet<String> parts = new java.util.LinkedHashSet<>();
        for (String value : values) {
            String s = clean(value);
            String u = s.toUpperCase(Locale.ROOT);
            if (s.isEmpty() || u.equals("DAB") || u.equals("DAB+") ||
                    u.equals("DAB-Z") || u.equals("DAB Z") ||
                    u.equals("DAB-Z PLAYER") || u.equals("USB")) continue;
            if (!parts.contains(s)) parts.add(s);
        }
        return clean(androidFreeJoin(parts));
    }

    static boolean isRadioPackage(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(Locale.ROOT);
        if (isDabPackage(pkg)) return false;
        return p.contains("radio") || p.contains("navimod") || p.contains("fmplayer") || p.contains("fmradio");
    }

    // Only for the local MP3 UI metadata fallback. Keep YouTube, MP4 and DAB
    // classification unchanged; never promote their UI labels to an MP3 title.
    static boolean isLocalMp3Package(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        String p = pkg.toLowerCase(Locale.ROOT);
        if (isDabPackage(pkg) || isRadioPackage(pkg) ||
                p.contains("youtube") || p.contains("video") ||
                p.contains("mp4") || p.contains("movie")) return false;
        return p.equals("mp3-ui") || p.contains("mp3") || p.contains("music") ||
                p.contains("audio") || p.contains("usbplayer") ||
                p.contains("localplayer");
    }

    static boolean isAudioPackage(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(Locale.ROOT);
        return p.contains("music") || p.contains("audio") || p.contains("player") ||
                p.contains("spotify") || p.contains("youtube") || p.contains("poweramp") ||
                p.contains("vlc") || p.contains("audiobook");
    }

    static boolean validStation(String s) {
        s = clean(s);
        if (s.length() < 3 || s.length() > 90 || frequency(s).length() > 0) return false;
        String u = s.toUpperCase(Locale.ROOT);
        if (!u.matches(".*[\\p{L}].*")) return false;
        if (u.equals("RADIO") || u.equals("FM") || u.equals("RADIO FM") || u.equals("FM RADIO") ||
                u.equals("TODO") || u.equals("TRYB") || u.equals("PROGRAMY") ||
                u.equals("EQUALIZER") || u.equals("USTAWIENIA") || u.equals("BRAK") ||
                u.equals("NONE") || u.equals("NULL") || u.equals("UNKNOWN")) return false;
        if (u.matches(".*\\b(?:AM|MHz|RDS|BAND|SCAN|EQ|STEREO|MONO|PTY|ST|LOC|DX|NEXT|PREV|SETTINGS|HOME|MENU|MP3|USB|BT|PLAY|PAUSE|STOP|MUTE|AUTO|SEARCH)\\b.*") && u.length() < 13) return false;
        if (u.contains("USTAWIEN") || u.contains("CZĘSTOTLIW") || u.contains("PRZEŁĄCZ") ||
                u.contains("GŁOŚNO") || u.contains("POPRZEDNI") || u.contains("NASTĘPNY") ||
                u.contains("PRESET") || u.contains("EQUALIZER") ||
                u.contains("PROGRAMY") || u.contains("WYBIERZ") ||
                u.contains("ULUBIONE") || u.contains("LISTA STACJI")) return false;
        int letters = 0, digits = 0;
        for (int i=0;i<s.length();i++) { if(Character.isLetter(s.charAt(i)))letters++; if(Character.isDigit(s.charAt(i)))digits++; }
        return letters >= 3 && digits < letters;
    }

    static String mediaDisplay(String title, String artist) {
        String t = clean(title), a = clean(artist);
        if (t.isEmpty()) return "";
        if (a.isEmpty() || t.equalsIgnoreCase(a)) return t;
        String full = a + " - " + t;
        return full.length() > 90 ? full.substring(0, 90) : full;
    }
}

