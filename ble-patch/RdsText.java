package pl.krupapiotr.mediaprobe;

import java.util.LinkedHashSet;

/** Radio data is not a music title: a clock inside an RDS field is valid. */
final class RdsText {
    static String join(String... values) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) continue;
            String text = value.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
            if (text.isEmpty() || text.length() > 240
                    || text.matches("(?i)NONE|NULL|UNDEFINED|UNKNOWN|N/A|<UNKNOWN>|T[1-4]|RDS|RT|PS|FM|AM|PTY|TA|AF|ST|STEREO")) continue;
            if (text.matches("(?i)(?:FM|AM)?\\s*\\d+[.,]\\d+\\s*(?:MHZ)?")) continue;
            parts.add(text);
        }
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) result.append(" | ");
            result.append(part);
        }
        return result.toString();
    }
}
