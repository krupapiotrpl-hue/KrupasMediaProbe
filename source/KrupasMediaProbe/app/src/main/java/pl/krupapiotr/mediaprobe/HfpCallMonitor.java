package pl.krupapiotr.mediaprobe;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.reflect.Method;

/**
 * Best-effort observer of the Bluetooth HFP *client* used by car head units.
 * It never invokes hidden services, sends AT commands, or controls calls.
 *
 * On some OEM devices AG_CALL_CHANGED is restricted to system apps. In that
 * case no HFP call is claimed, and the original media path remains untouched.
 */
final class HfpCallMonitor {
    private static final String CALL_CHANGED =
            "android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED";
    private static final String CONNECTION_CHANGED =
            "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED";
    private static final String EXTRA_CALL = "android.bluetooth.headsetclient.extra.CALL";
    private static final String EXTRA_STATE = "android.bluetooth.profile.extra.STATE";

    private final Context context;
    private boolean registered;
    private boolean observed;
    private int callState = 7; // terminated/no call
    private boolean outgoing;
    private String number = "";
    private long updated;

    HfpCallMonitor(Context ctx) { context = ctx.getApplicationContext(); }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (i == null) return;
            if (CONNECTION_CHANGED.equals(i.getAction())) {
                if (i.getIntExtra(EXTRA_STATE, -1) == 0) {
                    observed = false;
                    callState = 7;
                    number = "";
                    log("HFP rozlaczony");
                }
                return;
            }
            if (!CALL_CHANGED.equals(i.getAction())) return;
            try {
                // BluetoothHeadsetClientCall is a hidden Android system class.
                // Read its object only if the platform delivers this broadcast.
                Object call = i.getExtras() == null ? null : i.getExtras().get(EXTRA_CALL);
                if (call == null) { log("HFP: zdarzenie bez danych rozmowy"); return; }
                int state = ((Number)invoke(call, "getState")).intValue();
                boolean out = (Boolean)invoke(call, "isOutgoing");
                Object value = invoke(call, "getNumber");
                String n = value == null ? "" : String.valueOf(value).trim();

                if (state == 7) {
                    observed = false;
                    callState = 7;
                    number = "";
                } else if (state >= 0 && state <= 6) {
                    if (!observed) outgoing = out;
                    if (state == 2 || state == 3) outgoing = true;
                    if (state == 4 || state == 5) outgoing = false;
                    callState = state;
                    observed = true;
                    if (!n.isEmpty() && !n.equals("null")) number = n;
                }
                updated = System.currentTimeMillis();
                log(status());
            } catch (Exception ex) {
                log("HFP: ograniczony dostep " + ex.getClass().getSimpleName());
            }
        }
    };

    private static Object invoke(Object call, String name) throws Exception {
        Method method = call.getClass().getMethod(name);
        return method.invoke(call);
    }

    void start() {
        if (registered) return;
        if (Build.VERSION.SDK_INT >= 31 &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            log("HFP: brak BLUETOOTH_CONNECT");
            return;
        }
        try {
            IntentFilter filter = new IntentFilter(CALL_CHANGED);
            filter.addAction(CONNECTION_CHANGED);
            if (Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else context.registerReceiver(receiver, filter);
            registered = true;
            log("HFP: nasluch uruchomiony, czekam na zdarzenie");
        } catch (Exception ex) {
            log("HFP: nasluch niedostepny " + ex.getClass().getSimpleName());
        }
    }

    void stop() {
        if (registered) {
            try { context.unregisterReceiver(receiver); } catch (Exception ignored) {}
        }
        registered = false;
    }

    boolean active() {
        // Failsafe if an OEM implementation never sends TERMINATED.
        return observed && callState < 7 && updated > 0 &&
                System.currentTimeMillis() - updated < 2L * 60 * 60 * 1000;
    }

    String status() {
        if (!active()) return "HFP: brak aktywnej rozmowy";
        return "HFP: " + (outgoing ? "WYCHODZACE" : "PRZYCHODZACE") +
                " state=" + callState + (number.isEmpty() ? "" : " numer dostepny");
    }

    String display() {
        String who = SourceRouter.clean(number);
        if (who.isEmpty()) who = "ROZMOWA";
        // ESP supplies T|; do not insert additional direction markers.
        return who;
    }

    private void log(String message) {
        context.getSharedPreferences("krupas_diag", Context.MODE_PRIVATE)
                .edit().putString("hfp_status", message).apply();
    }
}
