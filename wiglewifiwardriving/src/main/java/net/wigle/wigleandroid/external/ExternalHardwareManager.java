package net.wigle.wigleandroid.external;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Controller for External Companion Hardware.
 * Ingests promiscuous Wi-Fi frames from ESP8266/ESP32 companions (via USB Serial OTG / Wi-Fi AP / Bluetooth SPP),
 * Raspberry Pi / Kali monitor streams, RTL-SDR receivers, and bidirectional Desktop Suite synchronization.
 */
public class ExternalHardwareManager {

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final String PREF_PASSIVE_CAPTURE_ONLY = "pref_passive_capture_only";
    public static final String ACTION_USB_PERMISSION = "droid.wardriver.hx.os.USB_PERMISSION";

    private static volatile ExternalHardwareManager instance;

    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final AtomicBoolean esp32Connected = new AtomicBoolean(false);
    private final AtomicBoolean piConnected = new AtomicBoolean(false);
    private final AtomicBoolean sdrConnected = new AtomicBoolean(false);
    private final AtomicBoolean usbConnected = new AtomicBoolean(false);
    private final AtomicBoolean ghostHConnected = new AtomicBoolean(false);
    private final AtomicBoolean desktopConnected = new AtomicBoolean(false);
    private final AtomicLong packetsReceived = new AtomicLong(0);

    private BluetoothSocket btSocket;
    private Socket piSocket;
    private Socket sdrSocket;
    private Socket ghostHSocket;
    private Socket desktopSocket;
    private PrintWriter desktopWriter;
    private UsbSerialPort usbSerialPort;

    public interface ConnectionCallback {
        void onResult(boolean success, String message);
    }

    public interface TelemetryListener {
        void onTelemetryLine(String line);
    }

    private final List<TelemetryListener> telemetryListeners = new CopyOnWriteArrayList<>();

    private ExternalHardwareManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized ExternalHardwareManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new ExternalHardwareManager(context);
        }
        return instance;
    }

    public void addTelemetryListener(TelemetryListener listener) {
        if (listener != null && !telemetryListeners.contains(listener)) {
            telemetryListeners.add(listener);
        }
    }

    public void removeTelemetryListener(TelemetryListener listener) {
        telemetryListeners.remove(listener);
    }

    public boolean isEsp32Connected() {
        return esp32Connected.get();
    }

    public boolean isUsbConnected() {
        return usbConnected.get();
    }

    public boolean isGhostHConnected() {
        return ghostHConnected.get();
    }

    public boolean isDesktopConnected() {
        return desktopConnected.get();
    }

    public long getEsp32PacketsReceived() {
        return packetsReceived.get();
    }

    public void connectRtlSdr(@NonNull final String host, final int port, final ConnectionCallback callback) {
        connectSdr(host, port, callback);
    }

    public boolean isPiConnected() {
        return piConnected.get();
    }

    public boolean isSdrConnected() {
        return sdrConnected.get();
    }

    public long getPacketsReceived() {
        return packetsReceived.get();
    }

    public boolean isPassiveCaptureOnly() {
        return prefs.getBoolean(PREF_PASSIVE_CAPTURE_ONLY, false);
    }

    public void setPassiveCaptureOnly(boolean enabled) {
        prefs.edit().putBoolean(PREF_PASSIVE_CAPTURE_ONLY, enabled).apply();
    }

    /**
     * Scan and Connect directly to NodeMCU / ESP8266 / ESP32 via USB Serial (OTG).
     */
    public void scanAndConnectUsbSerial(final ConnectionCallback callback) {
        if (usbConnected.get()) {
            disconnectUsbSerial();
            if (callback != null) callback.onResult(false, "USB Serial Disconnected");
            return;
        }

        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            if (callback != null) callback.onResult(false, "USB Service not available on this device");
            return;
        }

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            if (callback != null) callback.onResult(false, "No USB Serial hardware detected. Connect NodeMCU via USB OTG cable.");
            return;
        }

        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();

        if (!usbManager.hasPermission(device)) {
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
            PendingIntent pi = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), flags);
            usbManager.requestPermission(device, pi);
            if (callback != null) callback.onResult(false, "USB permission requested for " + device.getDeviceName() + ". Allow permission and tap scan again.");
            return;
        }

        executor.execute(() -> {
            try {
                UsbDeviceConnection connection = usbManager.openDevice(device);
                if (connection == null) {
                    if (callback != null) callback.onResult(false, "Unable to open USB connection (Null handle)");
                    return;
                }

                UsbSerialPort port = driver.getPorts().get(0);
                port.open(connection);
                port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                port.setDTR(true);
                port.setRTS(true);

                usbSerialPort = port;
                usbConnected.set(true);

                String devName = device.getProductName() != null ? device.getProductName() : ("Device " + device.getDeviceId());
                if (callback != null) callback.onResult(true, "Connected to USB Serial: " + devName + " @ 115200 baud");

                byte[] buffer = new byte[2048];
                StringBuilder sb = new StringBuilder();

                while (usbConnected.get()) {
                    int len = port.read(buffer, 1000);
                    if (len > 0) {
                        sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                        int newlineIdx;
                        while ((newlineIdx = sb.indexOf("\n")) != -1) {
                            String line = sb.substring(0, newlineIdx).trim();
                            sb.delete(0, newlineIdx + 1);
                            if (!line.isEmpty()) {
                                processPacketLine(line);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                Logging.error("USB Serial error: " + ex, ex);
                disconnectUsbSerial();
                if (callback != null) callback.onResult(false, "USB Serial error: " + ex.getMessage());
            }
        });
    }

    public void disconnectUsbSerial() {
        usbConnected.set(false);
        try {
            if (usbSerialPort != null) {
                usbSerialPort.close();
                usbSerialPort = null;
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Connect to NodeMCU GhostH Access Point telemetry stream over TCP (Default 192.168.4.1:8888).
     */
    public void connectGhostH(@NonNull final String host, final int port, final ConnectionCallback callback) {
        if (ghostHConnected.get()) {
            disconnectGhostH();
            if (callback != null) callback.onResult(false, "Disconnected GhostH AP");
            return;
        }

        executor.execute(() -> {
            try {
                ghostHSocket = new Socket();
                ghostHSocket.connect(new InetSocketAddress(host, port), 5000);
                ghostHConnected.set(true);

                if (callback != null) callback.onResult(true, "Connected to GhostH AP at " + host + ":" + port);

                BufferedReader reader = new BufferedReader(new InputStreamReader(ghostHSocket.getInputStream()));
                String line;
                while (ghostHConnected.get() && (line = reader.readLine()) != null) {
                    processPacketLine(line);
                }
            } catch (Exception ex) {
                Logging.error("GhostH AP TCP connection error: " + ex, ex);
                disconnectGhostH();
                if (callback != null) callback.onResult(false, "GhostH connection error: " + ex.getMessage());
            }
        });
    }

    public void disconnectGhostH() {
        ghostHConnected.set(false);
        try {
            if (ghostHSocket != null) {
                ghostHSocket.close();
                ghostHSocket = null;
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Connect to the GhostH Desktop Suite over local Wi-Fi (default port 8889).
     */
    public void connectDesktopApp(@NonNull final String host, final int port, final ConnectionCallback callback) {
        if (desktopConnected.get()) {
            disconnectDesktopApp();
            if (callback != null) callback.onResult(false, "Disconnected from Desktop App");
            return;
        }

        executor.execute(() -> {
            try {
                desktopSocket = new Socket();
                desktopSocket.connect(new InetSocketAddress(host, port), 5000);
                desktopWriter = new PrintWriter(desktopSocket.getOutputStream(), true);
                desktopConnected.set(true);

                // Send handshake
                desktopWriter.println("GHOSTH_HANDSHAKE,ANDROID_APP,VERSION=2.2");

                if (callback != null) callback.onResult(true, "Connected to Desktop App at " + host + ":" + port);

                BufferedReader reader = new BufferedReader(new InputStreamReader(desktopSocket.getInputStream()));
                String line;
                while (desktopConnected.get() && (line = reader.readLine()) != null) {
                    processPacketLine(line);
                }
            } catch (Exception ex) {
                Logging.error("Desktop App connection error: " + ex, ex);
                disconnectDesktopApp();
                if (callback != null) callback.onResult(false, "Desktop connection error: " + ex.getMessage());
            }
        });
    }

    public void disconnectDesktopApp() {
        desktopConnected.set(false);
        try {
            if (desktopWriter != null) {
                desktopWriter.close();
                desktopWriter = null;
            }
            if (desktopSocket != null) {
                desktopSocket.close();
                desktopSocket = null;
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Sync cached observations from Android App to Desktop Suite.
     */
    public void syncObservationsToDesktop(final ConnectionCallback callback) {
        if (!desktopConnected.get() || desktopWriter == null) {
            if (callback != null) callback.onResult(false, "Desktop App not connected");
            return;
        }

        executor.execute(() -> {
            try {
                int count = 0;
                if (MainActivity.getNetworkCache() != null) {
                    for (Network net : MainActivity.getNetworkCache().values()) {
                        String bssid = net.getBssid();
                        String ssid = net.getSsid();
                        if (ssid != null && ssid.toUpperCase(Locale.US).contains("GHOSTH")) {
                            continue; // Exclude internal companion network by Wi-Fi name
                        }
                        int freq = net.getFrequency();
                        String caps = net.getCapabilities();
                        int level = net.getLevel();
                        double lat = 0.0;
                        double lon = 0.0;
                        if (net.getLatLng() != null) {
                            lat = net.getLatLng().latitude;
                            lon = net.getLatLng().longitude;
                        }
                        String syncLine = String.format(Locale.US, "SYNC_NET,%s,%s,%d,%s,%d,%.6f,%.6f",
                                bssid, ssid, freq, caps, level, lat, lon);
                        desktopWriter.println(syncLine);
                        count++;
                    }
                }
                desktopWriter.println("SYNC_COMPLETE," + count);
                if (callback != null) callback.onResult(true, "Synced " + count + " networks to Desktop Suite");
            } catch (Exception ex) {
                Logging.error("Error syncing observations to desktop: " + ex, ex);
                if (callback != null) callback.onResult(false, "Sync error: " + ex.getMessage());
            }
        });
    }

    /**
     * Trigger Desktop App to upload all observations directly to WiGLE API.
     */
    public void triggerDesktopWigleUpload(final ConnectionCallback callback) {
        if (!desktopConnected.get() || desktopWriter == null) {
            if (callback != null) callback.onResult(false, "Desktop App not connected");
            return;
        }

        executor.execute(() -> {
            try {
                desktopWriter.println("CMD,UPLOAD_WIGLE");
                if (callback != null) callback.onResult(true, "WiGLE upload command transmitted to Desktop App");
            } catch (Exception ex) {
                Logging.error("Error triggering WiGLE upload on desktop: " + ex, ex);
                if (callback != null) callback.onResult(false, "Upload command error: " + ex.getMessage());
            }
        });
    }

    /**
     * Connect to an ESP32 Promiscuous Sniffer companion via Bluetooth Serial (SPP).
     */
    @SuppressLint("MissingPermission")
    public void connectEsp32Bluetooth(@Nullable final String targetDeviceName, final ConnectionCallback callback) {
        if (esp32Connected.get()) {
            disconnectEsp32();
            if (callback != null) callback.onResult(false, "Disconnected");
            return;
        }

        executor.execute(() -> {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null || !adapter.isEnabled()) {
                    if (callback != null) callback.onResult(false, "Bluetooth is disabled");
                    return;
                }

                Set<BluetoothDevice> paired = adapter.getBondedDevices();
                BluetoothDevice target = null;

                for (BluetoothDevice dev : paired) {
                    String name = dev.getName();
                    if (name != null) {
                        if (targetDeviceName != null && name.equalsIgnoreCase(targetDeviceName)) {
                            target = dev;
                            break;
                        } else if (name.contains("ESP32") || name.contains("GhostH") || name.contains("Sniffer")) {
                            target = dev;
                            break;
                        }
                    }
                }

                if (target == null) {
                    if (callback != null) callback.onResult(false, "No paired ESP32 found. Pair 'GhostH-ESP32-Sniffer' first.");
                    return;
                }

                adapter.cancelDiscovery();
                btSocket = target.createRfcommSocketToServiceRecord(SPP_UUID);
                btSocket.connect();
                esp32Connected.set(true);

                if (callback != null) callback.onResult(true, "Connected to " + target.getName());

                BufferedReader reader = new BufferedReader(new InputStreamReader(btSocket.getInputStream()));
                String line;
                while (esp32Connected.get() && (line = reader.readLine()) != null) {
                    processPacketLine(line);
                }

            } catch (Exception ex) {
                Logging.error("ESP32 Bluetooth connection error: " + ex, ex);
                disconnectEsp32();
                if (callback != null) callback.onResult(false, "Connection error: " + ex.getMessage());
            }
        });
    }

    public void disconnectEsp32() {
        esp32Connected.set(false);
        try {
            if (btSocket != null) {
                btSocket.close();
                btSocket = null;
            }
        } catch (Exception ignored) {
        }
    }

    public void connectPiKali(@NonNull final String host, final int port, final ConnectionCallback callback) {
        if (piConnected.get()) {
            disconnectPiKali();
            if (callback != null) callback.onResult(false, "Disconnected");
            return;
        }

        executor.execute(() -> {
            try {
                piSocket = new Socket();
                piSocket.connect(new InetSocketAddress(host, port), 5000);
                piConnected.set(true);

                if (callback != null) callback.onResult(true, "Connected to Pi/Kali at " + host + ":" + port);

                BufferedReader reader = new BufferedReader(new InputStreamReader(piSocket.getInputStream()));
                String line;
                while (piConnected.get() && (line = reader.readLine()) != null) {
                    processPacketLine(line);
                }
            } catch (Exception ex) {
                Logging.error("Pi/Kali TCP connection error: " + ex, ex);
                disconnectPiKali();
                if (callback != null) callback.onResult(false, "Connection error: " + ex.getMessage());
            }
        });
    }

    public void disconnectPiKali() {
        piConnected.set(false);
        try {
            if (piSocket != null) {
                piSocket.close();
                piSocket = null;
            }
        } catch (Exception ignored) {
        }
    }

    public void connectSdr(@NonNull final String host, final int port, final ConnectionCallback callback) {
        if (sdrConnected.get()) {
            disconnectSdr();
            if (callback != null) callback.onResult(false, "Disconnected");
            return;
        }

        executor.execute(() -> {
            try {
                sdrSocket = new Socket();
                sdrSocket.connect(new InetSocketAddress(host, port), 5000);
                sdrConnected.set(true);

                if (callback != null) callback.onResult(true, "Connected to RTL-SDR at " + host + ":" + port);
            } catch (Exception ex) {
                disconnectSdr();
                if (callback != null) callback.onResult(false, "SDR connection error: " + ex.getMessage());
            }
        });
    }

    public void disconnectSdr() {
        sdrConnected.set(false);
        try {
            if (sdrSocket != null) {
                sdrSocket.close();
                sdrSocket = null;
            }
        } catch (Exception ignored) {
        }
    }

    public void processPacketLine(@NonNull final String line) {
        for (TelemetryListener listener : telemetryListeners) {
            try {
                listener.onTelemetryLine(line);
            } catch (Exception ignored) {
            }
        }

        if (!line.startsWith("GHOSTH,")) return;

        try {
            String[] parts = line.split(",", -1);
            if (parts.length >= 6) {
                String frameType = parts[1].trim();
                String bssid = parts[2].trim().toUpperCase();
                int rssi = Integer.parseInt(parts[3].trim());
                int channel = Integer.parseInt(parts[4].trim());
                String ssid = parts[5].trim();

                packetsReceived.incrementAndGet();

                int freq = channel <= 14 ? 2407 + (channel * 5) : 5000 + (channel * 5);

                if (MainActivity.getNetworkCache() != null) {
                    Network net = MainActivity.getNetworkCache().get(bssid);
                    if (net == null) {
                        String capabilities = "ALPR".equalsIgnoreCase(frameType) ? "[FLOCK-ALPR-OPTICAL]" : "[WPA2][ESS]";
                        net = new Network(bssid, ssid, freq, capabilities, rssi, NetworkType.WIFI);
                        net.setLevel(rssi);
                        if (ListFragment.lameStatic.location != null) {
                            net.setLatLng(new net.wigle.wigleandroid.model.LatLng(
                                    ListFragment.lameStatic.location.getLatitude(),
                                    ListFragment.lameStatic.location.getLongitude()
                            ));
                        }
                        MainActivity.getNetworkCache().put(bssid, net);

                        DatabaseHelper db = ListFragment.lameStatic.dbHelper;
                        if (db != null) {
                            db.addObservation(net, ListFragment.lameStatic.location, true);
                        }
                    } else {
                        net.setLevel(rssi);
                        if (!ssid.isEmpty()) {
                            net.setSsid(ssid);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Logging.error("Error parsing external packet line: " + line + " - " + ex);
        }
    }
}
