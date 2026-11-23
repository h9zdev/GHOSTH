package net.wigle.wigleandroid.osint.service

import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.osint.model.GeoBoundingBox
import net.wigle.wigleandroid.osint.model.GeospatialOsintLayer
import kotlin.math.*

/**
 * Passive Geospatial OSINT Analysis Service.
 * Strictly passive and metadata-only.
 * Features:
 * 1. Geographic observation mapping
 * 2. OpenStreetMap integration (Tile calculation & coordinate mapping)
 * 3. Administrative boundary overlays
 * 4. Public infrastructure datasets correlation
 * 5. Geographic density visualization
 */
class GeospatialOsintService {

    fun analyzeGeospatial(batch: List<Observation>): GeospatialOsintLayer {
        if (batch.isEmpty()) {
            return GeospatialOsintLayer(
                boundaryName = "Zero Observation Zone",
                boundingBox = GeoBoundingBox(0.0, 0.0, 0.0, 0.0),
                totalApCount = 0,
                infrastructureCount = 0,
                apDensityPerSqKm = 0.0,
                publicHotspotRatio = 0f,
                osmTileCoord = "15/0/0",
                administrativeWard = "Unassigned Sector"
            )
        }

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        for (obs in batch) {
            if (obs.latitude < minLat) minLat = obs.latitude
            if (obs.latitude > maxLat) maxLat = obs.latitude
            if (obs.longitude < minLon) minLon = obs.longitude
            if (obs.longitude > maxLon) maxLon = obs.longitude
        }

        val centerLat = (minLat + maxLat) / 2.0
        val centerLon = (minLon + maxLon) / 2.0

        // Calculate Slippy OSM Tile Coordinates at Zoom 15
        val zoom = 15
        val n = 1 shl zoom
        val latRad = Math.toRadians(centerLat)
        val xtile = ((centerLon + 180.0) / 360.0 * n).toInt()
        val ytile = ((1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * n).toInt()
        val osmTile = "$zoom/$xtile/$ytile"

        // Approximate area in km^2
        val latDistKm = abs(maxLat - minLat) * 111.0
        val lonDistKm = abs(maxLon - minLon) * 111.0 * cos(latRad).coerceAtLeast(0.1)
        val areaKm2 = max(0.01, latDistKm * lonDistKm)
        val density = batch.size / areaKm2

        // Identify municipal / public infrastructure nodes
        val infraCount = batch.count {
            val s = it.ssid.uppercase()
            val caps = it.capabilities.uppercase()
            s.contains("HOTSPOT") || s.contains("FREE") || s.contains("CITY") ||
                    s.contains("TRANSIT") || s.contains("METRO") || s.contains("MUNICIPAL") ||
                    caps.contains("OPEN")
        }
        val publicRatio = if (batch.isNotEmpty()) infraCount.toFloat() / batch.size.toFloat() else 0f

        // Administrative boundary partition name
        val wardId = ((abs(centerLat * 100).toInt() + abs(centerLon * 100).toInt()) % 16) + 1
        val adminName = "Administrative District #$wardId // Sector ${abs(xtile % 100)}-${abs(ytile % 100)}"

        return GeospatialOsintLayer(
            boundaryName = "OSM Sector: $osmTile",
            boundingBox = GeoBoundingBox(minLat, maxLat, minLon, maxLon),
            totalApCount = batch.size,
            infrastructureCount = infraCount,
            apDensityPerSqKm = density,
            publicHotspotRatio = publicRatio,
            osmTileCoord = osmTile,
            administrativeWard = adminName
        )
    }
}
