package pl.krupapiotr.mediaprobe;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity {
    private TextView out;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered;
    private BleDisplayClient bleDisplay;
    private static final int REQ_BLE = 706;

    private final BroadcastReceiver legacyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            Bundle e = intent.getExtras();
            String track = first(e, "track", "title", "song");
            String artist = first(e, "artist", "ARTIST_NAME");
            String album = first(e, "album", "ALBUM_NAME");
            String playing = first(e, "playing", "playstate", "state");
            MediaStateStore.save(context, "BROADCAST", intent.getPackage(), track, artist, album, playing,
                    "action=" + intent.getAction() + "; extras=" + String.valueOf(e));
            if (!track.isEmpty() && bleDisplay != null) bleDisplay.sendText(track);
            refresh();
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        bleDisplay = BleDisplayClient.get(this);
        requestBlePermissionsAndStart();
        registerLegacyReceiver();
        handler.post(ticker);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (receiverRegistered) unregisterReceiver(legacyReceiver);
        super.onDestroy();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 18, 24, 18);

        TextView title = new TextView(this);
        title.setText("KRUPAS MEDIA PROBE 0.3 RDS — K706");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("MediaSession + RDS z ekranu aplikacji FM + BLE do ZAFIRA-DISPLAY.");
        hint.setTextSize(16);
        hint.setPadding(0, 6, 0, 12);
        root.addView(hint);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.START);

        Button access = new Button(this);
        access.setText("POWIADOMIENIA");
        access.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception ex) {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            }
        });
        buttons.addView(access);

        Button rds = new Button(this);
        rds.setText("WŁĄCZ ODCZYT RDS");
        rds.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception ex) {
                startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));
            }
        });
        buttons.addView(rds);

        Button refresh = new Button(this);
        refresh.setText("ODŚWIEŻ");
        refresh.setOnClickListener(v -> refresh());
        buttons.addView(refresh);

        root.addView(buttons);

        ScrollView sv = new ScrollView(this);
        out = new TextView(this);
        out.setTextSize(17);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextIsSelectable(true);
        out.setPadding(0, 12, 0, 0);
        sv.addView(out);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    private void refresh() {
        if (out == null) return;

        StringBuilder s = new StringBuilder();

        s.append("NOTIFICATION LISTENER: ")
                .append(isNotificationAccessEnabled() ? "WŁĄCZONY" : "WYŁĄCZONY")
                .append("\n");

        s.append("ODCZYT RDS Z EKRANU: ")
                .append(isAccessibilityEnabled() ? "WŁĄCZONY" : "WYŁĄCZONY")
                .append("\n\n");

        s.append(readMediaSessions());
        s.append("\n\n").append(MediaStateStore.dump(this));

        out.setText(s.toString());
    }

    private boolean isNotificationAccessEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private boolean isAccessibilityEnabled() {
        String flat = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        if (flat == null) return false;

        String wanted = getPackageName() + "/" + RdsAccessibilityService.class.getName();
        String wantedShort = getPackageName() + "/.RdsAccessibilityService";

        return flat.contains(wanted) || flat.contains(wantedShort);
    }

    private String readMediaSessions() {
        StringBuilder s = new StringBuilder("ACTIVE MEDIA SESSIONS\n");

        try {
            MediaSessionManager msm =
                    (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

            ComponentName listener =
                    new ComponentName(this, MediaProbeNotificationService.class);

            List<MediaController> list =
                    msm.getActiveSessions(listener);

            if (list == null || list.isEmpty()) {
                s.append("brak aktywnych sesji");
                return s.toString();
            }

            int i = 0;

            for (MediaController c : list) {
                i++;

                MediaMetadata m = c.getMetadata();
                PlaybackState p = c.getPlaybackState();

                String title =
                        meta(m, MediaMetadata.METADATA_KEY_TITLE);

                String artist =
                        meta(m, MediaMetadata.METADATA_KEY_ARTIST);

                String album =
                        meta(m, MediaMetadata.METADATA_KEY_ALBUM);

                String state =
                        p == null ? "?" : stateName(p.getState());

                s.append("#").append(i).append("  ").append(c.getPackageName()).append("\n")
                        .append("  title: ").append(title).append("\n")
                        .append("  artist: ").append(artist).append("\n")
                        .append("  album: ").append(album).append("\n")
                        .append("  state: ").append(state).append("\n");

                if (!title.isEmpty() || !artist.isEmpty()) {
                    MediaStateStore.save(
                            this,
                            "MEDIA_SESSION",
                            c.getPackageName(),
                            title,
                            artist,
                            album,
                            state,
                            "position=" + (p == null ? -1 : p.getPosition())
                    );
                }

                if (!title.isEmpty() && bleDisplay != null) {
                    bleDisplay.sendText(title);
                }
            }

        } catch (SecurityException ex) {
            s.append("BRAK UPRAWNIEŃ — włącz Dostęp do powiadomień.\n")
                    .append(ex.getMessage());

        } catch (Throwable ex) {
            s.append("BŁĄD: ")
                    .append(ex.getClass().getSimpleName())
                    .append(": ")
                    .append(ex.getMessage());
        }

        return s.toString();
    }

    private void registerLegacyReceiver() {
        IntentFilter f = new IntentFilter();

        String[] actions = {
                "com.android.music.metachanged",
                "com.android.music.playstatechanged",
                "com.android.music.playbackcomplete",
                "com.android.music.queuechanged",
                "com.miui.player.metachanged",
                "com.miui.player.playstatechanged",
                "com.htc.music.metachanged",
                "fm.last.android.metachanged"
        };

        for (String a : actions) {
            f.addAction(a);
        }

        registerReceiver(legacyReceiver, f);
        receiverRegistered = true;
    }

    private void requestBlePermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                        },
                        REQ_BLE
                );

                return;
            }

        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQ_BLE
                );

                return;
            }
        }

        if (bleDisplay != null) {
            bleDisplay.start();
        }
    }

    @Override public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_BLE && bleDisplay != null) {
            bleDisplay.start();
        }
    }

    private static String meta(MediaMetadata m, String key) {
        if (m == null) return "";

        CharSequence x = m.getText(key);

        return x == null ? "" : x.toString();
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

    private static String first(Bundle b, String... keys) {
        if (b == null) return "";

        for (String key : keys) {
            Object o = b.get(key);
            if (o != null) return String.valueOf(o);
        }

        return "";
    }
}
