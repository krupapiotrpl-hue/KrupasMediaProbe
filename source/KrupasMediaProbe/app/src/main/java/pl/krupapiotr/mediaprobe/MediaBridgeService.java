package pl.krupapiotr.mediaprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * 3.0 DIAG-1: radio-only bridge.
 * Jedyny tor: ekran radia -> rozpoznane FM/RDS -> BLE -> ESP.
 */
public final class MediaBridgeService extends Service {
    static final String ACTION_TEST_FM="pl.krupapiotr.mediaprobe.TEST_FM";

    private final Handler h = new Handler(Looper.getMainLooper());
    private BleDisplayClient ble;
    private String sentFreq = "";
    private String sentStation = "";

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            try { refresh(); }
            catch (Exception e) { diag("error", e.toString()); }
            h.postDelayed(this, 500);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        foreground();
        ble = new BleDisplayClient(this, () -> {
            sentFreq = "";
            sentStation = "";
            diag("connect", String.valueOf(System.currentTimeMillis()));
            refresh();
        });
        ble.start();
        h.post(loop);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TEST_FM.equals(intent.getAction())) {
            sendFrame("FM:FM 107.0", "TEST_FM");
            sendFrame("RDS:TEST FM", "TEST_RDS");
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        h.removeCallbacks(loop);
        if (ble != null) ble.stop();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void foreground() {
        NotificationManager mgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        String id = "krupas_media_bridge";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(id, "KRUPAS Media", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("KRUPAS Media Pro 3.0 DIAG-1");
            mgr.createNotificationChannel(c);
        }
        Intent open = new Intent(this, MainActivity.class);
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent tap = PendingIntent.getActivity(this, 0, open, f);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, id) : new Notification.Builder(this);
        Notification n = b.setContentTitle("KRUPAS Media Pro 3.0 DIAG-1")
                .setContentText("Diagnostyka FM/RDS -> ESP")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setContentIntent(tap)
                .build();
        startForeground(19, n);
    }

    private void diag(String k, String v) {
        getSharedPreferences("krupas_diag", 0).edit().putString(k, v == null ? "" : v).apply();
    }

    private void refresh() {
        if (ble == null) return;
        ble.watchdog();

        SharedPreferences radio = getSharedPreferences("krupas_radio", 0);
        String freq = SourceRouter.clean(radio.getString("freq", ""));
        String station = SourceRouter.clean(radio.getString("station", ""));
        long seen = radio.getLong("seen", 0);

        diag("route", "RADIO ONLY");
        diag("radio_status", "FM=" + freq + " | RDS=" + station + " | seen=" + seen);

        if (!ble.ready()) return;

        if (!freq.isEmpty() && !freq.equals(sentFreq)) {
            if (!sentFreq.isEmpty()) {
                sendFrame("CLEAR:RDS", "RETUNE");
                sentStation = "";
            }
            sendFrame("FM:FM " + freq, "RADIO_FREQ");
            sentFreq = freq;
        }

        if (!station.isEmpty() && !station.equals(sentStation)) {
            sendFrame("RDS:" + station, "RADIO_RDS");
            sentStation = station;
        }
    }

    private void sendFrame(String frame, String reason) {
        if (frame == null || frame.isEmpty() || ble == null) return;
        getSharedPreferences("krupas_diag", 0).edit()
                .putString("last_frame", frame)
                .putString("last_frame_reason", reason)
                .putLong("last_frame_time", System.currentTimeMillis())
                .apply();
        ble.send(frame);
    }
}
