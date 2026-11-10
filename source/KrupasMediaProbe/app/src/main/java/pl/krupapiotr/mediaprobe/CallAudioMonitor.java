package pl.krupapiotr.mediaprobe;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Build;
import java.util.List;

/**
 * Read-only audio-policy fallback for K706 OEM hands-free.
 * Does not request audio focus, capture microphone data or change routes.
 */
final class CallAudioMonitor {
    private final AudioManager audio;

    CallAudioMonitor(Context context) {
        audio=(AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
    }

    Snapshot snapshot() {
        if(audio==null)return new Snapshot(-1,false,false,"BRAK AUDIO MANAGER");
        int mode=-1;
        boolean sco=false,voice=false;
        String error="";
        try {
            mode=audio.getMode();
            sco=audio.isBluetoothScoOn();
            if(Build.VERSION.SDK_INT>=26) {
                List<AudioPlaybackConfiguration> players=audio.getActivePlaybackConfigurations();
                if(players!=null) for(AudioPlaybackConfiguration p:players) {
                    if(p==null || p.getAudioAttributes()==null)continue;
                    int usage=p.getAudioAttributes().getUsage();
                    if(p.getPlayerState()==AudioPlaybackConfiguration.PLAYER_STATE_STARTED &&
                            (usage==AudioAttributes.USAGE_VOICE_COMMUNICATION ||
                             usage==AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING))
                        voice=true;
                }
            }
        }catch(SecurityException ex){error="BRAK DOSTEPU AUDIO";}
        catch(RuntimeException ex){error="AUDIO "+ex.getClass().getSimpleName();}
        return new Snapshot(mode,sco,voice,error);
    }

    static final class Snapshot {
        final int mode;
        final boolean sco,voicePlayback;
        final String error;
        Snapshot(int mode,boolean sco,boolean voicePlayback,String error) {
            this.mode=mode;this.sco=sco;this.voicePlayback=voicePlayback;this.error=error;
        }
        String status(){
            String name=mode==AudioManager.MODE_IN_CALL?"IN_CALL":
                    mode==AudioManager.MODE_IN_COMMUNICATION?"IN_COMMUNICATION":
                    mode==AudioManager.MODE_NORMAL?"NORMAL":String.valueOf(mode);
            return "tryb="+name+" / SCO="+sco+" / VOICE="+voicePlayback+
                    (error.isEmpty()?"":" / "+error);
        }
    }
}
