package pl.krupapiotr.mediaprobe;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class MediaProbeNotificationService extends NotificationListenerService {
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
