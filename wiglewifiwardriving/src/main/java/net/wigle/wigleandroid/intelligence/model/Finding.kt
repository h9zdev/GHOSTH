package net.wigle.wigleandroid.intelligence.model

enum class Severity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class AnalyzerType(val displayName: String) {
    DENSITY_PREDICTION("Density Prediction"),
    ANOMALY_DETECTION("Anomaly Detection"),
    SIGNAL_INTELLIGENCE("Signal Intelligence"),
    TEMPORAL_INTELLIGENCE("Temporal Intelligence"),
    ML_CLUSTERING("ML Clustering"),
    GEOSPATIAL_ML("Geospatial ML"),
    PREDICTIVE_ANALYTICS("Predictive Analytics")
}

data class Finding(
    val id: Long = 0,
    val analyzerType: AnalyzerType,
    val title: String,
    val summary: String,
    val severity: Severity,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val bssid: String? = null,
    val ssid: String? = null,
    val score: Double = 0.0,
    val details: String = ""
)

