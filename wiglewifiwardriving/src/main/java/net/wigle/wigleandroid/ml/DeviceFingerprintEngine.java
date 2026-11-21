package net.wigle.wigleandroid.ml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Deep device classification using Wi-Fi Information Elements (IEs)
 * and BLE Manufacturer Data.
 */
public class DeviceFingerprintEngine {

    public static class FingerprintResult {
        public final String category;
        public final String vendorOrChipset;
        public final String capabilitiesSummary;
        public final int confidencePercent;

        public FingerprintResult(String category, String vendorOrChipset, String capabilitiesSummary, int confidencePercent) {
            this.category = category;
            this.vendorOrChipset = vendorOrChipset;
            this.capabilitiesSummary = capabilitiesSummary;
            this.confidencePercent = confidencePercent;
        }
    }

    @NonNull
    public static FingerprintResult fingerprint(@Nullable final Network network) {
        if (network == null) {
            return new FingerprintResult("UNKNOWN", "Generic", "N/A", 0);
        }

        final String ssid = network.getSsid() != null ? network.getSsid().toUpperCase(Locale.US) : "";
        final String bssid = network.getBssid() != null ? network.getBssid().toUpperCase(Locale.US) : "";
        final String cap = network.getCapabilities() != null ? network.getCapabilities().toUpperCase(Locale.US) : "";
        final NetworkType type = network.getType();

        // 1. BLE Manufacturer Data Analysis
        if (type == NetworkType.BLE || type == NetworkType.BT) {
            Integer mfgrId = network.getBleMfgrId();
            if (mfgrId != null) {
                switch (mfgrId) {
                    case 0x004C:
                        if (ssid.contains("AIRTAG") || ssid.contains("FINDMY") || ssid.contains("AIR-TAG")) {
                            return new FingerprintResult("BLE_TRACKER", "Apple AirTag / Find My", "Nearby Offline Beacon", 98);
                        }
                        return new FingerprintResult("AUDIO_PHONE", "Apple Device", "AirPods / iPhone / Mac Nearby", 90);
                    case 0x0075:
                        return new FingerprintResult("BLE_TRACKER", "Samsung SmartTag / Galaxy", "Samsung SmartThings Find", 95);
                    case 0x00D2:
                    case 0x0182:
                        return new FingerprintResult("BLE_TRACKER", "Tile Tracker", "Tile Network Beacon", 95);
                    case 0x00E0:
                        return new FingerprintResult("AUDIO_PHONE", "Google Device", "Google Fast Pair", 88);
                    case 0x0006:
                        return new FingerprintResult("COMPUTER", "Microsoft Device", "Windows Nearby Share / Swift Pair", 85);
                }
            }
        }

        // 2. Wi-Fi Information Elements (IE) & Capability Profiling
        StringBuilder techSummary = new StringBuilder();
        if (cap.contains("WPA3") || cap.contains("SAE")) {
            techSummary.append("802.11ax/WPA3 ");
        } else if (cap.contains("RSN") || cap.contains("WPA2")) {
            techSummary.append("802.11ac/WPA2 ");
        } else if (cap.contains("WEP")) {
            techSummary.append("Legacy 802.11b/WEP ");
        } else {
            techSummary.append("Open 802.11 ");
        }

        if (cap.contains("WPS")) {
            techSummary.append("[WPS-Active] ");
        }
        if (cap.contains("ESS")) {
            techSummary.append("[Infrastructure] ");
        }

        // Camera / Surveillance
        if (ssid.contains("FLOCK") || ssid.startsWith("FS-") || ssid.startsWith("FS_") ||
                ssid.contains("ALPR") || ssid.contains("GENETEC") || ssid.contains("ELSAG") ||
                ssid.contains("REKOR") || ssid.contains("VIGILANT")) {
            return new FingerprintResult("ALPR_SURVEILLANCE", "Flock / Automated License Plate Reader", techSummary.toString(), 95);
        }

        // IoT / Espressif microcontrollers
        if (ssid.startsWith("ESP_") || ssid.contains("ESP32") || ssid.contains("ESP8266") ||
                bssid.startsWith("24:0A:C4") || bssid.startsWith("30:AE:A4") || bssid.startsWith("CC:50:E3")) {
            return new FingerprintResult("IOT_EMBEDDED", "Espressif Systems (ESP32/ESP8266)", techSummary.toString(), 96);
        }

        // Raspberry Pi
        if (bssid.startsWith("B8:27:EB") || bssid.startsWith("DC:A6:32") || bssid.startsWith("E4:5F:01")) {
            return new FingerprintResult("SINGLE_BOARD_COMPUTER", "Raspberry Pi Foundation", techSummary.toString(), 94);
        }

        // Smart Home IoT
        if (ssid.contains("TUYA") || ssid.contains("SONOFF") || ssid.contains("SHELLY") ||
                ssid.contains("WYZE") || ssid.contains("RING") || ssid.contains("NEST")) {
            return new FingerprintResult("SMART_HOME_IOT", "Smart Home Appliance", techSummary.toString(), 90);
        }

        return new FingerprintResult("STANDARD_AP", "Standard WLAN Infrastructure", techSummary.toString(), 75);
    }
}

