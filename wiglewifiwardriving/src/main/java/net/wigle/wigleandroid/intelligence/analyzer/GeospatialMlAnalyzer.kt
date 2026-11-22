package net.wigle.wigleandroid.intelligence.analyzer

import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.intelligence.model.Severity
import kotlin.math.*

/**
 * Geospatial ML Analyzer
 * Strictly passive and metadata-only.
 * Implements classical DBSCAN (Density-Based Spatial Clustering of Applications with Noise)
 * to uncover geographic observation patterns, isolate spatial core clusters,
 * and detect mobile / roving APs (nodes with high spatial dispersion variance).
 */
class GeospatialMlAnalyzer(
    private val epsMeters: Double = 120.0,
    private val minPts: Int = 3
) : Analyzer {

    override suspend fun analyze(batch: List<Observation>): List<Finding> {
        val valid = batch.filter { it.latitude != 0.0 && it.longitude != 0.0 }
        if (valid.size < 2) return emptyList()

        val findings = mutableListOf<Finding>()

        // 1. Mobile AP Detection (High spatial variance for same BSSID)
        val bssidLocations = valid.groupBy { it.bssid }
        for ((bssid, obsList) in bssidLocations) {
            if (obsList.size >= 2) {
                val maxSeparation = maxPairwiseDistance(obsList)
                if (maxSeparation > 300.0) { // Traveled > 300m while transmitting
                    val sample = obsList.first()
                    findings.add(
                        Finding(
                            analyzerType = AnalyzerType.GEOSPATIAL_ML,
                            title = "Mobile / In-Transit Access Point",
                            summary = "BSSID $bssid ('${sample.ssid}') observed across ${"%.0f".format(maxSeparation)}m geographic dispersion (vehicular or personal hotspot).",
                            severity = Severity.MEDIUM,
                            latitude = sample.latitude,
                            longitude = sample.longitude,
                            bssid = bssid,
                            ssid = sample.ssid,
                            score = 0.82,
                            details = "Observed count: ${obsList.size}, Total displacement: ${"%.0f".format(maxSeparation)}m across timestamps."
                        )
                    )
                }
            }
        }

        // 2. DBSCAN Spatial Density Clustering
        if (valid.size >= minPts) {
            val visited = BooleanArray(valid.size)
            val clusterIds = IntArray(valid.size) { -1 } // -1 = unassigned / noise
            var clusterIdCounter = 0

            for (i in valid.indices) {
                if (visited[i]) continue
                visited[i] = true

                val neighbors = regionQuery(i, valid, epsMeters)
                if (neighbors.size >= minPts) {
                    // Form a new dense core cluster
                    expandCluster(i, neighbors, clusterIdCounter, valid, visited, clusterIds, epsMeters, minPts)
                    clusterIdCounter++
                }
            }

            // Group observations by cluster ID
            val clusteredMap = mutableMapOf<Int, MutableList<Observation>>()
            for (i in valid.indices) {
                val cid = clusterIds[i]
                if (cid >= 0) {
                    clusteredMap.getOrPut(cid) { mutableListOf() }.add(valid[i])
                }
            }

            for ((cid, clusterObs) in clusteredMap) {
                val cLat = clusterObs.map { it.latitude }.average()
                val cLon = clusterObs.map { it.longitude }.average()
                val uniqueBssids = clusterObs.map { it.bssid }.distinct().size

                findings.add(
                    Finding(
                        analyzerType = AnalyzerType.GEOSPATIAL_ML,
                        title = "DBSCAN Spatial Cluster #${cid + 1} ($uniqueBssids APs)",
                        summary = "Densely packed geographic cluster identified with $uniqueBssids unique wireless nodes within ~${epsMeters.toInt()}m.",
                        severity = Severity.INFO,
                        latitude = cLat,
                        longitude = cLon,
                        score = (uniqueBssids / 15.0).coerceIn(0.2, 1.0),
                        details = "Total cluster observations: ${clusterObs.size}, Density core: ${"%.4f".format(cLat)}, ${"%.4f".format(cLon)}."
                    )
                )
            }
        }

        return findings
    }

    private fun expandCluster(
        pIdx: Int,
        neighbors: MutableList<Int>,
        cid: Int,
        points: List<Observation>,
        visited: BooleanArray,
        clusterIds: IntArray,
        eps: Double,
        minPts: Int
    ) {
        clusterIds[pIdx] = cid
        var idx = 0
        while (idx < neighbors.size) {
            val nIdx = neighbors[idx]
            if (!visited[nIdx]) {
                visited[nIdx] = true
                val nNeighbors = regionQuery(nIdx, points, eps)
                if (nNeighbors.size >= minPts) {
                    neighbors.addAll(nNeighbors.filter { it !in neighbors })
                }
            }
            if (clusterIds[nIdx] < 0) {
                clusterIds[nIdx] = cid
            }
            idx++
        }
    }

    private fun regionQuery(pIdx: Int, points: List<Observation>, eps: Double): MutableList<Int> {
        val neighbors = mutableListOf<Int>()
        val p = points[pIdx]
        for (i in points.indices) {
            val dist = haversine(p.latitude, p.longitude, points[i].latitude, points[i].longitude)
            if (dist <= eps) {
                neighbors.add(i)
            }
        }
        return neighbors
    }

    private fun maxPairwiseDistance(points: List<Observation>): Double {
        var maxDist = 0.0
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val d = haversine(points[i].latitude, points[i].longitude, points[j].latitude, points[j].longitude)
                if (d > maxDist) maxDist = d
            }
        }
        return maxDist
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 6371000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
