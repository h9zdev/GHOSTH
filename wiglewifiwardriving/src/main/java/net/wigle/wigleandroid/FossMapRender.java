package net.wigle.wigleandroid;

import android.content.Context;

import androidx.annotation.NonNull;

import net.wigle.wigleandroid.model.Network;

import org.maplibre.android.maps.MapLibreMap;

public class FossMapRender {

    private final MapLibreMap map;

    public FossMapRender(@NonNull final Context context,
                          @NonNull final MapLibreMap map,
                          final boolean isDbResult) {
        this.map = map;
    }

    public boolean okForMapTab(final Network network) {
        return network != null && network.getLatLng() != null;
    }

    public void addItem(final Network network) {
    }

    public void clear() {
    }

    public void reCluster() {
    }

    public void updateNetwork(final Network network) {
    }

    public void destroy() {
    }
}
