package pl.krupapiotr.mediaprobe;

import android.content.Context;
import android.content.SharedPreferences;

final class MediaStateStore {
    private static final String PREF = "media_probe";

    private MediaStateStore() {}

    static void save(Context c, String source, String pkg, String title, String artist, String album, String state, String raw) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putLong("time", System.currentTimeMillis())
                .putString("source", nz(source))
                .putString("pkg", nz(pkg))
                .putString("title", nz(title))
                .putString("artist", nz(artist))
                .putString("album", nz(album))
                .putString("state", nz(state))
                .putString("raw", nz(raw))
                .apply();
    }

    static String dump(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long t = p.getLong("time", 0);
        return "OSTATNI ODCZYT\n" +
                "źródło: " + p.getString("source", "—") + "\n" +
                "pakiet: " + p.getString("pkg", "—") + "\n" +
                "tytuł: " + p.getString("title", "—") + "\n" +
                "wykonawca: " + p.getString("artist", "—") + "\n" +
                "album: " + p.getString("album", "—") + "\n" +
                "stan: " + p.getString("state", "—") + "\n" +
                "czas: " + (t == 0 ? "—" : new java.util.Date(t).toString()) + "\n" +
                "raw: " + p.getString("raw", "—");
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
