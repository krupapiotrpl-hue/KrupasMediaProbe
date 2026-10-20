package pl.krupapiotr.mediaprobe;

import java.util.LinkedHashSet;

/** Radio data is not a music title: a clock inside an RDS field is valid. */
final class RdsText {
    static String join(String... values) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) continue;
            String text = value.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
            // Vendor notifications use '-' or '|' for unavailable fields.
            // Keep punctuation within real titles, but never join placeholders.
            boolean content = false;
            for (int i = 0; i < text.length();) {
                int cp = text.codePointAt(i);
                if (Character.isLetterOrDigit(cp)) { content = true; break; }
                i += Character.charCount(cp);
            }
            if (!content) continue;
            if (text.isEmpty() || text.length() > 240
                    || text.matches("(?i)NONE|NULL|UNDEFINED|UNKNOWN|N/A|<UNKNOWN>|T[1-4]|RADIO|FM RADIO|RDS|RT|PS|FM|AM|PTY|TA|AF|ST|STEREO")) continue;
            if (text.matches("(?i)(?:FM|AM)?\\s*\\d+[.,]\\d+\\s*(?:MHZ)?")) continue;
            if (keys.add(text.toLowerCase(java.util.Locale.ROOT))) parts.add(text);
        }
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) result.append(" | ");
            result.append(part);
        }
        return result.toString();
    }
}
