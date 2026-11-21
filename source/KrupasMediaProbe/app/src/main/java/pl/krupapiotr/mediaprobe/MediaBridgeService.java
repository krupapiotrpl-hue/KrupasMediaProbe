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
    static final String ACTION_TEST_PHONE="pl.krupapiotr.mediaprobe.TEST_PHONE";
    private final Handler h=new Handler(Looper.getMainLooper());
    private BleDisplayClient ble;
    private final SourceAnnouncement sourceAnnouncement = new SourceAnnouncement();
    private boolean wakeRegistered;
    private final BroadcastReceiver wakeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            AccessHealth.event(context, "WAKE " + intent.getAction());
            AccessHealth.recoverNotificationListener(context);
            h.removeCallbacks(loop);
            h.post(loop);
        }
    };
    private PhoneCallMonitor phone;
    private HfpCallMonitor hfp;
    private QfOemBluetoothProbe qfProbe;
    private CallAudioMonitor callAudio;
    private final CallStatusTracker callTracker = new CallStatusTracker();
    private boolean phoneDisplayed;
    // ESP may still show the previous T| overlay after a BLE/service restart.
    private boolean phoneClearPending = true;
    private String lastPhoneText="";
    private long lastPhoneSent;
    private long testPhoneUntil;
    private boolean registered;
    private String oldMedia="",oldMediaSource="",oldFreq="",oldStation="",oldDab="";
    private long sentMedia, sentRadio, sentStation, sentDab;
    private boolean radioSessionStopped;
    // Only affects FM text; all other source routing remains unchanged.
    private final StationRdsRotator stationRdsRotator = new StationRdsRotator();
    private final Runnable loop=new Runnable(){@Override public void run(){
        try{refresh();}catch(Exception e){diag("error",e.toString());}
        h.postDelayed(this,1500);
    }};

    @Override public void onCreate(){
        super.onCreate();
        foreground();
        ble=new BleDisplayClient(this,()->{
            oldMedia="";oldMediaSource="";oldFreq="";oldStation="";oldDab="";sentMedia=sentRadio=sentStation=sentDab=0;
            sourceAnnouncement.reset();
            phoneDisplayed=false;lastPhoneSent=0;lastPhoneText="";
            phoneClearPending=true;
            diag("connect",""+System.currentTimeMillis());
        });
        ble.start();
        phone=new PhoneCallMonitor(this);
        phone.start();
        hfp=new HfpCallMonitor(this);
        hfp.start();
        qfProbe=new QfOemBluetoothProbe(this);
        qfProbe.start();
        callAudio=new CallAudioMonitor(this);
        registerMusicReceiver();
        IntentFilter wake = new IntentFilter();
        wake.addAction(Intent.ACTION_SCREEN_ON);
        wake.addAction(Intent.ACTION_USER_PRESENT);
        wake.addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(wakeReceiver, wake, Context.RECEIVER_EXPORTED);
        else registerReceiver(wakeReceiver, wake);
        wakeRegistered = true;
        AccessHealth.event(this, "BRIDGE_CREATED");
        h.post(loop);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && ACTION_TEST_FM.equals(intent.getAction())){
            ble.send("FM:107.0"); ble.send("RDS:TEST FM");
        }
        if(intent!=null && ACTION_TEST_MP3.equals(intent.getAction())) ble.send("TITLE:KRUPAS TEST MP3");
        if(intent!=null && ACTION_TEST_PHONE.equals(intent.getAction())) {
            testPhoneUntil=System.currentTimeMillis()+6000;
            phoneDisplayed=false; // force test message even after an earlier test
            diag("phone_test","TEST 6 SEKUND; PHONE:OFF konczy test");
        }
        if(hfp!=null)hfp.start(); // retry HFP registration after BLE permission granted
        return START_STICKY;
    }
    @Override public void onDestroy(){
        h.removeCallbacks(loop);
        if(registered)unregisterReceiver(musicReceiver);
        if(wakeRegistered)unregisterReceiver(wakeReceiver);
        if(phone!=null)phone.stop();
        if(hfp!=null)hfp.stop();
        if(qfProbe!=null)qfProbe.stop();
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
    private static String elapsedReadable(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        return seconds < 60L ? seconds + " s" :
                (seconds / 60L) + " min " + (seconds % 60L) + " s";
    }
    private void refresh(){
        if (ble == null) return;
        ble.watchdog();
        AccessHealth.summary(this);
        AccessHealth.recoverNotificationListener(this);
        if(qfProbe!=null)qfProbe.sample();
        final long now = System.currentTimeMillis();
        final SharedPreferences radio = getSharedPreferences("krupas_radio", 0);
        final String freq = SourceRouter.clean(radio.getString("freq", ""));
        String station = SourceRouter.cleanRds(radio.getString("station", ""));
        final String screenRds = SourceRouter.cleanRds(radio.getString("rds", ""));
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
        // A fresh, visible LOCAL MP3 title wins over delayed legacy broadcasts.
        // YouTube, MP4, DAB+ and FM retain the exact previous routing.
        MediaResult localMp3 = fallbackVisibleMp3(now, radioSeen);
        if (media.title.isEmpty() && !media.radio && !media.dab &&
                SourceRouter.isLocalMp3Package(localMp3.pkg) && !localMp3.title.isEmpty())
            media = localMp3;
        if (media.title.isEmpty() && !media.radio && !media.dab) media = fallbackLegacy(now);
        if (media.title.isEmpty() && !media.radio && !media.dab) media = localMp3;
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
        diag("rds_status", "station=" + station + " / rds=" + screenRds +
                " / active=" + rdsCurrent + " / age=" + (rdsSeen == 0 ? -1 : now - rdsSeen));
        diag("media_status", media.pkg + " / " + media.title + " / " + media.state);
        diag("radio_status", radio.getString("pkg", "") + " / " + freq +
                " / " + station + " / age=" + (radioSeen == 0 ? -1 : now - radioSeen) +
                "ms / visible=" + radioVisible);

        // An actually PLAYING media session is more reliable than a remembered
        // FM label. Visible live radio wins over legacy/notification fallbacks.
        boolean playingSession = "PLAYING".equals(media.state) && !media.radio;
        boolean preferDab = MediaRoutePolicy.preferDab(media.dab, !media.title.isEmpty(),
                media.state, radioLive, radioVisible);
        boolean preferRadio = !preferDab && radioLive && !playingSession;
        // When NavRadio+ provides a custom station label and live RDS, show
        // the label for five seconds and the RDS text for five seconds.
        // Keep the existing BLE RDS: protocol and the unchanged FM frequency line.
        String radioText;
        if (preferRadio) {
            radioText = stationRdsRotator.select(now, freq, station,
                    rdsCurrent ? screenRds : "");
        } else {
            stationRdsRotator.reset();
            radioText = rdsCurrent ? screenRds : station;
        }
        diag("dab_status", (preferDab ? "AKTYWNE" : "NIEAKTYWNE") + " / " +
                media.pkg + " / " + (media.dab ? media.title : "") + " / " + media.state);
        diag("route", preferDab ? "DAB+ / " + media.state :
                preferRadio ? "RADIO / " + media.state :
                !media.title.isEmpty() ? (SourceRouter.isYouTubePackage(media.pkg) ? "YOUTUBE" : "MP3 / MEDIA") : "BRAK AKTYWNEGO ZRODLA");

        // Missing OEM Bluetooth window is not evidence of a hangup.
        boolean hfpActive = hfp != null && hfp.active();
        boolean localCall = phone != null && phone.isActive();
        SharedPreferences phoneUi = getSharedPreferences("krupas_phone_ui",0);
        long uiSeen = phoneUi.getLong("seen",0);
        long positiveSeen = phoneUi.getLong("active_positive_seen",0);
        boolean uiCall = (phoneUi.getBoolean("active",false) &&
                uiSeen>0 && now>=uiSeen && now-uiSeen<=8000) ||
                (positiveSeen>0 && now>=positiveSeen && now-positiveSeen<=4000);
        long windowSeen = phoneUi.getLong("window_seen",0);
        boolean phoneWindow = phoneUi.getBoolean("phone_window",false) &&
                windowSeen>0 && now>=windowSeen && now-windowSeen<=7000;
        long hintSeen = phoneUi.getLong("caller_positive_seen",0);
        boolean callerEvidence = hintSeen>0 && now>=hintSeen && now-hintSeen<=4000;
        CallAudioMonitor.Snapshot audio = callAudio==null?null:callAudio.snapshot();
        boolean audioCandidate = audio!=null && CallAudioRules.likelyCall(
                audio.mode,audio.sco,audio.voicePlayback,phoneWindow);
        MediaProbeNotificationService.CallSnapshot notificationCall =
                MediaProbeNotificationService.activeCall(this);
        boolean callConfirmed = hfpActive || localCall || uiCall ||
                audioCandidate || callerEvidence || notificationCall.active;
        diag("call_notification",notificationCall.active?
                "AKTYWNE / pkg="+notificationCall.pkg+" / numer="+notificationCall.hasNumber:
                "BRAK");
        // End signals are explicit: HFP TERMINATED, telephony IDLE transition or UI ended.
        long endAt = Math.max(phoneUi.getLong("ended_seen",0),
                Math.max(hfp==null?0:hfp.endedAt(),phone==null?0:phone.endedAt()));
        boolean ongoingCall = callTracker.update(now,callConfirmed,endAt);
        String evidence = hfpActive ? "HFP" : localCall ? "TELEFONIA" :
                uiCall ? "OKNO ROZMOWY" : audioCandidate ? "AUDIO BT" :
                callerEvidence ? "OKNO BT + NUMER" :
                notificationCall.active ? "POWIADOMIENIE ROZMOWY" : "BRAK";
        diag("audio_status",(audio==null?"NIEDOSTEPNE":audio.status())+
                " / oknoBT="+phoneWindow+" / probka="+audioCandidate+
                " / rozmowa="+ongoingCall);
        diag("call_decision","ROZMOWA="+ongoingCall+
                " / zrodlo="+evidence+" / oknoBT="+phoneWindow+
                " / BLE="+ble.ready()+
                " / "+callTracker.reason()+
                (ongoingCall ? " / ostatnie potwierdzenie "+
                    elapsedReadable(now-callTracker.lastEvidenceAt())+" temu":""));
        if(ongoingCall)testPhoneUntil=0;
        boolean testCall=!ongoingCall && now<testPhoneUntil;
        // Phone overlays are emitted only on confirmed call evidence. ESP
        // renders the existing T| marker from a PHONE: payload.
        if(ongoingCall||testCall){
            String phase=CallDisplayPolicy.select(
                    hfpActive, hfpActive?hfp.phase():"",
                    uiCall, uiCall?phoneUi.getString("phase",""):"",
                    phone!=null&&phone.isRinging(),
                    notificationCall.active,notificationCall.phase);
            String number=PhoneUiDetector.firstNumber(
                    hfpActive?hfp.display():"",
                    uiCall?phoneUi.getString("display",""):"",
                    callerEvidence?phoneUi.getString("caller_hint",""):"",
                    notificationCall.active?notificationCall.display:"");
            // Never show a number during ringing/dialing, and never reuse
            // a number from an earlier call as the new caller's identity.
            String display=CallDisplayPolicy.display(phase,number,testCall);
            diag("call_phase",(testCall?"TEST":phase)+
                    " / numer_dostepny="+(!number.isEmpty()));
            diag("route",testCall?"TELEFON / TEST":
                    "TELEFON / "+(callConfirmed?evidence:"PODTRZYMANIE"));
            if(ble.ready()&&(!phoneDisplayed||!display.equals(lastPhoneText)||
                    now-lastPhoneSent>=10000)){
                ble.send("PHONE:"+display);
                lastPhoneText=display;
                phoneDisplayed=true;
                lastPhoneSent=now;
            }
            return;
        }
        if(phoneDisplayed || phoneClearPending){
            // PHONE:OFF must also be sent once on each BLE reconnect. Otherwise
            // an ESP retaining callActive=true ignores FM/MP3 test/source data.
            if(ble.ready()){
                ble.send("PHONE:OFF");
                phoneClearPending=false;
            }
            phoneDisplayed=false;
            lastPhoneSent=0;
            lastPhoneText="";
            sourceAnnouncement.reset();
            oldMedia=oldMediaSource=oldFreq=oldStation=oldDab="";
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
            ble.send("DAB:STOP");
            ble.send("MEDIA:STOP");
            oldDab=""; sentDab=0;
            oldMedia=""; oldMediaSource=""; sentMedia=0;
        }
        String outgoingSource = preferDab ? (nativeDab ? "DAB" : "MP3") :
                preferRadio ? "FM" : !media.title.isEmpty() ?
                (SourceRouter.isYouTubePackage(media.pkg) ? "YT" : "MP3") : "OFF";
        java.util.List<String> sourceCommands = sourceAnnouncement.commands(
                outgoingSource, SystemClock.elapsedRealtime());
        for (String command : sourceCommands) ble.send(command);
        // CLEAR:ALL invalidates ESP caches, so resend all active metadata too.
        if (sourceCommands.contains("CLEAR:ALL")) {
            oldMedia=oldMediaSource=oldFreq=oldStation=oldDab="";
            sentMedia=sentRadio=sentStation=sentDab=0;
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
                oldDab=media.title; oldMedia=media.title; oldMediaSource="DAB"; sentDab=sentMedia=now;
            }
        }
        else if (preferRadio) {
            if (!oldMedia.isEmpty()) {
                ble.send("MEDIA:STOP");
                oldMedia = "";
                oldMediaSource = "";
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
            // ESP understands YT:, while plain TITLE: may keep the old R| tag.
            final String nextSource = SourceRouter.isYouTubePackage(media.pkg) ? "YT" : "MP3";
            if (!media.title.equals(oldMedia) || !nextSource.equals(oldMediaSource) || now - sentMedia >= 10000) {
                ble.send(("YT".equals(nextSource) ? "YT:" : "TITLE:") + media.title);
                oldMedia = media.title;
                oldMediaSource = nextSource;
                sentMedia = now;
            }
        }
        else {
            // Stop immediately once all live music sources have disappeared.
            if (!oldMedia.isEmpty()) {
                ble.send("MEDIA:STOP");
                oldMedia = "";
                oldMediaSource = "";
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
            if(sessions==null) {
                diag("session_error","BRAK LISTY SESJI");
                return best;
            }
            // The previous failure must not remain visible after successful access.
            diag("session_error","OK");
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


