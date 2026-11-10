package pl.krupapiotr.mediaprobe;

/** No Android dependencies: call detection decision can be unit-tested. */
final class CallAudioRules {
    private CallAudioRules() {}

    static boolean likelyCall(int mode, boolean sco, boolean voicePlayback, boolean phoneWindow) {
        // AudioManager.MODE_IN_CALL = 2; MODE_IN_COMMUNICATION = 3.
        if(mode==2)return true;
        if(mode==3 && (sco || voicePlayback || phoneWindow))return true;
        // A bare SCO route can also be used by a voice assistant.
        return sco && phoneWindow;
    }
}
