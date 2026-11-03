package pl.krupapiotr.mediaprobe;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

/**
 * Isolated read-only telephone status observer.
 * READ_PHONE_STATE does not grant outgoing number access.
 * This code never accesses call logs or contacts.
 */
final class PhoneCallMonitor {
    private final Context context;
    private final TelephonyManager telephony;
    private PhoneStateListener listener;
    private volatile int state=TelephonyManager.CALL_STATE_IDLE;
    private volatile boolean authorized;

    PhoneCallMonitor(Context context) {
        this.context=context.getApplicationContext();
        telephony=(TelephonyManager)this.context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    void start() {
        authorized=Build.VERSION.SDK_INT<23 ||
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED;
        if(!authorized || telephony==null) {
            log("BRAK UPRAWNIENIA READ_PHONE_STATE");
            return;
        }
        if(listener!=null)return;
        try {
            listener=new PhoneStateListener() {
                @Override public void onCallStateChanged(int newState,String incomingNumber) {
                    state=newState;
                    // Do not trust incomingNumber without the restricted call-log permission.
                    log(status());
                }
            };
            telephony.listen(listener,PhoneStateListener.LISTEN_CALL_STATE);
            state=telephony.getCallState();
            log(status());
        } catch(SecurityException ex) {
            authorized=false;listener=null;log("BRAK DOSTEPU DO STANU ROZMOWY");
        } catch(Exception ex) {
            listener=null;log("BLAD TELEFONII "+ex.getClass().getSimpleName());
        }
    }

    void stop() {
        if(listener!=null && telephony!=null) {
            try {telephony.listen(listener,PhoneStateListener.LISTEN_NONE);} catch(Exception ignored){}
        }
        listener=null;
    }

    boolean isActive() {
        if(listener==null)start(); // permission may have been granted while service runs
        return authorized && (state==TelephonyManager.CALL_STATE_RINGING ||
                              state==TelephonyManager.CALL_STATE_OFFHOOK);
    }
    String status() {
        if(!authorized)return "BRAK UPRAWNIEN";
        if(state==TelephonyManager.CALL_STATE_RINGING)return "DZWONI";
        if(state==TelephonyManager.CALL_STATE_OFFHOOK)return "ROZMOWA / WYBIERANIE";
        return "BRAK ROZMOWY";
    }
    String display() {
        return state==TelephonyManager.CALL_STATE_RINGING ? "Polaczenie" : "Rozmowa";
    }
    private void log(String value) {
        context.getSharedPreferences("krupas_diag",0).edit().putString("phone_status",value).apply();
    }
}
