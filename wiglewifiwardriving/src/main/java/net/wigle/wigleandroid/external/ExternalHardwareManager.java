package net.wigle.wigleandroid.external;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Controller for External Companion Hardware.
 * Ingests promiscuous Wi-Fi frames from ESP32 companions (via Bluetooth Serial / USB),
 * Raspberry Pi / Kali monitor streams, and RTL-SDR receivers.
 */
public class ExternalHardwareManager {

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final String PREF_PASSIVE_CAPTURE_ONLY = "pref_passive_capture_only";

    private static volatile ExternalHardwareManager instance;

    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final AtomicBoolean esp32Connected = new AtomicBoolean(false);
    private final AtomicBoolean piConnected = new AtomicBoolean(false);
    private final AtomicBoolean sdrConnected = new AtomicBoolean(false);
    private final AtomicLong packetsReceived = new AtomicLong(0);

    private BluetoothSocket btSocket;
    private Socket piSocket;
    private Socket sdrSocket;

    public interface ConnectionCallback {
        void onResult(boolean success, String message);
    }

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

    public boolean isEsp32Connected() {
        return esp32Connected.get();
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

                // Read packet stream in background
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

    /**
     * Connect to a Raspberry Pi / Kali monitor-mode stream or Kismet/Airodump live endpoint over TCP.
     */
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

    /**
     * Connect to an RTL-SDR receiver stream (rtl_tcp).
     */
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

    /**
     * Parses frame telemetry from external hardware:
     * GHOSTH,<TYPE>,<BSSID>,<RSSI>,<CHANNEL>,<SSID>
     */
    public void processPacketLine(@NonNull final String line) {
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

                // Compute frequency from channel
                int freq = channel <= 14 ? 2407 + (channel * 5) : 5000 + (channel * 5);

                // Ingest into network cache
                if (MainActivity.getNetworkCache() != null) {
                    Network net = MainActivity.getNetworkCache().get(bssid);
                    if (net == null) {
                        net = new Network(bssid, ssid, freq, "[WPA2][ESS]", rssi, NetworkType.WIFI);
                        net.setLevel(rssi);
                        if (ListFragment.lameStatic.location != null) {
                            net.setLatLng(new net.wigle.wigleandroid.model.LatLng(
                                    ListFragment.lameStatic.location.getLatitude(),
                                    ListFragment.lameStatic.location.getLongitude()
                            ));
                        }
                        MainActivity.getNetworkCache().put(bssid, net);

                        // Save to Database
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

