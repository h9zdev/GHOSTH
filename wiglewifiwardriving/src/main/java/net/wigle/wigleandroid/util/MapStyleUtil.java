package net.wigle.wigleandroid.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.WiGLELibreTileProvider;
import net.wigle.wigleandroid.ui.WiGLEToast;

import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Utility for safely loading and managing OpenStreetMap styles in MapLibre for GhostH.
 * Guarantees zero-API-key operation out-of-the-box by bundling Tactical Dark,
 * Standard OpenStreetMap, and Satellite configs.
 */
public final class MapStyleUtil {

    private MapStyleUtil() {}

    public static final String OSM_DARK_ASSET = "osm_raster_style.json";
    public static final String OSM_STANDARD_ASSET = "osm_standard_style.json";
    public static final String SATELLITE_ASSET = "satellite_style.json";
    public static final String DEFAULT_FALLBACK_URL = "https://demotiles.maplibre.org/style.json";

    public static final int STYLE_TACTICAL_DARK = 0;
    public static final int STYLE_OSM_STANDARD = 1;
    public static final int STYLE_SATELLITE = 2;

    public static final String PREF_OSM_STYLE_MODE = "osmStyleMode";

    /**
     * Initializes MapLibre and configures the HTTP client with required User-Agent headers
     * and generous timeouts for tile fetching.
     */
    public static void initializeMapEngine(@NonNull final Context context) {
        try {
            final SharedPreferences prefs = context.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            WiGLELibreTileProvider.configureAuthentication(prefs);
        } catch (Throwable t) {
            Logging.error("Error initializing MapLibre HTTP engine: " + t, t);
        }
    }

    /**
     * Reads a bundled asset style JSON file from the apk.
     */
    @Nullable
    public static String getAssetStyleJson(@NonNull final Context context, @NonNull final String assetName) {
        try (InputStream is = context.getAssets().open(assetName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception ex) {
            Logging.error("Failed to read asset style " + assetName + ": " + ex, ex);
            return null;
        }
    }

    /**
     * Sets the map style safely based on preferences.
     */
    public static void applyMapStyle(@NonNull final Context context,
                                     @NonNull final MapLibreMap map,
                                     @Nullable final SharedPreferences prefs,
                                     @Nullable final Style.OnStyleLoaded onStyleLoaded) {
        final String customKey = prefs != null ? prefs.getString(PreferenceKeys.PREF_FOSS_MAPS_VECTOR_TILE_KEY, null) : null;
        final String customUrl = prefs != null ? prefs.getString(PreferenceKeys.PREF_FOSS_MAPS_VECTOR_TILE_STYLE, null) : null;

        // If user explicitly configured a custom tile server URL, try it
        if (customUrl != null && !customUrl.trim().isEmpty()) {
            final String fullUrl = (customKey != null && !customKey.trim().isEmpty())
                    ? customUrl.trim() + customKey.trim()
                    : customUrl.trim();
            try {
                Logging.info("Attempting custom map style URL: " + fullUrl);
                map.setStyle(fullUrl, style -> {
                    Logging.info("Custom map style loaded successfully");
                    if (onStyleLoaded != null) {
                        onStyleLoaded.onStyleLoaded(style);
                    }
                });
                return;
            } catch (Exception ex) {
                Logging.warn("Custom map style failed: " + ex + ", falling back to zero-key OpenStreetMap");
            }
        }

        final int styleMode = prefs != null ? prefs.getInt(PREF_OSM_STYLE_MODE, STYLE_TACTICAL_DARK) : STYLE_TACTICAL_DARK;
        applyStyleByMode(context, map, styleMode, onStyleLoaded);
    }

    /**
     * Applies a specific zero-key style by index.
     */
    public static void applyStyleByMode(@NonNull final Context context,
                                        @NonNull final MapLibreMap map,
                                        final int mode,
                                        @Nullable final Style.OnStyleLoaded onStyleLoaded) {
        final String assetName;
        switch (mode) {
            case STYLE_OSM_STANDARD:
                assetName = OSM_STANDARD_ASSET;
                break;
            case STYLE_SATELLITE:
                assetName = SATELLITE_ASSET;
                break;
            case STYLE_TACTICAL_DARK:
            default:
                assetName = OSM_DARK_ASSET;
                break;
        }

        final String json = getAssetStyleJson(context, assetName);
        if (json != null && !json.isEmpty()) {
            try {
                map.setStyle(new Style.Builder().fromJson(json), style -> {
                    Logging.info("Applied style " + assetName + " successfully (zero API key)");
                    if (onStyleLoaded != null) {
                        onStyleLoaded.onStyleLoaded(style);
                    }
                });
                return;
            } catch (Exception ex) {
                Logging.error("Failed applying style " + assetName + ": " + ex, ex);
            }
        }

        // Fallback to demo tiles if asset parsing fails
        try {
            map.setStyle(DEFAULT_FALLBACK_URL, style -> {
                Logging.info("Fallback demo style loaded");
                if (onStyleLoaded != null) {
                    onStyleLoaded.onStyleLoaded(style);
                }
            });
        } catch (Exception ex) {
            Logging.error("Failed applying fallback demo style: " + ex, ex);
        }
    }

    /**
     * Cycles to the next style mode: Tactical Dark -> OpenStreetMap Official -> Satellite -> Tactical Dark.
     */
    public static void cycleMapStyle(@NonNull final Activity activity,
                                     @NonNull final MapLibreMap map,
                                     @NonNull final SharedPreferences prefs,
                                     @Nullable final Style.OnStyleLoaded onStyleLoaded) {
        int current = prefs.getInt(PREF_OSM_STYLE_MODE, STYLE_TACTICAL_DARK);
        int next = (current + 1) % 3;
        prefs.edit().putInt(PREF_OSM_STYLE_MODE, next).apply();

        final String label;
        switch (next) {
            case STYLE_OSM_STANDARD:
                label = "OpenStreetMap Standard (Free Zero Key)";
                break;
            case STYLE_SATELLITE:
                label = "Satellite Imagery (Free Zero Key)";
                break;
            case STYLE_TACTICAL_DARK:
            default:
                label = "Tactical Dark Map (Free Zero Key)";
                break;
        }

        WiGLEToast.showOverActivity(activity, R.string.tab_map, label, Toast.LENGTH_SHORT);
        applyStyleByMode(activity, map, next, onStyleLoaded);
    }

    public static void applyBundledOsmStyle(@NonNull final Context context,
                                           @NonNull final MapLibreMap map,
                                           @Nullable final Style.OnStyleLoaded onStyleLoaded) {
        applyStyleByMode(context, map, STYLE_TACTICAL_DARK, onStyleLoaded);
    }
}
