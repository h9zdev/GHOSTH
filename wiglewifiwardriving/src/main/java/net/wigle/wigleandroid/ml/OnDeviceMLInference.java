package net.wigle.wigleandroid.ml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.model.Network;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * On-Device ML Inference Controller for GhostH.
 * Coordinates real-time threat evaluation, device fingerprinting, and anomaly scoring.
 */
public class OnDeviceMLInference {

    private static final AtomicInteger totalInferences = new AtomicInteger(0);
    private static final AtomicInteger anomaliesDetected = new AtomicInteger(0);
    private static final AtomicInteger evilTwinsDetected = new AtomicInteger(0);

    public static class FullInferenceResult {
        public final DeviceFingerprintEngine.FingerprintResult fingerprint;
        public final IsolationForestAnomalyDetector.AnomalyReport anomaly;

        public FullInferenceResult(DeviceFingerprintEngine.FingerprintResult fingerprint,
                                   IsolationForestAnomalyDetector.AnomalyReport anomaly) {
            this.fingerprint = fingerprint;
            this.anomaly = anomaly;
        }
    }

    @NonNull
    public static FullInferenceResult infer(@Nullable final Network network) {
        totalInferences.incrementAndGet();

        DeviceFingerprintEngine.FingerprintResult fp = DeviceFingerprintEngine.fingerprint(network);
        IsolationForestAnomalyDetector.AnomalyReport anomaly = IsolationForestAnomalyDetector.scoreNetwork(network);

        if (anomaly.isAnomalous) {
            anomaliesDetected.incrementAndGet();
        }

        return new FullInferenceResult(fp, anomaly);
    }

    @NonNull
    public static List<AdvancedEvilTwinDetector.EvilTwinFinding> evaluateEvilTwins(@Nullable Collection<Network> networks) {
        List<AdvancedEvilTwinDetector.EvilTwinFinding> findings = AdvancedEvilTwinDetector.evaluateNetworks(networks);
        int criticalCount = 0;
        for (AdvancedEvilTwinDetector.EvilTwinFinding f : findings) {
            if (f.severity != AdvancedEvilTwinDetector.EvilTwinSeverity.ROAMING_CLUSTER) {
                criticalCount++;
            }
        }
        evilTwinsDetected.set(criticalCount);
        return findings;
    }

    public static int getTotalInferences() {
        return totalInferences.get();
    }

    public static int getAnomaliesDetected() {
        return anomaliesDetected.get();
    }

    public static int getEvilTwinsDetected() {
        return evilTwinsDetected.get();
    }
}

