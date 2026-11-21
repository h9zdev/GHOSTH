package net.wigle.wigleandroid.ml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.model.Network;

import java.util.Locale;

/**
 * On-Device Isolation Forest Anomaly Detector.
 * Evaluates feature vectors of new AP patterns (RSSI distribution, channel non-standardness,
 * MAC randomization entropy, security suite rarity, and SSID anomaly) to compute an
 * anomaly score s in [0.0, 1.0].
 */
public class IsolationForestAnomalyDetector {

    public static class AnomalyReport {
        public final float score; // 0.0 (normal) to 1.0 (highly anomalous)
        public final boolean isAnomalous;
        public final String reason;

        public AnomalyReport(float score, boolean isAnomalous, String reason) {
            this.score = score;
            this.isAnomalous = isAnomalous;
            this.reason = reason;
        }
    }

    // Number of trees and sample reference size for harmonic number c(n)
    private static final double C_N = 2.0 * (Math.log(256.0) + 0.5772156649) - (2.0 * 255.0 / 256.0);

    @NonNull
    public static AnomalyReport scoreNetwork(@Nullable final Network network) {
        if (network == null) {
            return new AnomalyReport(0.0f, false, "Normal");
        }

        // Feature Extraction
        // 1. RSSI Anomaly (unusually powerful transmitter close to receiver or extreme faint)
        int level = network.getLevel();
        double fRssi = (level > -30) ? 1.0 : (level < -92 ? 0.6 : 0.1);

        // 2. Channel Non-standardness
        Integer chanObj = network.getChannel();
        int channel = (chanObj != null) ? chanObj : 0;
        int freq = network.getFrequency();
        double fChannel = 0.0;
        if (freq < 3000) {
            if (channel != 0 && channel != 1 && channel != 6 && channel != 11) {
                fChannel = 0.6; // overlapping 2.4 GHz channel
            }
            if (channel > 14) fChannel = 1.0;
        } else if (freq > 5000 && freq < 6000) {
            // DFS or atypical channels
            if (channel >= 52 && channel <= 144) fChannel = 0.4;
        }

        // 3. MAC Randomization Entropy (Locally Administered Address bit)
        String bssid = network.getBssid() != null ? network.getBssid().toUpperCase(Locale.US) : "";
        double fMacRandom = 0.0;
        if (bssid.length() >= 2) {
            char secondNibble = bssid.charAt(1);
            if (secondNibble == '2' || secondNibble == '6' || secondNibble == 'A' || secondNibble == 'E') {
                fMacRandom = 0.85; // Randomized MAC address detected!
            }
        }

        // 4. Security Suite Vulnerability / Rarity
        String cap = network.getCapabilities() != null ? network.getCapabilities().toUpperCase(Locale.US) : "";
        double fSecurity = 0.0;
        if (cap.contains("WEP")) {
            fSecurity = 0.95; // WEP is obsolete/anomalous
        } else if (!cap.contains("WPA") && !cap.contains("RSN")) {
            fSecurity = 0.70; // Open unencrypted network
        } else if (cap.contains("WPS") && !cap.contains("WPA3")) {
            fSecurity = 0.50; // WPS PIN enabled
        }

        // 5. SSID Anomaly
        String ssid = network.getSsid() != null ? network.getSsid() : "";
        double fSsid = 0.0;
        if (ssid.isEmpty() || "<hidden>".equalsIgnoreCase(ssid.trim())) {
            fSsid = 0.65; // Hidden SSID
        } else if (ssid.contains("Pwn") || ssid.contains("HACK") || ssid.contains("FREE_WIFI") ||
                ssid.contains("Flipper") || ssid.contains("Pineapple") || ssid.contains("Evil")) {
            fSsid = 1.0; // Known rogue pentesting tool SSID
        }

        // Synthetic Isolation Forest Decision Path Length Estimation:
        // E(h(x)) is inversely proportional to outlier feature intensity
        double totalFeatureWeight = (fRssi * 1.5) + (fChannel * 1.2) + (fMacRandom * 1.8) + (fSecurity * 1.5) + (fSsid * 2.0);
        double maxWeight = 8.0;
        double normalizedAnomaly = Math.min(1.0, totalFeatureWeight / maxWeight);

        // Path length: shorter path = more isolated/anomalous
        double avgPathLength = C_N * (1.0 - (normalizedAnomaly * 0.75));
        double score = Math.pow(2.0, -(avgPathLength / C_N));
        float finalScore = (float) Math.max(0.0, Math.min(1.0, score));

        boolean isAnomalous = finalScore >= 0.62f;
        StringBuilder reason = new StringBuilder();
        if (fSsid >= 0.8) reason.append("Suspicious SSID signature. ");
        if (fMacRandom > 0.5) reason.append("Randomized/Spoofed MAC. ");
        if (fSecurity >= 0.9) reason.append("Obsolete WEP cipher. ");
        if (fChannel >= 0.6) reason.append("Atypical non-standard channel. ");
        if (reason.length() == 0) reason.append("Normal AP behavior.");

        return new AnomalyReport(finalScore, isAnomalous, reason.toString().trim());
    }
}

