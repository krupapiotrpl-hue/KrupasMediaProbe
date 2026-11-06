package pl.teletechnika.canbusgesture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

public class GestureService extends AccessibilityService {
    public static GestureService instance;
    private SharedPreferences prefs;
    private WindowManager wm;
    private final Handler h = new Handler(Looper.getMainLooper());
    private BroadcastReceiver receiver;
    private View controls;
    private View capture;
    private JSONArray points = new JSONArray();
    private boolean recording;
    private boolean replaying;
    private long startedAt;
    private long lastAutoAt;
    private int replayIndex;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (Intent.ACTION_SCREEN_ON.equals(i.getAction()) &&
                    prefs.getBoolean("auto", false)) {
                    h.postDelayed(() -> runScript(false), 8000);
                }
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
        report("Usługa aktywna; automatyka " +
            (prefs.getBoolean("auto", false) ? "włączona" : "wyłączona") + ".");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (prefs == null || e == null || e.getPackageName() == null) return;
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String pkg = e.getPackageName().toString();
            if (!pkg.equals(getPackageName())) prefs.edit().putString("screen", pkg).apply();
        }
    }

    public void startRecording() {
        if (replaying) { report("Test już trwa, nie można nagrywać."); return; }
        recording = true;
        points = new JSONArray();
        prefs.edit().putBoolean("auto", false).putString("steps", "[]").apply();
        removeCapture();
        removeControls();
        showControls();
        report("Nagrywanie: dodawaj punkty od pulpitu do przycisku Zapisz.");
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private WindowManager.LayoutParams overlayParams(int w, int hgt, int gravity) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            w, hgt, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        p.gravity = gravity;
        return p;
    }
    private void showControls() {
        if (controls != null || !recording) return;
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(6, 5, 6, 5);
        bar.setBackgroundColor(Color.rgb(28, 45, 67));
        Button captureButton = new Button(this);
        captureButton.setText("DODAJ PUNKT");
        Button done = new Button(this);
        done.setText("GOTOWE");
        bar.addView(captureButton);
        bar.addView(done);
        captureButton.setOnClickListener(v -> beginCapture());
        done.setOnClickListener(v -> finishRecording());
        controls = bar;
        WindowManager.LayoutParams p = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        p.y = 65;
        try { wm.addView(controls, p); }
        catch (Exception e) { controls = null; report("Nie udało się wyświetlić paska: " + e.getMessage()); }
    }

    private void beginCapture() {
        if (!recording || capture != null) return;
        if (points.length() >= 12) {
            toast("Limit 12 punktów. Naciśnij GOTOWE."); return;
        }
        LinearLayout layer = new LinearLayout(this);
        layer.setOrientation(LinearLayout.VERTICAL);
        layer.setBackgroundColor(0x24000000);
        TextView hint = new TextView(this);
        hint.setText("PUNKT " + (points.length() + 1) +
            ": dotknij na ekranie elementu, który chcesz nacisnąć.\nAplikacja powtórzy to dotknięcie.");
        hint.setTextSize(18);
        hint.setTextColor(Color.WHITE);
        hint.setBackgroundColor(0xE5152B42);
        hint.setPadding(24, 16, 24, 16);
        layer.addView(hint);
        layer.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float x = event.getRawX();
                float y = event.getRawY();
                rememberAndTap(x, y);
                return true;
            }
            return true;
        });
        capture = layer;
        try {
            wm.addView(capture, overlayParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        } catch (Exception e) {
            capture = null;
            report("Nie udało się przechwycić punktu: " + e.getMessage());
        }
    }

    private Point size() {
        Point result = new Point();
        wm.getDefaultDisplay().getRealSize(result);
        return result;
    }

    private void rememberAndTap(float x, float y) {
        removeCapture();
        Point display = size();
        if (display.x <= 0 || display.y <= 0) { report("Brak wymiarów ekranu."); return; }
        JSONObject pt = new JSONObject();
        try {
            pt.put("x", x / display.x);
            pt.put("y", y / display.y);
            points.put(pt);
            prefs.edit().putString("steps", points.toString()).apply();
        } catch (Exception e) { report("Błąd zapisu punktu: " + e.getMessage()); return; }
        int n = points.length();
        report("Punkt " + n + " zapisany. Ekran: " + prefs.getString("screen", "?"));
        h.postDelayed(() -> touch(x, y, () -> { }), 230);
    }

    public void finishRecording() {
        recording = false;
        removeCapture();
        removeControls();
        report("Nagrywanie zakończone, liczba punktów: " + points.length() +
            ". Wykonaj TEST na postoju.");
    }

    public void stopEverything() {
        recording = false; replaying = false;
        h.removeCallbacksAndMessages(null);
        removeCapture();
        removeControls();
        report("Zatrzymano sterowanie ekranem.");
    }

    public void runScript(boolean manual) {
        if (prefs == null) return;
        if (recording) { report("Zakończ nagrywanie przed testem."); return; }
        if (replaying) return;
        if (!manual && !prefs.getBoolean("auto", false)) return;
        if (!manual && SystemClock.elapsedRealtime() - lastAutoAt < 45000) return;
        try { points = new JSONArray(prefs.getString("steps", "[]")); }
        catch (Exception e) { points = new JSONArray(); }
        if (points.length() == 0) { report("Brak punktów. Nagraj trasę do Zapisz."); return; }
        if (!manual) lastAutoAt = SystemClock.elapsedRealtime();
        replayIndex = 0;
        replaying = true;
        startedAt = SystemClock.elapsedRealtime();
        report((manual ? "TEST" : "AUTO") + ": odtwarzanie " + points.length() + " punktów.");
        removeControls();
        removeCapture();
        boolean home = performGlobalAction(GLOBAL_ACTION_HOME);
        if (!home) { replaying = false; report("Nie udało się przejść na pulpit Androida."); return; }
        h.postDelayed(() -> playNext(), 1600);
    }

    private void playNext() {
        if (!replaying) return;
        if (SystemClock.elapsedRealtime() - startedAt > 70000) {
            replaying = false;
            report("Upłynął limit czasu odtwarzania.");
            return;
        }
        if (replayIndex >= points.length()) {
            replaying = false;
            report("Dotknięcia wykonane: " + replayIndex +
                ". Sprawdź Multimedia i temperaturę płynu – brak odczytu stanu CANBUS.");
            return;
        }
        int index = replayIndex++;
        try {
            JSONObject pt = points.getJSONObject(index);
            float xf = (float) pt.getDouble("x");
            float yf = (float) pt.getDouble("y");
            if (xf < 0 || xf > 1 || yf < 0 || yf > 1) throw new IllegalArgumentException("Złe współrzędne");
            Point p = size();
            float x = p.x * xf, y = p.y * yf;
            report("Wykonuję dotknięcie " + (index + 1) + "/" + points.length());
            touch(x, y, () -> h.postDelayed(() -> playNext(), 2100));
        } catch (Exception e) {
            replaying = false;
            report("Błąd kroku " + (index + 1) + ": " + e.getMessage());
        }
    }

    private void touch(float x, float y, Runnable completed) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 100);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        boolean issued;
        try {
            issued = dispatchGesture(gesture, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription g) { completed.run(); }
                @Override public void onCancelled(GestureDescription g) {
                    if (replaying) {
                        replaying = false;
                        report("Android anulował dotknięcie – przerwano test.");
                    } else {
                        report("Android anulował dotknięcie testowe.");
                    }
                }
            }, h);
        } catch (Exception e) {
            report("Błąd wysyłania dotknięcia: " + e.getMessage());
            issued = false;
        }
        if (!issued && replaying) {
            replaying = false;
            report("Android nie zezwolił na gest dotknięcia.");
        }
    }

    private void removeControls() {
        if (controls != null) {
            try { wm.removeView(controls); } catch (Exception ignored) { }
            controls = null;
        }
    }
    private void removeCapture() {
        if (capture != null) {
            try { wm.removeView(capture); } catch (Exception ignored) { }
            capture = null;
        }
    }
    private void report(String msg) {
        if (prefs != null) prefs.edit().putString("last", msg).apply();
    }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); }
    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        recording = false; replaying = false;
        h.removeCallbacksAndMessages(null);
        if (wm != null) { removeCapture(); removeControls(); }
        if (receiver != null) try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        instance = null;
        super.onDestroy();
    }
}
