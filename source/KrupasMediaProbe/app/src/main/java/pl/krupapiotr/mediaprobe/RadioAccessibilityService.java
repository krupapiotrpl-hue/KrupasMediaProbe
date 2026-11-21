package pl.krupapiotr.mediaprobe;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Rect;
import android.util.DisplayMetrics;
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
    private static volatile boolean serviceConnected;
    private static volatile long lastSample;
    private static volatile String sampleError = "";
    static boolean isConnected() { return serviceConnected; }
    static String sampleStatus() {
        if (!serviceConnected) return "USŁUGA NIEPOŁĄCZONA";
        String age = lastSample == 0 ? "OCZEKIWANIE" :
                (android.os.SystemClock.elapsedRealtime() - lastSample) + " ms temu";
        return age + (sampleError.isEmpty() ? "" : " / BŁĄD: " + sampleError);
    }
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
                lastSample = android.os.SystemClock.elapsedRealtime();
                sampleError = "";
            } catch (Exception error) {
                String message = error.getClass().getSimpleName();
                if (!message.equals(sampleError)) AccessHealth.event(
                        RadioAccessibilityService.this, "READ_ERROR " + message);
                sampleError = message;
            }
            handler.postDelayed(this, 2000);
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        serviceConnected = true;
        lastSample = 0;
        sampleError = "";
        AccessHealth.event(this, "ACCESSIBILITY_CONNECTED");
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
        PhoneUiDetector.Result phoneWindowHint=null;
        int inspectedWindows=0;
        int floatingPanels=0;
        StringBuilder seenPackages=new StringBuilder();
        try {
            List<AccessibilityWindowInfo> windows=getWindows();
            if(windows!=null)for(AccessibilityWindowInfo window:windows) {
                if(window==null)continue;
                AccessibilityNodeInfo root=window.getRoot();
                if(root==null || root.getPackageName()==null)continue;
                String pkg=root.getPackageName().toString();
                if(pkg.startsWith("pl.krupapiotr."))continue;
                inspectedWindows++;
                if(seenPackages.length()<160) seenPackages.append(pkg).append(" ");
                // Scan even unrecognized OEM package names. The detector requires
                // strong call-window markers for these unknown packages.
                boolean floatingPanel=looksLikeK706PhonePanel(window,pkg,root);
                if(floatingPanel)floatingPanels++;
                String title=(android.os.Build.VERSION.SDK_INT>=24 && window.getTitle()!=null)
                        ? window.getTitle().toString() : "";
                PhoneUiDetector.Result result=readPhoneWindow(pkg,root,null,title,floatingPanel);
                if(result.phoneWindow && (phoneWindowHint==null ||
                        (phoneWindowHint.callerHint.isEmpty() && !result.callerHint.isEmpty())))
                    phoneWindowHint=result;
                // Do not stop on the first active SystemUI window: the vendor
                // phone window inspected later can contain the actual caller number.
                if(result.active) {
                    if(detected==null || !detected.active ||
                            (PhoneUiDetector.firstNumber(detected.display).isEmpty() &&
                             !PhoneUiDetector.firstNumber(result.display).isEmpty()))
                        detected=result;
                } else if(detected==null || (!detected.active && PhoneUiDetector.isCandidate(pkg))) {
                    detected=result;
                }
            }
        } catch(Exception ex) {
            getSharedPreferences("krupas_phone_ui",MODE_PRIVATE).edit()
                    .putString("status","ODCZYT OKIEN: "+ex.getClass().getSimpleName()).apply();
        }
        if(event!=null && event.getPackageName()!=null) {
            String pkg=event.getPackageName().toString();
            if(!pkg.startsWith("pl.krupapiotr.") && (detected==null||!detected.active)) {
                AccessibilityNodeInfo root=getRootInActiveWindow();
                if(root!=null && root.getPackageName()!=null &&
                        !pkg.equals(root.getPackageName().toString()))root=null;
                PhoneUiDetector.Result result=readPhoneWindow(pkg,root,event,"",false);
                if(result.phoneWindow && (phoneWindowHint==null ||
                        (phoneWindowHint.callerHint.isEmpty() && !result.callerHint.isEmpty())))
                    phoneWindowHint=result;
                if(detected==null || (result.active && (!detected.active ||
                        (PhoneUiDetector.firstNumber(detected.display).isEmpty() &&
                         !PhoneUiDetector.firstNumber(result.display).isEmpty()))))
                    detected=result;
            }
        }
        getSharedPreferences("krupas_phone_ui",MODE_PRIVATE).edit()
                .putString("windows","okna="+inspectedWindows+
                        " / paneleBT="+floatingPanels+
                        " / pakiety="+seenPackages.toString().trim())
                .apply();
        // Sparse BT UI can give caller hints even without end-call labels.
        // Audio policy independently confirms if a real voice call is active.
        android.content.SharedPreferences.Editor phoneHints=
                getSharedPreferences("krupas_phone_ui",MODE_PRIVATE).edit();
        if(phoneWindowHint!=null&&!phoneWindowHint.callerHint.isEmpty())
            phoneHints.putLong("caller_positive_seen",System.currentTimeMillis());
        phoneHints.putBoolean("phone_window",phoneWindowHint!=null)
                .putLong("window_seen",System.currentTimeMillis())
                .putString("caller_hint",phoneWindowHint==null?"":phoneWindowHint.callerHint)
                .apply();
        if(detected!=null) {
            android.content.SharedPreferences.Editor edit=getSharedPreferences("krupas_phone_ui",MODE_PRIVATE)
                    .edit().putBoolean("active",detected.active)
                    .putString("pkg",detected.pkg)
                    .putString("status",detected.diagnostic)
                    .putString("phase",detected.phase)
                    .putLong("seen",System.currentTimeMillis());
            if(detected.active){
                edit.putString("display",detected.display);
                edit.putLong("active_positive_seen",System.currentTimeMillis());
            }else edit.remove("display");
            if(detected.ended)edit.putLong("ended_seen",System.currentTimeMillis());
            edit.apply();
        } else {
            getSharedPreferences("krupas_phone_ui",MODE_PRIVATE).edit()
                    .putBoolean("active",false).remove("display").remove("phase")
                    .putString("status","BRAK OKNA ROZMOWY / okna="+inspectedWindows+
                            " / pakiety="+seenPackages.toString().trim())
                    .putLong("seen",System.currentTimeMillis()).apply();
        }
        // Phone status is independent from media-session status.
    }

    // Geometry is checked against the K706 floating three-control call panel,
    // not the full-screen SystemUI or its narrow status bar.
    private boolean looksLikeK706PhonePanel(AccessibilityWindowInfo window,
            String pkg,AccessibilityNodeInfo root) {
        if(window==null || root==null || !"com.android.systemui".equalsIgnoreCase(pkg))return false;
        Rect b=new Rect();
        window.getBoundsInScreen(b);
        DisplayMetrics dm=getResources().getDisplayMetrics();
        int w=dm.widthPixels,h=dm.heightPixels;
        if(w<300 || h<200 || b.isEmpty())return false;
        if(b.width()<w/4 || b.width()>w*85/100 ||
                b.height()<h/9 || b.height()>h*65/100 ||
                b.top<h/18 || b.bottom>h*90/100)return false;
        int controls=0,visited=0;
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
        queue.add(root);
        while(!queue.isEmpty() && visited++<160) {
            AccessibilityNodeInfo node=queue.removeFirst();
            if(node.isClickable() && node.isEnabled() && node.isVisibleToUser())controls++;
            for(int i=0;i<node.getChildCount();i++) {
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)queue.add(child);
            }
        }
        // Three buttons match the K706 dialpad, hangup and speaker controls.
        return controls>=3;
    }

    private PhoneUiDetector.Result readPhoneWindow(String pkg,
            AccessibilityNodeInfo root,AccessibilityEvent event,
            String title,boolean floatingPanel) {
        List<String> labels=new ArrayList<>();
        if(title!=null && !title.isEmpty())addUnique(labels,title);
        if(event!=null) {
            if(event.getText()!=null)for(CharSequence s:event.getText())
                if(s!=null)addUnique(labels,s.toString());
            if(event.getContentDescription()!=null)
                addUnique(labels,event.getContentDescription().toString());
        }
        ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();
        // The event's source is often the phone popup itself while the active
        // window root points at the underlying media app.
        if(event!=null) {
            AccessibilityNodeInfo source=event.getSource();
            if(source!=null) {
                nodes.add(source);
                AccessibilityNodeInfo parent=source.getParent();
                if(parent!=null)nodes.add(parent);
            }
        }
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
        return PhoneUiDetector.detect(pkg,labels,floatingPanel);
    }

    private static void addUnique(List<String> out, String text) {
        String s = SourceRouter.clean(text);
        if (!s.isEmpty() && !out.contains(s) && out.size() < 80) out.add(s);
    }

    @Override public void onInterrupt() {
        AccessHealth.event(this, "ACCESSIBILITY_INTERRUPTED");
    }
    @Override public boolean onUnbind(android.content.Intent intent) {
        connected = false;
        serviceConnected = false;
        handler.removeCallbacks(sampler);
        AccessHealth.event(this, "ACCESSIBILITY_UNBOUND");
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        connected = false;
        serviceConnected = false;
        handler.removeCallbacks(sampler);
        AccessHealth.event(this, "ACCESSIBILITY_DESTROYED");
        super.onDestroy();
    }
}

