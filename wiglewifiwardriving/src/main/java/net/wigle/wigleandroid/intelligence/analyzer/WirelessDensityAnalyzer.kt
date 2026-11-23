package net.wigle.wigleandroid.intelligence.analyzer

import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.intelligence.model.Severity
import kotlin.math.*

/**
 * Wireless Density Prediction Analyzer (HIGH PRIORITY)
 * Strictly passive and metadata-only.
 * Uses spatial grid binning and Inverse Distance Weighting (IDW) interpolation
 * to predict Wi-Fi access point density, identify high concentration zones,
 * and estimate signal distribution in poorly sampled areas.
 *
 * Distinguishes confirmed observations from model-estimated predictions.
 */
class WirelessDensityAnalyzer(
    private val gridResolution: Double = 0.0005, // ~55 meters
    private val highDensityThreshold: Int = 12,
    private val criticalDensityThreshold: Int = 24
) : Analyzer {

    data class DensityCell(
        val cellX: Int,
        val cellY: Int,
        val centerLat: Double,
        val centerLon: Double,
        val bssids: MutableSet<String> = mutableSetOf(),
        val ssids: MutableSet<String> = mutableSetOf(),
        val rssiValues: MutableList<Int> = mutableListOf(),
        var isPredicted: Boolean = false
    ) {
        val count: Int get() = bssids.size
        val avgRssi: Double get() = if (rssiValues.isEmpty()) -100.0 else rssiValues.average()
        // Approximate density in APs per km^2 (~55m grid cell is ~0.003 km^2)
        val densityPerSqKm: Double get() = count / 0.003025
    }

    data class HeatmapPoint(
        val lat: Double,
        val lon: Double,
        val weight: Double,
        val apCount: Int
    )

    fun generateHeatmap(batch: List<Observation>): List<HeatmapPoint> {
        val grid = mutableMapOf<String, DensityCell>()
        for (obs in batch) {
            if (obs.latitude == 0.0 && obs.longitude == 0.0) continue
            val cellX = (obs.latitude / gridResolution).roundToInt()
            val cellY = (obs.longitude / gridResolution).roundToInt()
            val key = "$cellX:$cellY"
            val cell = grid.getOrPut(key) {
                DensityCell(
                    cellX = cellX,
                    cellY = cellY,
                    centerLat = cellX * gridResolution,
                    centerLon = cellY * gridResolution
                )
            }
            cell.bssids.add(obs.bssid)
        }
        val maxCount = grid.values.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        return grid.values.map {
            HeatmapPoint(
                lat = it.centerLat,
                lon = it.centerLon,
                weight = it.count.toDouble() / maxCount,
                apCount = it.count
            )
        }
    }

    override suspend fun analyze(batch: List<Observation>): List<Finding> {
        if (batch.isEmpty()) return emptyList()

        val findings = mutableListOf<Finding>()
        val grid = mutableMapOf<String, DensityCell>()

        // 1. Spatial Grid Binning
        for (obs in batch) {
            if (obs.latitude == 0.0 && obs.longitude == 0.0) continue
            val cellX = (obs.latitude / gridResolution).roundToInt()
            val cellY = (obs.longitude / gridResolution).roundToInt()
            val key = "$cellX:$cellY"

            val cell = grid.getOrPut(key) {
                DensityCell(
                    cellX = cellX,
                    cellY = cellY,
                    centerLat = cellX * gridResolution,
                    centerLon = cellY * gridResolution,
                    isPredicted = obs.isEstimatedOrPredicted
                )
            }
            cell.bssids.add(obs.bssid)
            if (obs.ssid.isNotBlank()) cell.ssids.add(obs.ssid)
            cell.rssiValues.add(obs.rssi)
        }

        if (grid.isEmpty()) return emptyList()

        val totalUniqueAps = grid.values.sumOf { it.count }
        val totalObs = grid.values.sumOf { it.rssiValues.size }
        val totalCoverageSqKm = grid.size * 0.003025

        // 2. Identify High Network Concentration Zones
        val sortedCells = grid.values.sortedByDescending { it.count }
        for (cell in sortedCells) {
            if (cell.count >= highDensityThreshold) {
                val severity = if (cell.count >= criticalDensityThreshold) Severity.CRITICAL else Severity.HIGH
                val densityScore = (cell.count.toDouble() / 25.0).coerceIn(0.1, 1.0)
                val topSsids = cell.ssids.take(4).joinToString(", ")
                val densityLevel = if (cell.count >= criticalDensityThreshold) "CRITICAL" else "HIGH"

                findings.add(
                    Finding(
                        analyzerType = AnalyzerType.DENSITY_PREDICTION,
                        title = "High Network Concentration: $densityLevel Density Cluster (${cell.count} APs)",
                        summary = "Concentration: ${"%.0f".format(cell.densityPerSqKm)} APs/km² in ~55m sector. Top SSIDs: $topSsids",
                        severity = severity,
                        latitude = cell.centerLat,
                        longitude = cell.centerLon,
                        score = densityScore,
                        details = "Status: Confirmed Observation. Unique APs: ${cell.count}, Total Obs: ${cell.rssiValues.size}, Avg RSSI: ${"%.1f".format(cell.avgRssi)} dBm, Coverage: ~0.003 km².",
                        category = "Observed Density Concentration",
                        recommendedAction = "Verify local spectrum congestion and prioritize channel planning"
                    )
                )
            }
        }

        // 3. Estimate Signal Distribution in Poorly Sampled / Fringe Areas (IDW)
        // Explicitly distinguished as Model-Estimated Coverage
        val sampledLocations = grid.values.map { Triple(it.centerLat, it.centerLon, it.avgRssi) }
        if (sampledLocations.size >= 2) {
            val minLat = sampledLocations.minOf { it.first }
            val maxLat = sampledLocations.maxOf { it.first }
            val minLon = sampledLocations.minOf { it.second }
            val maxLon = sampledLocations.maxOf { it.second }

            val fringeLat = minLat - (gridResolution * 1.5)
            val fringeLon = minLon - (gridResolution * 1.5)

            var weightedRssi = 0.0
            var weightSum = 0.0
            for (loc in sampledLocations) {
                val dist = sqrt((loc.first - fringeLat).pow(2) + (loc.second - fringeLon).pow(2))
                if (dist > 0.0) {
                    val w = 1.0 / (dist * dist)
                    weightedRssi += loc.third * w
                    weightSum += w
                }
            }

            if (weightSum > 0.0) {
                val estimatedRssi = (weightedRssi / weightSum) - 10.0 // Account for fringe path loss
                findings.add(
                    Finding(
                        analyzerType = AnalyzerType.DENSITY_PREDICTION,
                        title = "Estimated Signal Fringe Coverage",
                        summary = "Model-estimated signal level ~${"%.1f".format(estimatedRssi)} dBm in unsampled boundary sector. (Prediction, not confirmed observation).",
                        severity = Severity.INFO,
                        latitude = fringeLat,
                        longitude = fringeLon,
                        score = 0.55,
                        details = "Status: Model-Estimated (IDW Interpolation). Never present predictions as confirmed observations. Predicted RSSI: ${"%.1f".format(estimatedRssi)} dBm, Bounding Region: ${"%.4f".format(minLat)}N, ${"%.4f".format(minLon)}W.",
                        category = "Model-Estimated Coverage",
                        recommendedAction = "Survey boundary perimeter to collect ground-truth observations"
                    )
                )
            }
        }

        return findings
    }
}
