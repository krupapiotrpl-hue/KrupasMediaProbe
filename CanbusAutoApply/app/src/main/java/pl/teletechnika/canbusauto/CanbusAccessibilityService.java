package pl.teletechnika.canbusauto;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.Locale;

public class CanbusAccessibilityService extends AccessibilityService {
    public static CanbusAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private BroadcastReceiver screenReceiver;
    private String lastScreenClass = "";
    private String lastScreenPackage = "";
    private boolean running = false;
    private int menuClicks = 0;
    private long started = 0;
    private long lastAction = 0;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_ON.equals(intent.getAction()) && prefs.getBoolean("auto", false)) {
                    handler.postDelayed(() -> begin("Wybudzenie"), 5000);
                }
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, f);
        }
        log("Usługa uruchomiona. Czekam na naukę lub wybudzenie.");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (prefs == null || event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (getPackageName().equals(pkg)) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastScreenClass = event.getClassName() == null ? "" : event.getClassName().toString();
            lastScreenPackage = pkg;
        }
        if (prefs.getBoolean("learning", false) &&
                event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            String clicked = getEventLabel(event).toLowerCase(Locale.ROOT);
            if (isSaveLabel(clicked)) {
                AccessibilityNodeInfo source = event.getSource();
                String id = source != null && source.getViewIdResourceName() != null
                    ? source.getViewIdResourceName() : "";
                String screenClass = pkg.equals(lastScreenPackage) ? lastScreenClass : "";
                prefs.edit().putString("pkg", pkg).putString("activity", screenClass)
                    .putString("saveId", id).putString("last", "Zapamiętano: " + pkg + ", przycisk: " + clicked)
                    .putBoolean("learning", false).apply();
                Toast.makeText(this, "CANBUS: zapamiętano Zapisz", Toast.LENGTH_LONG).show();
            }
        }
        if (running && pkg.equals(prefs.getString("pkg", ""))) {
            handler.removeCallbacks(scanTask);
            handler.postDelayed(scanTask, 250);
        }
    }

    private String getEventLabel(AccessibilityEvent e) {
        StringBuilder b = new StringBuilder();
        if (e.getText() != null) for (CharSequence s : e.getText()) if (s != null) b.append(s).append(" ");
        AccessibilityNodeInfo n = e.getSource();
        if (n != null) {
            if (n.getText() != null) b.append(n.getText()).append(" ");
            if (n.getContentDescription() != null) b.append(n.getContentDescription());
        }
        return b.toString().trim();
    }

    private boolean isSaveLabel(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).trim();
        return s.equals("zapisz") || s.equals("save") || s.equals("apply")
            || s.equals("zatwierdź") || s.equals("zatwierdz")
            || s.equals("zastosuj") || s.equals("confirm") || s.equals("保存")
            || s.contains("zapisz") || s.contains("save settings");
    }

    public void testNow() { handler.post(() -> begin("Test ręczny")); }

    private void begin(String reason) {
        if (prefs == null) return;
        String pkg = prefs.getString("pkg", "");
        if (pkg.isEmpty()) { log("Brak nauczonego przycisku."); return; }
        if (running && SystemClock.uptimeMillis() - started < 30000) return;
        running = true;
        menuClicks = 0;
        started = SystemClock.uptimeMillis();
        lastAction = 0;
        log(reason + ": próba otwarcia " + pkg);
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            String clz = prefs.getString("activity", "");
            if (clz.startsWith(pkg + ".")) {
                launch = new Intent();
                launch.setClassName(pkg, clz);
            }
        }
        if (launch != null) {
            try {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
            } catch (Exception e) {
                log("Android zablokował otwarcie aplikacji: " + e.getClass().getSimpleName());
            }
        } else {
            log("Brak dostępnego ekranu startowego. Sprawdzam, czy CANBUS jest już widoczny.");
        }
        handler.removeCallbacks(scanTask);
        handler.postDelayed(scanTask, 1600);
    }

    private final Runnable scanTask = new Runnable() {
        @Override public void run() { scan(); }
    };

    private void scan() {
        if (!running) return;
        if (SystemClock.uptimeMillis() - started > 25000) {
            running = false;
            log("Koniec testu: nie znaleziono dostępnego przycisku Zapisz.");
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String targetPkg = prefs.getString("pkg", "");
        if (root != null && root.getPackageName() != null &&
            targetPkg.equals(root.getPackageName().toString())) {
            AccessibilityNodeInfo save = findSave(root, prefs.getString("saveId", ""), 0);
            if (save != null) {
                if (clickNode(save)) {
                    running = false;
                    log("Naciśnięto Zapisz w " + targetPkg + " (CANBUS wymaga sprawdzenia).");
                    return;
                }
            }
            if (menuClicks < 3 && SystemClock.uptimeMillis() - lastAction > 1600) {
                AccessibilityNodeInfo menu = findMenu(root, 0);
                if (menu != null && clickNode(menu)) {
                    menuClicks++;
                    lastAction = SystemClock.uptimeMillis();
                    log("Otwarto pozycję CANBUS (" + menuClicks + "/3).");
                }
            }
        }
        handler.postDelayed(scanTask, 850);
    }

    private AccessibilityNodeInfo findSave(AccessibilityNodeInfo node, String savedId, int depth) {
        if (node == null || depth > 24) return null;
        String label = label(node).toLowerCase(Locale.ROOT);
        String id = node.getViewIdResourceName();
        boolean matched = isSaveLabel(label) ||
            (!TextUtils.isEmpty(savedId) && savedId.equals(id));
        if (matched && node.isVisibleToUser()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findSave(node.getChild(i), savedId, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findMenu(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 24) return null;
        String s = label(node).toLowerCase(Locale.ROOT).trim();
        if (node.isVisibleToUser() && (s.equals("canbus") || s.equals("can bus")
            || s.equals("can-bus") || s.equals("can settings")
            || s.equals("ustawienia canbus") || s.equals("ustawienia can"))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findMenu(node.getChild(i), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private String label(AccessibilityNodeInfo n) {
        String t = n.getText() == null ? "" : n.getText().toString();
        String d = n.getContentDescription() == null ? "" : n.getContentDescription().toString();
        return (t + " " + d).trim();
    }

    private boolean clickNode(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo current = n;
        for (int i = 0; i < 6 && current != null; i++) {
            if (current.isClickable() && current.isEnabled()) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            current = current.getParent();
        }
        return false;
    }

    private void log(String msg) {
        if (prefs != null) prefs.edit().putString("last", msg).apply();
    }

    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (screenReceiver != null) try { unregisterReceiver(screenReceiver); } catch (Exception ignored) { }
        instance = null;
        super.onDestroy();
    }
}
