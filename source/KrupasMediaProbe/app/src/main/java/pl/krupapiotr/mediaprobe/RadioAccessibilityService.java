package pl.krupapiotr.mediaprobe;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * KRUPAS Media Pro 3.0 DIAG-1
 * Minimalny, pasywny odczyt widocznego ekranu OEM FM/RDS.
 * Bez przewijania, rotacji, timeoutów źródeł i bez mieszania MP3.
 */
public final class RadioAccessibilityService extends AccessibilityService {
    private long lastScan;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (!SourceRouter.isRadioPackage(pkg)) return;

        long tick = android.os.SystemClock.elapsedRealtime();
        if (tick - lastScan < 250) return;
        lastScan = tick;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        List<String> strings = new ArrayList<>();

        if (event.getText() != null) {
            for (CharSequence text : event.getText()) {
                if (text != null) addUnique(strings, text.toString());
            }
        }
        if (event.getContentDescription() != null) {
            addUnique(strings, event.getContentDescription().toString());
        }

        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        if (root != null) q.add(root);
        int visited = 0;
        while (!q.isEmpty() && visited++ < 250) {
            AccessibilityNodeInfo n = q.removeFirst();
            if (n.getText() != null) addUnique(strings, n.getText().toString());
            if (n.getContentDescription() != null) addUnique(strings, n.getContentDescription().toString());
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }

        String freq = "";
        for (String s : strings) {
            String f = SourceRouter.frequency(s);
            if (!f.isEmpty()) { freq = f; break; }
        }

        String station = "";
        if (!freq.isEmpty()) {
            for (String s : strings) {
                if (!SourceRouter.validStation(s)) continue;
                String u = s.toUpperCase(Locale.ROOT);
                if (u.contains("RADIO") || u.contains("PRZEBOJE") || u.contains("RMF") ||
                    u.contains("ESKA") || u.contains("ANTY") || u.contains("ZET") ||
                    u.contains("ROCK") || u.contains("TOK") || u.contains("JEDYNKA") ||
                    u.contains("TRÓJKA") || u.contains("VOX") || u.contains("MARYJA")) {
                    station = s;
                    break;
                }
            }
            if (station.isEmpty()) {
                for (String s : strings) {
                    if (SourceRouter.validStation(s)) { station = s; break; }
                }
            }
        }

        long now = System.currentTimeMillis();
        SharedPreferences p = getSharedPreferences("krupas_radio", MODE_PRIVATE);
        String oldFreq = p.getString("freq", "");
        String oldStation = p.getString("station", "");

        String resolvedFreq = !freq.isEmpty() ? freq : oldFreq;
        boolean tuned = !freq.isEmpty() && !oldFreq.isEmpty() && !freq.equals(oldFreq);
        String resolvedStation = !station.isEmpty() ? station : (tuned ? "" : oldStation);

        p.edit()
                .putString("pkg", pkg)
                .putString("freq", resolvedFreq)
                .putString("station", resolvedStation)
                .putString("nodes", strings.toString())
                .putLong("seen", now)
                .apply();

        getSharedPreferences("krupas_diag", MODE_PRIVATE).edit()
                .putString("rx_raw", strings.toString())
                .putString("rx_recognized", "FM=" + resolvedFreq + " | RDS=" + resolvedStation)
                .putString("rx_pkg", pkg)
                .putLong("rx_time", now)
                .apply();
    }

    private static void addUnique(List<String> out, String s) {
        s = SourceRouter.clean(s);
        if (!s.isEmpty() && !out.contains(s) && out.size() < 80) out.add(s);
    }

    @Override public void onInterrupt() {}
}
