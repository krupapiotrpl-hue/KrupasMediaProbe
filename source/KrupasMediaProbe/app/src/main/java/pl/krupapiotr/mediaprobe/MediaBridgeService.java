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
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

/** Stable Android media/RDS -> tagged BLE records for ESP firmware v2.1+. */
public final class MediaBridgeService extends Service {
    static final String ACTION_TEST_FM="pl.krupapiotr.mediaprobe.TEST_FM";
    static final String ACTION_TEST_MP3="pl.krupapiotr.mediaprobe.TEST_MP3";
    private static final long POLL_MS=1500;
    private static final long ROUTE_HOLD_MS=3000;
    private static final long ROTATE_MS=5000;
    private static final long RESEND_MS=15000;

    private final Handler h=new Handler(Looper.getMainLooper());
    private BleDisplayClient ble;
    private boolean registered;

    private String activeRoute="", candidateRoute="";
    private long candidateSince;
    private String oldFreq="", oldRds="", oldMedia="";
    private long sentRadio, sentRds, sentMedia;
    private String rdsKey="", mediaKey="";
    private int rdsIndex, mediaIndex;
    private long nextRdsRotate, nextMediaRotate;

    private final Runnable loop=new Runnable(){@Override public void run(){
        try{refresh();}catch(Exception e){diag("error",e.toString());}
        h.postDelayed(this,POLL_MS);
    }};

    @Override public void onCreate(){
        super.onCreate();
        foreground();
        MediaProbeNotificationService.ensureBound(this);
        ble=new BleDisplayClient(this,()->{
            oldFreq=oldRds=oldMedia="";
            sentRadio=sentRds=sentMedia=0;
            rdsKey=mediaKey="";
            rdsIndex=mediaIndex=0;
            nextRdsRotate=nextMediaRotate=0;
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
        final String radioText = SourceRouter.clean(radio.getString("radio_text", ""));
        final long radioSeen = radio.getLong("seen", 0);
        final boolean radioVisible = radio.getBoolean("visible", false);
        final long radioAge = radioSeen == 0 ? Long.MAX_VALUE : Math.max(0, now-radioSeen);

        final boolean radioKnown = !freq.isEmpty() || !station.isEmpty() || !radioText.isEmpty();
        final boolean radioRecent = radioSeen > 0 && now >= radioSeen && radioAge <= 30000;
        final boolean radioLive = radioKnown &&
                (radioVisible || radioRecent || "RADIO".equals(activeRoute));

        MediaResult media = findPlayingMedia();
        if (!media.hasData()) media = fallbackLegacy(now);
        if (!media.hasData()) media = fallbackVisibleMp3(now, radioSeen);
        if (!media.hasData()) media = fallbackNotification(now, radioSeen);

        boolean playingSession = "PLAYING".equals(media.state);
        String wanted = playingSession ? "MEDIA" :
                radioLive ? "RADIO" :
                media.hasData() ? "MEDIA" : "NONE";
        String route = stabilizeRoute(wanted, now);

        diag("media_status", media.pkg + " / title=" + media.title +
                " / artist=" + media.artist + " / album=" + media.album + " / " + media.state);
        diag("radio_status", radio.getString("pkg", "") + " / " + freq +
                " / " + station + " / " + radioText +
                " / age=" + (radioSeen == 0 ? -1 : radioAge) +
                "ms / visible=" + radioVisible);
        diag("route", route + " (wanted=" + wanted + ")");

        if (!ble.ready()) return;

        if ("RADIO".equals(route)) {
            stopMediaIfNeeded();
            sendRadio(now, freq, station, radioText);
        } else if ("MEDIA".equals(route) && media.hasData()) {
            clearRadioIfNeeded();
            sendMedia(now, media);
        } else if ("NONE".equals(route)) {
            stopMediaIfNeeded();
            clearRadioIfNeeded();
        }
    }

    private String stabilizeRoute(String wanted,long now){
        if(activeRoute.isEmpty()){
            activeRoute=wanted;
            candidateRoute="";
            candidateSince=0;
            return activeRoute;
        }
        if(wanted.equals(activeRoute)){
            candidateRoute="";
            candidateSince=0;
            return activeRoute;
        }
        if(!wanted.equals(candidateRoute)){
            candidateRoute=wanted;
            candidateSince=now;
            return activeRoute;
        }
        if(now-candidateSince>=ROUTE_HOLD_MS){
            activeRoute=wanted;
            candidateRoute="";
            candidateSince=0;
        }
        return activeRoute;
    }

    private void sendRadio(long now,String freq,String station,String radioText){
        boolean frequencyChanged=!freq.equals(oldFreq);
        if(frequencyChanged && !oldFreq.isEmpty()){
            ble.send("CLEAR:RDS");
            oldRds="";
            sentRds=0;
            rdsKey="";
        }
        if(!freq.isEmpty() && (frequencyChanged || now-sentRadio>=RESEND_MS)){
            ble.send("FM:FM "+freq);
            oldFreq=freq;
            sentRadio=now;
        }else if(freq.isEmpty() && !oldFreq.isEmpty()){
            ble.send("CLEAR:FM");
            oldFreq="";
            sentRadio=0;
        }

        List<String> values=new ArrayList<>();
        addUnique(values,station);
        addUnique(values,radioText);
        String key=joinKey(values);
        if(!key.equals(rdsKey)){
            rdsKey=key;
            rdsIndex=0;
            nextRdsRotate=0;
            oldRds="";
        }
        if(values.isEmpty()){
            if(!oldRds.isEmpty()){
                ble.send("CLEAR:RDS");
                oldRds="";
                sentRds=0;
            }
            return;
        }

        boolean rotate=nextRdsRotate==0 || now>=nextRdsRotate;
        boolean heartbeat=now-sentRds>=RESEND_MS;
        if(rotate || heartbeat || oldRds.isEmpty()){
            String value=values.get(rdsIndex % values.size());
            if(!value.equals(oldRds) || heartbeat){
                ble.send("RDS:"+value);
                oldRds=value;
                sentRds=now;
            }
            if(values.size()>1) rdsIndex=(rdsIndex+1)%values.size();
            nextRdsRotate=now+ROTATE_MS;
        }
    }

    private void sendMedia(long now,MediaResult media){
        List<String> values=new ArrayList<>();
        addUnique(values,media.title);
        addUnique(values,media.artist);
        addUnique(values,media.album);
        if(values.isEmpty()) return;

        String key=joinKey(values);
        if(!key.equals(mediaKey)){
            mediaKey=key;
            mediaIndex=0;
            nextMediaRotate=0;
            oldMedia="";
        }

        boolean rotate=nextMediaRotate==0 || now>=nextMediaRotate;
        boolean heartbeat=now-sentMedia>=RESEND_MS;
        if(rotate || heartbeat || oldMedia.isEmpty()){
            String value=values.get(mediaIndex % values.size());
            if(!value.equals(oldMedia) || heartbeat){
                ble.send("TITLE:"+value);
                oldMedia=value;
                sentMedia=now;
            }
            if(values.size()>1) mediaIndex=(mediaIndex+1)%values.size();
            nextMediaRotate=now+ROTATE_MS;
        }
    }

    private void stopMediaIfNeeded(){
        if(!oldMedia.isEmpty() || !mediaKey.isEmpty()){
            ble.send("MEDIA:STOP");
            oldMedia="";
            mediaKey="";
            sentMedia=0;
            mediaIndex=0;
            nextMediaRotate=0;
        }
    }

    private void clearRadioIfNeeded(){
        if(!oldFreq.isEmpty() || !oldRds.isEmpty() || !rdsKey.isEmpty()){
            ble.send("CLEAR:FM");
            ble.send("CLEAR:RDS");
            oldFreq=oldRds="";
            rdsKey="";
            sentRadio=sentRds=0;
            rdsIndex=0;
            nextRdsRotate=0;
        }
    }

    private static void addUnique(List<String> out,String value){
        String v=SourceRouter.clean(value);
        if(v.isEmpty())return;
        for(String old:out)if(old.equalsIgnoreCase(v))return;
        out.add(v);
    }

    private static String joinKey(List<String> values){
        StringBuilder b=new StringBuilder();
        for(String v:values){if(b.length()>0)b.append('\u0001');b.append(v);}
        return b.toString();
    }

    private MediaResult findPlayingMedia(){
        MediaResult best=new MediaResult();
        final boolean access=MediaProbeNotificationService.isAccessEnabled(this);
        final boolean connected=MediaProbeNotificationService.isListenerConnected(this);
        diag("listener_status",access ? (connected ? "ACCESS=OK / CONNECTED" : "ACCESS=OK / DISCONNECTED") : "ACCESS=BRAK");

        if(!access){
            diag("session_error","BRAK DOSTĘPU DO POWIADOMIEŃ DLA LISTENERA");
            return best;
        }

        if(!connected) MediaProbeNotificationService.ensureBound(this);

        try{
            MediaSessionManager manager=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
            ComponentName listener=MediaProbeNotificationService.component(this);
            List<MediaController> sessions=manager.getActiveSessions(listener);
            if(sessions==null){
                diag("session_error","OK / ACTIVE SESSIONS = NULL");
                return best;
            }
            diag("session_error","OK / ACTIVE SESSIONS = "+sessions.size());
            for(MediaController c:sessions){
                PlaybackState state=c.getPlaybackState();
                if(state==null||state.getState()!=PlaybackState.STATE_PLAYING)continue;
                String pkg=c.getPackageName();
                if(SourceRouter.isRadioPackage(pkg))continue;
                MediaMetadata m=c.getMetadata();
                MediaResult candidate=new MediaResult();
                candidate.pkg=pkg;
                candidate.state="PLAYING";
                if(m!=null){
                    candidate.title=SourceRouter.clean(str(m.getText(MediaMetadata.METADATA_KEY_TITLE)));
                    candidate.artist=SourceRouter.clean(str(m.getText(MediaMetadata.METADATA_KEY_ARTIST)));
                    candidate.album=SourceRouter.clean(str(m.getText(MediaMetadata.METADATA_KEY_ALBUM)));
                    if(candidate.title.isEmpty())
                        candidate.title=SourceRouter.clean(str(m.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)));
                    if(candidate.artist.isEmpty())
                        candidate.artist=SourceRouter.clean(str(m.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)));
                }
                if(!candidate.hasData())continue;
                if(!best.hasData() || (SourceRouter.isAudioPackage(pkg)&&!SourceRouter.isAudioPackage(best.pkg)))best=candidate;
            }
        }catch(SecurityException err){
            MediaProbeNotificationService.ensureBound(this);
            diag("session_error","ACCESS=OK, LISTENER NIEPOŁĄCZONY: "+err.getMessage());
        }
        catch(Exception err){diag("session_error",err.toString());}
        return best;
    }

    private static String str(CharSequence a){return a==null?"":a.toString();}

    private MediaResult fallbackLegacy(long now){
        SharedPreferences p=getSharedPreferences("krupas_legacy",0);
        MediaResult x=new MediaResult();
        long t=p.getLong("seen",0);
        if(t<=0 || now<t || now-t>12000 || !p.getBoolean("playing",false))return x;
        x.title=SourceRouter.clean(p.getString("title",""));
        x.artist=SourceRouter.clean(p.getString("artist",""));
        x.album=SourceRouter.clean(p.getString("album",""));
        x.pkg=p.getString("pkg","legacy");
        x.state="LEGACY";
        return x;
    }

    private MediaResult fallbackVisibleMp3(long now,long radioSeen){
        MediaResult m=new MediaResult();
        SharedPreferences p=getSharedPreferences("krupas_mp3_ui",0);
        long stamp=p.getLong("seen",0);
        if(stamp==0 || now<stamp || now-stamp>12000 || stamp<radioSeen || !p.getBoolean("visible",false))return m;
        m.title=SourceRouter.clean(p.getString("title",""));
        m.artist=SourceRouter.clean(p.getString("artist",""));
        m.pkg=p.getString("pkg","mp3-ui");
        m.state="MP3 UI";
        return m;
    }

    private MediaResult fallbackNotification(long now,long radioSeen){
        MediaResult m=new MediaResult();
        SharedPreferences p=getSharedPreferences("krupas_mp3_notification",0);
        long t=p.getLong("seen",0);
        if(t==0 || now<t || now-t>12000 || t<radioSeen)return m;
        m.title=SourceRouter.clean(p.getString("title",""));
        m.artist=SourceRouter.clean(p.getString("artist",""));
        m.album=SourceRouter.clean(p.getString("album",""));
        m.pkg=p.getString("pkg","notification");
        m.state="NOTIFICATION";
        return m;
    }

    private static class MediaResult {
        String title="",artist="",album="",pkg="",state="NONE";
        boolean hasData(){return !title.isEmpty() || !artist.isEmpty() || !album.isEmpty();}
    }

    private final BroadcastReceiver musicReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(i==null)return;
            Bundle b=i.getExtras();
            if(b==null)return;
            String title=SourceRouter.clean(first(b,"track","title","song"));
            String artist=SourceRouter.clean(first(b,"artist","ARTIST_NAME"));
            String album=SourceRouter.clean(first(b,"album","album_name","ALBUM_NAME"));
            String state=first(b,"playing","playstate","state");
            boolean playing=!"false".equalsIgnoreCase(state) && !"0".equals(state) && !"paused".equalsIgnoreCase(state);
            getSharedPreferences("krupas_legacy",0).edit()
                    .putLong("seen",System.currentTimeMillis())
                    .putString("title",title)
                    .putString("artist",artist)
                    .putString("album",album)
                    .putString("pkg",i.getPackage()==null?"legacy":i.getPackage())
                    .putBoolean("playing",playing).apply();
        }
    };

    private static String first(Bundle b,String... keys){
        for(String k:keys){Object v=b.get(k);if(v!=null)return String.valueOf(v);}
        return "";
    }

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
