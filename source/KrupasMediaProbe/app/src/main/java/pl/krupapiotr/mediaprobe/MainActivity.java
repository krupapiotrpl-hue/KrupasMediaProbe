package pl.krupapiotr.mediaprobe;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Typeface;

public final class MainActivity extends Activity {
    private final Handler h=new Handler(Looper.getMainLooper());
    private TextView status;
    private final Runnable tick=new Runnable(){@Override public void run(){refresh();h.postDelayed(this,1300);}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setPadding(24,12,24,12);root.setOrientation(LinearLayout.VERTICAL);
        TextView heading=new TextView(this);heading.setText("KRUPAS MEDIA PRO 1.2 TEST • K706");
        heading.setTextSize(22);heading.setTypeface(Typeface.DEFAULT_BOLD);root.addView(heading);
        TextView hint=new TextView(this);hint.setText("Automatyczne BLE → ESP32-S3. FM/RDS zachowuje stan przez krótkie nakładki, multimedia wygaszają stare dane.");
        hint.setTextSize(14);root.addView(hint);
        addButton(root,"1. UPRAWNIENIA BLUETOOTH",()->requestBle());
        addButton(root,"2. DOSTĘP DO POWIADOMIEŃ (MP3)",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        addButton(root,"3. DOSTĘPNOŚĆ (ORYGINALNE FM/RDS)",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addButton(root,"START / WZNÓW AUTOMATYCZNE BLE",()->startBridge(null));
        addButton(root,"TEST FM 107.0",()->startBridge(MediaBridgeService.ACTION_TEST_FM));
        addButton(root,"TEST MP3",()->startBridge(MediaBridgeService.ACTION_TEST_MP3));
        ScrollView sv=new ScrollView(this);
        status=new TextView(this);status.setTextSize(14);status.setTypeface(Typeface.MONOSPACE);status.setTextIsSelectable(true);
        sv.addView(status);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        requestBle();
        startBridge(null);
        MediaProbeNotificationService.ensureBound(this);
        h.post(tick);
    }

    private void addButton(LinearLayout layout,String title,Runnable f){
        Button b=new Button(this);b.setText(title);b.setTextSize(13);b.setAllCaps(false);b.setOnClickListener(v->f.run());layout.addView(b);
    }

    private void startBridge(String action){
        try{
            Intent i=new Intent(this,MediaBridgeService.class);
            if(action!=null)i.setAction(action);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        }catch(Exception ex){
            getSharedPreferences("krupas_diag",0).edit().putString("error","start: "+ex).apply();
        }
    }

    private void requestBle(){
        if(Build.VERSION.SDK_INT>=31){
            String[] p={Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT};
            if(checkSelfPermission(p[0])!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(p[1])!=PackageManager.PERMISSION_GRANTED)requestPermissions(p,8);
        }else if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},8);
        }
    }

    @Override public void onRequestPermissionsResult(int rc,String[] perms,int[] grants){
        super.onRequestPermissionsResult(rc,perms,grants);startBridge(null);
    }

    private void refresh(){
        if(status==null)return;
        android.content.SharedPreferences d=getSharedPreferences("krupas_diag",0);
        android.content.SharedPreferences r=getSharedPreferences("krupas_radio",0);
        android.content.SharedPreferences n=getSharedPreferences("krupas_mp3_notification",0);
        boolean notificationAccess=MediaProbeNotificationService.isAccessEnabled(this);
        boolean listenerConnected=MediaProbeNotificationService.isListenerConnected(this);
        String accessibility=Settings.Secure.getString(getContentResolver(),"enabled_accessibility_services");
        status.setText("BLE: "+d.getString("ble","BRAK")+"\n"+
                "OSTATNIA TRANSMISJA: "+d.getString("last_tx","BRAK")+"\n"+
                "ŹRÓDŁO: "+d.getString("route","BRAK")+"\n"+
                "MEDIA: "+d.getString("media_status","BRAK")+"\n"+
                "RADIO: "+d.getString("radio_status","BRAK")+"\n"+
                "RDS / RADIOTEXT: "+r.getString("station","—")+" / "+r.getString("radio_text","—")+"\n"+
                "NOTYFIKACJE: "+(notificationAccess?"OK":"BRAK UPRAWNIEŃ")+"\n"+
                "LISTENER MEDIA: "+(listenerConnected?"POŁĄCZONY":"NIEPOŁĄCZONY / RE-BIND")+"\n"+
                "DOSTĘPNOŚĆ RADIO: "+(accessibility!=null&&accessibility.contains(getPackageName()+"/"+RadioAccessibilityService.class.getName())?"OK":"SPRAWDŹ")+"\n\n"+
                "OSTATNI EKRAN FM: "+r.getString("nodes","brak")+"\n\n"+
                "POWIADOMIENIE MEDIA: "+n.getString("artist","")+" / "+n.getString("title","brak")+" / "+n.getString("album","")+"\n"+
                "MP3 Z WIDOKU: "+getSharedPreferences("krupas_mp3_ui",0).getString("title","brak")+"\n\n"+
                "DIAG SESJI: "+d.getString("session_error","—")+"\n"+
                "BŁĄD: "+d.getString("error","—"));
    }

    @Override protected void onResume(){
        super.onResume();
        MediaProbeNotificationService.ensureBound(this);
        startBridge(null);
    }

    @Override protected void onDestroy(){h.removeCallbacks(tick);super.onDestroy();}
}
