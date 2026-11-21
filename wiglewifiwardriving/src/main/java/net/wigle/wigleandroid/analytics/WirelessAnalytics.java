package net.wigle.wigleandroid.analytics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cyberpunk GhostH Tactical Wireless Analytics & Intelligence Suite.
 * Provides security posture assessment, channel congestion analysis & best-channel suggestions,
 * Rogue AP / Evil-Twin detection, mobile AP detection, and trilateration / RSSI smoothing algorithms.
 */
public class WirelessAnalytics {

    // --- 1. Security Posture ---
    public static class SecurityPosture {
        public int totalCount = 0;
        public int openCount = 0;
        public int wepCount = 0;
        public int wpaCount = 0;
        public int wpa2Count = 0;
        public int wpa3Count = 0;

        public float getOpenPercent() {
            return totalCount == 0 ? 0f : (openCount * 100f / totalCount);
        }

        public float getWepPercent() {
            return totalCount == 0 ? 0f : (wepCount * 100f / totalCount);
        }

        public float getWpaPercent() {
            return totalCount == 0 ? 0f : (wpaCount * 100f / totalCount);
        }

        public float getWpa2Percent() {
            return totalCount == 0 ? 0f : (wpa2Count * 100f / totalCount);
        }

        public float getWpa3Percent() {
            return totalCount == 0 ? 0f : (wpa3Count * 100f / totalCount);
        }

        public int getPostureScore() {
            if (totalCount == 0) return 100;
            // Higher score = more secure environment
            float penalty = (openCount * 40f + wepCount * 30f + wpaCount * 10f) / totalCount;
            return Math.max(0, Math.min(100, Math.round(100f - penalty)));
        }
    }

    @NonNull
    public static SecurityPosture calculateSecurityPosture(@Nullable Collection<Network> networks) {
        SecurityPosture posture = new SecurityPosture();
        if (networks == null) return posture;

        for (Network net : networks) {
            if (net == null || net.getType() != NetworkType.WIFI) continue;
            posture.totalCount++;
            String cap = net.getCapabilities() != null ? net.getCapabilities().toUpperCase(Locale.US) : "";
            if (cap.contains("WPA3") || cap.contains("SAE")) {
                posture.wpa3Count++;
            } else if (cap.contains("WPA2") || cap.contains("RSN")) {
                posture.wpa2Count++;
            } else if (cap.contains("WPA")) {
                posture.wpaCount++;
            } else if (cap.contains("WEP")) {
                posture.wepCount++;
            } else {
                posture.openCount++;
            }
        }
        return posture;
    }

    // --- 2. Channel Congestion & Best Channel Suggestions ---
    public static class ChannelReport {
        public final Map<Integer, Integer> channelCounts = new HashMap<>();
        public int bestChannel24 = 1;
        public int bestChannel5 = 36;
        public int total24Nets = 0;
        public int total5Nets = 0;
    }

    @NonNull
    public static ChannelReport analyzeChannels(@Nullable Collection<Network> networks) {
        ChannelReport report = new ChannelReport();
        if (networks == null) return report;

        for (Network net : networks) {
            if (net == null || net.getType() != NetworkType.WIFI) continue;
            int freq = net.getFrequency();
            int channel = net.getChannel();
            if (channel <= 0) continue;

            int current = report.channelCounts.containsKey(channel) ? report.channelCounts.get(channel) : 0;
            report.channelCounts.put(channel, current + 1);

            if (freq < 3000) {
                report.total24Nets++;
            } else {
                report.total5Nets++;
            }
        }

        // Evaluate 2.4 GHz non-overlapping channels: 1, 6, 11
        int c1 = getInterferenceScore(report.channelCounts, 1);
        int c6 = getInterferenceScore(report.channelCounts, 6);
        int c11 = getInterferenceScore(report.channelCounts, 11);

        if (c1 <= c6 && c1 <= c11) {
            report.bestChannel24 = 1;
        } else if (c6 <= c1 && c6 <= c11) {
            report.bestChannel24 = 6;
        } else {
            report.bestChannel24 = 11;
        }

        // Evaluate common 5 GHz channels: 36, 40, 44, 48, 149, 153, 157, 161
        int[] fiveChannels = {36, 40, 44, 48, 149, 153, 157, 161};
        int minFiveScore = Integer.MAX_VALUE;
        int best5 = 36;
        for (int ch : fiveChannels) {
            int count = report.channelCounts.containsKey(ch) ? report.channelCounts.get(ch) : 0;
            if (count < minFiveScore) {
                minFiveScore = count;
                best5 = ch;
            }
        }
        report.bestChannel5 = best5;

        return report;
    }

    private static int getInterferenceScore(Map<Integer, Integer> counts, int centerChannel) {
        int score = 0;
        for (int ch = centerChannel - 2; ch <= centerChannel + 2; ch++) {
            if (counts.containsKey(ch)) {
                int weight = (ch == centerChannel) ? 2 : 1;
                score += counts.get(ch) * weight;
            }
        }
        return score;
    }

    // --- 3. Rogue AP & Evil-Twin Detection ---
    public static class ThreatAlert {
        public final String ssid;
        public final String alertType;
        public final String description;
        public final List<String> bssids;

        public ThreatAlert(String ssid, String alertType, String description, List<String> bssids) {
            this.ssid = ssid;
            this.alertType = alertType;
            this.description = description;
            this.bssids = bssids;
        }
    }

    @NonNull
    public static List<ThreatAlert> detectRogueAPs(@Nullable Collection<Network> networks) {
        List<ThreatAlert> alerts = new ArrayList<>();
        if (networks == null) return alerts;

        Map<String, List<Network>> bySsid = new HashMap<>();
        for (Network net : networks) {
            if (net == null || net.getType() != NetworkType.WIFI) continue;
            String ssid = net.getSsid();
            if (ssid == null || ssid.trim().isEmpty() || "<hidden>".equalsIgnoreCase(ssid.trim())) {
                continue;
            }
            if (!bySsid.containsKey(ssid)) {
                bySsid.put(ssid, new ArrayList<>());
            }
            bySsid.get(ssid).add(net);
        }

        for (Map.Entry<String, List<Network>> entry : bySsid.entrySet()) {
            List<Network> list = entry.getValue();
            if (list.size() < 2) continue;

            String ssid = entry.getKey();
            Set<String> encTypes = new HashSet<>();
            Set<String> ouis = new HashSet<>();
            List<String> bssids = new ArrayList<>();

            boolean hasSecure = false;
            boolean hasOpenOrWep = false;

            for (Network n : list) {
                bssids.add(n.getBssid());
                String cap = n.getCapabilities() != null ? n.getCapabilities().toUpperCase(Locale.US) : "";
                encTypes.add(cap);
                if (n.getBssid() != null && n.getBssid().length() >= 8) {
                    ouis.add(n.getBssid().substring(0, 8));
                }
                if (cap.contains("WPA2") || cap.contains("WPA3")) hasSecure = true;
                if (!cap.contains("WPA") && !cap.contains("WEP")) hasOpenOrWep = true;
                if (cap.contains("WEP")) hasOpenOrWep = true;
            }

            // Evil-Twin / Downgrade Attack: same SSID where one is WPA2/WPA3 and another is Open or WEP!
            if (hasSecure && hasOpenOrWep) {
                alerts.add(new ThreatAlert(ssid, "CRITICAL: EVIL-TWIN / DOWNGRADE ATTACK",
                        "Identical SSID detected with downgraded security (WPA2/WPA3 vs Open/WEP). Probable honeypot/interception attack.",
                        bssids));
            } else if (ouis.size() >= 2 && list.size() >= 2) {
                // Suspicious cloned AP with disparate vendor hardware
                alerts.add(new ThreatAlert(ssid, "SUSPICIOUS: HARDWARE MISMATCH",
                        "Multiple transmitters broadcasting identical SSID across conflicting vendor OUIs (" + ouis.size() + " vendors).",
                        bssids));
            }
        }

        return alerts;
    }

    // --- 4. Mobile Hotspot AP Detection ---
    public static boolean isLikelyMobileAP(@Nullable Network network) {
        if (network == null) return false;
        String ssid = network.getSsid() != null ? network.getSsid().toUpperCase(Locale.US) : "";
        return ssid.contains("IPHONE") || ssid.contains("GALAXY") || ssid.contains("PIXEL") ||
                ssid.contains("HOTSPOT") || ssid.contains("ANDROIDAP") || ssid.contains("PORTABLE") ||
                ssid.contains("MYPHONE") || ssid.contains("ONEPLUS") || ssid.contains("MIFI") ||
                ssid.contains("NETGEAR_MOBILE") || ssid.contains("VERIZON-MIFI") ||
                ssid.contains("JIOFI") || ssid.contains("HUAWEI-E");
    }

    // --- 5. Trilateration & Path-Loss Distance Estimation ---
    /**
     * Log-distance path loss distance estimation.
     * @param rssi measured RSSI in dBm
     * @param txPower1m estimated RSSI at 1 meter (default -40 dBm for WiFi, -59 dBm for BLE)
     * @param pathLossExponent environment exponent (2.0 in free space, 3.0 in suburban, 3.5 in dense urban)
     * @return estimated distance in meters
     */
    public static double estimateDistance(int rssi, double txPower1m, double pathLossExponent) {
        if (rssi == 0) return -1.0;
        double ratio = (txPower1m - rssi) / (10.0 * pathLossExponent);
        return Math.pow(10.0, ratio);
    }

    /**
     * Exponential Moving Average (EMA) filter for RSSI smoothing.
     * @param currentSample fresh raw RSSI sample
     * @param previousFiltered previous filtered value
     * @param alpha smoothing factor (0.1 to 0.4, default 0.25)
     */
    public static double smoothRssiEMA(double currentSample, double previousFiltered, double alpha) {
        if (previousFiltered == 0.0) return currentSample;
        return (alpha * currentSample) + ((1.0 - alpha) * previousFiltered);
    }
}

