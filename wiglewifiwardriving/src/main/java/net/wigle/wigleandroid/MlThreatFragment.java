package net.wigle.wigleandroid;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.wigle.wigleandroid.ml.AdvancedEvilTwinDetector;
import net.wigle.wigleandroid.ml.OnDeviceMLInference;
import net.wigle.wigleandroid.model.Network;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated Fragment for Machine Learning & Threat Intelligence:
 * - Isolation Forest Anomaly Detection
 * - Multi-Vector Evil-Twin Detector
 * - Deep Device Fingerprinting
 */
public class MlThreatFragment extends Fragment {

    private TextView mlInferencesCount;
    private TextView mlAnomaliesCount;
    private TextView mlEvilTwinsCount;
    private TextView mlTelemetryLog;
    private Button btnRunMlEval;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;

    private final Runnable statusUpdateTask = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            updateMetrics();
            handler.postDelayed(this, 1500);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.ml_threat, container, false);

        mlInferencesCount = view.findViewById(R.id.ml_inferences_count);
        mlAnomaliesCount = view.findViewById(R.id.ml_anomalies_count);
        mlEvilTwinsCount = view.findViewById(R.id.ml_eviltwins_count);
        mlTelemetryLog = view.findViewById(R.id.ml_telemetry_log);
        btnRunMlEval = view.findViewById(R.id.btn_run_ml_eval);

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

    private void updateMetrics() {
        if (!isAdded()) return;
        mlInferencesCount.setText(String.valueOf(OnDeviceMLInference.getTotalInferences()));
        mlAnomaliesCount.setText(String.valueOf(OnDeviceMLInference.getAnomaliesDetected()));
        mlEvilTwinsCount.setText(String.valueOf(OnDeviceMLInference.getEvilTwinsDetected()));
    }

    private void runFullMlEvaluation() {
        final Collection<Network> networks = MainActivity.getNetworkCache() != null
                ? MainActivity.getNetworkCache().values() : null;

        if (networks == null || networks.isEmpty()) {
            Toast.makeText(getContext(), "No cached networks in range to evaluate.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnRunMlEval.setEnabled(false);
        btnRunMlEval.setText("EVALUATING INFERENCE MODELS...");

        new Thread(() -> {
            int evaluated = 0;
            int anomalous = 0;
            StringBuilder logBuilder = new StringBuilder();
            logBuilder.append(String.format(Locale.US, "// ML Evaluation run on %d APs:\n", networks.size()));

            for (Network n : networks) {
                if (n == null) continue;
                evaluated++;
                OnDeviceMLInference.FullInferenceResult res = OnDeviceMLInference.infer(n);
                if (res.anomaly.isAnomalous) {
                    anomalous++;
                    if (logBuilder.length() < 1200) {
                        logBuilder.append(String.format(Locale.US,
                                "(!) [ANOMALY %.2f] %s | %s | %s\n",
                                res.anomaly.score,
                                (n.getSsid() != null && !n.getSsid().isEmpty()) ? n.getSsid() : "<HIDDEN>",
                                n.getBssid() != null ? n.getBssid() : "N/A",
                                res.anomaly.reason));
                    }
                }
            }

            // Also check evil twins
            List<AdvancedEvilTwinDetector.EvilTwinFinding> evilTwins =
                    OnDeviceMLInference.evaluateEvilTwins(networks);

            for (AdvancedEvilTwinDetector.EvilTwinFinding et : evilTwins) {
                logBuilder.append(String.format(Locale.US,
                        "[EVIL-TWIN] %s: %s\n", et.ssid, et.diagnostic));
            }

            final int finalEvaluated = evaluated;
            final int finalAnomalous = anomalous;
            final int finalEvilTwins = evilTwins.size();
            final String finalLog = logBuilder.toString();

            handler.post(() -> {
                btnRunMlEval.setEnabled(true);
                btnRunMlEval.setText("EXECUTE FULL ON-DEVICE ML EVALUATION");
                updateMetrics();
                if (mlTelemetryLog != null) {
                    mlTelemetryLog.setText(finalLog);
                }

                if (getContext() != null) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("ML Threat Intelligence Assessment")
                            .setMessage(String.format(Locale.US,
                                    "Evaluated Nodes: %d\nAnomalous Signatures: %d\nEvil-Twin Attacks: %d\n\nPosture: %s",
                                    finalEvaluated,
                                    finalAnomalous,
                                    finalEvilTwins,
                                    (finalAnomalous == 0 && finalEvilTwins == 0) ? "SECURE (Normal)" : "ALERT: Potential Threats Flagged"))
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }).start();
    }
}
