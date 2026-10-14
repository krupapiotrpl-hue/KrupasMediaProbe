package pl.krupapiotr.mediaprobe;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class MediaProbeNotificationService extends NotificationListenerService {

    private static final String LISTENER_PREFS="krupas_listener";
    private static final long REBIND_COOLDOWN_MS=8000L;
    private static volatile boolean connectedNow=false;

    public static ComponentName component(Context context) {
        return new ComponentName(context, MediaProbeNotificationService.class);
    }

    public static boolean isAccessEnabled(Context context) {
        String raw=Settings.Secure.getString(context.getContentResolver(),"enabled_notification_listeners");
        if(raw==null || raw.trim().isEmpty()) return false;
        ComponentName mine=component(context);
        for(String item:raw.split(":")) {
            ComponentName enabled=ComponentName.unflattenFromString(item);
            if(mine.equals(enabled)) return true;
        }
        return false;
    }

    public static boolean isListenerConnected(Context context) {
        return connectedNow;
    }

    public static void ensureBound(Context context) {
        if(Build.VERSION.SDK_INT<24 || connectedNow || !isAccessEnabled(context)) return;
        SharedPreferences p=context.getSharedPreferences(LISTENER_PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        long last=p.getLong("last_rebind",0L);
        if(last>0 && now>=last && now-last<REBIND_COOLDOWN_MS) return;
        p.edit().putLong("last_rebind",now).apply();
        try {
            NotificationListenerService.requestRebind(component(context));
            context.getSharedPreferences("krupas_diag",Context.MODE_PRIVATE).edit()
                    .putString("listener_rebind","REQUESTED @ "+now).apply();
        } catch(Exception e) {
            context.getSharedPreferences("krupas_diag",Context.MODE_PRIVATE).edit()
                    .putString("listener_rebind_error",e.toString()).apply();
        }
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connectedNow=true;
        long now=System.currentTimeMillis();
        getSharedPreferences(LISTENER_PREFS,MODE_PRIVATE).edit()
                .putBoolean("connected",true).putLong("last_connected",now).apply();
        getSharedPreferences("krupas_diag",MODE_PRIVATE).edit()
                .putString("listener_status","ACCESS=OK / CONNECTED").apply();
    }

    @Override public void onListenerDisconnected() {
        connectedNow=false;
        long now=System.currentTimeMillis();
        getSharedPreferences(LISTENER_PREFS,MODE_PRIVATE).edit()
                .putBoolean("connected",false).putLong("last_disconnected",now).apply();
        getSharedPreferences("krupas_diag",MODE_PRIVATE).edit()
                .putString("listener_status","ACCESS=OK / DISCONNECTED").apply();
        super.onListenerDisconnected();
        ensureBound(this);
    }

    @Override public void onDestroy() {
        connectedNow=false;
        getSharedPreferences(LISTENER_PREFS,MODE_PRIVATE).edit()
                .putBoolean("connected",false).apply();
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        Notification n = sbn.getNotification();
        Bundle e = n.extras;
        String title = SourceRouter.clean(text(e, Notification.EXTRA_TITLE));
        String artist = SourceRouter.clean(text(e, Notification.EXTRA_TEXT));
        String album = SourceRouter.clean(text(e, Notification.EXTRA_SUB_TEXT));
        String info = text(e, Notification.EXTRA_INFO_TEXT);

        String pkg=sbn.getPackageName();
        if(pkg!=null && !pkg.equals(getPackageName()) && !SourceRouter.isRadioPackage(pkg)
                && (SourceRouter.isAudioPackage(pkg) || Notification.CATEGORY_TRANSPORT.equals(n.category))) {
            String candidate=SourceRouter.mediaDisplay(title,artist);
            String upper=title.toUpperCase(java.util.Locale.ROOT);
            if(!candidate.isEmpty() && !upper.equals("MUSIC") && !upper.equals("MUZYKA")
                    && !upper.equals("USB") && !upper.equals("MP3")) {
                getSharedPreferences("krupas_mp3_notification",MODE_PRIVATE).edit()
                        .putString("pkg",pkg)
                        .putString("title",title)
                        .putString("artist",artist)
                        .putString("album",album)
                        .putLong("seen",System.currentTimeMillis()).apply();
            }
        }
        MediaStateStore.save(
                this,
                "NOTIFICATION",
                sbn.getPackageName(),
                title,
                artist,
                album,
                "posted",
                "info=" + info + "; category=" + n.category
        );
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if(sbn==null)return;
        android.content.SharedPreferences p=getSharedPreferences("krupas_mp3_notification",MODE_PRIVATE);
        if(sbn.getPackageName().equals(p.getString("pkg",""))) {
            p.edit().remove("title").remove("artist").remove("album").remove("seen").apply();
        }
    }

    private static String text(Bundle b, String key) {
        if (b == null) return "";
        CharSequence s = b.getCharSequence(key);
        return s == null ? "" : s.toString();
    }
}
