package pl.krupapiotr.mediaprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import java.util.List;

/** Active Android media/RDS -> tagged BLE records for ESP firmware v2.1+. */
public final class MediaBridgeService extends Service {
    static final String ACTION_TEST_FM="pl.krupapiotr.mediaprobe.TEST_FM";
    static final String ACTION_TEST_MP3="pl.krupapiotr.mediaprobe.TEST_MP3";
    private final Handler h=new Handler(Looper.getMainLooper());
    private BleDisplayClient ble;
    private boolean registered;
    private String oldMedia="",oldFreq="",oldStation="";
    private long sentMedia, sentRadio, sentStation;
    private boolean phoneCallActive = false;
    private long phoneLastSeen = 0L, phoneLastSent = 0L;
    private final Runnable loop=new Runnable(){@Override public void run(){
        try{refresh();}catch(Exception e){diag("error",e.toString());}
        h.postDelayed(this,1500);
    }};

    @Override public void onCreate(){
        super.onCreate();
        foreground();
        ble=new BleDisplayClient(this,()->{
            oldMedia="";oldFreq="";oldStation="";sentMedia=sentRadio=sentStation=0;
            phoneLastSent=0L;
            diag("connect",""+System.currentTimeMillis());
        });
        ble.start();
        registerMusicReceiver();
        h.post(loop);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && ACTION_TEST_FM.equals(intent.getAction())){
            ble.send("FM:107.0"); ble.send("RDS:TEST FM");
        }
        if(intent!=null && ACTION_TEST_MP3.equals(intent.getAction())) ble.send("TITLE:KRUPAS TEST MP3");
        return START_STICKY;
    }
    @Override public void onDestroy(){
        h.removeCallbacks(loop);
        if(registered)unregisterReceiver(musicReceiver);
        if(ble!=null)ble.stop();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
    private void foreground(){
        NotificationManager mgr=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        String id="krupas_media_bridge";
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel=new NotificationChannel(id,"KRUPAS Media",NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Połączenie z wyświetlaczem ZAFIRA-DISPLAY");
            mgr.createNotificationChannel(channel);
        }
        Intent open=new Intent(this,MainActivity.class);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=23)flags|=PendingIntent.FLAG_IMMUTABLE;
        PendingIntent tap=PendingIntent.getActivity(this,0,open,flags);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26 ? new Notification.Builder(this,id):new Notification.Builder(this);
        Notification n=builder.setContentTitle("KRUPAS Media — BLE")
                .setContentText("Nasłuch MP3 i radia FM")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true).setContentIntent(tap).build();
        startForeground(19,n);
    }
    private void diag(String k,String v){getSharedPreferences("krupas_diag",0).edit().putString(k,v).apply();}
    private void refresh(){
        if (ble == null) return;
        ble.watchdog();
        final long now = System.currentTimeMillis();
        final SharedPreferences radio = getSharedPreferences("krupas_radio", 0);
        final String freq = SourceRouter.clean(radio.getString("freq", ""));
        final String station = SourceRouter.clean(radio.getString("station", ""));
        final long radioSeen = radio.getLong("seen", 0);
        final boolean radioVisible = radio.getBoolean("visible", false);
        final boolean radioLive = radioVisible && radioSeen > 0 &&
                now >= radioSeen && now - radioSeen <= 25000 &&
                (!freq.isEmpty() || !station.isEmpty());

        MediaResult media = findPlayingMedia();
        if (media.title.isEmpty()) media = fallbackLegacy(now);
        if (media.title.isEmpty()) media = fallbackVisibleMp3(now, radioSeen);
        if (media.title.isEmpty()) media = fallbackNotification(now, radioSeen);

        diag("media_status", media.pkg + " / " + media.title + " / " + media.state);
        diag("radio_status", radio.getString("pkg", "") + " / " + freq +
                " / " + station + " / age=" + (radioSeen == 0 ? -1 : now - radioSeen) +
                "ms / visible=" + radioVisible);

        // An actually PLAYING media session is more reliable than a remembered
        // FM label. Visible live radio wins over legacy/notification fallbacks.
        boolean playingSession = "PLAYING".equals(media.state);
        boolean preferRadio = radioLive && !playingSession;
        diag("route", preferRadio ? "OEM FM/RDS" :
                !media.title.isEmpty() ? "MP3 / MEDIA" : "BRAK AKTYWNEGO ZRODLA");

        // Detect a live call independently of MediaSession and OEM radio metadata.
        // Keep a short grace period to avoid one-second OEM audio-mode glitches.
        boolean phoneObserved = false;
        try {
            AudioManager audio = (AudioManager)getSystemService(AUDIO_SERVICE);
            int mode = audio == null ? AudioManager.MODE_NORMAL : audio.getMode();
            phoneObserved = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION;
            diag("phone_mode", String.valueOf(mode));
        } catch (Exception ex) { diag("phone_error", ex.toString()); }
        if (phoneObserved) phoneLastSeen = SystemClock.elapsedRealtime();
        long monotonicNow = SystemClock.elapsedRealtime();
        boolean phoneNow = phoneObserved || (phoneCallActive && monotonicNow - phoneLastSeen <= 2500);
        diag("phone_status", phoneNow ? "ROZMOWA" : "BRAK ROZMOWY");

        // Do not update cached sent values while BLE is disconnected.
        // After reconnection the ready callback resets all caches.
        if (!ble.ready()) return;

        // Calls have priority over radio and media. Refresh while active and
        // send CALL_END once on hangup; never clear the media caches here.
        if (phoneNow) {
            if (!phoneCallActive || monotonicNow - phoneLastSent >= 2000) {
                ble.send("CALL:ROZMOWA");
                phoneLastSent = monotonicNow;
            }
            phoneCallActive = true;
            return;
        }
        if (phoneCallActive) {
            ble.send("CALL_END");
            phoneCallActive = false;
            phoneLastSent = 0L;
            oldMedia = oldFreq = oldStation = "";
            sentMedia = sentRadio = sentStation = 0L;
        }

        if (preferRadio) {
            if (!oldMedia.isEmpty()) {
                ble.send("MEDIA:STOP");
                oldMedia = "";
                sentMedia = 0;
            }

            final boolean frequencyChanged = !freq.equals(oldFreq);
            final boolean stationChanged = !station.equals(oldStation);

            if (frequencyChanged && !oldFreq.isEmpty()) {
                // Invalidate previous station only on actual retuning.
                ble.send("CLEAR:RDS");
                oldStation = "";
            }
            if (!freq.isEmpty() && (frequencyChanged || now - sentRadio >= 10000)) {
                ble.send("FM:FM " + freq);
                oldFreq = freq;
                sentRadio = now;
            } else if (freq.isEmpty() && !oldFreq.isEmpty()) {
                ble.send("CLEAR:FM");
                oldFreq = "";
            }

            if (!station.isEmpty() && (frequencyChanged || stationChanged || now - sentStation >= 10000)) {
                ble.send("RDS:" + station);
                oldStation = station;
                sentStation = now;
            } else if (station.isEmpty() && !oldStation.isEmpty()) {
                ble.send("CLEAR:RDS");
                oldStation = "";
            }
        }
        else if (!media.title.isEmpty()) {
            if (!oldFreq.isEmpty() || !oldStation.isEmpty()) {
                ble.send("CLEAR:FM");
                ble.send("CLEAR:RDS");
                oldFreq = "";
                oldStation = "";
                sentRadio = sentStation = 0;
            }
            if (!media.title.equals(oldMedia) || now - sentMedia >= 10000) {
                ble.send("TITLE:" + media.title);
                oldMedia = media.title;
                sentMedia = now;
            }
        }
        else {
            // Stop immediately once all live music sources have disappeared.
            if (!oldMedia.isEmpty()) {
                ble.send("MEDIA:STOP");
                oldMedia = "";
                sentMedia = 0;
            }
            if (!oldFreq.isEmpty() || !oldStation.isEmpty()) {
                ble.send("CLEAR:FM");
                ble.send("CLEAR:RDS");
                oldFreq = oldStation = "";
                sentRadio = sentStation = 0;
            }
        }
    }
    private MediaResult findPlayingMedia(){
        MediaResult best=new MediaResult();
        try{
            MediaSessionManager manager=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
            ComponentName listener=new ComponentName(this,MediaProbeNotificationService.class);
            List<MediaController> sessions=manager.getActiveSessions(listener);
            if(sessions==null)return best;
            for(MediaController c:sessions){
                PlaybackState state=c.getPlaybackState();
                if(state==null||state.getState()!=PlaybackState.STATE_PLAYING)continue;
                String pkg=c.getPackageName();
                if(SourceRouter.isRadioPackage(pkg))continue;
                MediaMetadata m=c.getMetadata();
                String title=m==null?"":SourceRouter.mediaDisplay(str(m.getText(MediaMetadata.METADATA_KEY_TITLE)),str(m.getText(MediaMetadata.METADATA_KEY_ARTIST)));
                if(title.isEmpty()&&m!=null)title=SourceRouter.clean(str(m.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)));
                if(title.isEmpty())continue;
                MediaResult candidate=new MediaResult();candidate.pkg=pkg;candidate.title=title;candidate.state="PLAYING";
                if(best.title.isEmpty() || (SourceRouter.isAudioPackage(pkg)&&!SourceRouter.isAudioPackage(best.pkg)))best=candidate;
            }
        }catch(SecurityException err){diag("session_error","WŁĄCZ DOSTĘP DO POWIADOMIEŃ: "+err.getMessage());}
        catch(Exception err){diag("session_error",err.toString());}
        return best;
    }
    private static String str(CharSequence a){return a==null?"":a.toString();}
    private MediaResult fallbackLegacy(long now){
        SharedPreferences p=getSharedPreferences("krupas_legacy",0);
        MediaResult x=new MediaResult();
        long t=p.getLong("seen",0);
        if(t<=0 || now<t || now-t>8000 || !p.getBoolean("playing",false))return x;
        x.title=SourceRouter.mediaDisplay(p.getString("title",""),p.getString("artist",""));
        x.pkg=p.getString("pkg","legacy");x.state="LEGACY";
        return x;
    }
    private MediaResult fallbackVisibleMp3(long now,long radioSeen){
        MediaResult m=new MediaResult();
        SharedPreferences p=getSharedPreferences("krupas_mp3_ui",0);
        long stamp=p.getLong("seen",0);
        if(stamp==0 || now<stamp || now-stamp>7000 || stamp<radioSeen || !p.getBoolean("visible",false))return m;
        m.title=SourceRouter.clean(p.getString("title",""));
        m.pkg=p.getString("pkg","mp3-ui");m.state="MP3 UI";
        return m;
    }
    private MediaResult fallbackNotification(long now,long radioSeen){
        MediaResult m=new MediaResult();
        SharedPreferences p=getSharedPreferences("krupas_mp3_notification",0);
        long t=p.getLong("seen",0);
        if(t==0 || now<t || now-t>12000 || t<radioSeen)return m;
        m.title=SourceRouter.clean(p.getString("title",""));
        m.pkg=p.getString("pkg","notification");m.state="NOTIFICATION";
        return m;
    }
    private static class MediaResult {String title="",pkg="",state="NONE";}
    private final BroadcastReceiver musicReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(i==null)return;
            Bundle b=i.getExtras();
            if(b==null)return;
            String title=SourceRouter.clean(first(b,"track","title","song"));
            String artist=SourceRouter.clean(first(b,"artist","ARTIST_NAME"));
            String state=first(b,"playing","playstate","state");
            boolean playing=!"false".equalsIgnoreCase(state) && !"0".equals(state) && !"paused".equalsIgnoreCase(state);
            getSharedPreferences("krupas_legacy",0).edit().putLong("seen",System.currentTimeMillis())
                    .putString("title",title).putString("artist",artist).putString("pkg",i.getPackage()==null?"legacy":i.getPackage())
                    .putBoolean("playing",playing).apply();
        }
    };
    private static String first(Bundle b,String... keys){for(String k:keys){Object v=b.get(k);if(v!=null)return String.valueOf(v);}return "";}
    private void registerMusicReceiver(){
        IntentFilter f=new IntentFilter();
        for(String a:new String[]{"com.android.music.metachanged","com.android.music.playstatechanged",
                "com.android.music.playbackcomplete","com.android.music.queuechanged",
                "com.htc.music.metachanged","com.miui.player.metachanged","fm.last.android.metachanged"})f.addAction(a);
        try{
            if(Build.VERSION.SDK_INT>=33)registerReceiver(musicReceiver,f,Context.RECEIVER_EXPORTED);
            else registerReceiver(musicReceiver,f);
            registered=true;
        }catch(Exception e){diag("receiver_error",e.toString());}
    }
}
