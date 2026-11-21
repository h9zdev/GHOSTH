package net.wigle.wigleandroid.classifier;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;

import java.util.Locale;

/**
 * Fingerprints wireless devices (Wi-Fi, Bluetooth, BLE) into specific hardware classes
 * such as TV, Headphones/Audio, Car, Camera/ALPR, Phone, Tracker, and IoT.
 */
public class DeviceTypeClassifier {

    public enum DeviceType {
        TV("TV", R.drawable.ic_device_tv),
        HEADPHONE("AUDIO", R.drawable.ic_device_headphone),
        CAR("CAR", R.drawable.ic_device_car),
        CAM("CAM", R.drawable.ic_device_cam),
        PHONE("PHONE", R.drawable.ic_device_phone),
        TRACKER("TRACKER", R.drawable.ic_device_tracker),
        IOT("IoT", R.drawable.ic_device_iot),
        WIFI("WIFI", R.drawable.ic_wifi_sm),
        BLUETOOTH("BT", R.drawable.ic_bt_sm),
        CELL("CELL", R.drawable.ic_cell_sm);

        private final String label;
        private final int iconRes;

        DeviceType(String label, @DrawableRes int iconRes) {
            this.label = label;
            this.iconRes = iconRes;
        }

        public String getLabel() {
            return label;
        }

        @DrawableRes
        public int getIconRes() {
            return iconRes;
        }
    }

    @NonNull
    public static DeviceType classify(@Nullable final Network network) {
        if (network == null) {
            return DeviceType.WIFI;
        }

        final NetworkType netType = network.getType();
        final String ssid = network.getSsid() != null ? network.getSsid().toUpperCase(Locale.US) : "";
        final String bssid = network.getBssid() != null ? network.getBssid().toUpperCase(Locale.US) : "";
        final String capabilities = network.getCapabilities() != null ? network.getCapabilities().toUpperCase(Locale.US) : "";

        // 1. Camera / ALPR / Surveillance
        if (ssid.contains("FLOCK") || ssid.startsWith("FS-") || ssid.startsWith("FS_") ||
                ssid.contains("CAM") || ssid.contains("ALPR") || ssid.contains("AUTOVU") ||
                ssid.contains("VIGILANT") || ssid.contains("GENETEC") || ssid.contains("ELSAG") ||
                ssid.contains("RING") || ssid.contains("NEST") || ssid.contains("WYZE") ||
                ssid.contains("ARLO") || ssid.contains("HIKVISION") || ssid.contains("DAHUA") ||
                ssid.contains("REOLINK") || ssid.contains("BLINK") || ssid.contains("EYE") ||
                capabilities.contains("FLOCK") || capabilities.contains("ALPR")) {
            return DeviceType.CAM;
        }

        // 2. Automotive / Vehicle
        if (ssid.contains("CAR") || ssid.contains("SYNC") || ssid.contains("CARPLAY") ||
                ssid.contains("TESLA") || ssid.contains("BMW") || ssid.contains("AUDI") ||
                ssid.contains("MERCEDES") || ssid.contains("BENZ") || ssid.contains("FORD") ||
                ssid.contains("CHEVY") || ssid.contains("CHEVROLET") || ssid.contains("TOYOTA") ||
                ssid.contains("HONDA") || ssid.contains("HYUNDAI") || ssid.contains("KIA") ||
                ssid.contains("SUBARU") || ssid.contains("VOLVO") || ssid.contains("NISSAN") ||
                ssid.contains("PORSCHE") || ssid.contains("MAZDA") || ssid.contains("AUTO") ||
                ssid.contains("UCONNECT") || ssid.contains("MYLINK")) {
            return DeviceType.CAR;
        }

        // 3. Headphones / Audio / Speakers
        if (ssid.contains("HEADPHONE") || ssid.contains("EARBUD") || ssid.contains("AIRPOD") ||
                ssid.contains("BUDS") || ssid.contains("BOSE") || ssid.contains("BEATS") ||
                ssid.contains("JBL") || ssid.contains("SONY WH-") || ssid.contains("SONY WF-") ||
                ssid.contains("WH-1000") || ssid.contains("WF-1000") || ssid.contains("SPEAKER") ||
                ssid.contains("AUDIO") || ssid.contains("SOUNDBAR") || ssid.contains("HEADSET") ||
                ssid.contains("JABRA") || ssid.contains("SENNHEISER") || ssid.contains("MARSHALL") ||
                ssid.contains("ANKER SOUNDCORE") || ssid.contains("SOUNDCORE")) {
            return DeviceType.HEADPHONE;
        }

        // 4. Smart TV / Streaming Displays
        if (ssid.contains("TV") || ssid.contains("ROKU") || ssid.contains("CHROMECAST") ||
                ssid.contains("BRAVIA") || ssid.contains("SAMSUNG TV") || ssid.contains("LG TV") ||
                ssid.contains("FIRETV") || ssid.contains("FIRE STICK") || ssid.contains("APPLE TV") ||
                ssid.contains("VIZIO") || ssid.contains("TCL") || ssid.contains("SHIELD") ||
                ssid.contains("DISPLAY") || ssid.contains("KODI")) {
            return DeviceType.TV;
        }

        // 5. Trackers / Find My / Beacons
        if (ssid.contains("AIRTAG") || ssid.contains("TILE") || ssid.contains("SMARTTAG") ||
                ssid.contains("CHIPOLO") || ssid.contains("PEBBLEBEE") || ssid.contains("FLIPPER") ||
                ssid.contains("EDDYSTONE") || ssid.contains("IBEACON") || ssid.contains("FINDMY")) {
            return DeviceType.TRACKER;
        }
        if (network.getBleMfgrId() != null) {
            int mfgr = network.getBleMfgrId();
            if (mfgr == 0x004C || mfgr == 0x0075 || mfgr == 0x00D2 || mfgr == 0x0182 || mfgr == 0x0286) {
                return DeviceType.TRACKER;
            }
        }

        // 6. Phone / Tablet
        if (ssid.contains("IPHONE") || ssid.contains("GALAXY") || ssid.contains("PIXEL") ||
                ssid.contains("XIAOMI") || ssid.contains("REDMI") || ssid.contains("ONEPLUS") ||
                ssid.contains("HUAWEI") || ssid.contains("IPAD") || ssid.contains("PHONE") ||
                ssid.contains("TAB") || ssid.contains("SURFACE") || ssid.contains("MACBOOK") ||
                ssid.contains("LAPTOP")) {
            return DeviceType.PHONE;
        }

        // 7. IoT / Smart Home
        if (ssid.startsWith("ESP_") || ssid.contains("ESP32") || ssid.contains("ESP8266") ||
                ssid.contains("TUYA") || ssid.contains("SONOFF") || ssid.contains("TASMOTA") ||
                ssid.contains("SHELLY") || ssid.contains("KASA") || ssid.contains("WEMO") ||
                ssid.contains("GOSUND") || ssid.contains("AQARA") || ssid.contains("YEELIGHT") ||
                ssid.contains("MEROSS") || ssid.contains("GOVEE") || ssid.contains("NANOLEAF") ||
                ssid.contains("SMART")) {
            return DeviceType.IOT;
        }

        // Fallbacks based on NetworkType
        if (netType == NetworkType.BLE || netType == NetworkType.BT) {
            return DeviceType.BLUETOOTH;
        }
        if (netType == NetworkType.GSM || netType == NetworkType.CDMA ||
                netType == NetworkType.LTE || netType == NetworkType.NR) {
            return DeviceType.CELL;
        }

        return DeviceType.WIFI;
    }

    @DrawableRes
    public static int getDeviceIconRes(@Nullable final Network network) {
        return classify(network).getIconRes();
    }
}

