package pl.krupapiotr.mediaprobe;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Passive OEM FM/RDS and MP3 reader for K706.
 * Keeps radio state stable across short overlays and reads station + RadioText separately.
 */
public final class RadioAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastScan;
    private boolean connected;
    private final Runnable sampler = new Runnable() {
        @Override public void run() {
            if (!connected) return;
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null && root.getPackageName() != null) {
                    String pkg = root.getPackageName().toString();
                    if (SourceRouter.isRadioPackage(pkg) || SourceRouter.isAudioPackage(pkg)) {
                        capture(pkg, null, root);
                    }
                }
            } catch (Exception ignored) {}
            handler.postDelayed(this, 5000);
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        handler.removeCallbacks(sampler);
        handler.postDelayed(sampler, 1500);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        boolean radio = SourceRouter.isRadioPackage(pkg);
        boolean mp3 = SourceRouter.isAudioPackage(pkg) && !radio;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (radio) {
                getSharedPreferences("krupas_radio", MODE_PRIVATE).edit()
                        .putBoolean("visible", true).putString("last_window", pkg).apply();
                getSharedPreferences("krupas_mp3_ui", MODE_PRIVATE).edit()
                        .putBoolean("visible", false).apply();
            } else if (mp3) {
                getSharedPreferences("krupas_mp3_ui", MODE_PRIVATE).edit()
                        .putBoolean("visible", true).apply();
                getSharedPreferences("krupas_radio", MODE_PRIVATE).edit()
                        .putBoolean("visible", false).putString("last_window", pkg).apply();
            } else {
                // System overlays, volume panels and navigation must not invalidate FM/RDS.
                getSharedPreferences("krupas_mp3_ui", MODE_PRIVATE).edit()
                        .putBoolean("visible", false).apply();
                getSharedPreferences("krupas_radio", MODE_PRIVATE).edit()
                        .putString("last_window", pkg).apply();
            }
        }
        if (!radio && !mp3) return;

        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastScan < 250 &&
                event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        lastScan = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && root.getPackageName() != null &&
                !pkg.equals(root.getPackageName().toString())) root = null;
        capture(pkg, event, root);
    }

    private void capture(String pkg, AccessibilityEvent event, AccessibilityNodeInfo root) {
        boolean radio = SourceRouter.isRadioPackage(pkg);
        boolean mp3 = SourceRouter.isAudioPackage(pkg) && !radio;
        if (!radio && !mp3) return;

        List<String> strings = new ArrayList<>();
        if (event != null) {
            if (event.getText() != null)
                for (CharSequence text : event.getText())
                    if (text != null) addUnique(strings, text.toString());
            if (event.getContentDescription() != null)
                addUnique(strings, event.getContentDescription().toString());
        }
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        if (root != null) queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 250) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node.getText() != null) addUnique(strings, node.getText().toString());
            if (node.getContentDescription() != null) addUnique(strings, node.getContentDescription().toString());
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }

        long now = System.currentTimeMillis();
        if (mp3) {
            String title = "";
            String artist = "";
            for (String item : strings) {
                String u = item.toUpperCase(java.util.Locale.ROOT);
                if (item.length() < 3 || item.length() > 90 || !item.matches(".*[\\p{L}].*")) continue;
                if (u.matches(".*\\b(?:MP3|USB|SD|MUSIC|LOCAL|ALL SONGS|PLAYLIST|ALBUM|ARTIST|REPEAT|SHUFFLE|NEXT|BACK|PLAY|PAUSE|STOP|NOW PLAYING|FOLDER|FAVORITES|SETTINGS)\\b.*")) continue;
                if (item.matches("^[0-9:./ -]+$")) continue;
                if (title.isEmpty()) title = item;
                else if (!item.equalsIgnoreCase(title)) { artist = item; break; }
            }
            android.content.SharedPreferences.Editor e = getSharedPreferences("krupas_mp3_ui", MODE_PRIVATE)
                    .edit().putString("pkg", pkg).putString("nodes", strings.toString())
                    .putBoolean("visible", true);
            if (!title.isEmpty()) {
                e.putString("title", title).putString("artist", artist).putLong("seen", now);
            }
            e.apply();
            return;
        }

        String freq = "";
        for (String item : strings) {
            String detected = SourceRouter.frequency(item);
            if (!detected.isEmpty()) { freq = detected; break; }
        }

        List<String> stationCandidates = new ArrayList<>();
        for (String item : strings) if (SourceRouter.validStation(item)) stationCandidates.add(item);
        String station = "";
        for (String item : stationCandidates) {
            if (item.length() <= 20) { station = item; break; }
        }
        if (station.isEmpty() && !stationCandidates.isEmpty()) station = stationCandidates.get(0);

        String radioText = "";
        for (String item : strings) {
            if (SourceRouter.validRadioText(item, station)) {
                if (!item.equalsIgnoreCase(station)) { radioText = item; break; }
            }
        }

        android.content.SharedPreferences pref = getSharedPreferences("krupas_radio", MODE_PRIVATE);
        String previousFreq = pref.getString("freq", "");
        String previousStation = pref.getString("station", "");
        String previousText = pref.getString("radio_text", "");
        boolean tuned = !freq.isEmpty() && !freq.equals(previousFreq);

        String resolvedStation = !station.isEmpty() ? station : (tuned ? "" : previousStation);
        String resolvedText = !radioText.isEmpty() ? radioText : (tuned ? "" : previousText);

        android.content.SharedPreferences.Editor e = pref.edit()
                .putString("pkg", pkg)
                .putString("nodes", strings.toString())
                .putBoolean("visible", true);
        if (!freq.isEmpty()) e.putString("freq", freq);
        if (!station.isEmpty() || tuned) e.putString("station", resolvedStation);
        if (!radioText.isEmpty() || tuned) e.putString("radio_text", resolvedText);

        if (!freq.isEmpty() || !station.isEmpty() || !radioText.isEmpty() ||
                ((!previousFreq.isEmpty() || !previousStation.isEmpty() || !previousText.isEmpty()) && root != null)) {
            e.putLong("seen", now);
        }
        e.apply();
    }

    private static void addUnique(List<String> out, String text) {
        String s = SourceRouter.clean(text);
        if (!s.isEmpty() && !out.contains(s) && out.size() < 80) out.add(s);
    }

    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        connected = false;
        handler.removeCallbacks(sampler);
        super.onDestroy();
    }
}
