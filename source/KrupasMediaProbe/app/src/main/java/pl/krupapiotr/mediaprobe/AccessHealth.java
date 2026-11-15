package pl.krupapiotr.mediaprobe;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.os.SystemClock;

/** Read-only permission inspection; never toggles a user's accessibility grant. */
final class AccessHealth {
    private static long lastRebind = -30000;
    private AccessHealth() {}

    static boolean accessibilityEnabled(Context c) {
        return Settings.Secure.getInt(c.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
                EnabledComponents.contains(Settings.Secure.getString(c.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                        c.getPackageName(), RadioAccessibilityService.class.getName());
    }

    static boolean notificationEnabled(Context c) {
        return EnabledComponents.contains(Settings.Secure.getString(c.getContentResolver(),
                "enabled_notification_listeners"),
                c.getPackageName(), MediaProbeNotificationService.class.getName());
    }

    static void event(Context c, String message) {
        SharedPreferences p = c.getSharedPreferences("krupas_access_health", 0);
        String line = System.currentTimeMillis() + " uptime=" +
                SystemClock.elapsedRealtime() + " " + message;
        String old = p.getString("events", "");
        String[] lines = (old + line + "\n").split("\n");
        StringBuilder log = new StringBuilder();
        for (int i = Math.max(0, lines.length - 12); i < lines.length; i++)
            log.append(lines[i]).append('\n');
        p.edit().putString("events", log.toString()).apply();
    }

    static String summary(Context c) {
        boolean granted = accessibilityEnabled(c);
        boolean bound = RadioAccessibilityService.isConnected();
        boolean notifications = notificationEnabled(c);
        String current = "grant=" + granted + " bound=" + bound +
                " notification=" + notifications +
                " listener=" + MediaProbeNotificationService.isConnected();
        SharedPreferences p = c.getSharedPreferences("krupas_access_health", 0);
        if (!current.equals(p.getString("state", ""))) {
            event(c, current);
            p.edit().putString("state", current).apply();
        }
        String radio = !granted ? "WYŁĄCZONE W SYSTEMIE" :
                (bound ? "WŁĄCZONE / USŁUGA POŁĄCZONA" : "WŁĄCZONE / USŁUGA NIEPOŁĄCZONA");
        String listener = !notifications ? "WYŁĄCZONE W SYSTEMIE" :
                (MediaProbeNotificationService.isConnected() ? "WŁĄCZONE / USŁUGA POŁĄCZONA" :
                        "WŁĄCZONE / USŁUGA NIEPOŁĄCZONA");
        return "DOSTĘPNOŚĆ RADIO: " + radio + "\nPOWIADOMIENIA: " + listener +
                "\nODCZYT EKRANU: " + RadioAccessibilityService.sampleStatus();
    }

    static void recoverNotificationListener(Context c) {
        if (android.os.Build.VERSION.SDK_INT < 24) return;
        long now = SystemClock.elapsedRealtime();
        if (!notificationEnabled(c) || MediaProbeNotificationService.isConnected() ||
                now - lastRebind < 30000) return;
        lastRebind = now;
        try {
            NotificationListenerService.requestRebind(
                    new ComponentName(c, MediaProbeNotificationService.class));
            event(c, "NOTIFICATION_REBIND_REQUESTED");
        } catch (Exception e) {
            event(c, "NOTIFICATION_REBIND_FAILED " + e.getClass().getSimpleName());
        }
    }
}
