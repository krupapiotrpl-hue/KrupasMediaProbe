package pl.krupapiotr.mediaprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;

public class MediaBridgeService extends Service {
    private static final String CHANNEL_ID = "krupas_media_bridge";
    private static final int NOTIFICATION_ID = 706;
    private static final long POLL_MS = 1000;
    static String diagnostic = "Usługa wysyłania jeszcze nie uruchomiona";
    static long observedAt;
    static String diagnosticNow() {
        if (observedAt == 0) return diagnostic;
        return diagnostic + "\nWiek odczytu: "
                + ((SystemClock.elapsedRealtime() - observedAt) / 1000) + " s";
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BleDisplayClient ble;
    private DisplayLease displayLease = new DisplayLease();
    private final SourceSelector selector = new SourceSelector();

    static void start(Context context) {
        Intent i = new Intent(context, MediaBridgeService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(i);
        } else {
            context.startService(i);
        }
    }

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            try {
                pollAndSend();
            } catch (Throwable error) {
                diagnostic = "Błąd usługi: " + error.getClass().getSimpleName();
                publishCandidate("");
            }

            if (ble != null) ble.start();
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        startForeground(
                NOTIFICATION_ID,
                buildNotification("Łączenie z ZAFIRA-DISPLAY…")
        );

        ble = BleDisplayClient.get(this);
        ble.start();

        handler.post(poller);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (ble == null) ble = BleDisplayClient.get(this);
        ble.start();

        handler.removeCallbacks(poller);
        handler.post(poller);

        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(poller);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    private void pollAndSend() {
        String visibleStation = RdsAccessibilityService.readVisibleStation();
        String radioPackage = RdsAccessibilityService.stationPackage;
        if (!visibleStation.isEmpty()) visibleStation = radioLabel(visibleStation);
        MediaSessionManager manager =
                (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        List<MediaController> sessions = null;
        try {
            if (manager != null) sessions = manager.getActiveSessions(
                    new ComponentName(this, MediaProbeNotificationService.class));
        } catch (SecurityException ignored) {
            // A visible radio without MediaSession can still supply RDS.
        }

        java.util.ArrayList<SourceSelector.Source> active = new java.util.ArrayList<>();
        boolean visibleStopped = false;
        String foreground = RdsAccessibilityService.foregroundPackage;
        for (MediaController controller : sessions == null
                ? java.util.Collections.<MediaController>emptyList() : sessions) {
            if (controller == null) continue;
            String pkg = controller.getPackageName();
            PlaybackState state = controller.getPlaybackState();
            int value = state == null ? PlaybackState.STATE_NONE : state.getState();
            boolean playing = value == PlaybackState.STATE_PLAYING
                    || value == PlaybackState.STATE_BUFFERING
                    || value == PlaybackState.STATE_CONNECTING;
            if (!playing) {
                if (pkg.equals(foreground) && state != null
                        && (value == PlaybackState.STATE_PAUSED || value == PlaybackState.STATE_STOPPED
                        || value == PlaybackState.STATE_ERROR)) visibleStopped = true;
                continue;
            }
            MediaMetadata metadata = controller.getMetadata();
            boolean isRadio = pkg.toLowerCase(java.util.Locale.ROOT).contains("radio")
                    || pkg.toLowerCase(java.util.Locale.ROOT).contains(".fm");
            String title = isRadio ? RdsText.join(
                    meta(metadata, MediaMetadata.METADATA_KEY_TITLE),
                    meta(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                    meta(metadata, MediaMetadata.METADATA_KEY_ARTIST),
                    meta(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                    meta(metadata, MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION))
                    : MediaTitles.first(meta(metadata, MediaMetadata.METADATA_KEY_TITLE),
                    meta(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
            if (title.isEmpty()) title = MediaProbeNotificationService.titleFor(pkg);
            if (pkg.equals(foreground) && title.isEmpty()) title = RdsAccessibilityService.mediaTitle;
            if (title.isEmpty()) title = RdsAccessibilityService.titleFor(pkg);
            if (pkg.equals(radioPackage) && !visibleStation.isEmpty()) title = visibleStation;
            else if (isRadio && !title.isEmpty()) title = radioLabel(title);
            active.add(new SourceSelector.Source(pkg, title,
                    state == null ? 0 : state.getLastPositionUpdateTime()));
        }
        SourceSelector.Source visible = null;
        if (foreground.toLowerCase(java.util.Locale.ROOT).contains("youtube")) {
            visible = new SourceSelector.Source(foreground,
                    MediaProbeNotificationService.titleFor(foreground), 0);
        }
        if (!visibleStopped && !foreground.isEmpty()) {
            String title = visibleStation;
            if (title.isEmpty() && MediaTitles.factory(foreground)) {
                title = MediaTitles.first(RdsAccessibilityService.mediaTitle,
                        MediaProbeNotificationService.titleFor(foreground));
            }
            if (!title.isEmpty()) visible = new SourceSelector.Source(foreground, title, 0);
        }
        SourceSelector.Source selected = selector.select(foreground, active, visible);
        // Do not show the old owner's title during the new owner's grace period.
        if (selector.changed) displayLease = new DisplayLease();
        String candidate = selected == null ? "" : selected.title;
        observedAt = SystemClock.elapsedRealtime();
        diagnostic = "BIEŻĄCE ŹRÓDŁO: " + (selected == null ? "brak" : selected.pkg)
                + "\nBIEŻĄCY TYTUŁ: " + (candidate.isEmpty() ? "brak metadanych" : candidate);
        publishCandidate(candidate);
    }

    private static String radioLabel(String title) {
        String clean = title == null ? "" : title.trim();
        if (clean.isEmpty() || clean.matches("(?i)(?:FM\\\\s*)?\\\\d{2,3}[.,]\\\\d+\\\\s*(?:MHZ)?")) return "";
        return clean.matches("(?i)^RADIO(?:\\\\s+.*)?$") ? clean : "RADIO " + clean;
    }

    private void publishCandidate(String candidate) {
        if (ble == null) ble = BleDisplayClient.get(this);
        String current = displayLease.update(candidate, SystemClock.elapsedRealtime());
        if (current.isEmpty()) {
            // Replace the pending value too, so reconnect cannot replay a closed app.
            ble.clearText();
            updateNotification("Brak aktywnego źródła — tekst wygaszony");
        } else {
            // Identical text is a BLE keepalive; the ESP never resets its scroll.
            ble.sendText(current);
            updateNotification((ble.isConnected() ? "BLE OK — " : "BLE łączenie — ") + current);
        }
    }
    private long score(PlaybackState state) {
        if (state == null) return 0;

        long base;

        switch (state.getState()) {
            case PlaybackState.STATE_PLAYING:
                base = 1000000000000L;
                break;

            case PlaybackState.STATE_BUFFERING:
                base = 900000000000L;
                break;

            case PlaybackState.STATE_CONNECTING:
                base = 850000000000L;
                break;

            case PlaybackState.STATE_PAUSED:
                base = 200000000000L;
                break;

            default:
                base = 0;
                break;
        }

        long update = state.getLastPositionUpdateTime();

        if (update <= 0) {
            update = SystemClock.elapsedRealtime();
        }

        return base + update;
    }

    private static String meta(MediaMetadata m, String key) {
        if (m == null) return "";

        CharSequence x = m.getText(key);

        return x == null ? "" : x.toString().trim();
    }

    private static String stateName(int s) {
        switch (s) {
            case PlaybackState.STATE_NONE: return "NONE";
            case PlaybackState.STATE_STOPPED: return "STOPPED";
            case PlaybackState.STATE_PAUSED: return "PAUSED";
            case PlaybackState.STATE_PLAYING: return "PLAYING";
            case PlaybackState.STATE_FAST_FORWARDING: return "FAST_FORWARD";
            case PlaybackState.STATE_REWINDING: return "REWIND";
            case PlaybackState.STATE_BUFFERING: return "BUFFERING";
            case PlaybackState.STATE_ERROR: return "ERROR";
            case PlaybackState.STATE_CONNECTING: return "CONNECTING";
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS: return "PREVIOUS";
            case PlaybackState.STATE_SKIPPING_TO_NEXT: return "NEXT";
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM: return "QUEUE_ITEM";
            default: return String.valueOf(s);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;

        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (nm == null) return;

        NotificationChannel ch =
                new NotificationChannel(
                        CHANNEL_ID,
                        "KRUPAS Media",
                        NotificationManager.IMPORTANCE_MIN
                );

        ch.setDescription("Połączenie BLE z wyświetlaczem Opla");
        ch.setShowBadge(false);

        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        Notification.Builder b;

        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }

        return b
                .setContentTitle("Krupas Media Pro 0.6.6 RADIO SCREEN RDS")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (nm != null) {
            nm.notify(
                    NOTIFICATION_ID,
                    buildNotification(text)
            );
        }
    }
}

