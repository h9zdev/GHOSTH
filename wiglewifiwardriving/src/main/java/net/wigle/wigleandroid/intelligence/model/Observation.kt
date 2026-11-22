package net.wigle.wigleandroid.intelligence.model

enum class NetworkType {
    WIFI,
    BLE,
    CELL,
    UNKNOWN
}

/**
 * Passive observation metadata.
 * Strictly passive and metadata-only: no payload capture, no network connection.
 */
data class Observation(
    val bssid: String,
    val ssid: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val rssi: Int,
    val frequency: Int = 2412,
    val channel: Int = 1,
    val capabilities: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: NetworkType = NetworkType.WIFI,
    val vendor: String? = null
)

