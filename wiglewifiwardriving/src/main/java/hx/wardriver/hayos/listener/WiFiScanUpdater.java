package hx.wardriver.hayos.listener;

import android.location.Location;

public interface WiFiScanUpdater {
    void handleWiFiSeen(final String bssid, final Integer rssi, Location location);
}
