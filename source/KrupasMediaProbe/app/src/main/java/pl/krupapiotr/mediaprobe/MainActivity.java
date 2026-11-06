package pl.krupapiotr.mediaprobe;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.graphics.Typeface;

public final class MainActivity extends Activity {
    private final Handler h=new Handler(Looper.getMainLooper());
    private TextView status;
    private final Runnable tick=new Runnable(){@Override public void run(){refresh();h.postDelayed(this,1300);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setPadding(24,12,24,12);root.setOrientation(LinearLayout.VERTICAL);
        TextView heading=new TextView(this);heading.setText("KRUPAS MEDIA PRO " + getAppVersion() + " • K706");
        heading.setTextSize(24);heading.setTypeface(Typeface.DEFAULT_BOLD);root.addView(heading);
        TextView hint=new TextView(this);hint.setText("FM / MP3 / DAB-Z: powiadomienia. DAB+: przygotowany protokół DAB dla przyszłego firmware ESP.");
        hint.setTextSize(18);root.addView(hint);
        CheckBox dabNative=new CheckBox(this);
        dabNative.setText("DAB+ D| (włącz dopiero po aktualizacji ESP; teraz M|)");
        dabNative.setTextSize(16);
        dabNative.setChecked(getSharedPreferences("krupas_settings",0)
                .getBoolean("dab_native_protocol",false));
        dabNative.setOnCheckedChangeListener((button,checked)->getSharedPreferences("krupas_settings",0)
                .edit().putBoolean("dab_native_protocol",checked).apply());
        root.addView(dabNative);
        HorizontalScrollView buttonsScroll=new HorizontalScrollView(this);
        buttonsScroll.setHorizontalScrollBarEnabled(true);
        buttonsScroll.setFillViewport(true);
        LinearLayout buttons=new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttonsScroll.addView(buttons);
        root.addView(buttonsScroll,new LinearLayout.LayoutParams(-1,-2));
        addButton(buttons,"Bluetooth",()->requestBle());
        addButton(buttons,"Powiadomienia",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        addButton(buttons,"Dostępność",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addButton(buttons,"Telefon",()->requestPhone());
        addButton(buttons,"Start BLE",()->startBridge(null));
        addButton(buttons,"Test FM",()->startBridge(MediaBridgeService.ACTION_TEST_FM));
        addButton(buttons,"Test MP3",()->startBridge(MediaBridgeService.ACTION_TEST_MP3));
        addButton(buttons,"Kopiuj",()->{
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clipboard!=null && status!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText("KRUPAS diagnostyka",status.getText()));
            android.widget.Toast.makeText(this,"Skopiowano diagnostykę",android.widget.Toast.LENGTH_SHORT).show();
        });
        ScrollView sv=new ScrollView(this);
        status=new TextView(this);status.setTextSize(18);status.setTypeface(Typeface.MONOSPACE);status.setTextIsSelectable(true);
        sv.addView(status);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        requestBle();startBridge(null);
        h.post(tick);
    }
    private String getAppVersion(){
        try {
            return getPackageManager().getPackageInfo(getPackageName(),0).versionName;
        } catch(Exception e) {
            return "wersja nieznana";
        }
    }
    private void addButton(LinearLayout layout,String title,Runnable f){Button b=new Button(this);b.setText(title);b.setTextSize(16);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(18,9,18,9);b.setAllCaps(false);b.setSingleLine(true);b.setOnClickListener(v->f.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMargins(0,0,8,0);layout.addView(b,lp);}
    private void startBridge(String action){
        try{Intent i=new Intent(this,MediaBridgeService.class);if(action!=null)i.setAction(action);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        }catch(Exception ex){getSharedPreferences("krupas_diag",0).edit().putString("error","start: "+ex).apply();}
    }
    private void requestPhone(){
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE},9);
        else startBridge(null);
    }
    private void requestBle(){
        if(Build.VERSION.SDK_INT>=31){
            String[] p={Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT};
            if(checkSelfPermission(p[0])!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(p[1])!=PackageManager.PERMISSION_GRANTED)requestPermissions(p,8);
        }else if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},8);
        }
    }
    @Override public void onRequestPermissionsResult(int rc,String[] perms,int[] grants){super.onRequestPermissionsResult(rc,perms,grants);startBridge(null);}
    private void refresh(){
        if(status==null)return;
        android.content.SharedPreferences d=getSharedPreferences("krupas_diag",0);
        android.content.SharedPreferences r=getSharedPreferences("krupas_radio",0);
        String notificationListeners=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        String accessibility=Settings.Secure.getString(getContentResolver(),"enabled_accessibility_services");
        status.setText("WERSJA: "+getAppVersion()+" / pl.krupapiotr.mediaprobe\n"+
                "BLE: "+d.getString("ble","BRAK")+"\n"+
                "OSTATNIA TRANSMISJA: "+d.getString("last_tx","BRAK")+"\n"+
                "ŹRÓDŁO: "+d.getString("route","BRAK")+"\n"+
                "MP3: "+d.getString("media_status","BRAK")+"\n"+
                "TELEFON: "+d.getString("phone_status","BRAK")+"\n"+
                "RADIO: "+d.getString("radio_status","BRAK")+"\n"+
                "DAB+: "+d.getString("dab_status","BRAK")+"\n"+
                "RDS DYNAMICZNY: "+d.getString("rds_status","BRAK")+"\n"+
                "NOTYFIKACJE: "+(notificationListeners!=null&&notificationListeners.contains(getPackageName())?"OK":"BRAK UPRAWNIEŃ")+"\n"+
                "DOSTĘPNOŚĆ RADIO: "+(accessibility!=null&&accessibility.contains(getPackageName()+"/"+RadioAccessibilityService.class.getName())?"OK":"SPRAWDŹ")+"\n\n"+
                "OSTATNI EKRAN FM: "+r.getString("nodes","brak")+"\n\n"+
                "OSTATNIE MP3 Z POWIADOMIENIA: "+getSharedPreferences("krupas_mp3_notification",0).getString("title","brak")+"\n"+
                "MP3 Z WIDOKU: "+getSharedPreferences("krupas_mp3_ui",0).getString("title","brak")+"\n\n"+
                "SESJE: "+d.getString("sessions","—")+"\n"+
                "BLOKADA TODO: "+d.getString("blocked_tx","—")+"\n"+
                "DIAG SESJI: "+d.getString("session_error","—")+"\n"+
                "BŁĄD: "+d.getString("error","—"));
    }
    @Override protected void onDestroy(){h.removeCallbacks(tick);super.onDestroy();}
}

