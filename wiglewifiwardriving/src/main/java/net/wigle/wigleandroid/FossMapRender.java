package net.wigle.wigleandroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.classifier.AdvancedDeviceClassifier;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.util.Logging;

import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenStreetMap MapLibre Device & Marker Renderer for GhostH.
 * Plots and tracks all nearby Wi-Fi, BLE, and cellular devices on OpenStreetMap in real time
 * with high-contrast tactical cyber blip markers on dark tiles.
 */
public class FossMapRender {

    private final Context context;
    private final MapLibreMap map;
    private final boolean isDbResult;
    private final Map<String, Marker> markers = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isDestroyed = false;

    // Distinct tactical markers
    private final Icon iconWifi;
    private final Icon iconBt;
    private final Icon iconThreat;
    private final Icon iconIot;
    private final Icon iconCell;

    public FossMapRender(@NonNull final Context context,
                         @NonNull final MapLibreMap map,
                         final boolean isDbResult) {
        this.context = context;
        this.map = map;
        this.isDbResult = isDbResult;

        final IconFactory factory = IconFactory.getInstance(context);
        this.iconWifi = factory.fromBitmap(createBlipBitmap(Color.parseColor("#00FFF2")));  // Neon Cyan
        this.iconBt = factory.fromBitmap(createBlipBitmap(Color.parseColor("#3884FF")));    // Electric Blue
        this.iconThreat = factory.fromBitmap(createBlipBitmap(Color.parseColor("#FF1F8F")));// Neon Pink (Flock/ALPR)
        this.iconIot = factory.fromBitmap(createBlipBitmap(Color.parseColor("#FFE600")));   // Cyber Yellow
        this.iconCell = factory.fromBitmap(createBlipBitmap(Color.parseColor("#FF8800")));  // Neon Amber
    }

    private static Bitmap createBlipBitmap(int color) {
        int size = 26;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Dark outer halo
        p.setColor(Color.parseColor("#CC05070E"));
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(size / 2f, size / 2f, (size / 2f) - 1f, p);

        // Colored ring
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f);
        c.drawCircle(size / 2f, size / 2f, (size / 2f) - 3f, p);

        // Core glowing fill
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawCircle(size / 2f, size / 2f, (size / 2f) - 6f, p);

        return bmp;
    }

    private Icon getIconForNetwork(@Nullable final Network network) {
        if (network == null) return iconWifi;

        final AdvancedDeviceClassifier.DeviceCategory cat = AdvancedDeviceClassifier.classify(network);
        if (cat == AdvancedDeviceClassifier.DeviceCategory.FLOCK_ALPR ||
                cat == AdvancedDeviceClassifier.DeviceCategory.ALPR_SYSTEM) {
            return iconThreat;
        }

        final NetworkType type = network.getType();
        if (type == NetworkType.BT || type == NetworkType.BLE) {
            return iconBt;
        }
        if (type == NetworkType.GSM || type == NetworkType.CDMA ||
                type == NetworkType.LTE || type == NetworkType.NR) {
            return iconCell;
        }
        if (cat == AdvancedDeviceClassifier.DeviceCategory.IOT_DEVICE) {
            return iconIot;
        }

        return iconWifi;
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

                final Icon blipIcon = getIconForNetwork(network);
                final Marker existing = markers.get(bssid);
                if (existing != null) {
                    existing.setPosition(mapLL);
                    existing.setSnippet(snippet);
                    existing.setIcon(blipIcon);
                } else {
                    final Marker marker = map.addMarker(new MarkerOptions()
                            .position(mapLL)
                            .icon(blipIcon)
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
