package pl.krupapiotr.mediaprobe;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class MediaProbeNotificationService extends NotificationListenerService {
    @Override public void onListenerConnected(){super.onListenerConnected();MediaBridgeService.poke(this,"NOTIFICATION_LISTENER_READY");}

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        Notification n = sbn.getNotification();
        Bundle e = n.extras;
        String title = text(e, Notification.EXTRA_TITLE);
        String body = text(e, Notification.EXTRA_TEXT);
        String sub = text(e, Notification.EXTRA_SUB_TEXT);
        String info = text(e, Notification.EXTRA_INFO_TEXT);
        String pkg=sbn.getPackageName();

        MediaStateStore.save(this,"NOTIFICATION",pkg,title,body,sub,"posted","info=" + info + "; category=" + n.category);

        // Notification is an additional trigger. MediaSession changes are handled directly by MediaBridgeService.
        if (Notification.CATEGORY_TRANSPORT.equals(n.category) || SourceRouter.isAudioPackage(pkg) || SourceRouter.isRadioPackage(pkg))
            MediaBridgeService.poke(this,"MEDIA_NOTIFICATION");
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn){
        if(sbn!=null && (SourceRouter.isAudioPackage(sbn.getPackageName())||SourceRouter.isRadioPackage(sbn.getPackageName())))
            MediaBridgeService.poke(this,"MEDIA_NOTIFICATION_REMOVED");
    }

    private static String text(Bundle b, String key) {
        if (b == null) return "";
        CharSequence s = b.getCharSequence(key);
        return s == null ? "" : s.toString();
    }
}
