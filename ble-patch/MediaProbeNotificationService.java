package pl.krupapiotr.mediaprobe;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Read the current notification set, never a persisted last notification. */
public class MediaProbeNotificationService extends NotificationListenerService {
    private static MediaProbeNotificationService instance;
    @Override public void onListenerConnected() {
        instance = this;
        MediaBridgeService.start(this);
    }
    @Override public void onListenerDisconnected() { instance = null; }
    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }
    static String titleFor(String pkg) {
        if (instance == null || pkg == null || pkg.isEmpty()) return "";
        try {
            StatusBarNotification[] current = instance.getActiveNotifications();
            StatusBarNotification best = null;
            String title = "";
            if (current != null) for (StatusBarNotification sbn : current) {
                if (!pkg.equals(sbn.getPackageName())) continue;
                Notification n = sbn.getNotification();
                if (n == null || n.extras == null) continue;
                if (!Notification.CATEGORY_TRANSPORT.equals(n.category)
                        && !n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
                        && !(MediaTitles.factory(pkg)
                        && (n.flags & Notification.FLAG_ONGOING_EVENT) != 0)) continue;
                Bundle b = n.extras;
                String candidate = MediaTitles.first(text(b, Notification.EXTRA_TITLE),
                        text(b, Notification.EXTRA_BIG_TEXT), text(b, Notification.EXTRA_TEXT));
                if (!candidate.isEmpty() && (best == null || sbn.getPostTime() > best.getPostTime())) {
                    best = sbn; title = candidate;
                }
            }
            return title;
        } catch (SecurityException ignored) { return ""; }
    }
    /** Caller information is only a hint from a current CALL notification.
     * Some head units expose no such notification; never infer a contact. */
    static String currentCallLabel() {
        if (instance == null) return "";
        try {
            StatusBarNotification[] notifications = instance.getActiveNotifications();
            if (notifications == null) return "";
            StatusBarNotification latest = null;
            String label = "";
            for (StatusBarNotification sbn : notifications) {
                Notification n = sbn.getNotification();
                if (n == null || n.extras == null
                        || !Notification.CATEGORY_CALL.equals(n.category)) continue;
                String candidate = text(n.extras, Notification.EXTRA_TITLE).trim();
                if (candidate.isEmpty()) continue;
                if (latest == null || sbn.getPostTime() > latest.getPostTime()) {
                    latest = sbn;
                    label = candidate;
                }
            }
            return label;
        } catch (SecurityException ex) {
            return "";
        }
    }
    private static String text(Bundle b, String key) {
        CharSequence s = b == null ? null : b.getCharSequence(key);
        return s == null ? "" : s.toString();
    }
}
