package net.wigle.wigleandroid.osint.model

/**
 * Data models for Passive Wireless & Geospatial OSINT Analysis.
 * Strictly passive and metadata-only.
 */

data class WirelessOsintFinding(
    val id: String,
    val bssid: String,
    val ssid: String,
    val manufacturer: String,
    val isPublicHotspot: Boolean,
    val publicCategory: String,
    val securityType: String,
    val wigleRecordStatus: String,
    val frequency: Int,
    val rssi: Int,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val historicalDeltaDays: Int
)

data class GeoBoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

data class GeospatialOsintLayer(
    val boundaryName: String,
    val boundingBox: GeoBoundingBox,
    val totalApCount: Int,
    val infrastructureCount: Int,
    val apDensityPerSqKm: Double,
    val publicHotspotRatio: Float,
    val osmTileCoord: String,
    val administrativeWard: String
)

