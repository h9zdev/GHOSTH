package net.wigle.wigleandroid;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.classifier.AdvancedDeviceClassifier;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.util.Logging;

import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenStreetMap MapLibre Device & Marker Renderer for GhostH.
 * Plots and tracks all nearby Wi-Fi, BLE, and cellular devices on OpenStreetMap in real time.
 */
public class FossMapRender {

    private final Context context;
    private final MapLibreMap map;
    private final boolean isDbResult;
    private final Map<String, Marker> markers = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isDestroyed = false;

    public FossMapRender(@NonNull final Context context,
                         @NonNull final MapLibreMap map,
                         final boolean isDbResult) {
        this.context = context;
        this.map = map;
        this.isDbResult = isDbResult;
    }

    public boolean okForMapTab(@Nullable final Network network) {
        return network != null && network.getLatLng() != null &&
                (network.getLatLng().latitude != 0d || network.getLatLng().longitude != 0d);
    }

    public void addItem(@Nullable final Network network) {
        if (isDestroyed || !okForMapTab(network)) {
            return;
        }

        final String bssid = network.getBssid();
        if (bssid == null) return;

        mainHandler.post(() -> {
            if (isDestroyed || map == null) return;
            try {
                final net.wigle.wigleandroid.model.LatLng netLL = network.getLatLng();
                if (netLL == null) return;

                final LatLng mapLL = new LatLng(netLL.latitude, netLL.longitude);
                final String ssid = network.getSsid() != null && !network.getSsid().isEmpty()
                        ? network.getSsid() : "<HIDDEN>";

                final AdvancedDeviceClassifier.DeviceCategory cat = AdvancedDeviceClassifier.classify(network);
                final String catPrefix = cat.isSpecial() ? "[" + cat.getLabel() + "] " : "";

                final String snippet = catPrefix + bssid + " | " + network.getLevel() + " dBm | "
                        + (network.getFrequency() > 0 ? network.getFrequency() + "MHz" : network.getType().name());

                final Marker existing = markers.get(bssid);
                if (existing != null) {
                    existing.setPosition(mapLL);
                    existing.setSnippet(snippet);
                } else {
                    final Marker marker = map.addMarker(new MarkerOptions()
                            .position(mapLL)
                            .setTitle(ssid)
                            .setSnippet(snippet));
                    if (marker != null) {
                        markers.put(bssid, marker);
                    }
                }
            } catch (Exception ex) {
                Logging.error("FossMapRender addItem failed for " + bssid + ": " + ex, ex);
            }
        });
    }

    public void updateNetwork(@Nullable final Network network) {
        addItem(network);
    }

    public void clear() {
        mainHandler.post(() -> {
            markers.clear();
            if (map != null && !isDestroyed) {
                try {
                    map.clear();
                } catch (Exception ex) {
                    Logging.error("FossMapRender clear failed: " + ex, ex);
                }
            }
        });
    }

    public void reCluster() {
        // MapLibre native annotations re-render
    }

    public void destroy() {
        isDestroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        markers.clear();
    }
}
