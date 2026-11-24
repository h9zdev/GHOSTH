package net.wigle.wigleandroid;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import net.wigle.wigleandroid.external.ExternalHardwareManager;

import java.util.Locale;

/**
 * Fragment dedicated strictly to External Hardware:
 * - NodeMCU GhostH Promiscuous Sniffer & Optical Flock ALPR Detector (USB Serial OTG & Wi-Fi AP 192.168.4.1)
 * - GhostH Desktop Suite Local Network Synchronization & WiGLE Uploader (Port 8889)
 * - ESP32 Promiscuous Sniffer (Bluetooth SPP)
 * - Raspberry Pi / Kali Companion (TCP stream for Kismet/airodump/gpsd)
 * - RTL-SDR stream (rtl_tcp)
 * - Stealth Passive Capture Mode switch
 * - Real-time hardware telemetry log
 */
public class ExternalHardwareFragment extends Fragment implements ExternalHardwareManager.TelemetryListener {

    private ExternalHardwareManager hardwareManager;

    private SwitchCompat switchPassiveCapture;

    // NodeMCU GhostH UI
    private TextView nodemcuStatusBadge;
    private TextView nodemcuPacketCount;
    private Button btnScanUsb;
    private Button btnConnectGhosthWifi;
    private Button btnOpenGhosthPortal;

    // Desktop Suite UI
    private TextView desktopStatusBadge;
    private EditText editDesktopHost;
    private EditText editDesktopPort;
    private Button btnConnectDesktop;
    private Button btnSyncDesktop;
    private Button btnUploadWigleDesktop;

    // ESP32 UI
    private TextView esp32StatusBadge;
    private TextView esp32PacketCount;
    private Button btnConnectEsp32;

    // Pi / Kali UI
    private TextView piStatusBadge;
    private EditText editPiHost;
    private EditText editPiPort;
    private Button btnConnectPi;

    // RTL-SDR UI
    private TextView sdrStatusBadge;
    private Button btnConnectSdr;

    private TextView hardwareTelemetryLog;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;

    private final Runnable statusUpdateTask = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            updateStatusUi();
            handler.postDelayed(this, 1000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.external_hardware, container, false);
        hardwareManager = ExternalHardwareManager.getInstance(requireContext());

        switchPassiveCapture = view.findViewById(R.id.switch_passive_capture);

        // NodeMCU GhostH
        nodemcuStatusBadge = view.findViewById(R.id.nodemcu_status_badge);
        nodemcuPacketCount = view.findViewById(R.id.nodemcu_packet_count);
        btnScanUsb = view.findViewById(R.id.btn_scan_usb);
        btnConnectGhosthWifi = view.findViewById(R.id.btn_connect_ghosth_wifi);
        btnOpenGhosthPortal = view.findViewById(R.id.btn_open_ghosth_portal);

        // Desktop Suite
        desktopStatusBadge = view.findViewById(R.id.desktop_status_badge);
        editDesktopHost = view.findViewById(R.id.edit_desktop_host);
        editDesktopPort = view.findViewById(R.id.edit_desktop_port);
        btnConnectDesktop = view.findViewById(R.id.btn_connect_desktop);
        btnSyncDesktop = view.findViewById(R.id.btn_sync_desktop);
        btnUploadWigleDesktop = view.findViewById(R.id.btn_upload_wigle_desktop);

        // ESP32
        esp32StatusBadge = view.findViewById(R.id.esp32_status_badge);
        esp32PacketCount = view.findViewById(R.id.esp32_packet_count);
        btnConnectEsp32 = view.findViewById(R.id.btn_connect_esp32);

        // Pi / Kali
        piStatusBadge = view.findViewById(R.id.pi_status_badge);
        editPiHost = view.findViewById(R.id.edit_pi_host);
        editPiPort = view.findViewById(R.id.edit_pi_port);
        btnConnectPi = view.findViewById(R.id.btn_connect_pi);

        // RTL-SDR
        sdrStatusBadge = view.findViewById(R.id.sdr_status_badge);
        btnConnectSdr = view.findViewById(R.id.btn_connect_sdr);

        hardwareTelemetryLog = view.findViewById(R.id.hardware_telemetry_log);

        // Passive Capture Mode
        switchPassiveCapture.setChecked(hardwareManager.isPassiveCaptureOnly());
        switchPassiveCapture.setOnCheckedChangeListener((buttonView, isChecked) -> {
            hardwareManager.setPassiveCaptureOnly(isChecked);
            Toast.makeText(getContext(), isChecked
                    ? "Passive Capture Enabled (Stealth Monitor Mode)"
                    : "Active Scanning Enabled", Toast.LENGTH_SHORT).show();
            appendLog(isChecked ? "[STEALTH] Passive monitor mode enabled." : "[ACTIVE] Wi-Fi active broadcast probes enabled.");
        });

        // 1. USB Serial OTG Scan & Connect
        btnScanUsb.setOnClickListener(v -> {
            if (hardwareManager.isUsbConnected()) {
                hardwareManager.disconnectUsbSerial();
                appendLog("[USB] Disconnected from USB Serial.");
                updateStatusUi();
                return;
            }
            appendLog("[USB] Probing for USB-OTG serial devices (NodeMCU / CH340 / CP2102)...");
            hardwareManager.scanAndConnectUsbSerial((success, message) -> {
                handler.post(() -> {
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    appendLog("[USB] " + message);
                    updateStatusUi();
                });
            });
        });

        // 2. GhostH Wi-Fi Direct Connect (192.168.4.1:8888)
        btnConnectGhosthWifi.setOnClickListener(v -> {
            if (hardwareManager.isGhostHConnected()) {
                hardwareManager.disconnectGhostH();
                appendLog("[GHOSTH-AP] Disconnected from GhostH telemetry stream.");
                updateStatusUi();
                return;
            }
            appendLog("[GHOSTH-AP] Connecting to GhostH telemetry stream at 192.168.4.1:8888...");
            hardwareManager.connectGhostH("192.168.4.1", 8888, (success, message) -> {
                handler.post(() -> {
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    appendLog("[GHOSTH-AP] " + message);
                    updateStatusUi();
                });
            });
        });

        // 3. Open NodeMCU Web Portal in Browser
        btnOpenGhosthPortal.setOnClickListener(v -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://192.168.4.1"));
                startActivity(browserIntent);
            } catch (Exception ex) {
                Toast.makeText(getContext(), "Unable to open browser: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // 4. GhostH Desktop Suite Sync Connect
        btnConnectDesktop.setOnClickListener(v -> {
            if (hardwareManager.isDesktopConnected()) {
                hardwareManager.disconnectDesktopApp();
                appendLog("[DESKTOP] Disconnected from Desktop Suite.");
                updateStatusUi();
                return;
            }
            String host = editDesktopHost.getText().toString().trim();
            if (host.isEmpty()) host = "192.168.4.2";
            int port = 8889;
            try {
                String pStr = editDesktopPort.getText().toString().trim();
                if (!pStr.isEmpty()) port = Integer.parseInt(pStr);
            } catch (Exception ignored) {
            }

            final String fHost = host;
            final int fPort = port;
            appendLog(String.format(Locale.US, "[DESKTOP] Connecting to Desktop Suite at %s:%d...", fHost, fPort));
            hardwareManager.connectDesktopApp(fHost, fPort, (success, message) -> {
                handler.post(() -> {
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    appendLog("[DESKTOP] " + message);
                    updateStatusUi();
                });
            });
        });

        // 5. Sync Observations to Desktop
        btnSyncDesktop.setOnClickListener(v -> {
            appendLog("[DESKTOP] Transmitting local observations to Desktop Suite...");
            hardwareManager.syncObservationsToDesktop((success, message) -> {
                handler.post(() -> {
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    appendLog("[DESKTOP] " + message);
                });
            });
        });

        // 6. Trigger Desktop WiGLE API Upload
        btnUploadWigleDesktop.setOnClickListener(v -> {
            appendLog("[DESKTOP] Requesting Desktop Suite to upload observations to WiGLE API...");
            hardwareManager.triggerDesktopWigleUpload((success, message) -> {
                handler.post(() -> {
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    appendLog("[DESKTOP] " + message);
                });
            });
        });

        // ESP32 Bluetooth SPP
        btnConnectEsp32.setOnClickListener(v -> {
            btnConnectEsp32.setEnabled(false);
            appendLog("[ESP32] Initiating Bluetooth SPP connection...");
            hardwareManager.connectEsp32Bluetooth(null, (success, message) -> {
                handler.post(() -> {
                    btnConnectEsp32.setEnabled(true);
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    appendLog("[ESP32] " + message);
                    updateStatusUi();
                });
            });
        });

        // Pi / Kali TCP stream
        btnConnectPi.setOnClickListener(v -> {
            String host = editPiHost.getText().toString().trim();
            if (host.isEmpty()) host = "192.168.4.1";
            int port = 2501;
            try {
                String pStr = editPiPort.getText().toString().trim();
                if (!pStr.isEmpty()) port = Integer.parseInt(pStr);
            } catch (Exception ignored) {
            }

            final String fHost = host;
            final int fPort = port;
            btnConnectPi.setEnabled(false);
            appendLog(String.format(Locale.US, "[PI/KALI] Connecting to TCP %s:%d...", fHost, fPort));
            hardwareManager.connectPiKali(host, port, (success, message) -> {
                handler.post(() -> {
                    btnConnectPi.setEnabled(true);
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    appendLog("[PI/KALI] " + message);
                    updateStatusUi();
                });
            });
        });

        // RTL-SDR stream
        btnConnectSdr.setOnClickListener(v -> {
            appendLog("[RTL-SDR] Connecting to rtl_tcp localhost:1234...");
            hardwareManager.connectRtlSdr("127.0.0.1", 1234, (success, message) -> {
                handler.post(() -> {
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    appendLog("[RTL-SDR] " + message);
                    updateStatusUi();
                });
            });
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        isRunning = true;
        hardwareManager.addTelemetryListener(this);
        handler.post(statusUpdateTask);
    }

    @Override
    public void onPause() {
        super.onPause();
        isRunning = false;
        hardwareManager.removeTelemetryListener(this);
        handler.removeCallbacks(statusUpdateTask);
    }

    @Override
    public void onTelemetryLine(final String line) {
        handler.post(() -> appendLog("[RX] " + line));
    }

    private void updateStatusUi() {
        if (!isAdded()) return;

        // Update NodeMCU GhostH
        boolean usbConn = hardwareManager.isUsbConnected();
        boolean wifiConn = hardwareManager.isGhostHConnected();
        if (usbConn) {
            nodemcuStatusBadge.setText("USB CONNECTED");
            nodemcuStatusBadge.setTextColor(Color.parseColor("#00FF66"));
            btnScanUsb.setText("DISCONNECT USB SERIAL");
        } else if (wifiConn) {
            nodemcuStatusBadge.setText("AP CONNECTED");
            nodemcuStatusBadge.setTextColor(Color.parseColor("#00FF66"));
            btnConnectGhosthWifi.setText("DISCONNECT GHOSTH WI-FI");
        } else {
            nodemcuStatusBadge.setText("DISCONNECTED");
            nodemcuStatusBadge.setTextColor(Color.parseColor("#FF1F8F"));
            btnScanUsb.setText("SCAN & CONNECT USB SERIAL (OTG)");
            btnConnectGhosthWifi.setText("CONNECT GHOSTH WI-FI (192.168.4.1:8888)");
        }
        nodemcuPacketCount.setText(String.format(Locale.US, "Packets Ingested: %d", hardwareManager.getPacketsReceived()));

        // Update Desktop Suite
        boolean desktopConn = hardwareManager.isDesktopConnected();
        desktopStatusBadge.setText(desktopConn ? "CONNECTED" : "DISCONNECTED");
        desktopStatusBadge.setTextColor(desktopConn ? Color.parseColor("#00FF66") : Color.parseColor("#FF1F8F"));
        btnConnectDesktop.setText(desktopConn ? "DISCONNECT DESKTOP SUITE" : "CONNECT TO DESKTOP SUITE");

        // Update ESP32
        boolean esp32Connected = hardwareManager.isEsp32Connected();
        esp32StatusBadge.setText(esp32Connected ? "CONNECTED" : "DISCONNECTED");
        esp32StatusBadge.setTextColor(esp32Connected ? Color.parseColor("#00FF66") : Color.parseColor("#FF1F8F"));
        esp32PacketCount.setText(String.format(Locale.US, "Packets: %d", hardwareManager.getEsp32PacketsReceived()));

        // Update Pi / Kali
        boolean piConnected = hardwareManager.isPiConnected();
        piStatusBadge.setText(piConnected ? "CONNECTED" : "OFFLINE");
        piStatusBadge.setTextColor(piConnected ? Color.parseColor("#00FF66") : Color.parseColor("#FF1F8F"));

        // Update RTL-SDR
        boolean sdrConnected = hardwareManager.isSdrConnected();
        sdrStatusBadge.setText(sdrConnected ? "STREAMING" : "STANDBY");
        sdrStatusBadge.setTextColor(sdrConnected ? Color.parseColor("#00FF66") : Color.parseColor("#FFE600"));
    }

    private void appendLog(String message) {
        if (hardwareTelemetryLog != null) {
            String current = hardwareTelemetryLog.getText().toString();
            if (current.length() > 3000) {
                current = current.substring(current.length() - 1500);
            }
            hardwareTelemetryLog.setText(current + "\n" + message);
        }
    }
}
