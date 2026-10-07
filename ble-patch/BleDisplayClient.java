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
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class BleDisplayClient {
    private static final String DEVICE_NAME = "ZAFIRA-DISPLAY";
    private static final UUID SERVICE_UUID = UUID.fromString("6fbd0001-7c40-4a16-93c5-6c0c70696f74");
    private static final UUID RX_UUID = UUID.fromString("6fbd0002-7c40-4a16-93c5-6c0c70696f74");

    private static BleDisplayClient INSTANCE;

    static synchronized BleDisplayClient get(Context context) {
        if (INSTANCE == null) INSTANCE = new BleDisplayClient(context.getApplicationContext());
        return INSTANCE;
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter;

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic rx;
    private boolean scanning;
    private boolean connected;
    private String pending = "";
    private String lastSent = "";

    private BleDisplayClient(Context context) {
        this.context = context;
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
    }

    synchronized boolean isConnected() {
        return connected && gatt != null && rx != null;
    }

    synchronized String getPendingText() {
        return pending;
    }

    void start() {
        if (!hasPermissions() || adapter == null || !adapter.isEnabled() || scanning || gatt != null) return;
        try {
            scanning = adapter.startLeScan(scanCallback);
            if (scanning) {
                handler.postDelayed(() -> {
                    stopScan();
                    if (gatt == null) handler.postDelayed(this::start, 2500);
                }, 8000);
            }
        } catch (Throwable ignored) {}
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
        stopScan();
        if (gatt != null) {
            try { gatt.disconnect(); } catch (Throwable ignored) {}
            try { gatt.close(); } catch (Throwable ignored) {}
        }
        gatt = null;
        rx = null;
        connected = false;
        lastSent = "";
    }

    void sendText(String text) {
        text = clean(text);
        if (text.isEmpty()) return;

        synchronized (this) {
            pending = text;
        }

        if (rx != null && gatt != null) {
            writePending();
        } else {
            start();
        }
    }

    private void stopScan() {
        if (!scanning || adapter == null) {
            scanning = false;
            return;
        }
        try { adapter.stopLeScan(scanCallback); } catch (Throwable ignored) {}
        scanning = false;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected = true;
                lastSent = "";
                boolean mtuRequested = false;
                if (Build.VERSION.SDK_INT >= 21) {
                    try { mtuRequested = g.requestMtu(185); } catch (Throwable ignored) {}
                }
                if (!mtuRequested) {
                    try { g.discoverServices(); } catch (Throwable ignored) {}
                }
            } else {
                connected = false;
                rx = null;
                lastSent = "";
                try { g.close(); } catch (Throwable ignored) {}
                if (gatt == g) gatt = null;
                handler.postDelayed(BleDisplayClient.this::start, 1500);
            }
        }

        @Override public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            try { g.discoverServices(); } catch (Throwable ignored) {}
        }

        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                reconnect(g);
                return;
            }

            BluetoothGattService service = g.getService(SERVICE_UUID);
            if (service == null) {
                reconnect(g);
                return;
            }

            rx = service.getCharacteristic(RX_UUID);
            if (rx == null) {
                reconnect(g);
                return;
            }

            rx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            lastSent = "";
            writePending();
        }
    };

    private void reconnect(BluetoothGatt g) {
        connected = false;
        rx = null;
        lastSent = "";
        try { g.disconnect(); } catch (Throwable ignored) {}
        try { g.close(); } catch (Throwable ignored) {}
        if (gatt == g) gatt = null;
        handler.postDelayed(this::start, 1500);
    }

    private final BluetoothAdapter.LeScanCallback scanCallback = (device, rssi, scanRecord) -> {
        if (device == null || !hasPermissions()) return;

        String name = null;
        try { name = device.getName(); } catch (Throwable ignored) {}
        if (!DEVICE_NAME.equals(name)) return;

        stopScan();

        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (Throwable ex) {
            gatt = null;
            connected = false;
            handler.postDelayed(this::start, 1500);
        }
    };

    private void writePending() {
        if (!hasPermissions() || gatt == null || rx == null) return;

        final String value;
        synchronized (this) {
            value = pending;
        }

        if (value.isEmpty() || value.equals(lastSent)) return;

        try {
            rx.setValue(value.getBytes(StandardCharsets.UTF_8));
            if (gatt.writeCharacteristic(rx)) {
                lastSent = value;
            }
        } catch (Throwable ignored) {}
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private static String clean(String text) {
        if (text == null) return "";

        StringBuilder out = new StringBuilder();

        for (int i = 0; i < text.length() && out.length() < 96; i++) {
            char c = text.charAt(i);

            if (c == '\n' || c == '\r' || c == '\t') {
                c = ' ';
            }

            if (!Character.isISOControl(c)) {
                out.append(c);
            }
        }

        String result = out.toString().trim();

        while (result.contains("  ")) {
            result = result.replace("  ", " ");
        }

        return result;
    }
}
