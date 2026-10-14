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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final Handler h=new Handler(Looper.getMainLooper());
    private TextView status;
    private final Runnable tick=new Runnable(){@Override public void run(){refresh();h.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setPadding(16,8,16,8);root.setOrientation(LinearLayout.VERTICAL);
        TextView heading=new TextView(this);heading.setText("KRUPAS MEDIA PRO 2.1 • K706");
        heading.setTextSize(20);heading.setTypeface(Typeface.DEFAULT_BOLD);root.addView(heading);
        TextView hint=new TextView(this);hint.setText("AUTO: MediaSession + radio UI → BLE → ZAFIRA-DISPLAY. Po uruchomieniu nie trzeba wciskać START.");
        hint.setTextSize(13);root.addView(hint);

        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);
        addButton(row1,"Bluetooth",()->requestBle());
        addButton(row1,"Powiadomienia",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        addButton(row1,"Dostępność FM",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(row1);
        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);
        addButton(row2,"Wznów BLE",()->startBridge(null));
        addButton(row2,"Test FM",()->startBridge(MediaBridgeService.ACTION_TEST_FM));
        addButton(row2,"Test MP3",()->startBridge(MediaBridgeService.ACTION_TEST_MP3));
        root.addView(row2);

        ScrollView sv=new ScrollView(this);
        status=new TextView(this);status.setTextSize(13);status.setTypeface(Typeface.MONOSPACE);status.setTextIsSelectable(true);
        sv.addView(status);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        requestBle();startBridge(null);
        h.post(tick);
    }
    private void addButton(LinearLayout layout,String title,Runnable f){
        Button b=new Button(this);b.setText(title);b.setTextSize(11);b.setAllCaps(false);b.setOnClickListener(v->f.run());
        layout.addView(b,new LinearLayout.LayoutParams(0,-2,1));
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
    private static String time(long t){return t<=0?"—":new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(t));}
    private void refresh(){
        if(status==null)return;
        android.content.SharedPreferences d=getSharedPreferences("krupas_diag",0);
        android.content.SharedPreferences r=getSharedPreferences("krupas_radio",0);
        String notificationListeners=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        String accessibility=Settings.Secure.getString(getContentResolver(),"enabled_accessibility_services");
        status.setText("BLE: "+d.getString("ble","BRAK")+"\n"+
                "ŹRÓDŁO: "+d.getString("route","BRAK")+"\n"+
                "WYZWALACZ: "+d.getString("trigger","BRAK")+"\n"+
                "MP3: "+d.getString("media_status","BRAK")+"\n"+
                "RADIO: "+d.getString("radio_status","BRAK")+"\n"+
                "SESJE: "+d.getString("session_watch","BRAK")+"\n"+
                "OSTATNIA RAMKA: "+d.getString("last_frame","—")+" @ "+time(parseLong(d.getString("last_frame_time","0")))+"\n"+
                "BLE POTWIERDZIŁ: "+d.getString("ble_last_ok","—")+" @ "+time(d.getLong("ble_last_ok_time",0))+"\n"+
                "NOTYFIKACJE: "+(notificationListeners!=null&&notificationListeners.contains(getPackageName())?"OK":"BRAK UPRAWNIEŃ")+"\n"+
                "DOSTĘPNOŚĆ RADIO: "+(accessibility!=null&&accessibility.contains(getPackageName()+"/"+RadioAccessibilityService.class.getName())?"OK":"SPRAWDŹ")+"\n\n"+
                "OSTATNI EKRAN FM: "+r.getString("nodes","brak")+"\n\n"+
                "DIAG SESJI: "+d.getString("session_error","—")+"\n"+
                "BŁĄD: "+d.getString("error","—"));
    }
    private static long parseLong(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    @Override protected void onDestroy(){h.removeCallbacks(tick);super.onDestroy();}
}
