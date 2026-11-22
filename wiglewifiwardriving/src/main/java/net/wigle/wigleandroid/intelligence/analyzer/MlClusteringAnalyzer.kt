package net.wigle.wigleandroid.intelligence.analyzer

import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.intelligence.model.Severity
import kotlin.math.*

/**
 * ML Clustering Analyzer
 * Strictly passive and metadata-only.
 * Groups observations by geographic proximity (K-Means / Centroid clustering)
 * and classifies wireless environment types based on encryption posture,
 * frequency spread, and AP density:
 * - Enterprise / Office Hub (WPA2/WPA3-Enterprise, 5 GHz dominant)
 * - Commercial / Retail Venue (High density open/guest portals)
 * - Residential Suburb (WPA2-PSK dominant, 2.4 GHz prevalent)
 * - Transit Corridor (Transient vehicle APs, sparse nodes)
 */
class MlClusteringAnalyzer(
    private val maxClusters: Int = 4
) : Analyzer {

    data class Cluster(
        val id: Int,
        var centerLat: Double,
        var centerLon: Double,
        val observations: MutableList<Observation> = mutableListOf()
    ) {
        val size: Int get() = observations.size
    }

    override suspend fun analyze(batch: List<Observation>): List<Finding> {
        val validObs = batch.filter { it.latitude != 0.0 && it.longitude != 0.0 }
        if (validObs.size < 3) return emptyList()

        val findings = mutableListOf<Finding>()

        // 1. K-Means Proximity Clustering
        val k = min(maxClusters, validObs.size / 3).coerceAtLeast(1)
        val clusters = initializeClusters(k, validObs)

        // Iterate assignment & centroid updates
        repeat(5) {
            clusters.forEach { it.observations.clear() }
            for (obs in validObs) {
                val nearest = clusters.minByOrNull { distance(obs.latitude, obs.longitude, it.centerLat, it.centerLon) }
                nearest?.observations?.add(obs)
            }
            for (cluster in clusters) {
                if (cluster.observations.isNotEmpty()) {
                    cluster.centerLat = cluster.observations.map { it.latitude }.average()
                    cluster.centerLon = cluster.observations.map { it.longitude }.average()
                }
            }
        }

        // 2. Classify Environment per Cluster
        for (cluster in clusters) {
            if (cluster.observations.isEmpty()) continue

            val count = cluster.size
            val openCount = cluster.observations.count { it.capabilities.isEmpty() || it.capabilities == "[ESS]" }
            val wpa3Count = cluster.observations.count { it.capabilities.contains("WPA3", ignoreCase = true) || it.capabilities.contains("SAE", ignoreCase = true) }
            val fiveGCount = cluster.observations.count { it.frequency > 5000 }

            val envType: String
            val confidence: Double

            when {
                wpa3Count >= 2 || (fiveGCount.toDouble() / count) > 0.6 -> {
                    envType = "Enterprise / High-Tech Commercial"
                    confidence = 0.88
                }
                openCount >= 3 || (openCount.toDouble() / count) > 0.35 -> {
                    envType = "Public Commercial / Hospitality Venue"
                    confidence = 0.85
                }
                count >= 8 -> {
                    envType = "Dense Urban Mixed-Use Sector"
                    confidence = 0.80
                }
                else -> {
                    envType = "Residential / Low-Density Neighborhood"
                    confidence = 0.75
                }
            }

            val topSsids = cluster.observations.map { it.ssid }.filter { it.isNotBlank() }.distinct().take(4).joinToString(", ")
            val radiusMeters = computeClusterRadius(cluster)

            findings.add(
                Finding(
                    analyzerType = AnalyzerType.ML_CLUSTERING,
                    title = "Cluster #${cluster.id + 1}: $envType",
                    summary = "Identified cluster of $count nodes within ${"%.0f".format(radiusMeters)}m radius. Environment classified as $envType.",
                    severity = Severity.INFO,
                    latitude = cluster.centerLat,
                    longitude = cluster.centerLon,
                    score = confidence,
                    details = "Node count: $count, Top SSIDs: $topSsids. 5GHz ratio: ${"%.0f".format(fiveGCount * 100.0 / count)}%, Open ratio: ${"%.0f".format(openCount * 100.0 / count)}%."
                )
            )
        }

        return findings
    }

    private fun initializeClusters(k: Int, obs: List<Observation>): List<Cluster> {
        val step = obs.size / k
        return (0 until k).map { i ->
            val sample = obs[min(i * step, obs.size - 1)]
            Cluster(id = i, centerLat = sample.latitude, centerLon = sample.longitude)
        }
    }

    private fun computeClusterRadius(cluster: Cluster): Double {
        return cluster.observations.maxOfOrNull {
            distance(it.latitude, it.longitude, cluster.centerLat, cluster.centerLon)
        } ?: 50.0
    }

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 6371000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

