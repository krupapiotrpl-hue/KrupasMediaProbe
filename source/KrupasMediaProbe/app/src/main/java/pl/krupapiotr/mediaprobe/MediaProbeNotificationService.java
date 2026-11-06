package pl.krupapiotr.mediaprobe;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class MediaProbeNotificationService extends NotificationListenerService {
    private static volatile MediaProbeNotificationService connected;
    @Override public void onListenerConnected() { connected=this; }
    @Override public void onListenerDisconnected() { connected=null; }
    @Override public void onDestroy() { if(connected==this)connected=null;super.onDestroy(); }

    static MediaBridgeService.MediaResult activeRadio(android.content.Context context) {
        MediaBridgeService.MediaResult out=new MediaBridgeService.MediaResult();
        MediaProbeNotificationService service=connected;
        if(service==null)return out;
        try {
            StatusBarNotification[] notifications=service.getActiveNotifications();
            if(notifications==null)return out;
            for(StatusBarNotification sbn:notifications) {
                Notification n=sbn.getNotification();
                if(!SourceRouter.isRadioPackage(sbn.getPackageName()) || n==null || !sbn.isOngoing())continue;
                out.radio=true;out.pkg=sbn.getPackageName();out.state="RADIO NOTIFICATION";
                out.title=SourceRouter.radioDisplay(text(n.extras,Notification.EXTRA_TITLE),
                    text(n.extras,Notification.EXTRA_TEXT),text(n.extras,Notification.EXTRA_BIG_TEXT),
                    text(n.extras,Notification.EXTRA_SUB_TEXT));
                if(!out.title.isEmpty())return out;
            }
        } catch(Exception e) {
            context.getSharedPreferences("krupas_diag",0).edit().putString("notification_error",e.toString()).apply();
        }
        return out;
    }

    // For versions of DAB-Z that expose metadata only through a foreground notification.
    static MediaBridgeService.MediaResult activeDab(android.content.Context context) {
        MediaBridgeService.MediaResult out = new MediaBridgeService.MediaResult();
        MediaProbeNotificationService service = connected;
        if (service == null) return out;
        try {
            StatusBarNotification[] active = service.getActiveNotifications();
            if (active == null) return out;
            for (StatusBarNotification sbn : active) {
                if (!SourceRouter.isDabPackage(sbn.getPackageName())) continue;
                Notification n = sbn.getNotification();
                if (n == null || !sbn.isOngoing()) continue;
                String title = SourceRouter.dabDisplay(
                        text(n.extras, Notification.EXTRA_TITLE),
                        text(n.extras, Notification.EXTRA_TEXT),
                        text(n.extras, Notification.EXTRA_BIG_TEXT),
                        text(n.extras, Notification.EXTRA_SUB_TEXT));
                if (title.isEmpty()) continue;
                out.dab = true;
                out.pkg = sbn.getPackageName();
                out.title = title;
                out.state = "DAB NOTIFICATION";
                return out;
            }
        } catch (Exception ex) {
            context.getSharedPreferences("krupas_diag",0).edit()
                    .putString("dab_notification_error",ex.toString()).apply();
        }
        return out;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        Notification n = sbn.getNotification();
        Bundle e = n.extras;
        String title = text(e, Notification.EXTRA_TITLE);
        String text = text(e, Notification.EXTRA_TEXT);
        String sub = text(e, Notification.EXTRA_SUB_TEXT);
        String info = text(e, Notification.EXTRA_INFO_TEXT);

        // Music players on K706 do not always expose a MediaSession.
        // Save audio notifications as a bounded metadata fallback.
        String pkg=sbn.getPackageName();
        if(pkg!=null && !pkg.equals(getPackageName()) && !SourceRouter.isRadioPackage(pkg)
                && (SourceRouter.isAudioPackage(pkg) || Notification.CATEGORY_TRANSPORT.equals(n.category))) {
            String candidate=SourceRouter.mediaDisplay(title,text);
            String upper=title.toUpperCase(java.util.Locale.ROOT);
            if(!candidate.isEmpty() && !upper.equals("MUSIC") && !upper.equals("MUZYKA")
                    && !upper.equals("USB") && !upper.equals("MP3")) {
                getSharedPreferences("krupas_mp3_notification",MODE_PRIVATE).edit()
                        .putString("pkg",pkg).putString("title",candidate)
                        .putLong("seen",System.currentTimeMillis()).apply();
            }
        }
        MediaStateStore.save(
                this,
                "NOTIFICATION",
                sbn.getPackageName(),
                title,
                text,
                sub,
                "posted",
                "info=" + info + "; category=" + n.category
        );
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if(sbn==null)return;
        android.content.SharedPreferences p=getSharedPreferences("krupas_mp3_notification",MODE_PRIVATE);
        if(sbn.getPackageName().equals(p.getString("pkg","")))p.edit().remove("title").remove("seen").apply();
    }

    private static String text(Bundle b, String key) {
        if (b == null) return "";
        CharSequence s = b.getCharSequence(key);
        return s == null ? "" : s.toString();
    }
}

