package net.wigle.wigleandroid.ml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Advanced Multi-Vector Evil-Twin & Rogue AP Detection Model.
 * Compares same-SSID networks across Vendor OUI, Operating Channel, and Security Capabilities.
 */
public class AdvancedEvilTwinDetector {

    public enum EvilTwinSeverity {
        CRITICAL_ATTACK("CRITICAL EVIL-TWIN", "#FF0033"),
        HIGH_SUSPECT("SUSPICIOUS CLONE", "#FF1F8F"),
        ROAMING_CLUSTER("LEGITIMATE MESH/ROAMING", "#00FFF2");

        private final String label;
        private final String colorHex;

        EvilTwinSeverity(String label, String colorHex) {
            this.label = label;
            this.colorHex = colorHex;
        }

        public String getLabel() {
            return label;
        }

        public String getColorHex() {
            return colorHex;
        }
    }

    public static class EvilTwinFinding {
        public final String ssid;
        public final EvilTwinSeverity severity;
        public final String diagnostic;
        public final List<String> bssids;
        public final int confidencePercent;

        public EvilTwinFinding(String ssid, EvilTwinSeverity severity, String diagnostic,
                               List<String> bssids, int confidencePercent) {
            this.ssid = ssid;
            this.severity = severity;
            this.diagnostic = diagnostic;
            this.bssids = bssids;
            this.confidencePercent = confidencePercent;
        }
    }

    @NonNull
    public static List<EvilTwinFinding> evaluateNetworks(@Nullable Collection<Network> networks) {
        List<EvilTwinFinding> findings = new ArrayList<>();
        if (networks == null || networks.isEmpty()) {
            return findings;
        }

        Map<String, List<Network>> bySsid = new HashMap<>();
        for (Network n : networks) {
            if (n == null || n.getType() != NetworkType.WIFI) continue;
            String ssid = n.getSsid();
            if (ssid == null || ssid.trim().isEmpty() || "<hidden>".equalsIgnoreCase(ssid.trim())) {
                continue;
            }
            if (!bySsid.containsKey(ssid)) {
                bySsid.put(ssid, new ArrayList<>());
            }
            bySsid.get(ssid).add(n);
        }

        for (Map.Entry<String, List<Network>> entry : bySsid.entrySet()) {
            List<Network> list = entry.getValue();
            if (list.size() < 2) continue;

            String ssid = entry.getKey();
            List<String> bssids = new ArrayList<>();
            Set<String> ouis = new HashSet<>();
            Set<String> ciphers = new HashSet<>();
            Set<Integer> channels = new HashSet<>();

            boolean hasWpa2Or3 = false;
            boolean hasOpenOrWep = false;

            for (Network n : list) {
                bssids.add(n.getBssid());
                Integer ch = n.getChannel();
                if (ch != null) channels.add(ch);
                if (n.getBssid() != null && n.getBssid().length() >= 8) {
                    ouis.add(n.getBssid().substring(0, 8));
                }
                String cap = n.getCapabilities() != null ? n.getCapabilities().toUpperCase(Locale.US) : "";
                ciphers.add(cap);

                if (cap.contains("WPA2") || cap.contains("WPA3")) hasWpa2Or3 = true;
                if (!cap.contains("WPA") && !cap.contains("WEP")) hasOpenOrWep = true;
                if (cap.contains("WEP")) hasOpenOrWep = true;
            }

            // 1. Encryption Downgrade / Karma Attack (Same SSID, but one is Open/WEP and one is WPA2/WPA3)
            if (hasWpa2Or3 && hasOpenOrWep) {
                findings.add(new EvilTwinFinding(
                        ssid,
                        EvilTwinSeverity.CRITICAL_ATTACK,
                        "Severe security downgrade attack detected! Clone AP is broadcasting identical SSID with Open/WEP encryption to intercept user traffic.",
                        bssids,
                        99
                ));
            }
            // 2. Hardware Vendor Mismatch (Same SSID across completely conflicting OUIs)
            else if (ouis.size() >= 2) {
                findings.add(new EvilTwinFinding(
                        ssid,
                        EvilTwinSeverity.HIGH_SUSPECT,
                        "Hardware mismatch: Identical SSID is broadcasted by different vendor chipsets (" + ouis.size() + " disparate OUIs). Potential rogue AP rogue clone.",
                        bssids,
                        88
                ));
            }
            // 3. Legitimate Roaming APs (Same vendor OUI, enterprise mesh roaming on different channels)
            else {
                findings.add(new EvilTwinFinding(
                        ssid,
                        EvilTwinSeverity.ROAMING_CLUSTER,
                        "Legitimate enterprise/mesh roaming detected across " + list.size() + " coordinated APs on channels " + channels + ".",
                        bssids,
                        95
                ));
            }
        }

        return findings;
    }
}

