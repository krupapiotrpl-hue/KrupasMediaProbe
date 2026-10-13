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
        LinearLayout root=new LinearLayout(this);root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setOrientation(LinearLayout.VERTICAL);
        TextView heading=new TextView(this);heading.setText("KRUPAS MEDIA PRO 2.0 TEST • K706");
        heading.setTextSize(20);heading.setTypeface(Typeface.DEFAULT_BOLD);root.addView(heading);
        TextView hint=new TextView(this);hint.setText("Automatyczne BLE → ESP32-S3, FM / RDS / MP3. Dostęp FM: Dostępność. MP3: Powiadomienia.");
        hint.setTextSize(12);root.addView(hint);
        // Compact landscape layout: all controls are arranged horizontally, in two rows of three.
        LinearLayout row1=buttonRow(root);
        addButton(row1,"Bluetooth",()->requestBle());
        addButton(row1,"Powiadomienia",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        addButton(row1,"Dostęp FM",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        LinearLayout row2=buttonRow(root);
        addButton(row2,"Start BLE",()->startBridge(null));
        addButton(row2,"Test FM",()->startBridge(MediaBridgeService.ACTION_TEST_FM));
        addButton(row2,"Test MP3",()->startBridge(MediaBridgeService.ACTION_TEST_MP3));
        ScrollView sv=new ScrollView(this);
        status=new TextView(this);status.setTextSize(14);status.setTypeface(Typeface.MONOSPACE);status.setTextIsSelectable(true);
        sv.addView(status);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        requestBle();startBridge(null);
        h.post(tick);
    }
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    private LinearLayout buttonRow(LinearLayout root){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=dp(3);
        root.addView(row,params);
        return row;
    }
    private void addButton(LinearLayout row,String title,Runnable f){
        Button b=new Button(this);
        b.setText(title);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setSingleLine(true);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(3),0,dp(3),0);
        b.setOnClickListener(v->f.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(44),1f);
        params.leftMargin=dp(2);
        params.rightMargin=dp(2);
        row.addView(b,params);
    }
    private void startBridge(String action){
        try{Intent i=new Intent(this,MediaBridgeService.class);if(action!=null)i.setAction(action);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        }catch(Exception ex){getSharedPreferences("krupas_diag",0).edit().putString("error","start: "+ex).apply();}
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
        status.setText("BLE: "+d.getString("ble","BRAK")+"\n"+
                "OSTATNIA TRANSMISJA: "+d.getString("last_tx","BRAK")+"\n"+
                "ŹRÓDŁO: "+d.getString("route","BRAK")+"\n"+
                "MP3: "+d.getString("media_status","BRAK")+"\n"+
                "RADIO: "+d.getString("radio_status","BRAK")+"\n"+
                "NOTYFIKACJE: "+(notificationListeners!=null&&notificationListeners.contains(getPackageName())?"OK":"BRAK UPRAWNIEŃ")+"\n"+
                "DOSTĘPNOŚĆ RADIO: "+(accessibility!=null&&accessibility.contains(getPackageName()+"/"+RadioAccessibilityService.class.getName())?"OK":"SPRAWDŹ")+"\n\n"+
                "OSTATNI EKRAN FM: "+r.getString("nodes","brak")+"\n\n"+
                "OSTATNIE MP3 Z POWIADOMIENIA: "+getSharedPreferences("krupas_mp3_notification",0).getString("title","brak")+"\n"+
                "MP3 Z WIDOKU: "+getSharedPreferences("krupas_mp3_ui",0).getString("title","brak")+"\n\n"+
                "DIAG SESJI: "+d.getString("session_error","—")+"\n"+
                "BŁĄD: "+d.getString("error","—"));
    }
    @Override protected void onDestroy(){h.removeCallbacks(tick);super.onDestroy();}
}
