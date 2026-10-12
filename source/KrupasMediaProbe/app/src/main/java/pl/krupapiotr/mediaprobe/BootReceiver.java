package pl.krupapiotr.mediaprobe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        if (i == null) return;
        String action=i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        if (!c.getSharedPreferences("krupas_config",Context.MODE_PRIVATE).getBoolean("auto",true)) return;
        Intent run = new Intent(c, MediaBridgeService.class);
        try { if(Build.VERSION.SDK_INT>=26)c.startForegroundService(run); else c.startService(run); }
        catch(Exception e) {c.getSharedPreferences("krupas_diag",0).edit().putString("error","Boot: "+e).apply();}
    }
}
