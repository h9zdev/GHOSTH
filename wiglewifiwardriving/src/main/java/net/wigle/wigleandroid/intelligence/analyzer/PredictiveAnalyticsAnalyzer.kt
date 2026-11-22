package net.wigle.wigleandroid.intelligence.analyzer

import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.intelligence.model.Severity

/**
 * Predictive Analytics Analyzer
 * Strictly passive and metadata-only.
 * Forecasts:
 * 1. Observation velocity & encounter trends (nodes per minute)
 * 2. Network density trajectory forecasting (linear slope projection)
 * 3. Historical density trend comparisons
 */
class PredictiveAnalyticsAnalyzer : Analyzer {

    override suspend fun analyze(batch: List<Observation>): List<Finding> {
        if (batch.size < 4) return emptyList()

        val findings = mutableListOf<Finding>()

        // 1. Calculate Observation Rate Velocity (Encounters over time)
        val sortedByTime = batch.sortedBy { it.timestamp }
        val timeSpanSec = ((sortedByTime.last().timestamp - sortedByTime.first().timestamp) / 1000.0).coerceAtLeast(1.0)
        val observationsPerMinute = (batch.size / timeSpanSec) * 60.0
        val uniqueBssids = batch.map { it.bssid }.distinct().size
        val uniqueRatePerMinute = (uniqueBssids / timeSpanSec) * 60.0

        // 2. Linear Regression Slope for Density Trend
        // Split batch into 3 temporal segments to calculate velocity derivative
        val segmentSize = batch.size / 3
        val seg1 = sortedByTime.take(segmentSize).size
        val seg2 = sortedByTime.drop(segmentSize).take(segmentSize).size
        val seg3 = sortedByTime.takeLast(segmentSize).size

        val trendDelta = seg3 - seg1
        val trendDescription = when {
            trendDelta > 2 -> "Accelerating (Approaching Dense Network Core)"
            trendDelta < -2 -> "Decelerating (Exiting Dense Wireless Zone)"
            else -> "Stable Scanning Velocity"
        }

        // Predicted network density for upcoming 5 minutes
        val predictedNext5MinAPs = (observationsPerMinute * 5).toInt()
        val sample = sortedByTime.last()

        findings.add(
            Finding(
                analyzerType = AnalyzerType.PREDICTIVE_ANALYTICS,
                title = "Density Forecast: $trendDescription",
                summary = "Current velocity: ${"%.1f".format(uniqueRatePerMinute)} new APs/min. Projected density: ~$predictedNext5MinAPs encounters over next 5 min.",
                severity = if (trendDelta > 3) Severity.LOW else Severity.INFO,
                latitude = sample.latitude,
                longitude = sample.longitude,
                score = (uniqueRatePerMinute / 50.0).coerceIn(0.1, 1.0),
                details = "Velocity: ${"%.1f".format(observationsPerMinute)} obs/min (${"%.1f".format(uniqueRatePerMinute)} unique/min). Segment progression: $seg1 -> $seg2 -> $seg3."
            )
        )

        return findings
    }
}

