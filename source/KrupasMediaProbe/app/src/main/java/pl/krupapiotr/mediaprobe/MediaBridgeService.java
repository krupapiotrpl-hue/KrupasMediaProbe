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
    private PhoneCallMonitor phone;
    private HfpCallMonitor hfp;
    private boolean phoneDisplayed;
    private String lastPhoneText="";
    private long lastPhoneSent;
    private boolean registered;
    private String oldMedia="",oldFreq="",oldStation="",oldDab="";
    private long sentMedia, sentRadio, sentStation, sentDab;
    private boolean radioSessionStopped;
    private final Runnable loop=new Runnable(){@Override public void run(){
        try{refresh();}catch(Exception e){diag("error",e.toString());}
        h.postDelayed(this,1500);
    }};

    @Override public void onCreate(){
        super.onCreate();
        foreground();
        ble=new BleDisplayClient(this,()->{
            oldMedia="";oldFreq="";oldStation="";oldDab="";sentMedia=sentRadio=sentStation=sentDab=0;
            phoneDisplayed=false;lastPhoneSent=0;lastPhoneText="";
            diag("connect",""+System.currentTimeMillis());
        });
        ble.start();
        phone=new PhoneCallMonitor(this);
        phone.start();
        hfp=new HfpCallMonitor(this);
        hfp.start();
        registerMusicReceiver();
        h.post(loop);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && ACTION_TEST_FM.equals(intent.getAction())){
            ble.send("FM:107.0"); ble.send("RDS:TEST FM");
        }
        if(intent!=null && ACTION_TEST_MP3.equals(intent.getAction())) ble.send("TITLE:KRUPAS TEST MP3");
        if(hfp!=null)hfp.start(); // retry HFP registration after BLE permission granted
        return START_STICKY;
    }
    @Override public void onDestroy(){
        h.removeCallbacks(loop);
        if(registered)unregisterReceiver(musicReceiver);
        if(phone!=null)phone.stop();
        if(hfp!=null)hfp.stop();
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
                .setContentText("Nasłuch FM, MP3 i DAB-Z")
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
        String station = SourceRouter.clean(radio.getString("station", ""));
        final String screenRds = SourceRouter.clean(radio.getString("rds", ""));
        final long rdsSeen = radio.getLong("rds_seen", 0);
        final long radioSeen = radio.getLong("seen", 0);
        final boolean radioVisible = radio.getBoolean("visible", false);
        // The accessibility window may be hidden while the radio keeps playing.
        // Accept only recent captured metadata; never treat cached text as indefinitely live.
        boolean radioLive = radioSeen > 0 &&
                now >= radioSeen && now - radioSeen <= 25000 &&
                (!freq.isEmpty() || !station.isEmpty() || !screenRds.isEmpty());

        MediaResult media = findPlayingMedia();
        // DAB-Z notifications do not always expose a MediaSession.
        if (media.title.isEmpty() && !media.dab) {
            MediaResult dabNotification = MediaProbeNotificationService.activeDab(this);
            if (dabNotification.dab) media = dabNotification;
        }
        if (media.title.isEmpty() && !radioSessionStopped && !media.dab) {
            MediaResult notification = MediaProbeNotificationService.activeRadio(this);
            if (notification.radio && (!media.radio || notification.pkg.equals(media.pkg))) media = notification;
        }
        if (media.title.isEmpty() && !media.radio && !media.dab) media = fallbackDabUi(now);
        if (media.title.isEmpty() && !media.radio && !media.dab) media = fallbackLegacy(now);
        if (media.title.isEmpty() && !media.radio && !media.dab) media = fallbackVisibleMp3(now, radioSeen);
        if (media.title.isEmpty() && !media.radio && !media.dab) media = fallbackNotification(now, radioSeen);
        if (radioSessionStopped && !media.radio) radioLive = false;
        if (media.radio) {
            radioLive = true;
            // Prefer actual radio session/notification data. Do not reuse UI
            // metadata indefinitely or associate another radio's screen with it.
            station = !media.title.isEmpty() ? media.title :
                    (now >= radioSeen && now - radioSeen <= 25000 &&
                     media.pkg.equals(radio.getString("pkg", "")) ? station : "");
        }

        // Prefer fresh RDS from the same radio over the fixed station label.
        boolean rdsCurrent = radioLive && rdsSeen > 0 && now >= rdsSeen &&
                now - rdsSeen <= 25000 && !screenRds.isEmpty() &&
                !screenRds.equalsIgnoreCase(station) &&
                (media.pkg.isEmpty() || media.pkg.equals(radio.getString("pkg", "")));
        final String radioText = rdsCurrent ? screenRds : station;
        diag("rds_status", "station=" + station + " / rds=" + screenRds +
                " / active=" + rdsCurrent + " / age=" + (rdsSeen == 0 ? -1 : now - rdsSeen));
        diag("media_status", media.pkg + " / " + media.title + " / " + media.state);
        diag("radio_status", radio.getString("pkg", "") + " / " + freq +
                " / " + station + " / age=" + (radioSeen == 0 ? -1 : now - radioSeen) +
                "ms / visible=" + radioVisible);

        // An actually PLAYING media session is more reliable than a remembered
        // FM label. Visible live radio wins over legacy/notification fallbacks.
        boolean playingSession = "PLAYING".equals(media.state) && !media.radio;
        boolean preferDab = media.dab && !media.title.isEmpty();
        boolean preferRadio = !preferDab && radioLive && !playingSession;
        diag("dab_status", (preferDab ? "AKTYWNE" : "NIEAKTYWNE") + " / " +
                media.pkg + " / " + (media.dab ? media.title : "") + " / " + media.state);
        diag("route", preferDab ? "DAB+ / " + media.state :
                preferRadio ? "RADIO / " + media.state :
                !media.title.isEmpty() ? "MP3 / MEDIA" : "BRAK AKTYWNEGO ZRODLA");

        // HFP calls from a paired phone take priority over local TelephonyManager.
        // Never change the existing DAB, FM, MP3, YouTube or MP4 media routing.
        boolean hfpActive = hfp!=null && hfp.active();
        boolean localCall = !hfpActive && phone!=null && phone.isActive();
        if(hfpActive || localCall) {
            String caller = hfpActive ? hfp.display() : phone.display();
            diag("route", "TELEFON / " + (hfpActive ? hfp.status() : phone.status()));
            if(ble.ready() && (!phoneDisplayed || !caller.equals(lastPhoneText) || now-lastPhoneSent>=10000)) {
                ble.send("PHONE:" + caller); // ESP owns the fixed T| prefix
                lastPhoneText=caller;
                phoneDisplayed=true;
                lastPhoneSent=now;
            }
            return;
        }
        if(phoneDisplayed) {
            if(ble.ready()) ble.send("PHONE:STOP");
            phoneDisplayed=false;
            lastPhoneSent=0;
            lastPhoneText="";
            oldMedia=oldFreq=oldStation=oldDab="";
            sentMedia=sentRadio=sentStation=sentDab=0;
        }

        // Do not update cached sent values while BLE is disconnected.
        // After reconnection the ready callback resets all caches.
        if (!ble.ready()) return;

        final boolean nativeDab = getSharedPreferences("krupas_settings",0)
                .getBoolean("dab_native_protocol",false);
        // Prepare the native D| protocol now; default to TITLE: for current ESP 1.9x17.
        // Switching the UI toggle later enables DAB:TEXT without another Android update.
        if (!preferDab && !oldDab.isEmpty()) {
            ble.send(nativeDab ? "DAB:STOP" : "MEDIA:STOP");
            oldDab=""; sentDab=0;
            oldMedia=""; sentMedia=0;
        }
        if (preferDab) {
            if (!oldFreq.isEmpty() || !oldStation.isEmpty()) {
                ble.send("CLEAR:FM");
                ble.send("CLEAR:RDS");
                oldFreq=oldStation=""; sentRadio=sentStation=0;
            }
            if (!media.title.equals(oldDab) || now-sentDab >= 10000) {
                // For 1.9x17 TITLE becomes M|. Future ESP DAB:TEXT becomes D|.
                ble.send(nativeDab ? "DAB:TEXT:" + media.title : "TITLE:" + media.title);
                oldDab=media.title; oldMedia=media.title; sentDab=sentMedia=now;
            }
        }
        else if (preferRadio) {
            if (!oldMedia.isEmpty()) {
                ble.send("MEDIA:STOP");
                oldMedia = "";
                sentMedia = 0;
            }

            final boolean frequencyChanged = !freq.equals(oldFreq);
            final boolean stationChanged = !radioText.equals(oldStation);

            if (frequencyChanged && !oldFreq.isEmpty()) {
                // Invalidate previous station only on actual retuning.
                ble.send("CLEAR:RDS");
                oldStation = "";
            }
            // Opel already renders FM frequency in its original center line.
            // Always clear the app-side FM fallback; never duplicate frequency above it.
            if (frequencyChanged || sentRadio == 0) ble.send("CLEAR:FM");
            oldFreq = freq;
            sentRadio = now;

            if (!radioText.isEmpty() && (frequencyChanged || stationChanged || now - sentStation >= 10000)) {
                ble.send("RDS:" + radioText);
                oldStation = radioText;
                sentStation = now;
            } else if (radioText.isEmpty() && !oldStation.isEmpty()) {
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
        radioSessionStopped = false;
        StringBuilder sessionLog = new StringBuilder();
        try{
            MediaSessionManager manager=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
            ComponentName listener=new ComponentName(this,MediaProbeNotificationService.class);
            List<MediaController> sessions=manager.getActiveSessions(listener);
            if(sessions==null)return best;
            for(MediaController c:sessions){
                PlaybackState state=c.getPlaybackState();
                String pkg=c.getPackageName();
                boolean isRadio = SourceRouter.isRadioPackage(pkg);
                boolean isDab = SourceRouter.isDabPackage(pkg);
                sessionLog.append(pkg).append(" state=").append(state==null ? -1 : state.getState()).append("; ");
                if(state==null||state.getState()!=PlaybackState.STATE_PLAYING) {
                    if (isRadio && state!=null && (state.getState()==PlaybackState.STATE_PAUSED ||
                            state.getState()==PlaybackState.STATE_STOPPED || state.getState()==PlaybackState.STATE_NONE)) radioSessionStopped=true;
                    continue;
                }
                MediaMetadata m=c.getMetadata();
                if (isDab) {
                    MediaResult candidate=new MediaResult();
                    candidate.pkg=pkg;candidate.dab=true;candidate.state="PLAYING";
                    candidate.title=m==null ? "" : SourceRouter.dabDisplay(
                        str(m.getText(MediaMetadata.METADATA_KEY_TITLE)),
                        str(m.getText(MediaMetadata.METADATA_KEY_ARTIST)),
                        str(m.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)),
                        str(m.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)));
                    if (!candidate.title.isEmpty()) best=candidate;
                    continue;
                }
                if (isRadio) {
                    MediaResult candidate=new MediaResult();
                    candidate.pkg=pkg;candidate.radio=true;candidate.state="RADIO SESSION";
                    candidate.title=m==null ? "" : SourceRouter.radioDisplay(
                        str(m.getText(MediaMetadata.METADATA_KEY_TITLE)), str(m.getText(MediaMetadata.METADATA_KEY_ARTIST)),
                        str(m.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)), str(m.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)));
                    if (best.state.equals("NONE") || best.radio) best=candidate;
                    continue;
                }
                String title=m==null?"":SourceRouter.mediaDisplay(str(m.getText(MediaMetadata.METADATA_KEY_TITLE)),str(m.getText(MediaMetadata.METADATA_KEY_ARTIST)));
                if(title.isEmpty()&&m!=null)title=SourceRouter.clean(str(m.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)));
                if(title.isEmpty())continue;
                MediaResult candidate=new MediaResult();candidate.pkg=pkg;candidate.title=title;candidate.state="PLAYING";
                if(best.title.isEmpty() || best.radio || (SourceRouter.isAudioPackage(pkg)&&!SourceRouter.isAudioPackage(best.pkg)))best=candidate;
            }
        }catch(SecurityException err){diag("session_error","WŁĄCZ DOSTĘP DO POWIADOMIEŃ: "+err.getMessage());}
        catch(Exception err){diag("session_error",err.toString());}
        diag("sessions", sessionLog.toString());
        if(best.radio) radioSessionStopped=false;
        return best;
    }
    private static String str(CharSequence a){return a==null?"":a.toString();}
    private MediaResult fallbackDabUi(long now) {
        MediaResult m = new MediaResult();
        SharedPreferences p=getSharedPreferences("krupas_dab_ui",0);
        long stamp=p.getLong("seen",0);
        if(stamp<=0 || now<stamp || now-stamp>7000 || !p.getBoolean("visible",false))return m;
        m.title=SourceRouter.clean(p.getString("title",""));
        if(m.title.isEmpty())return m;
        m.pkg=p.getString("pkg","com.zoulou.dab");m.state="DAB UI";m.dab=true;
        return m;
    }
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
    static class MediaResult {String title="",pkg="",state="NONE";boolean radio,dab;}
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

