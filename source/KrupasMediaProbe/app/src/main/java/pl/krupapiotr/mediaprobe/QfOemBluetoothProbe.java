package pl.krupapiotr.mediaprobe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Read-only probe of the QF001 factory Bluetooth layer on Roco K706.
 *
 * The factory com.qf.bluetooth service, not the standard Android HFP client,
 * owns the GOC phone module. This probe never opens /dev/goc_serial, never
 * accesses GOC sockets, sends AT commands or changes audio routing.
 *
 * Observing music-information broadcasts only proves that the factory
 * channel is accessible; it MUST NOT be interpreted as a phone call.
 * Caller data stays on the device. Copyable diagnostics contain key names,
 * package status and timings, but never raw broadcast values or phone numbers.
 */
final class QfOemBluetoothProbe {
    private static final String VENDOR_PACKAGE = "com.qf.bluetooth";
    private static final String MUSIC_INFO = "com.qf.action.BT.MUSIC.INFO";
    private static final String[] OTHER_READ_ONLY = {
            "BT_STATE", "UPDATE_MEDIA_INFO", "UPDATE_MEDIA_STATE"
    };

    private final Context context;
    private boolean registered;
    private String packageStatus = "NIE SPRAWDZONO";
    private String propertyStatus = "NIE SPRAWDZONO";
    private String eventName = "BRAK";
    private String eventKeys = "-";
    private int eventCount;
    private long lastEventAt;
    private long lastPollAt;

    QfOemBluetoothProbe(Context ctx) {
        context = ctx.getApplicationContext();
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            eventName = intent.getAction();
            eventCount++;
            lastEventAt = System.currentTimeMillis();
            eventKeys = safeKeys(intent);
            publish();
        }
    };

    void start() {
        if (registered) return;
        try {
            context.getPackageManager().getPackageInfo(VENDOR_PACKAGE, 0);
            packageStatus = "OBECNY";
        } catch (PackageManager.NameNotFoundException ex) {
            packageStatus = "BRAK LUB NIEWIDOCZNY";
        } catch (SecurityException ex) {
            packageStatus = "ODCZYT ZABLOKOWANY";
        }

        IntentFilter filter = new IntentFilter(MUSIC_INFO);
        for (String action : OTHER_READ_ONLY) filter.addAction(action);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            registered = true;
        } catch (RuntimeException ex) {
            eventName = "NASLUCH NIEDOSTEPNY " + ex.getClass().getSimpleName();
        }
        sample();
    }

    void stop() {
        if (registered) {
            try { context.unregisterReceiver(receiver); }
            catch (RuntimeException ignored) { }
        }
        registered = false;
    }

    void sample() {
        long now = System.currentTimeMillis();
        if (now - lastPollAt < 5000) return;
        lastPollAt = now;
        // Known, read-only QF property. This is a connection indicator, NOT
        // a validated ongoing-call indicator.
        propertyStatus = readSystemProperty("sys.qf.bt.is.connect");
        publish();
    }

    private void publish() {
        long age = lastEventAt == 0 ? -1 : Math.max(0, System.currentTimeMillis() - lastEventAt);
        String status = "QF PAKIET=" + packageStatus
                + " / BT POLACZONY=" + propertyStatus
                + " / ZDARZENIA=" + eventCount
                + " / OSTATNIE=" + eventName
                + " / POLA=" + eventKeys
                + " / WIEK_MS=" + age
                + " / ROZMOWA=NIEROZSTRZYGNIETA";
        context.getSharedPreferences("krupas_diag", Context.MODE_PRIVATE)
                .edit().putString("qf_oem_status", status).apply();
    }

    private static String safeKeys(Intent intent) {
        try {
            if (intent.getExtras() == null) return "-";
            Set<String> source = intent.getExtras().keySet();
            List<String> keys = new ArrayList<>();
            for (String key : source) {
                if (key != null && key.length() <= 64 && keys.size() < 18) keys.add(key);
            }
            Collections.sort(keys);
            return keys.toString();
        } catch (RuntimeException ex) {
            return "NIEDOSTEPNE";
        }
    }

    private static String readSystemProperty(String name) {
        try {
            Class<?> type = Class.forName("android.os.SystemProperties");
            Method getter = type.getMethod("get", String.class, String.class);
            Object value = getter.invoke(null, name, "");
            String result = value == null ? "" : String.valueOf(value).trim();
            return result.isEmpty() ? "BRAK WARTOSCI" : result.substring(0, Math.min(24, result.length()));
        } catch (Throwable ex) {
            // Hidden API restrictions vary across QF ROM versions.
            return "NIEDOSTEPNE";
        }
    }
}
