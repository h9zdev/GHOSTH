package net.wigle.wigleandroid;

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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import net.wigle.wigleandroid.external.ExternalHardwareManager;
import net.wigle.wigleandroid.ml.AdvancedEvilTwinDetector;
import net.wigle.wigleandroid.ml.OnDeviceMLInference;
import net.wigle.wigleandroid.model.Network;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Fragment for configuring External Hardware (ESP32 sniffer, Pi/Kali monitor, RTL-SDR)
 * and triggering On-Device ML threat evaluation.
 */
public class ExternalHardwareFragment extends Fragment {

    private ExternalHardwareManager hardwareManager;

    private SwitchCompat switchPassiveCapture;
    private TextView esp32StatusBadge;
    private TextView esp32PacketCount;
    private Button btnConnectEsp32;

    private TextView piStatusBadge;
    private EditText editPiHost;
    private EditText editPiPort;
    private Button btnConnectPi;

    private TextView mlInferencesCount;
    private TextView mlAnomaliesCount;
    private TextView mlEvilTwinsCount;
    private Button btnRunMlEval;

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
        esp32StatusBadge = view.findViewById(R.id.esp32_status_badge);
        esp32PacketCount = view.findViewById(R.id.esp32_packet_count);
        btnConnectEsp32 = view.findViewById(R.id.btn_connect_esp32);

        piStatusBadge = view.findViewById(R.id.pi_status_badge);
        editPiHost = view.findViewById(R.id.edit_pi_host);
        editPiPort = view.findViewById(R.id.edit_pi_port);
        btnConnectPi = view.findViewById(R.id.btn_connect_pi);

        mlInferencesCount = view.findViewById(R.id.ml_inferences_count);
        mlAnomaliesCount = view.findViewById(R.id.ml_anomalies_count);
        mlEvilTwinsCount = view.findViewById(R.id.ml_eviltwins_count);
        btnRunMlEval = view.findViewById(R.id.btn_run_ml_eval);

        // Passive Capture Mode
        switchPassiveCapture.setChecked(hardwareManager.isPassiveCaptureOnly());
        switchPassiveCapture.setOnCheckedChangeListener((buttonView, isChecked) -> {
            hardwareManager.setPassiveCaptureOnly(isChecked);
            Toast.makeText(getContext(), isChecked
                    ? "Passive Capture Enabled (Stealth Monitor Mode)"
                    : "Active Scanning Enabled", Toast.LENGTH_SHORT).show();
        });

        // ESP32 Bluetooth SPP
        btnConnectEsp32.setOnClickListener(v -> {
            btnConnectEsp32.setEnabled(false);
            hardwareManager.connectEsp32Bluetooth(null, (success, message) -> {
                handler.post(() -> {
                    btnConnectEsp32.setEnabled(true);
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
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

            btnConnectPi.setEnabled(false);
            hardwareManager.connectPiKali(host, port, (success, message) -> {
                handler.post(() -> {
                    btnConnectPi.setEnabled(true);
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    updateStatusUi();
                });
            });
        });

        // Run On-Device ML Evaluation
        btnRunMlEval.setOnClickListener(v -> runFullMlEvaluation());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        isRunning = true;
        handler.post(statusUpdateTask);
    }

    @Override
    public void onPause() {
        super.onPause();
        isRunning = false;
        handler.removeCallbacks(statusUpdateTask);
    }

    private void updateStatusUi() {
        if (hardwareManager == null) return;

        // ESP32 Status
        boolean esp32Online = hardwareManager.isEsp32Connected();
        esp32StatusBadge.setText(esp32Online ? "[ONLINE & SNIFFING]" : "[OFFLINE]");
        esp32StatusBadge.setTextColor(getResources().getColor(
                esp32Online ? R.color.cyber_green : R.color.cyber_pink));
        btnConnectEsp32.setText(esp32Online ? "DISCONNECT ESP32" : "CONNECT BLUETOOTH SPP");
        esp32PacketCount.setText(String.format(Locale.US, "PACKETS: %d", hardwareManager.getPacketsReceived()));

        // Pi/Kali Status
        boolean piOnline = hardwareManager.isPiConnected();
        piStatusBadge.setText(piOnline ? "[STREAMING ACTIVE]" : "[OFFLINE]");
        piStatusBadge.setTextColor(getResources().getColor(
                piOnline ? R.color.cyber_green : R.color.cyber_pink));
        btnConnectPi.setText(piOnline ? "DISCONNECT" : "CONNECT");

        // ML Counters
        mlInferencesCount.setText(String.format(Locale.US, "INFERENCES: %d", OnDeviceMLInference.getTotalInferences()));
        mlAnomaliesCount.setText(String.format(Locale.US, "ANOMALIES: %d", OnDeviceMLInference.getAnomaliesDetected()));
        mlEvilTwinsCount.setText(String.format(Locale.US, "EVIL-TWINS: %d", OnDeviceMLInference.getEvilTwinsDetected()));
    }

    private void runFullMlEvaluation() {
        Collection<Network> networks = MainActivity.getNetworkCache() != null
                ? MainActivity.getNetworkCache().values() : null;

        if (networks == null || networks.isEmpty()) {
            Toast.makeText(getContext(), "No wireless transmitters in local cache to evaluate", Toast.LENGTH_SHORT).show();
            return;
        }

        int anomalies = 0;
        for (Network net : networks) {
            OnDeviceMLInference.FullInferenceResult res = OnDeviceMLInference.infer(net);
            if (res.anomaly.isAnomalous) {
                anomalies++;
            }
        }

        List<AdvancedEvilTwinDetector.EvilTwinFinding> findings = OnDeviceMLInference.evaluateEvilTwins(networks);
        updateStatusUi();

        // Display results alert dialog
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "Scanned Transmitters: %d\n", networks.size()));
        sb.append(String.format(Locale.US, "Isolation Forest Anomalies Flagged: %d\n\n", anomalies));

        if (!findings.isEmpty()) {
            sb.append("=== EVIL-TWIN & ROGUE AP FINDINGS ===\n");
            for (AdvancedEvilTwinDetector.EvilTwinFinding f : findings) {
                sb.append(String.format(Locale.US, "[%s] SSID: %s (%d%% Confidence)\n%s\n\n",
                        f.severity.getLabel(), f.ssid, f.confidencePercent, f.diagnostic));
            }
        } else {
            sb.append("No rogue APs or evil-twin clones detected in immediate proximity.");
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("GhostH ML Intelligence Report")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }
}

