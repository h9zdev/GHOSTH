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
 */
class WirelessDensityAnalyzer(
    private val gridResolution: Double = 0.0005, // ~55 meters at equator
    private val highDensityThreshold: Int = 8,
    private val criticalDensityThreshold: Int = 18
) : Analyzer {

    data class DensityCell(
        val cellX: Int,
        val cellY: Int,
        val centerLat: Double,
        val centerLon: Double,
        val bssids: MutableSet<String> = mutableSetOf(),
        val ssids: MutableSet<String> = mutableSetOf(),
        val rssiValues: MutableList<Int> = mutableListOf()
    ) {
        val count: Int get() = bssids.size
        val avgRssi: Double get() = if (rssiValues.isEmpty()) -100.0 else rssiValues.average()
    }

    data class HeatmapPoint(
        val lat: Double,
        val lon: Double,
        val weight: Double,
        val apCount: Int
    )

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
                    centerLon = cellY * gridResolution
                )
            }
            cell.bssids.add(obs.bssid)
            if (obs.ssid.isNotBlank()) cell.ssids.add(obs.ssid)
            cell.rssiValues.add(obs.rssi)
        }

        if (grid.isEmpty()) return emptyList()

        // 2. Identify High Network Concentration Zones
        val sortedCells = grid.values.sortedByDescending { it.count }
        for (cell in sortedCells) {
            if (cell.count >= highDensityThreshold) {
                val severity = if (cell.count >= criticalDensityThreshold) Severity.CRITICAL else Severity.HIGH
                val densityScore = (cell.count.toDouble() / 25.0).coerceIn(0.1, 1.0)
                val topSsids = cell.ssids.take(4).joinToString(", ")
                findings.add(
                    Finding(
                        analyzerType = AnalyzerType.DENSITY_PREDICTION,
                        title = "High Network Concentration (${cell.count} APs)",
                        summary = "Detected dense cluster of ${cell.count} unique APs in ~50m zone. Top SSIDs: $topSsids",
                        severity = severity,
                        latitude = cell.centerLat,
                        longitude = cell.centerLon,
                        score = densityScore,
                        details = "Unique APs: ${cell.count}, Avg RSSI: ${"%.1f".format(cell.avgRssi)} dBm, Total observations: ${cell.rssiValues.size}"
                    )
                )
            }
        }

        // 3. Estimate Signal Distribution in Poorly Sampled / Fringe Areas (IDW)
        val sampledLocations = grid.values.map { Triple(it.centerLat, it.centerLon, it.avgRssi) }
        if (sampledLocations.size >= 2) {
            val minLat = sampledLocations.minOf { it.first }
            val maxLat = sampledLocations.maxOf { it.first }
            val minLon = sampledLocations.minOf { it.second }
            val maxLon = sampledLocations.maxOf { it.second }

            // Evaluate sparse fringe corners
            val testPoints = listOf(
                Pair(minLat - gridResolution, minLon - gridResolution),
                Pair(maxLat + gridResolution, maxLon + gridResolution)
            )

            for ((tLat, tLon) in testPoints) {
                val predictedRssi = predictRssiIdw(tLat, tLon, sampledLocations)
                if (predictedRssi > -95.0) {
                    findings.add(
                        Finding(
                            analyzerType = AnalyzerType.DENSITY_PREDICTION,
                            title = "Predicted Coverage Boundary",
                            summary = "Estimated signal distribution extends beyond observed tracks (Predicted RSSI: ${"%.1f".format(predictedRssi)} dBm).",
                            severity = Severity.LOW,
                            latitude = tLat,
                            longitude = tLon,
                            score = (predictedRssi + 100.0) / 70.0,
                            details = "IDW Predicted RSSI: ${"%.1f".format(predictedRssi)} dBm based on ${sampledLocations.size} spatial anchors."
                        )
                    )
                }
            }
        }

        // 4. Overall Macro Density Summary Finding
        val totalUniqueBssids = batch.map { it.bssid }.toSet().size
        val avgDensity = sortedCells.map { it.count }.average()
        findings.add(
            Finding(
                analyzerType = AnalyzerType.DENSITY_PREDICTION,
                title = "Wireless Density Index: ${"%.1f".format(avgDensity)} APs/cell",
                summary = "Surveyed ${sortedCells.size} active grid sectors with $totalUniqueBssids distinct wireless nodes.",
                severity = Severity.INFO,
                latitude = sortedCells.first().centerLat,
                longitude = sortedCells.first().centerLon,
                score = (avgDensity / 15.0).coerceIn(0.0, 1.0),
                details = "Total BSSIDs: $totalUniqueBssids, Active Sectors: ${sortedCells.size}, Max Sector Density: ${sortedCells.first().count} APs"
            )
        )

        return findings
    }

    /**
     * Generate geographic coverage heatmap points from observation batch.
     */
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
            cell.rssiValues.add(obs.rssi)
        }

        val maxCount = grid.values.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        return grid.values.map { cell ->
            HeatmapPoint(
                lat = cell.centerLat,
                lon = cell.centerLon,
                weight = (cell.count.toDouble() / maxCount).coerceIn(0.1, 1.0),
                apCount = cell.count
            )
        }
    }

    /**
     * Inverse Distance Weighting (IDW) interpolation
     */
    private fun predictRssiIdw(
        lat: Double,
        lon: Double,
        samples: List<Triple<Double, Double, Double>>,
        power: Double = 2.0
    ): Double {
        var weightedSum = 0.0
        var weightSum = 0.0

        for ((sLat, sLon, sRssi) in samples) {
            val dLat = lat - sLat
            val dLon = lon - sLon
            val dist = sqrt(dLat * dLat + dLon * dLon)
            if (dist < 1e-6) return sRssi

            val w = 1.0 / dist.pow(power)
            weightedSum += w * sRssi
            weightSum += w
        }

        return if (weightSum > 0) weightedSum / weightSum else -100.0
    }
}

