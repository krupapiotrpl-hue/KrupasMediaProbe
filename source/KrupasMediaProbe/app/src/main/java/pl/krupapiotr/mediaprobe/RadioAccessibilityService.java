package pl.krupapiotr.mediaprobe;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Only reads visible OEM radio text. Never performs clicks or remote input. */
public final class RadioAccessibilityService extends AccessibilityService {
    private long lastScan;
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        boolean radio = SourceRouter.isRadioPackage(pkg);
        boolean mp3 = SourceRouter.isAudioPackage(pkg) && !radio;
        if (!radio && !mp3) return;
        final long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastScan < 350) return;
        lastScan = now;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        List<String> strings = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int visited = 0;
        while (!q.isEmpty() && visited++ < 250) {
            AccessibilityNodeInfo n = q.removeFirst();
            if (n.getText() != null) addUnique(strings,n.getText().toString());
            if (n.getContentDescription() != null) addUnique(strings,n.getContentDescription().toString());
            for (int i=0;i<n.getChildCount();i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        if (mp3) {
            String title="";
            for(String item:strings) {
                String u=item.toUpperCase(java.util.Locale.ROOT);
                if(item.length()<5 || item.length()>80 || !item.matches(".*[\\p{L}].*"))continue;
                if(u.matches(".*\\b(?:MP3|USB|SD|MUSIC|LOCAL|ALL SONGS|PLAYLIST|ALBUM|ARTIST|REPEAT|SHUFFLE|NEXT|BACK|PLAY|PAUSE|STOP|NOW PLAYING|FOLDER|FAVORITES|SETTINGS)\\b.*"))continue;
                if(item.matches("^[0-9:./ -]+$"))continue;
                title=item;break;
            }
            getSharedPreferences("krupas_mp3_ui",MODE_PRIVATE).edit()
                    .putString("pkg",pkg).putString("title",title)
                    .putString("nodes",strings.toString())
                    .putLong("seen",System.currentTimeMillis()).apply();
            return;
        }
        String freq = "", station = "";
        for (String s : strings) {
            String f = SourceRouter.frequency(s);
            if (!f.isEmpty()) { freq = f; break; }
        }
        // A station should be supported by a visible frequency: otherwise do not
        // mistake a generic Android button/menu label for an RDS station name.
        if (!freq.isEmpty()) for (String s : strings) {
            if (!SourceRouter.validStation(s)) continue;
            String u = s.toUpperCase(java.util.Locale.ROOT);
            if (u.contains("RADIO") || u.contains("PRZEBOJE") || u.contains("RMF") ||
                u.contains("ESKA") || u.contains("ANTY") || u.contains("ZET") ||
                u.contains("ROCK") || u.contains("TOK") || u.contains("JEDYNKA") ||
                u.contains("TRÓJKA")) {station=s; break;}
        }
        getSharedPreferences("krupas_radio", MODE_PRIVATE).edit()
                .putString("pkg",pkg).putString("freq",freq)
                .putString("station",station).putLong("seen",System.currentTimeMillis())
                .putString("nodes",strings.toString()).apply();
    }
    private static void addUnique(List<String> out,String s) {
        s=SourceRouter.clean(s);
        if (!s.isEmpty() && !out.contains(s) && out.size()<80) out.add(s);
    }
    @Override public void onInterrupt() {}
}
