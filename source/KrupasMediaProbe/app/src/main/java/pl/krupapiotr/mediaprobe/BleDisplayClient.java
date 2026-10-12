package pl.krupapiotr.mediaprobe;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.UUID;

/** Self-healing BLE GATT client, independent of FM/MP3 routing. */
final class BleDisplayClient {
    private static final String NAME="ZAFIRA-DISPLAY";
    private static final UUID SERVICE=UUID.fromString("6fbd0001-7c40-4a16-93c5-6c0c70696f74");
    private static final UUID CHAR=UUID.fromString("6fbd0002-7c40-4a16-93c5-6c0c70696f74");
    private final Context context;
    private final Handler h=new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> queue=new ArrayDeque<>();
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic write;
    private boolean running, scanning, ready, busy;
    private long startedConnect;
    private int negotiatedMtu=23;
    private boolean servicesRequested;
    private int failures;
    private String status="STOP";
    private Runnable retryTask,timeoutTask;
    private final Runnable notifyReady;

    BleDisplayClient(Context c,Runnable onReady) {context=c.getApplicationContext();notifyReady=onReady;}
    String status(){return status;}
    boolean ready(){return ready;}

    private boolean has(String permission){return Build.VERSION.SDK_INT<23||context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    private boolean permitted(){
        if(Build.VERSION.SDK_INT>=31)return has(Manifest.permission.BLUETOOTH_SCAN)&&has(Manifest.permission.BLUETOOTH_CONNECT);
        return has(Manifest.permission.ACCESS_FINE_LOCATION);
    }
    private void log(String s){status=s;context.getSharedPreferences("krupas_diag",0).edit().putString("ble",s).apply();}
    void start(){ if(running)return; running=true; failures=0; attempt(); }
    void stop(){running=false; cancelTasks(); stopScan(); cleanup(); queue.clear(); log("STOP");}
    private void cancelTasks(){if(retryTask!=null)h.removeCallbacks(retryTask);if(timeoutTask!=null)h.removeCallbacks(timeoutTask);}
    private void attempt(){
        if(!running)return;
        cancelTasks();stopScan();cleanup();
        if(!permitted()){log("BRAK UPRAWNIEŃ BLE"); retry(10000);return;}
        BluetoothManager mgr=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter=mgr==null?null:mgr.getAdapter();
        if(adapter==null || !adapter.isEnabled()){log("BLUETOOTH WYŁĄCZONY"); retry(6000);return;}
        try{
            scanner=adapter.getBluetoothLeScanner();
            if(scanner==null){log("BRAK SKANERA BLE");retry(6000);return;}
            scanning=true;log("SZUKAM ZAFIRA-DISPLAY");
            scanner.startScan(scanCallback);
            timeoutTask=()->{if(scanning){stopScan();fail("SKANOWANIE - BRAK ESP");}};
            h.postDelayed(timeoutTask,12000);
        }catch(SecurityException ex){fail("BRAK UPRAWNIEŃ "+ex.getMessage());}
        catch(Exception ex){fail("SCAN: "+ex.getClass().getSimpleName());}
    }
    private void stopScan(){
        if(!scanning)return;
        scanning=false;
        try{if(scanner!=null)scanner.stopScan(scanCallback);}catch(Exception ignored){}
    }
    private final ScanCallback scanCallback=new ScanCallback(){
        @Override public void onScanResult(int callbackType,ScanResult result){
            if(!scanning||result==null)return;
            String n="";
            try{
                if(result.getScanRecord()!=null)n=result.getScanRecord().getDeviceName();
                if((n==null||n.isEmpty())&&result.getDevice()!=null)n=result.getDevice().getName();
            }catch(SecurityException ignored){}
            if(NAME.equalsIgnoreCase(n)){
                BluetoothDevice device=result.getDevice();
                stopScan();
                if(timeoutTask!=null)h.removeCallbacks(timeoutTask);
                connect(device);
            }
        }
        @Override public void onScanFailed(int errorCode){if(scanning){stopScan();fail("SCAN ERROR "+errorCode);}}
    };
    private void connect(BluetoothDevice device){
        if(!running)return;
        log("ŁĄCZĘ GATT");startedConnect=SystemClock.elapsedRealtime();servicesRequested=false;negotiatedMtu=23;
        try{
            gatt=device.connectGatt(context,false,gattCallback,BluetoothDevice.TRANSPORT_LE);
            if(gatt==null){fail("GATT NULL");return;}
            timeoutTask=()->{if(!ready)fail("TIMEOUT GATT");};
            h.postDelayed(timeoutTask,12000);
        }catch(Exception ex){fail("GATT: "+ex.getClass().getSimpleName());}
    }
    private final BluetoothGattCallback gattCallback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt source,int status,int newState){
            h.post(()->{
                if(source!=gatt){safeClose(source);return;}
                if(!running){cleanup();return;}
                if(status!=BluetoothGatt.GATT_SUCCESS){fail("GATT STATUS "+status);return;}
                if(newState==BluetoothProfile.STATE_CONNECTED){
                    log("GATT POŁĄCZONY - NEGOCJACJA MTU");
                    try {
                        if(!source.requestMtu(185)){discover(source);}
                        else h.postDelayed(()->{if(source==gatt&&!servicesRequested)discover(source);},2000);
                    } catch(Exception ex){discover(source);}
                }else if(newState==BluetoothProfile.STATE_DISCONNECTED){fail("GATT ROZŁĄCZONY");}
            });
        }
        @Override public void onMtuChanged(BluetoothGatt source,int mtu,int status){ h.post(()->{
            if(source!=gatt||!running)return;
            if(status==BluetoothGatt.GATT_SUCCESS)negotiatedMtu=mtu;
            discover(source);
        });}
        @Override public void onServicesDiscovered(BluetoothGatt source,int status){h.post(()->{
            if(source!=gatt||!running)return;
            if(status!=BluetoothGatt.GATT_SUCCESS){fail("DISCOVERY "+status);return;}
            BluetoothGattService s=source.getService(SERVICE);
            write=s==null?null:s.getCharacteristic(CHAR);
            if(write==null){fail("BRAK CHAR WRITE - SPRAWDŹ UUID ESP");return;}
            if(timeoutTask!=null)h.removeCallbacks(timeoutTask);
            failures=0;ready=true;busy=false;queue.clear();
            log("BLE OK ZAFIRA-DISPLAY"); notifyReady.run();
        });}
        @Override public void onCharacteristicWrite(BluetoothGatt source,BluetoothGattCharacteristic ch,int status){h.post(()->{
            if(source!=gatt)return;
            busy=false;
            if(status==BluetoothGatt.GATT_SUCCESS){if(!queue.isEmpty())queue.pollFirst();sendNext();}
            else fail("BLE WRITE STATUS "+status);
        });}
    };
    private void discover(BluetoothGatt source){
        if(servicesRequested||source!=gatt)return;
        servicesRequested=true;
        log("ODKRYWAM USŁUGI (MTU="+negotiatedMtu+")");
        try {if(!source.discoverServices())fail("DISCOVER FALSE");}
        catch(Exception ex){fail("DISCOVER ERROR");}
    }
    void send(String s){
        if(s==null || s.isEmpty())return;
        h.post(()->{
            if(!ready)return;
            if(queue.size()>=20)queue.pollFirst();
            queue.addLast(s);
            sendNext();
        });
    }
    private void sendNext(){
        if(!ready||busy||queue.isEmpty()||gatt==null||write==null)return;
        try{
            byte[] bytes=limitUtf8(queue.peekFirst(),Math.min(120,Math.max(20,negotiatedMtu-3)));
            busy=true;lastWrite=SystemClock.elapsedRealtime();
            boolean ok;
            if(Build.VERSION.SDK_INT>=33){
                ok=gatt.writeCharacteristic(write,bytes,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothGatt.GATT_SUCCESS;
            }else{
                write.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                write.setValue(bytes);
                ok=gatt.writeCharacteristic(write);
            }
            if(!ok){busy=false;fail("BLE WRITE FALSE");}
        }catch(Exception ex){busy=false;fail("BLE WRITE "+ex.getClass().getSimpleName());}
    }
    private static byte[] limitUtf8(String text,int limit){
        byte[] raw=text.getBytes(StandardCharsets.UTF_8);
        if(raw.length<=limit)return raw;
        StringBuilder result=new StringBuilder();
        int length=0;
        for(int i=0;i<text.length();){
            int cp=text.codePointAt(i);
            byte[] b=new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
            if(length+b.length>limit)break;
            result.appendCodePoint(cp);length+=b.length;i+=Character.charCount(cp);
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }
    void watchdog(){
        if(ready&&busy && SystemClock.elapsedRealtime()-startedConnect>15000) {
            // Guard only an actual stuck write: timestamps updated on enqueue below.
            if(lastWrite>0 && SystemClock.elapsedRealtime()-lastWrite>10000)fail("TIMEOUT BLE WRITE");
        }
    }
    private long lastWrite;
    private void fail(String reason){log(reason);stopScan();cleanup();failures++;retry(Math.min(30000,2000*(1<<Math.min(4,failures-1))));}
    private void retry(int delay){ if(!running)return; if(retryTask!=null)h.removeCallbacks(retryTask);retryTask=this::attempt;h.postDelayed(retryTask,delay);}
    private void cleanup(){
        ready=false;busy=false;write=null;queue.clear();
        BluetoothGatt old=gatt;gatt=null;
        safeClose(old);
    }
    private static void safeClose(BluetoothGatt a){if(a!=null){try{a.disconnect();}catch(Exception ignored){}try{a.close();}catch(Exception ignored){}}}
}
