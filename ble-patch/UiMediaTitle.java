package pl.krupapiotr.mediaprobe;

import java.util.List;
import java.util.Locale;

/** Vendor UI fields can lack resource IDs. Accept a small now-playing panel,
 * never a scrolling playlist or clickable controls. */
final class UiMediaTitle {
    static String radio(List<Field> fields) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (Field f : fields) {
            if (f.list || f.control || f.id.contains("preset") || !f.id.matches(".*(rds|station_name|program_service|radio_text|radiotext|/ps$|/rt$|/tv_ps$|/tv_rt$).*")) continue;
            values.add(f.text);
        }
        if (!values.isEmpty()) {
            String named = RdsText.join(values.toArray(new String[0]));
            if (!named.isEmpty()) return named;
            // Radio UI sometimes exposes only a frequency in an RDS field.
            for (String value : values) {
                String freq = normalizedFrequency(value);
                if (!freq.isEmpty()) return freq;
            }
        }
        // Some K706 radio skins expose the current station as plain TextView
        // without a resource ID. Use it ONLY if the visible panel also exposes
        // a frequency and has exactly one eligible text candidate. Never use
        // preset/list or clickable station buttons as evidence of live RDS.
        boolean hasFrequency = false;
        String currentFrequency = "";
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        for (Field f : fields) {
            if (f.list || f.control || f.id.contains("preset")) continue;
            String t = f.text == null ? "" : f.text.trim();
            if (t.matches("(?i)(?:FM\\s*)?\\d{2,3}[.,]\\d{1,2}\\s*(?:MHZ)?")) {
                hasFrequency = true;
                currentFrequency = t.replace(',', '.');
                continue;
            }
            if (!f.id.isEmpty() && !f.id.matches(".*(current|station|radio|display|name|text|title).*")) continue;
            if (t.length() < 3 || t.length() > 42 || !t.matches(".*\\p{L}.*")
                    || t.matches("(?i)(RDS|FM|AM|STEREO|SCAN|BAND|PTY|TA|AF|LOC|DX|SETTINGS|MENU|RADIO|UNKNOWN|NONE|NULL|[Tt][1-4])"))
                continue;
            candidates.add(t);
        }
        return hasFrequency && candidates.size() == 1
                ? RdsText.join(candidates.iterator().next())
                : (hasFrequency ? normalizedFrequency(currentFrequency) : "");

    }
    private static String normalizedFrequency(String value) {
        if (value == null) return "";
        String text = value.trim().replace(',', '.');
        if (!text.matches("(?i)(?:FM\\s*)?\\d{2,3}\\.\\d{1,2}\\s*(?:MHZ)?")) return "";
        text = text.replaceFirst("(?i)^FM\\s*", "").trim();
        return "FM " + text;
    }
    static final class Field {
        final String id, text;
        final boolean list, control;
        Field(String id, String text, boolean list, boolean control) {
            this.id = id == null ? "" : id.toLowerCase(Locale.ROOT);
            this.text = text; this.list = list; this.control = control;
        }
    }
    static String choose(List<Field> fields) {
        String best = "", fallback = "";
        int bestScore = 0, count = 0;
        for (Field f : fields) {
            if (f.list || f.control) continue;
            String text = MediaTitles.clean(f.text);
            if (text.isEmpty() || text.matches("(?i).*\\b(?:FM|AM)\\s*\\d+[.,]\\d+.*")) continue;
            if (f.id.matches(".*(artist|singer|album|folder|directory|path).*")) continue;
            int score = f.id.matches(".*(title|song|track|file|music_name|name_music).*" ) ? 100 : 0;
            if (text.matches("(?i).*\\.(mp3|flac|wav|aac|m4a|ogg)$")) score += 80;
            if (score > bestScore) { best = text; bestScore = score; }
            if (text.length() >= 3) { count++; if (fallback.isEmpty()) fallback = text; }
        }
        return bestScore > 0 ? best : count <= 4 ? fallback : "";
    }
}
