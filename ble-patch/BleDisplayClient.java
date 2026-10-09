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
import android.os.SystemClock;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class BleDisplayClient {
    private static final String DEVICE_NAME = "ZAFIRA-DISPLAY";
    private static final UUID SERVICE_UUID = UUID.fromString("6fbd0001-7c40-4a16-93c5-6c0c70696f74");
    private static final UUID RX_UUID = UUID.fromString("6fbd0002-7c40-4a16-93c5-6c0c70696f74");

    private static final long SCAN_TIMEOUT_MS = 8000;
    private static final long CONNECT_TIMEOUT_MS = 12000;
    private static final long RETRY_MS = 1500;

    private static BleDisplayClient INSTANCE;

    static synchronized BleDisplayClient get(Context context) {
        if (INSTANCE == null) INSTANCE = new BleDisplayClient(context.getApplicationContext());
        return INSTANCE;
    }

    private Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter;

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic rx;
    private boolean scanning;
    private boolean connected;
    private long gattStartedAt;
    private String pending = "";
    private String lastSent = "";
    private boolean stopped;
    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            synchronized (BleDisplayClient.this) {
                if (stopped) return;
                if (gatt != null && rx == null && gattStartedAt > 0
                        && SystemClock.elapsedRealtime() - gattStartedAt >= CONNECT_TIMEOUT_MS) {
                    closeCurrentGattLocked();
                }
            }
            start();
            handler.postDelayed(this, 3000);
        }
    };

    private BleDisplayClient(Context context) {
        this.context = context;
        BluetoothManager manager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
    }

    synchronized boolean isConnected() {
        return connected && gatt != null && rx != null;
    }

    synchronized String getPendingText() {
        return pending;
    }

    void start() {
        stopped = false;
        handler.removeCallbacks(watchdog);
        handler.postDelayed(watchdog, 3000);
        if (!hasPermissions() || adapter == null || !adapter.isEnabled()) return;

        synchronized (this) {
            if (isConnected() || scanning) return;

            if (gatt != null) {
                long age = gattStartedAt <= 0
                        ? CONNECT_TIMEOUT_MS + 1
                        : SystemClock.elapsedRealtime() - gattStartedAt;

                if (age < CONNECT_TIMEOUT_MS) return;

                closeCurrentGattLocked();
            }
        }

        try {
            // Kluczowa poprawka:
            // szukamy po UUID uslugi, a nie tylko po nazwie urzadzenia.
            // Na K706 device.getName() potrafi byc null do czasu,
            // az inna aplikacja (np. nRF Connect) odswiezy cache BLE.
            scanning = adapter.startLeScan(
                    new UUID[]{SERVICE_UUID},
                    scanCallback
            );

            if (scanning) {
                handler.postDelayed(() -> {
                    stopScan();

                    synchronized (BleDisplayClient.this) {
                        if (gatt == null && !connected) {
                            handler.postDelayed(BleDisplayClient.this::start, RETRY_MS);
                        }
                    }
                }, SCAN_TIMEOUT_MS);
            } else {
                handler.postDelayed(this::start, RETRY_MS);
            }

        } catch (Throwable ignored) {
            scanning = false;
            handler.postDelayed(this::start, RETRY_MS);
        }
    }

    void stop() {
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        stopScan();

        synchronized (this) {
            closeCurrentGattLocked();
            pending = "";
        }
    }

    void sendText(String text) {
        text = clean(text);
        if (text.isEmpty()) return;

        synchronized (this) {
            pending = text;
        }

        if (isConnected()) {
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

        try {
            adapter.stopLeScan(scanCallback);
        } catch (Throwable ignored) {}

        scanning = false;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(
                BluetoothGatt callbackGatt,
                int status,
                int newState
        ) {
            synchronized (BleDisplayClient.this) {
                if (gatt != callbackGatt) {
                    try { callbackGatt.close(); } catch (Throwable ignored) {}
                    return;
                }

                if (newState == BluetoothProfile.STATE_CONNECTED
                        && status == BluetoothGatt.GATT_SUCCESS) {
                    connected = true;
                    // Keep start timestamp until service/characteristic discovery completes.
                    lastSent = "";
                } else {
                    connected = false;
                    rx = null;
                    lastSent = "";
                    closeGattLocked(callbackGatt);
                    handler.postDelayed(BleDisplayClient.this::start, RETRY_MS);
                    return;
                }
            }

            boolean mtuRequested = false;

            if (Build.VERSION.SDK_INT >= 21) {
                try {
                    mtuRequested = callbackGatt.requestMtu(185);
                } catch (Throwable ignored) {}
            }

            if (!mtuRequested) {
                try {
                    callbackGatt.discoverServices();
                } catch (Throwable ignored) {
                    reconnect(callbackGatt);
                }
            }
        }

        @Override public void onMtuChanged(
                BluetoothGatt callbackGatt,
                int mtu,
                int status
        ) {
            try {
                callbackGatt.discoverServices();
            } catch (Throwable ignored) {
                reconnect(callbackGatt);
            }
        }

        @Override public void onServicesDiscovered(
                BluetoothGatt callbackGatt,
                int status
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                reconnect(callbackGatt);
                return;
            }

            BluetoothGattService service = callbackGatt.getService(SERVICE_UUID);

            if (service == null) {
                reconnect(callbackGatt);
                return;
            }

            BluetoothGattCharacteristic characteristic =
                    service.getCharacteristic(RX_UUID);

            if (characteristic == null) {
                reconnect(callbackGatt);
                return;
            }

            synchronized (BleDisplayClient.this) {
                if (gatt != callbackGatt) return;
                rx = characteristic;
                gattStartedAt = 0;
                rx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                lastSent = "";
            }

            writePending();
        }
    };

    private void reconnect(BluetoothGatt callbackGatt) {
        synchronized (this) {
            if (gatt == callbackGatt) {
                closeGattLocked(callbackGatt);
            } else {
                try { callbackGatt.close(); } catch (Throwable ignored) {}
            }
        }

        handler.postDelayed(this::start, RETRY_MS);
    }

    private final BluetoothAdapter.LeScanCallback scanCallback =
            (device, rssi, scanRecord) -> {
                if (device == null || !hasPermissions()) return;

                stopScan();

                synchronized (BleDisplayClient.this) {
                    closeCurrentGattLocked();

                    try {
                        gattStartedAt = SystemClock.elapsedRealtime();
                        gatt = device.connectGatt(
                                context,
                                false,
                                gattCallback,
                                BluetoothDevice.TRANSPORT_LE
                        );

                        if (gatt == null) {
                            gattStartedAt = 0;
                            handler.postDelayed(BleDisplayClient.this::start, RETRY_MS);
                        }

                    } catch (Throwable ignored) {
                        gatt = null;
                        gattStartedAt = 0;
                        connected = false;
                        handler.postDelayed(BleDisplayClient.this::start, RETRY_MS);
                    }
                }
            };

    private void writePending() {
        final BluetoothGatt currentGatt;
        final BluetoothGattCharacteristic currentRx;
        final String value;

        synchronized (this) {
            currentGatt = gatt;
            currentRx = rx;
            value = pending;
        }

        if (!hasPermissions() || currentGatt == null || currentRx == null) return;
        if (value.isEmpty() || value.equals(lastSent)) return;

        try {
            currentRx.setValue(value.getBytes(StandardCharsets.UTF_8));

            if (currentGatt.writeCharacteristic(currentRx)) {
                synchronized (this) {
                    lastSent = value;
                }
            }
        } catch (Throwable ignored) {
            reconnect(currentGatt);
        }
    }

    private void closeCurrentGattLocked() {
        BluetoothGatt current = gatt;

        gatt = null;
        rx = null;
        connected = false;
        gattStartedAt = 0;
        lastSent = "";

        if (current != null) {
            try { current.disconnect(); } catch (Throwable ignored) {}
            try { current.close(); } catch (Throwable ignored) {}
        }
    }

    private void closeGattLocked(BluetoothGatt target) {
        if (gatt == target) {
            gatt = null;
            rx = null;
            connected = false;
            gattStartedAt = 0;
            lastSent = "";
        }

        try { target.disconnect(); } catch (Throwable ignored) {}
        try { target.close(); } catch (Throwable ignored) {}
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                            == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                            == PackageManager.PERMISSION_GRANTED;
        }

        if (Build.VERSION.SDK_INT >= 23) {
            return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }

    private static String clean(String text) {
        if (text == null) return "";

        StringBuilder out = new StringBuilder();

        for (int i = 0; i < text.length() && out.length() < 96; i++) {
            char c = text.charAt(i);

            if (c == '\n' || c == '\r' || c == '\t') c = ' ';

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
