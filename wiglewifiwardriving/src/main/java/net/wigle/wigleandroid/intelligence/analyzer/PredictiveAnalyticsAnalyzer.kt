package net.wigle.wigleandroid.intelligence.analyzer

import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.intelligence.model.Severity
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Predictive Analytics Analyzer
 * Strictly passive and metadata-only.
 * Powered by an embedded on-device TinyML Multi-Layer Perceptron.
 *
 * Forecasts:
 * 1. Observation velocity & encounter trends (nodes per minute)
 * 2. Network density trajectory forecasting using TinyML on-device inference
 * 3. Historical density trend comparisons
 */
class PredictiveAnalyticsAnalyzer : Analyzer {

    private val tinyMlPredictor = TinyMlDensityPredictor()

    override suspend fun analyze(batch: List<Observation>): List<Finding> {
        if (batch.size < 4) return emptyList()

        val findings = mutableListOf<Finding>()

        // 1. Calculate Observation Rate Velocity (Encounters over time)
        val sortedByTime = batch.sortedBy { it.timestamp }
        val timeSpanSec = ((sortedByTime.last().timestamp - sortedByTime.first().timestamp) / 1000.0).coerceAtLeast(1.0)
        val observationsPerMinute = (batch.size / timeSpanSec) * 60.0
        val uniqueBssids = batch.map { it.bssid }.distinct().size
        val uniqueRatePerMinute = (uniqueBssids / timeSpanSec) * 60.0

        // 2. Feature Extraction for TinyML Model
        val obsVelocityNormalized = (observationsPerMinute / 120.0).coerceIn(0.0, 1.0).toFloat()
        val uniqueRatio = (uniqueBssids.toFloat() / batch.size.toFloat()).coerceIn(0f, 1f)

        // Band Shannon Entropy
        val band24Count = batch.count { it.frequency in 2400..2500 }
        val band5Count = batch.count { it.frequency in 5000..5900 }
        val band6Count = batch.count { it.frequency > 5900 }
        val total = batch.size.toDouble()

        var entropy = 0.0
        for (count in listOf(band24Count, band5Count, band6Count)) {
            if (count > 0) {
                val p = count / total
                entropy -= p * (ln(p) / ln(3.0))
            }
        }
        val bandEntropy = entropy.coerceIn(0.0, 1.0).toFloat()

        // Mean normalized RSSI
        val meanRssi = batch.map { it.rssi }.average().toFloat()
        val normalizedRssi = ((meanRssi + 100f) / 70f).coerceIn(0f, 1f)

        // Spatial dispersion variance
        val meanLat = batch.map { it.latitude }.average()
        val meanLon = batch.map { it.longitude }.average()
        val dispersion = sqrt(batch.map {
            val dLat = it.latitude - meanLat
            val dLon = it.longitude - meanLon
            dLat * dLat + dLon * dLon
        }.average()).toFloat()
        val normalizedDispersion = (dispersion * 1000f).coerceIn(0f, 1f)

        // 3. Run TinyML On-Device Model Inference
        val mlResult = tinyMlPredictor.predict(
            obsVelocityPerSec = (batch.size / timeSpanSec).toFloat() / 20f,
            uniqueRatio = uniqueRatio,
            bandEntropy = bandEntropy,
            normalizedRssi = normalizedRssi,
            spatialDispersion = normalizedDispersion
        )

        val sample = sortedByTime.last()

        findings.add(
            Finding(
                analyzerType = AnalyzerType.PREDICTIVE_ANALYTICS,
                title = "TinyML Forecast: ${mlResult.predictedDensityClass}",
                summary = "Confidence: ${"%.1f".format(mlResult.confidence * 100)}%. Trend: ${mlResult.densityVelocityTrend}. Projected 5-min density: ~${mlResult.projected5MinAps} APs.",
                severity = if (mlResult.confidence > 0.85f && mlResult.predictedDensityClass.contains("Ultra-Dense")) Severity.LOW else Severity.INFO,
                latitude = sample.latitude,
                longitude = sample.longitude,
                score = mlResult.confidence.toDouble(),
                details = "TinyML Model Architecture: MLP [5x4x3] Quantized Float. " +
                        "Current Velocity: ${"%.1f".format(observationsPerMinute)} obs/min (${"%.1f".format(uniqueRatePerMinute)} unique/min). " +
                        "Band Entropy: ${"%.2f".format(bandEntropy)}. Mean RSSI: ${"%.1f".format(meanRssi)} dBm."
            )
        )

        return findings
    }
}
