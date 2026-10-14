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

/** Active Android media/RDS -> tagged BLE records for ESP firmware v3.0+. */
public final class MediaBridgeService extends Service {
    static final String ACTION_TEST_FM="pl.krupapiotr.mediaprobe.TEST_FM";
    static final String ACTION_TEST_MP3="pl.krupapiotr.mediaprobe.TEST_MP3";
    static final String ACTION_REFRESH="pl.krupapiotr.mediaprobe.REFRESH";
    static final String EXTRA_REASON="reason";

    private final Handler h=new Handler(Looper.getMainLooper());
    private BleDisplayClient ble;
    private boolean registered;
    private String oldMedia="",oldFreq="",oldStation="";
    private long sentMedia, sentRadio;
    private boolean radioKnown;
    private MediaSessionManager mediaManager;
    private ComponentName listenerComponent;
    private final List<MediaController> watchedControllers=new ArrayList<>();

    private final Runnable loop=new Runnable(){@Override public void run(){
        safeRefresh("FALLBACK_POLL");
        h.postDelayed(this,750);
    }};

    private final MediaController.Callback mediaCallback=new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata){triggerRefresh("MEDIA_METADATA");}
        @Override public void onPlaybackStateChanged(PlaybackState state){triggerRefresh("MEDIA_STATE");}
        @Override public void onSessionDestroyed(){h.post(()->{rebuildSessionWatchers();safeRefresh("SESSION_DESTROYED");});}
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener activeSessionsChangedListener = controllers ->
            h.post(()->{rebuildSessionWatchers(controllers);safeRefresh("ACTIVE_SESSIONS");});

    static void poke(Context c,String reason){
        try{
            Intent i=new Intent(c,MediaBridgeService.class).setAction(ACTION_REFRESH).putExtra(EXTRA_REASON,reason==null?"EVENT":reason);
            if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);
        }catch(Exception e){c.getSharedPreferences("krupas_diag",0).edit().putString("error","poke: "+e).apply();}
    }

    @Override public void onCreate(){
        super.onCreate();
        foreground();
        mediaManager=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
        listenerComponent=new ComponentName(this,MediaProbeNotificationService.class);
        ble=new BleDisplayClient(this,()->{
            oldMedia="";oldFreq="";oldStation="";sentMedia=sentRadio=0;
            diag("connect",""+System.currentTimeMillis());
            safeRefresh("BLE_READY_REPLAY");
        });
        ble.start();
        registerMusicReceiver();
        registerSessionWatcher();
        h.post(loop);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && ACTION_TEST_FM.equals(intent.getAction())){
            sendFrame("FM:FM 107.0","TEST"); sendFrame("RDS:TEST FM","TEST");
        } else if(intent!=null && ACTION_TEST_MP3.equals(intent.getAction())) {
            sendFrame("TITLE:KRUPAS TEST MP3","TEST");
        } else if(intent!=null && ACTION_REFRESH.equals(intent.getAction())) {
            safeRefresh(intent.getStringExtra(EXTRA_REASON));
        }
        return START_STICKY;
    }

    @Override public void onDestroy(){
        h.removeCallbacks(loop);
        unregisterSessionWatcher();
        if(registered)try{unregisterReceiver(musicReceiver);}catch(Exception ignored){}
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
        Notification n=builder.setContentTitle("KRUPAS Media Pro — BLE")
                .setContentText("Automatyczny most Android → ESP32")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true).setContentIntent(tap).build();
        startForeground(19,n);
    }

    private void diag(String k,String v){getSharedPreferences("krupas_diag",0).edit().putString(k,v==null?"":v).apply();}
    private void triggerRefresh(String reason){h.removeCallbacks(eventRefresh);pendingReason=reason;h.postDelayed(eventRefresh,80);}
    private String pendingReason="EVENT";
    private final Runnable eventRefresh=()->safeRefresh(pendingReason);
    private void safeRefresh(String reason){
        try{diag("trigger",reason==null?"UNKNOWN":reason);refresh();}
        catch(Exception e){diag("error",e.toString());}
    }

    private void refresh(){
        if(ble==null)return;
        ble.watchdog();
        final long now=System.currentTimeMillis();
        final SharedPreferences radio=getSharedPreferences("krupas_radio",0);
        String freq=SourceRouter.clean(radio.getString("freq",""));
        String station=SourceRouter.clean(radio.getString("station",""));
        long radioSeen=radio.getLong("seen",0);
        String radioPkg=radio.getString("pkg", "");
        boolean validRadio=radioSeen>0 && now-radioSeen<15000 && !freq.isEmpty();
        if(validRadio)radioKnown=true;

        MediaResult media=findPlayingMedia();
        if(media.title.isEmpty())media=fallbackLegacy(now);
        if(media.title.isEmpty())media=fallbackVisibleMp3(now,radioSeen);

        diag("media_status",media.pkg+" / "+media.title+" / "+media.state);
        diag("radio_status",radioPkg+" / "+freq+" / "+station+" / age="+(radioSeen==0?"—":(now-radioSeen)+"ms"));
        boolean preferRadio=(validRadio || radioKnown) && media.title.isEmpty();
        diag("route",preferRadio?"OEM FM":!media.title.isEmpty()?"MP3 / MEDIA":"BRAK DANYCH");

        if(!ble.ready())return;

        if(preferRadio){
            if(!oldMedia.isEmpty()){sendFrame("MEDIA:STOP","ROUTE_TO_FM");oldMedia="";}
            String effectiveFreq=!freq.isEmpty()?freq:oldFreq;
            boolean frequencyChanged=!effectiveFreq.isEmpty()&&!effectiveFreq.equals(oldFreq);
            if(!effectiveFreq.isEmpty() && (frequencyChanged||now-sentRadio>60000)){
                sendFrame("FM:FM "+effectiveFreq,"FM_CHANGE"); oldFreq=effectiveFreq;sentRadio=now;
            }
            if((validRadio || radioKnown) && (frequencyChanged || !station.equals(oldStation))){
                if(station.isEmpty())sendFrame("CLEAR:RDS","RDS_CLEAR");
                else sendFrame("RDS:"+station,"RDS_CHANGE");
                oldStation=station;
            }
        } else if(!media.title.isEmpty()){
            if(!oldFreq.isEmpty()||!oldStation.isEmpty()){
                sendFrame("CLEAR:FM","ROUTE_TO_MEDIA");sendFrame("CLEAR:RDS","ROUTE_TO_MEDIA");
                oldFreq="";oldStation="";radioKnown=false;
            }
            if(!media.title.equals(oldMedia)||now-sentMedia>60000){
                sendFrame("TITLE:"+media.title,"MEDIA_CHANGE");oldMedia=media.title;sentMedia=now;
            }
        }else if(!oldMedia.isEmpty()){
            sendFrame("MEDIA:STOP","MEDIA_STOP");oldMedia="";
        }
    }

    private void sendFrame(String frame,String reason){
        if(frame==null||frame.isEmpty())return;
        diag("last_frame",frame);
        diag("last_frame_reason",reason);
        diag("last_frame_time",String.valueOf(System.currentTimeMillis()));
        ble.send(frame);
    }

    private MediaResult findPlayingMedia(){
        MediaResult best=new MediaResult();
        try{
            if(mediaManager==null)return best;
            List<MediaController> sessions=mediaManager.getActiveSessions(listenerComponent);
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
        if(now-t>10000 || !p.getBoolean("playing",false))return x;
        x.title=SourceRouter.mediaDisplay(p.getString("title",""),p.getString("artist",""));
        x.pkg=p.getString("pkg","legacy");x.state="LEGACY";
        return x;
    }

    private MediaResult fallbackVisibleMp3(long now,long radioSeen){
        MediaResult m=new MediaResult();
        SharedPreferences p=getSharedPreferences("krupas_mp3_ui",0);
        long stamp=p.getLong("seen",0);
        if(stamp==0 || now-stamp>20000 || stamp<radioSeen)return m;
        m.title=SourceRouter.clean(p.getString("title",""));
        m.pkg=p.getString("pkg","mp3-ui");m.state="MP3 UI";
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
            boolean playing=!"false".equalsIgnoreCase(state) && !"0".equals(state) && !"paused".equalsIgnoreCase(state) && !"pause".equalsIgnoreCase(state);
            getSharedPreferences("krupas_legacy",0).edit().putLong("seen",System.currentTimeMillis())
                    .putString("title",title).putString("artist",artist).putString("pkg",i.getPackage()==null?"legacy":i.getPackage())
                    .putBoolean("playing",playing).apply();
            safeRefresh("LEGACY_BROADCAST");
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

    private void registerSessionWatcher(){
        try{
            if(mediaManager==null)return;
            mediaManager.addOnActiveSessionsChangedListener(activeSessionsChangedListener,listenerComponent);
            rebuildSessionWatchers();
            diag("session_watch","OK");
        }catch(Exception e){diag("session_watch","NIEAKTYWNE: "+e.getClass().getSimpleName());}
    }

    private void unregisterSessionWatcher(){
        try{if(mediaManager!=null)mediaManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener);}catch(Exception ignored){}
        for(MediaController c:new ArrayList<>(watchedControllers))try{c.unregisterCallback(mediaCallback);}catch(Exception ignored){}
        watchedControllers.clear();
    }

    private void rebuildSessionWatchers(){
        try{rebuildSessionWatchers(mediaManager==null?null:mediaManager.getActiveSessions(listenerComponent));}
        catch(Exception e){diag("session_watch","ERR: "+e.getClass().getSimpleName());}
    }

    private void rebuildSessionWatchers(List<MediaController> controllers){
        for(MediaController c:new ArrayList<>(watchedControllers))try{c.unregisterCallback(mediaCallback);}catch(Exception ignored){}
        watchedControllers.clear();
        if(controllers==null)return;
        for(MediaController c:controllers){
            if(c==null||SourceRouter.isRadioPackage(c.getPackageName()))continue;
            try{c.registerCallback(mediaCallback,h);watchedControllers.add(c);}catch(Exception ignored){}
        }
        diag("session_watch","OK / "+watchedControllers.size()+" sesji");
    }
}
