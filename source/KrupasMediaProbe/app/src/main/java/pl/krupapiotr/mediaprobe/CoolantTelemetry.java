package pl.krupapiotr.mediaprobe;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Optional coolant telemetry storage. Never synthesizes temperature values.
 * ESP firmware must supply independently verified readings before this is active.
 * Does not modify radio text, MediaSession, or the existing BLE TX characteristic.
 */
final class CoolantTelemetry {
    private static final String PREFS = "krupas_coolant";
    private static final long MAX_AGE_MS = 10000L;
    private CoolantTelemetry() {}

    static boolean accept(Context context, String message) {
        if (message == null || !message.startsWith("COOLANT:")) return false;
        try {
            String value = message.substring("COOLANT:".length()).trim();
            if (!value.matches("-?[0-9]{1,3}")) return false;
            int celsius = Integer.parseInt(value);
            // Plausibility gate only: not proof that the CAN decoding is correct.
            if (celsius < -40 || celsius > 150) return false;
            context.getSharedPreferences(PREFS, 0).edit()
                    .putInt("celsius", celsius)
                    .putLong("received_at", System.currentTimeMillis())
                    .apply();
            return true;
        } catch (RuntimeException ex) { return false; }
    }

    static String display(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, 0);
        long stamp = p.getLong("received_at", 0L);
        long age = System.currentTimeMillis() - stamp;
        if (stamp <= 0L || age < 0L || age > MAX_AGE_MS) return "Brak danych";
        return p.getInt("celsius", 0) + "°C";
    }
}
