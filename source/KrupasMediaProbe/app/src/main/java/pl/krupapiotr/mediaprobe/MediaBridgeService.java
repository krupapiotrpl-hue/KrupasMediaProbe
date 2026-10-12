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
    private long sentMedia, sentRadio;
    private boolean radioKnown;
    private final Runnable loop=new Runnable(){@Override public void run(){
        try{refresh();}catch(Exception e){diag("error",e.toString());}
        h.postDelayed(this,1500);
    }};

    @Override public void onCreate(){
        super.onCreate();
        foreground();
        ble=new BleDisplayClient(this,()->{
            oldMedia="";oldFreq="";oldStation="";sentMedia=sentRadio=0;
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
        if(ble==null)return;
        ble.watchdog();
        final long now=System.currentTimeMillis();
        final SharedPreferences radio=getSharedPreferences("krupas_radio",0);
        String freq=SourceRouter.clean(radio.getString("freq",""));
        String station=SourceRouter.clean(radio.getString("station",""));
        long radioSeen=radio.getLong("seen",0);
        String radioPkg=radio.getString("pkg", "");
        boolean validRadio=now-radioSeen<15000 && !freq.isEmpty();
        if(validRadio)radioKnown=true;
        MediaResult media=findPlayingMedia();
        if(media.title.isEmpty())media=fallbackLegacy(now);
        if(media.title.isEmpty())media=fallbackVisibleMp3(now,radioSeen);
        diag("media_status",media.pkg+" / "+media.title+" / "+media.state);
        diag("radio_status",radioPkg+" / "+freq+" / "+station+" / age="+(now-radioSeen)+"ms");
        // FM may stay on in the background: preserve the last known station
        // during this service session, but give actual playing MP3 precedence.
        boolean preferRadio=(validRadio || radioKnown) && media.title.isEmpty();
        if(preferRadio){
            if(!oldMedia.isEmpty()){ble.send("MEDIA:STOP");oldMedia="";}
            String effectiveFreq=!freq.isEmpty()?freq:oldFreq;
            boolean frequencyChanged=!effectiveFreq.isEmpty()&&!effectiveFreq.equals(oldFreq);
            if(ble.ready() && !effectiveFreq.isEmpty() && (!effectiveFreq.equals(oldFreq)||now-sentRadio>60000)){
                ble.send("FM:FM "+effectiveFreq); oldFreq=effectiveFreq;sentRadio=now;
            }
            if(ble.ready() && (validRadio || radioKnown) && (frequencyChanged || !station.equals(oldStation))){
                if(station.isEmpty())ble.send("CLEAR:RDS");
                else ble.send("RDS:"+station);
                oldStation=station;
            }
        } else if(!media.title.isEmpty()){
            if(!oldFreq.isEmpty()||!oldStation.isEmpty()){
                ble.send("CLEAR:FM");ble.send("CLEAR:RDS");oldFreq="";oldStation="";radioKnown=false;
            }
            if(ble.ready() && (!media.title.equals(oldMedia)||now-sentMedia>60000)){
                ble.send("TITLE:"+media.title);oldMedia=media.title;sentMedia=now;
            }
        }else{
            if(!oldMedia.isEmpty()){ble.send("MEDIA:STOP");oldMedia="";}
            // FM/RDS are static until the station changes or MP3 starts.
            // Simply leaving the FM window must not erase the Opel display.
        }
        diag("route",preferRadio?"OEM FM":!media.title.isEmpty()?"MP3 / MEDIA":"BRAK DANYCH");
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
            if(title.isEmpty())return;
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
