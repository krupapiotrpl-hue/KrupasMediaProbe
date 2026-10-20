package pl.krupapiotr.mediaprobe;

import java.util.List;
import java.util.Locale;

/** Vendor UI fields can lack resource IDs. Accept a small now-playing panel,
 * never a scrolling playlist or clickable controls. */
final class UiMediaTitle {
    static String radio(List<Field> fields) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (Field f : fields) {
            // Some head units make the current PS/RT label clickable. A named
            // RDS field is still data; presets and list rows remain excluded.
            if (f.list || f.id.contains("preset") || !f.id.matches(".*(rds|station_name|program_service|radio_text|radiotext|/ps$|/rt$|/tv_ps$|/tv_rt$).*")) continue;
            values.add(f.text);
        }
        return RdsText.join(values.toArray(new String[0]));
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
