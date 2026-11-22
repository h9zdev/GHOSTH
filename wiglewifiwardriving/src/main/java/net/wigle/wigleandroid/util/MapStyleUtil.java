package net.wigle.wigleandroid.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.WiGLELibreTileProvider;

import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Utility for safely loading and managing OpenStreetMap styles in MapLibre for GhostH.
 * Guarantees zero-API-key operation out-of-the-box by bundling CartoDB Dark Matter
 * and standard OpenStreetMap tile configs.
 */
public final class MapStyleUtil {

    private MapStyleUtil() {}

    public static final String OSM_DARK_ASSET = "osm_raster_style.json";
    public static final String DEFAULT_FALLBACK_URL = "https://demotiles.maplibre.org/style.json";

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
     * Reads the bundled zero-API-key OpenStreetMap style JSON from assets.
     */
    @Nullable
    public static String getBundledOsmStyleJson(@NonNull final Context context) {
        try (InputStream is = context.getAssets().open(OSM_DARK_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception ex) {
            Logging.error("Failed to read bundled OSM style: " + ex, ex);
            return null;
        }
    }

    /**
     * Sets the map style safely. Prioritizes custom tile server URL if configured by user,
     * otherwise applies the bundled zero-key OpenStreetMap style. Never throws unhandled
     * exceptions or triggers blocking dialogs.
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

        // Apply bundled OpenStreetMap style (zero API key)
        applyBundledOsmStyle(context, map, onStyleLoaded);
    }

    /**
     * Applies the bundled zero-key OpenStreetMap Dark Matter style.
     */
    public static void applyBundledOsmStyle(@NonNull final Context context,
                                           @NonNull final MapLibreMap map,
                                           @Nullable final Style.OnStyleLoaded onStyleLoaded) {
        final String json = getBundledOsmStyleJson(context);
        if (json != null && !json.isEmpty()) {
            try {
                map.setStyle(new Style.Builder().fromJson(json), style -> {
                    Logging.info("Bundled OpenStreetMap style loaded successfully (zero API key)");
                    if (onStyleLoaded != null) {
                        onStyleLoaded.onStyleLoaded(style);
                    }
                });
                return;
            } catch (Exception ex) {
                Logging.error("Failed applying bundled style from JSON: " + ex, ex);
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
}

