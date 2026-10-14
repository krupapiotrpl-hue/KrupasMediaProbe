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
        String s = v.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        String u = s.toLowerCase(Locale.ROOT);
        if (u.equals("unknown") || u.equals("<unknown>") || u.equals("null") ||
                u.equals("unknown title") || u.equals("none") || u.equals("todo") ||
                u.equals("n/a") || u.equals("undefined") || u.equals("not set") ||
                u.equals("no title")) return "";
        return s.length() > 90 ? s.substring(0, 90) : s;
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

    static boolean isRadioPackage(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(Locale.ROOT);
        return p.contains("radio") || p.contains("navimod") || p.contains("fmplayer") || p.contains("fmradio");
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
        if (s.length() < 3 || s.length() > 34 || frequency(s).length() > 0) return false;
        String u = s.toUpperCase(Locale.ROOT);
        if (!u.matches(".*[\\p{L}].*")) return false;
        if (u.equals("RADIO") || u.equals("FM") || u.equals("RADIO FM") || u.equals("FM RADIO")) return false;
        if (u.matches(".*\\b(?:AM|MHZ|RDS|BAND|SCAN|EQ|STEREO|MONO|PTY|ST|LOC|DX|NEXT|PREV|SETTINGS|HOME|MENU|MP3|USB|BT|PLAY|PAUSE|STOP|MUTE|AUTO|SEARCH)\\b.*") && u.length() < 13) return false;
        if (u.contains("USTAWIEN") || u.contains("CZĘSTOTLIW") || u.contains("PRZEŁĄCZ") ||
                u.contains("GŁOŚNO") || u.contains("POPRZEDNI") || u.contains("NASTĘPNY") ||
                u.contains("PRESET") || u.contains("EQUALIZER")) return false;
        int letters = 0, digits = 0;
        for (int i=0;i<s.length();i++) {
            if(Character.isLetter(s.charAt(i))) letters++;
            if(Character.isDigit(s.charAt(i))) digits++;
        }
        return letters >= 3 && digits < letters;
    }

    static boolean validRadioText(String value, String station) {
        String s = clean(value);
        if (s.length() < 5 || frequency(s).length() > 0 || s.equalsIgnoreCase(clean(station))) return false;
        String u = s.toUpperCase(Locale.ROOT);
        if (!u.matches(".*[\\p{L}].*")) return false;
        if (u.matches(".*\\b(?:AM|MHZ|BAND|SCAN|EQ|STEREO|MONO|PTY|LOC|DX|NEXT|PREV|SETTINGS|HOME|MENU|USB|BT|PLAY|PAUSE|STOP|MUTE|AUTO|SEARCH|PRESET|EQUALIZER)\\b.*") && s.length() < 20) return false;
        int letters = 0;
        for (int i=0;i<s.length();i++) if(Character.isLetter(s.charAt(i))) letters++;
        return letters >= 4;
    }

    static String mediaDisplay(String title, String artist) {
        String t = clean(title), a = clean(artist);
        if (t.isEmpty()) return "";
        if (a.isEmpty() || t.equalsIgnoreCase(a)) return t;
        String full = a + " - " + t;
        return full.length() > 90 ? full.substring(0, 90) : full;
    }
}
