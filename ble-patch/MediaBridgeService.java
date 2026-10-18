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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BleDisplayClient ble;
    private final DisplayLease displayLease = new DisplayLease();

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
            } catch (Throwable ignored) { publishCandidate(""); }

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
        MediaSessionManager manager =
                (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        List<MediaController> sessions = null;
        try {
            if (manager != null) sessions = manager.getActiveSessions(
                    new ComponentName(this, MediaProbeNotificationService.class));
        } catch (SecurityException ignored) {
            // A visible radio without MediaSession can still supply RDS.
        }

        MediaController best = null;
        long bestScore = Long.MIN_VALUE;
        boolean visibleRadioStopped = false;
        if (sessions != null) for (MediaController controller : sessions) {
            if (controller == null) continue;
            PlaybackState state = controller.getPlaybackState();
            if (state == null) continue;
            int value = state.getState();
            boolean active = value == PlaybackState.STATE_PLAYING
                    || value == PlaybackState.STATE_BUFFERING
                    || value == PlaybackState.STATE_CONNECTING;
            if (!active) {
                if (controller.getPackageName().equals(radioPackage)) visibleRadioStopped = true;
                continue;
            }
            long candidateScore = score(state);
            if (candidateScore > bestScore) {
                best = controller;
                bestScore = candidateScore;
            }
        }

        String candidate = "";
        if (best != null) {
            candidate = meta(best.getMetadata(), MediaMetadata.METADATA_KEY_TITLE);
            if (best.getPackageName().equals(radioPackage) && !visibleStation.isEmpty()) {
                candidate = visibleStation;
            }
        } else if (!visibleRadioStopped) {
            candidate = visibleStation;
        }
        publishCandidate(candidate);
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
                .setContentTitle("KRUPAS MEDIA 0.6.2 TEST")
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

