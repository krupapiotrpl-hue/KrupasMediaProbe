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

    private static String text(Bundle b, String key) {
        if (b == null) return "";
        CharSequence s = b.getCharSequence(key);
        return s == null ? "" : s.toString();
    }
}
