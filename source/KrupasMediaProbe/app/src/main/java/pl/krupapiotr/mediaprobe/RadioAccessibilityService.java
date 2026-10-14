package pl.krupapiotr.mediaprobe;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Only reads visible OEM radio/player text. Never performs clicks or remote input. */
public final class RadioAccessibilityService extends AccessibilityService {
    private long lastScan;
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        boolean radio = SourceRouter.isRadioPackage(pkg);
        boolean mp3 = SourceRouter.isAudioPackage(pkg) && !radio;
        if (!radio && !mp3) return;
        final long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastScan < 250) return;
        lastScan = now;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        List<String> strings = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int visited = 0;
        while (!q.isEmpty() && visited++ < 300) {
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
                String u=item.toUpperCase(Locale.ROOT);
                if(item.length()<3 || item.length()>80 || !item.matches(".*[\\p{L}].*"))continue;
                if(SourceRouter.isPlaceholder(item))continue;
                if(u.matches(".*\\b(?:MP3|USB|SD|MUSIC|LOCAL|ALL SONGS|PLAYLIST|ALBUM|ARTIST|REPEAT|SHUFFLE|NEXT|BACK|PLAY|PAUSE|STOP|NOW PLAYING|FOLDER|FAVORITES|SETTINGS)\\b.*"))continue;
                if(item.matches("^[0-9:./ -]+$"))continue;
                title=item;break;
            }
            SharedPreferences p=getSharedPreferences("krupas_mp3_ui",MODE_PRIVATE);
            String old=p.getString("title","");
            p.edit().putString("pkg",pkg).putString("title",title).putString("nodes",strings.toString())
                    .putLong("seen",System.currentTimeMillis()).apply();
            if(!title.equals(old))MediaBridgeService.poke(this,"MP3_UI_CHANGE");
            return;
        }
        String freq = "", station = "";
        for (String s : strings) {
            String f = SourceRouter.frequency(s);
            if (!f.isEmpty()) { freq = f; break; }
        }
        if (!freq.isEmpty()) {
            for (String s : strings) {
                if (!SourceRouter.validStation(s)) continue;
                String u = s.toUpperCase(Locale.ROOT);
                if (u.contains("PRZEBOJE") || u.contains("RMF") || u.contains("ESKA") ||
                    u.contains("ANTY") || u.contains("ZET") || u.contains("ROCK") ||
                    u.contains("TOK") || u.contains("JEDYNKA") || u.contains("TRÓJKA") ||
                    u.contains("VOX") || u.contains("MARYJA")) {station=s; break;}
            }
            if(station.isEmpty()){
                for(String s:strings){if(SourceRouter.validStation(s)){station=s;break;}}
            }
        }
        SharedPreferences p=getSharedPreferences("krupas_radio", MODE_PRIVATE);
        String oldFreq=p.getString("freq","");
        String oldStation=p.getString("station","");
        p.edit().putString("pkg",pkg).putString("freq",freq).putString("station",station)
                .putLong("seen",System.currentTimeMillis()).putString("nodes",strings.toString()).apply();
        if(!freq.equals(oldFreq)||!station.equals(oldStation))MediaBridgeService.poke(this,"RADIO_UI_CHANGE");
    }
    private static void addUnique(List<String> out,String s) {
        s=SourceRouter.clean(s);
        if (!s.isEmpty() && !out.contains(s) && out.size()<100) out.add(s);
    }
    @Override public void onInterrupt() {}
}
