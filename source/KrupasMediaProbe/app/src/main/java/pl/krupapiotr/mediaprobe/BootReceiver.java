package pl.krupapiotr.mediaprobe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Start the background media bridge without opening the activity. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (i == null) return;
        String action = i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) &&
                !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        startBridge(c, action);
    }

    static void startBridge(Context c, String trigger) {
        if (!c.getSharedPreferences("krupas_config", Context.MODE_PRIVATE).getBoolean("auto", true)) return;
        try {
            Intent run = new Intent(c, MediaBridgeService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(run);
            else c.startService(run);
            c.getSharedPreferences("krupas_diag", 0).edit()
                    .putString("autostart", "Start requested: " + trigger).apply();
        } catch (Exception e) {
            c.getSharedPreferences("krupas_diag", 0).edit()
                    .putString("autostart", "Failed: " + trigger)
                    .putString("error", "Auto: " + e).apply();
        }
    }
}
