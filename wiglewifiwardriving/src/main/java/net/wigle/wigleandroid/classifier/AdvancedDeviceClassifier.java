package net.wigle.wigleandroid.classifier;

import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;

import java.util.List;
import java.util.Locale;

/**
 * Cyberpunk Wardriver HX OS Advanced Device Classifier.
 * Classifies scanned networks and BLE devices into Flock Safety / ALPR,
 * IoT devices, and Low-Range BLE trackers.
 */
public class AdvancedDeviceClassifier {

    public enum DeviceCategory {
        FLOCK_ALPR("FLOCK / ALPR", "#FF1F8F"),   // Neon Pink
        ALPR_SYSTEM("ALPR CAM", "#FFE600"),       // Cyber Yellow
        IOT_DEVICE("IoT DEVICE", "#00FFF2"),      // Neon Cyan
        LOW_RANGE_BLE("BLE TRACKER", "#00FF66"),  // Neon Green
        STANDARD("", "#00FFF2");

        private final String label;
        private final String colorHex;

        DeviceCategory(String label, String colorHex) {
            this.label = label;
            this.colorHex = colorHex;
        }

        public String getLabel() {
            return label;
        }

        public String getColorHex() {
            return colorHex;
        }

        public int getColor() {
            return Color.parseColor(colorHex);
        }

        public boolean isSpecial() {
            return this != STANDARD;
        }
    }

    @NonNull
    public static DeviceCategory classify(@Nullable final Network network) {
        if (network == null) {
            return DeviceCategory.STANDARD;
        }

        final String ssid = network.getSsid() != null ? network.getSsid().toUpperCase(Locale.US) : "";
        final String bssid = network.getBssid() != null ? network.getBssid().toUpperCase(Locale.US) : "";
        final String capabilities = network.getCapabilities() != null ? network.getCapabilities().toUpperCase(Locale.US) : "";
        final NetworkType type = network.getType();

        // 1. Flock Safety & ALPR Cameras
        if (isFlockCamera(ssid, capabilities)) {
            return DeviceCategory.FLOCK_ALPR;
        }
        if (isAlprSystem(ssid)) {
            return DeviceCategory.ALPR_SYSTEM;
        }

        // 2. Low-Range Bluetooth Trackers / Beacons
        if (type == NetworkType.BLE || type == NetworkType.BT) {
            if (isLowRangeBleTracker(network, ssid)) {
                return DeviceCategory.LOW_RANGE_BLE;
            }
        }

        // 3. IoT Devices
        if (isIotDevice(ssid, bssid)) {
            return DeviceCategory.IOT_DEVICE;
        }

        return DeviceCategory.STANDARD;
    }

    private static boolean isFlockCamera(String ssid, String capabilities) {
        return ssid.contains("FLOCK") || ssid.startsWith("FS-") || ssid.startsWith("FS_") ||
                ssid.contains("FLOCKSAFETY") || capabilities.contains("FLOCK");
    }

    private static boolean isAlprSystem(String ssid) {
        return ssid.contains("GENETEC") || ssid.contains("ELSAG") || ssid.contains("VIGILANT") ||
                ssid.contains("ALPR") || ssid.contains("LPR_") || ssid.contains("CITYCAM") ||
                ssid.contains("TRAFFICCAM") || ssid.contains("MOTOROLA_ALPR");
    }

    private static boolean isIotDevice(String ssid, String bssid) {
        if (ssid.startsWith("ESP_") || ssid.contains("ESP32") || ssid.contains("ESP8266") ||
                ssid.contains("TUYA") || ssid.contains("SONOFF") || ssid.contains("TASMOTA") ||
                ssid.contains("SMARTLIFE") || ssid.contains("SHELLY") || ssid.contains("RING-") ||
                ssid.contains("NEST-") || ssid.contains("ARLO") || ssid.contains("WYZE") ||
                ssid.contains("HIKVISION") || ssid.contains("DAHUA") || ssid.contains("REOLINK") ||
                ssid.contains("AXIS-") || ssid.contains("BLINK-") || ssid.contains("KASA") ||
                ssid.contains("WEMO") || ssid.contains("GOSUND")) {
            return true;
        }

        // OUI matching for common IoT hardware vendors (Espressif, Tuya, Hikvision, Dahua)
        return bssid.startsWith("24:0A:C4") || bssid.startsWith("30:AE:A4") || bssid.startsWith("84:0D:8E") ||
                bssid.startsWith("CC:50:E3") || bssid.startsWith("DC:4F:22") || bssid.startsWith("D8:F1:5B") ||
                bssid.startsWith("70:89:76") || bssid.startsWith("10:D5:61") || bssid.startsWith("18:69:D8") ||
                bssid.startsWith("10:1D:C1") || bssid.startsWith("40:16:3B") || bssid.startsWith("38:AF:29");
    }

    private static boolean isLowRangeBleTracker(Network network, String ssid) {
        if (ssid.contains("AIRTAG") || ssid.contains("TILE") || ssid.contains("SMARTTAG") ||
                ssid.contains("CHIPOLO") || ssid.contains("EDDYSTONE") || ssid.contains("IBEACON") ||
                ssid.contains("FINDMY")) {
            return true;
        }

        Integer mfgrId = network.getBleMfgrId();
        if (mfgrId != null) {
            // Apple (0x004C), Samsung (0x0075), Tile (0x00D2 / 0x0182)
            if (mfgrId == 0x004C || mfgrId == 0x0075 || mfgrId == 0x00D2 || mfgrId == 0x0182) {
                return true;
            }
        }

        List<String> serviceUuids = network.getBleServiceUuids();
        if (serviceUuids != null) {
            for (String uuid : serviceUuids) {
                String u = uuid.toUpperCase(Locale.US);
                if (u.contains("FEED") || u.contains("FEEC") || u.contains("FE65") ||
                        u.contains("FEAA") || u.contains("FDA5")) {
                    return true;
                }
            }
        }

        return false;
    }
}
