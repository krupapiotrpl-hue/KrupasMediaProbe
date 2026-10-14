package pl.krupapiotr.mediaprobe;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Typeface;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView status;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            h.postDelayed(this, 700);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setPadding(18, 10, 18, 10);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView heading = new TextView(this);
        heading.setText("KRUPAS MEDIA PRO 3.0 DIAG-2 • K706");
        heading.setTextSize(20);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(heading);

        TextView hint = new TextView(this);
        hint.setText("RADIO-ONLY: bez przewijania, rotacji i MP3. Cel: pewny odczyt FM/RDS i pewna wysyłka do ESP.");
        hint.setTextSize(13);
        root.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addButton(row, "Bluetooth", this::requestBle);
        addButton(row, "Dostępność FM", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addButton(row, "Wznów BLE", () -> startBridge(MediaBridgeService.ACTION_RESTART_BLE));
        addButton(row, "Test FM", () -> startBridge(MediaBridgeService.ACTION_TEST_FM));
        root.addView(row);

        ScrollView sv = new ScrollView(this);
        status = new TextView(this);
        status.setTextSize(13);
        status.setTypeface(Typeface.MONOSPACE);
        status.setTextIsSelectable(true);
        sv.addView(status);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        requestBle();
        startBridge(null);
        h.post(tick);
    }

    private void addButton(LinearLayout layout, String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        layout.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void startBridge(String action) {
        try {
            Intent i = new Intent(this, MediaBridgeService.class);
            if (action != null) i.setAction(action);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Exception ex) {
            getSharedPreferences("krupas_diag", 0).edit().putString("error", "start: " + ex).apply();
        }
    }

    private void requestBle() {
        if (Build.VERSION.SDK_INT >= 31) {
            String[] p = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
            if (checkSelfPermission(p[0]) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(p[1]) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(p, 8);
                return;
            }
        } else if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 8);
            return;
        }
        try {
            BluetoothManager mgr=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter adapter=mgr==null?null:mgr.getAdapter();
            if(adapter!=null && !adapter.isEnabled()) {
                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            } else {
                startBridge(MediaBridgeService.ACTION_RESTART_BLE);
            }
        } catch (Exception ex) {
            getSharedPreferences("krupas_diag",0).edit().putString("error","BT: "+ex).apply();
        }
    }

    @Override public void onRequestPermissionsResult(int rc, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(rc, perms, grants);
        requestBle();
    }

    private static String time(long t) {
        return t <= 0 ? "—" : new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(t));
    }

    private void refresh() {
        if (status == null) return;

        android.content.SharedPreferences d = getSharedPreferences("krupas_diag", 0);
        android.content.SharedPreferences r = getSharedPreferences("krupas_radio", 0);
        String accessibility = Settings.Secure.getString(getContentResolver(), "enabled_accessibility_services");

        status.setText(
                "1. RADIO ODEBRAŁO\n" +
                d.getString("rx_raw", "brak danych") + "\n" +
                "pakiet: " + d.getString("rx_pkg", "—") + "\n" +
                "czas: " + time(d.getLong("rx_time", 0)) + "\n\n" +

                "2. APLIKACJA ROZPOZNAŁA\n" +
                "FM: " + r.getString("freq", "—") + "\n" +
                "RDS: " + r.getString("station", "—") + "\n\n" +

                "3. WYSŁANO DO ESP\n" +
                d.getString("last_frame", "—") + "\n" +
                "powód: " + d.getString("last_frame_reason", "—") + "\n" +
                "czas: " + time(d.getLong("last_frame_time", 0)) + "\n" +
                "próba GATT: " + d.getString("tx_attempt", "—") + "\n" +
                "stan TX: " + d.getString("tx_state", "—") + "\n" +
                "GATT potwierdził: " + d.getString("last_tx", "—") + "\n" +
                "czas GATT: " + time(d.getLong("last_tx_time", 0)) + "\n\n" +

                "4. STAN ŁĄCZA\n" +
                "BLE: " + d.getString("ble", "BRAK") + "\n" +
                "Dostępność FM: " +
                (accessibility != null && accessibility.contains(getPackageName() + "/" + RadioAccessibilityService.class.getName())
                        ? "OK" : "SPRAWDŹ") + "\n" +
                "Tryb: " + d.getString("route", "RADIO ONLY") + "\n\n" +

                "BŁĄD: " + d.getString("error", "—")
        );
    }

    @Override protected void onDestroy() {
        h.removeCallbacks(tick);
        super.onDestroy();
    }
}
