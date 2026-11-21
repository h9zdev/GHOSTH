package net.wigle.wigleandroid.classifier;

import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;

import java.util.List;
import java.util.Locale;

/**
 * Cyberpunk GhostH Wardriver Advanced Device Classifier.
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

    private static final String[] FLOCK_ROUTER_OUIS = {
            "00:20:A6", "00:14:22", "4C:3B:74", "00:A0:D5", // Sierra Wireless
            "00:30:44", "94:F8:78", "48:A2:33",             // Cradlepoint
            "C4:93:00", "AC:6F:7A",                         // InHand Networks
            "00:04:F3",                                     // Digi International
            "00:1E:42",                                     // Teltonika
            "00:1A:DD"                                      // Peplink
    };

    private static final String[] ESPRESSIF_OUIS = {
            "24:0A:C4", "30:AE:A4", "84:0D:8E", "CC:50:E3", "DC:4F:22",
            "D8:F1:5B", "70:89:76", "10:D5:61", "18:69:D8", "10:1D:C1",
            "40:16:3B", "38:AF:29", "A4:CF:12", "A0:20:A6", "24:62:AB",
            "24:6F:28", "24:DC:C3", "3C:61:05", "3C:71:BF", "48:3F:DA",
            "48:55:19", "54:5A:A6", "68:C6:3A", "7C:DF:A1", "80:7D:3A",
            "8C:AA:B5", "94:B9:7E", "AC:67:B2", "B4:E6:2D", "BC:DD:C2",
            "C4:4F:33", "C8:2B:96"
    };

    private static final String[] RPI_OUIS = {
            "B8:27:EB", "DC:A6:32", "E4:5F:01", "28:CD:C1"
    };

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
        if (isFlockCamera(ssid, bssid, capabilities)) {
            return DeviceCategory.FLOCK_ALPR;
        }
        if (isAlprSystem(ssid, capabilities)) {
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

    private static boolean isFlockCamera(String ssid, String bssid, String capabilities) {
        if (ssid.contains("FLOCK") || ssid.startsWith("FS-") || ssid.startsWith("FS_") ||
                ssid.contains("FLOCKSAFETY") || ssid.contains("FALCON") || ssid.contains("SPARROW") ||
                ssid.contains("CONDOR") || ssid.contains("RAVEN") || ssid.startsWith("PIG-") ||
                ssid.startsWith("PENGUIN-") || ssid.startsWith("OWL-") || ssid.contains("TALON") ||
                ssid.contains("EXT-FS") || capabilities.contains("FLOCK")) {
            return true;
        }

        for (String oui : FLOCK_ROUTER_OUIS) {
            if (bssid.startsWith(oui)) {
                // Cellular modem commonly deployed with Flock cameras
                if (ssid.isEmpty() || ssid.contains("FS") || ssid.contains("CAM") || ssid.contains("SEC")) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isAlprSystem(String ssid, String capabilities) {
        return ssid.contains("GENETEC") || ssid.contains("AUTOVU") || ssid.contains("SHARPV") ||
                ssid.contains("ELSAG") || ssid.contains("MPH-900") || ssid.contains("PLATEHUNTER") ||
                ssid.contains("VIGILANT") || ssid.contains("REKOR") || ssid.contains("LEONARDO") ||
                ssid.contains("PIPS") || ssid.contains("SPIKE") || ssid.contains("NEOLOGY") ||
                ssid.contains("MOTOROLA_ALPR") || ssid.contains("MOTO_ALPR") || ssid.contains("L5M") ||
                ssid.contains("L6Q") || ssid.contains("ALPR") || ssid.contains("LPR_") ||
                ssid.contains("LPR-") || ssid.contains("CITYCAM") || ssid.contains("TRAFFICCAM") ||
                ssid.contains("SPEEDCAM") || ssid.contains("REDLIGHT") || ssid.contains("PLATECAM") ||
                capabilities.contains("ALPR");
    }

    private static boolean isIotDevice(String ssid, String bssid) {
        if (ssid.startsWith("ESP_") || ssid.contains("ESP32") || ssid.contains("ESP8266") ||
                ssid.contains("TUYA") || ssid.contains("SONOFF") || ssid.contains("TASMOTA") ||
                ssid.contains("SMARTLIFE") || ssid.contains("SHELLY") || ssid.contains("RING-") ||
                ssid.contains("RING_") || ssid.contains("NEST-") || ssid.contains("ARLO") ||
                ssid.contains("WYZE") || ssid.contains("HIKVISION") || ssid.contains("DAHUA") ||
                ssid.contains("REOLINK") || ssid.contains("AXIS-") || ssid.contains("BLINK-") ||
                ssid.contains("KASA") || ssid.contains("TP-LINK_SMART") || ssid.contains("WEMO") ||
                ssid.contains("GOSUND") || ssid.contains("AQARA") || ssid.contains("YEELIGHT") ||
                ssid.contains("MEROSS") || ssid.contains("GOVEE") || ssid.contains("NANOLEAF") ||
                ssid.contains("EUFY")) {
            return true;
        }

        for (String oui : ESPRESSIF_OUIS) {
            if (bssid.startsWith(oui)) {
                return true;
            }
        }

        for (String oui : RPI_OUIS) {
            if (bssid.startsWith(oui)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isLowRangeBleTracker(Network network, String ssid) {
        if (ssid.contains("AIRTAG") || ssid.contains("AIR-TAG") || ssid.contains("FINDMY") ||
                ssid.contains("TILE") || ssid.contains("SMARTTAG") || ssid.contains("CHIPOLO") ||
                ssid.contains("PEBBLEBEE") || ssid.contains("FLIPPER") || ssid.contains("FLPR") ||
                ssid.contains("EDDYSTONE") || ssid.contains("IBEACON") || ssid.contains("ALTBEACON")) {
            return true;
        }

        Integer mfgrId = network.getBleMfgrId();
        if (mfgrId != null) {
            // Apple (0x004C), Samsung (0x0075), Tile (0x00D2 / 0x0182), Chipolo (0x0286)
            if (mfgrId == 0x004C || mfgrId == 0x0075 || mfgrId == 0x00D2 ||
                    mfgrId == 0x0182 || mfgrId == 0x0286) {
                return true;
            }
        }

        List<String> serviceUuids = network.getBleServiceUuids();
        if (serviceUuids != null) {
            for (String uuid : serviceUuids) {
                String u = uuid.toUpperCase(Locale.US);
                if (u.contains("FEED") || u.contains("FEEC") || u.contains("FE65") ||
                        u.contains("FEAA") || u.contains("FDA5") || u.contains("FD6F")) {
                    return true;
                }
            }
        }

        return false;
    }
}
