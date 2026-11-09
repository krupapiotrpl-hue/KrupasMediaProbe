package pl.krupapiotr.mediaprobe;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Passive OEM FM/RDS and MP3 reader for K706.
 * Refreshes the visible radio window even when Android does not send text events.
 * No buttons are clicked and no text is injected into the radio application.
 */
public final class RadioAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastScan;
    private boolean connected;
    private long lastStartRequest;
    private long lastPhoneScan;
    private final Runnable sampler = new Runnable() {
        @Override public void run() {
            if (!connected) return;
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null && root.getPackageName() != null) {
                    String pkg = root.getPackageName().toString();
                    if (SourceRouter.isRadioPackage(pkg) || SourceRouter.isAudioPackage(pkg) || SourceRouter.isDabPackage(pkg)) {
                        capture(pkg, null, root);
                    }
                }
                scanPhoneWindows(null);
            } catch (Exception ignored) {}
            handler.postDelayed(this, 2000);
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        BootReceiver.startBridge(this, "ACCESSIBILITY_CONNECTED");
        lastStartRequest = android.os.SystemClock.elapsedRealtime();
        handler.removeCallbacks(sampler);
        handler.postDelayed(sampler, 2000);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        // On some K706 firmware standby does not broadcast BOOT_COMPLETED.
        // A newly visible music window can request the already-idempotent service.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                android.os.SystemClock.elapsedRealtime() - lastStartRequest > 60000 &&
                (SourceRouter.isRadioPackage(pkg) || SourceRouter.isAudioPackage(pkg) || SourceRouter.isDabPackage(pkg))) {
            lastStartRequest = android.os.SystemClock.elapsedRealtime();
            BootReceiver.startBridge(this, "MEDIA_WINDOW");
        }
        boolean radio = SourceRouter.isRadioPackage(pkg);
        boolean dab = SourceRouter.isDabPackage(pkg);
        boolean mp3 = SourceRouter.isAudioPackage(pkg) && !radio && !dab;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            getSharedPreferences("krupas_radio", MODE_PRIVATE).edit()
                    .putBoolean("visible", radio).apply();
            getSharedPreferences("krupas_mp3_ui", MODE_PRIVATE).edit()
                    .putBoolean("visible", mp3).apply();
            getSharedPreferences("krupas_dab_ui", MODE_PRIVATE).edit()
                    .putBoolean("visible", dab).apply();
        }
        // Passive Bluetooth phone window monitor; media parsing stays unchanged.
        scanPhoneWindows(event);
        if (!radio && !mp3 && !dab) return;

        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastScan < 250 &&
                event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        lastScan = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        // Do not attach labels from another application to an OEM FM event.
        if (root != null && root.getPackageName() != null &&
                !pkg.equals(root.getPackageName().toString())) root = null;
        capture(pkg, event, root);
    }

    private void capture(String pkg, AccessibilityEvent event, AccessibilityNodeInfo root) {
        boolean radio = SourceRouter.isRadioPackage(pkg);
        boolean dab = SourceRouter.isDabPackage(pkg);
        boolean mp3 = SourceRouter.isAudioPackage(pkg) && !radio && !dab;
        if (!radio && !mp3 && !dab) return;

        List<String> strings = new ArrayList<>();
        List<String> screenLabels = new ArrayList<>();
        List<String> eventLabels = new ArrayList<>();
        if (event != null) {
            if (event.getText() != null)
                for (CharSequence text : event.getText())
                    if (text != null) { addUnique(strings, text.toString()); addUnique(eventLabels, text.toString()); }
            if (event.getContentDescription() != null)
                { addUnique(strings, event.getContentDescription().toString()); addUnique(eventLabels, event.getContentDescription().toString()); }
        }
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        if (root != null) queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 250) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node.getText() != null) { addUnique(strings, node.getText().toString()); addUnique(screenLabels, node.getText().toString()); }
            if (node.getContentDescription() != null) { addUnique(strings, node.getContentDescription().toString()); addUnique(screenLabels, node.getContentDescription().toString()); }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        long now = System.currentTimeMillis();
        if (dab) {
            String candidate = "";
            // Best-effort on-screen DLS/station fallback, never a stale background UI.
            for (String item : strings) {
                String u=item.toUpperCase(java.util.Locale.ROOT);
                if (item.length()<3 || item.length()>90 || !item.matches(".*[\\p{L}].*")) continue;
                if (u.equals("DAB-Z") || u.equals("DAB+") || u.equals("DAB") ||
                        u.equals("USB") || u.equals("RADIO") || u.equals("SETTINGS") ||
                        u.equals("SCAN") || u.equals("SEARCH") || u.equals("PLAY") ||
                        u.equals("STOP") || u.equals("FAVORITES")) continue;
                candidate=item;
                break;
            }
            android.content.SharedPreferences.Editor e=getSharedPreferences("krupas_dab_ui",MODE_PRIVATE)
                    .edit().putString("pkg",pkg).putString("nodes",strings.toString())
                    .putBoolean("visible",true);
            if (!candidate.isEmpty())e.putString("title",candidate).putLong("seen",now);
            e.apply();
            return;
        }
        if (mp3) {
            String title = "";
            for (String item : strings) {
                String u = item.toUpperCase(java.util.Locale.ROOT);
                if (item.length() < 5 || item.length() > 90 || !item.matches(".*[\\p{L}].*")) continue;
                if (u.matches(".*\\b(?:MP3|USB|SD|MUSIC|LOCAL|ALL SONGS|PLAYLIST|ALBUM|ARTIST|REPEAT|SHUFFLE|NEXT|BACK|PLAY|PAUSE|STOP|NOW PLAYING|FOLDER|FAVORITES|SETTINGS)\\b.*")) continue;
                if (item.matches("^[0-9:./ -]+$")) continue;
                title = item;
                break;
            }
            android.content.SharedPreferences.Editor e = getSharedPreferences("krupas_mp3_ui", MODE_PRIVATE)
                    .edit().putString("pkg", pkg).putString("nodes", strings.toString())
                    .putBoolean("visible", true);
            if (!title.isEmpty()) e.putString("title", title).putLong("seen", now);
            e.apply();
            return;
        }

        String freq = "";
        for (String item : strings) {
            String detected = SourceRouter.frequency(item);
            if (!detected.isEmpty()) { freq = detected; break; }
        }

        android.content.SharedPreferences pref = getSharedPreferences("krupas_radio", MODE_PRIVATE);
        String previousFreq = pref.getString("freq", "");
        String previousStation = pref.getString("station", "");
        boolean tuned = !freq.isEmpty() && !freq.equals(previousFreq);

        // The station name and the changing RDS line are separate fields.
        // The visible radio view is preferred over event-only labels: events
        // often begin with the currently changing RDS text.
        List<String> labels = new ArrayList<>();
        for (String item : screenLabels)
            if (SourceRouter.validStation(item) && !item.equalsIgnoreCase("NavRadio+")
                    && !item.equalsIgnoreCase("NavRadio")) addUnique(labels, item);
        String station = "";
        if (!tuned && !previousStation.isEmpty()) {
            for (String item : labels)
                if (item.equalsIgnoreCase(previousStation)) { station = item; break; }
        }
        if (station.isEmpty() && !labels.isEmpty()) station = labels.get(0);
        String rds = "";
        for (String item : labels) {
            if (!item.equalsIgnoreCase(station)) { rds = item; break; }
        }
        // Some radio builds expose the second line only in accessibility events.
        if (rds.isEmpty() && !station.isEmpty()) {
            for (String item : eventLabels) {
                if (SourceRouter.validStation(item) && !item.equalsIgnoreCase(station)) {
                    rds = item; break;
                }
            }
        }
        String resolvedStation = !station.isEmpty() ? station : (tuned ? "" : previousStation);

        android.content.SharedPreferences.Editor e = pref.edit()
                .putString("pkg", pkg)
                .putString("nodes", strings.toString())
                .putBoolean("visible", true)
                .putString("station", resolvedStation)
                .putString("rds", rds)
                .putLong("rds_seen", rds.isEmpty() ? 0 : now);
        if (!freq.isEmpty()) e.putString("freq", freq);
        // Do not keep radiotext from the station tuned previously.
        if (tuned && rds.isEmpty()) e.remove("rds");
        if (!freq.isEmpty() || !station.isEmpty() ||
                (!previousFreq.isEmpty() || !previousStation.isEmpty()) && root != null) {
            e.putLong("seen", now);
        }
        e.apply();
    }

    // A separate, read-only phone UI path for K706 builds that hide HFP broadcasts.
    private void scanPhoneWindows(AccessibilityEvent event) {
        long uptime=android.os.SystemClock.elapsedRealtime();
        if(event!=null && uptime-lastPhoneScan<600 &&
                event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
        lastPhoneScan=uptime;
        PhoneUiDetector.Result detected=null;
        try {
            List<AccessibilityWindowInfo> windows=getWindows();
            if(windows!=null)for(AccessibilityWindowInfo window:windows) {
                if(window==null)continue;
                AccessibilityNodeInfo root=window.getRoot();
                if(root==null || root.getPackageName()==null)continue;
                String pkg=root.getPackageName().toString();
                if(!PhoneUiDetector.isCandidate(pkg))continue;
                PhoneUiDetector.Result result=readPhoneWindow(pkg,root,null);
                if(result.active){detected=result;break;}
                if(detected==null)detected=result;
            }
        } catch(Exception ex) {
            getSharedPreferences("krupas_phone_ui",MODE_PRIVATE).edit()
                    .putString("status","ODCZYT OKIEN: "+ex.getClass().getSimpleName()).apply();
        }
        if(event!=null && event.getPackageName()!=null) {
            String pkg=event.getPackageName().toString();
            if(PhoneUiDetector.isCandidate(pkg) && (detected==null||!detected.active)) {
                AccessibilityNodeInfo root=getRootInActiveWindow();
                if(root!=null && root.getPackageName()!=null &&
                        !pkg.equals(root.getPackageName().toString()))root=null;
                PhoneUiDetector.Result result=readPhoneWindow(pkg,root,event);
                if(result.active||detected==null)detected=result;
            }
        }
        if(detected!=null) {
            android.content.SharedPreferences.Editor edit=getSharedPreferences("krupas_phone_ui",MODE_PRIVATE)
                    .edit().putBoolean("active",detected.active)
                    .putString("pkg",detected.pkg)
                    .putString("status",detected.diagnostic)
                    .putLong("seen",System.currentTimeMillis());
            if(detected.active)edit.putString("display",detected.display);
            else edit.remove("display");
            edit.apply();
        }
        // No visible phone window: the bridge expires the observation after 8 seconds.
    }

    private PhoneUiDetector.Result readPhoneWindow(String pkg,
            AccessibilityNodeInfo root,AccessibilityEvent event) {
        List<String> labels=new ArrayList<>();
        if(event!=null) {
            if(event.getText()!=null)for(CharSequence s:event.getText())
                if(s!=null)addUnique(labels,s.toString());
            if(event.getContentDescription()!=null)
                addUnique(labels,event.getContentDescription().toString());
        }
        ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();
        if(root!=null)nodes.add(root);
        int inspected=0;
        while(!nodes.isEmpty() && inspected++<280) {
            AccessibilityNodeInfo n=nodes.removeFirst();
            if(n.getText()!=null)addUnique(labels,n.getText().toString());
            if(n.getContentDescription()!=null)
                addUnique(labels,n.getContentDescription().toString());
            if(n.getViewIdResourceName()!=null)addUnique(labels,n.getViewIdResourceName());
            for(int i=0;i<n.getChildCount();i++) {
                AccessibilityNodeInfo c=n.getChild(i);
                if(c!=null)nodes.add(c);
            }
        }
        return PhoneUiDetector.detect(pkg,labels);
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
