package net.wigle.wigleandroid.intelligence.service

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.wigle.wigleandroid.ListFragment
import net.wigle.wigleandroid.intelligence.model.*
import net.wigle.wigleandroid.util.FileUtility
import java.io.File
import kotlin.math.*

data class PipelineMetrics(
    val totalObservations: Int = 0,
    val uniqueAccessPoints: Int = 0,
    val anomaliesCount: Int = 0,
    val geographicRegionsCount: Int = 0,
    val signalQualityCounts: Map<String, Int> = emptyMap(), // "Excellent", "Good", "Fair", "Poor"
    val bandDistributionCounts: Map<String, Int> = emptyMap(), // "2.4 GHz", "5 GHz", "6 GHz"
    val channelDistribution: Map<Int, Int> = emptyMap(),
    val dataQualityCounts: Map<DataQualityStatus, Int> = emptyMap(),
    val observedCoverageSqKm: Double = 0.0,
    val estimatedCoverageSqKm: Double = 0.0,
    val averageDensityPerSqKm: Double = 0.0,
    val isRunning: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class GridCell(
    val key: String,
    val centerLat: Double,
    val centerLon: Double,
    val apCount: Int,
    val avgRssi: Double,
    val densityLevel: String, // "HIGH", "MEDIUM", "LOW"
    val isPredicted: Boolean = false
)

/**
 * Shared Data Processing Pipeline
 * Real DB ingestion, validation, normalization, data quality analysis, feature extraction,
 * and dispatching to intelligence analyzers.
 */
class ObservationPipeline private constructor(private val context: Context) {

    private val engine = IntelligenceEngine.getInstance(context)

    private val _pipelineMetrics = MutableStateFlow(PipelineMetrics())
    val pipelineMetrics: StateFlow<PipelineMetrics> = _pipelineMetrics.asStateFlow()

    private val _gridCells = MutableStateFlow<List<GridCell>>(emptyList())
    val gridCells: StateFlow<List<GridCell>> = _gridCells.asStateFlow()

    private val _processedObservations = MutableStateFlow<List<Observation>>(emptyList())
    val processedObservations: StateFlow<List<Observation>> = _processedObservations.asStateFlow()

    /**
     * Executes the complete processing pipeline on actual application data.
     */
    suspend fun executePipeline(maxRecords: Int = 5000): PipelineMetrics = withContext(Dispatchers.IO) {
        _pipelineMetrics.value = _pipelineMetrics.value.copy(isRunning = true)

        try {
            // Step 1: Retrieve available wireless observations from DB & live cache
            val rawObservations = retrieveObservationsFromDatabase(maxRecords)

            // Step 2 - 5: Validate, Normalize, Handle missing fields, Flag duplicates
            val enrichedObservations = processAndValidate(rawObservations)

            // Step 6: Generate analytical features & spatial density
            val cells = computeGridBins(enrichedObservations)
            _gridCells.value = cells

            // Calculate metrics
            val uniqueAps = enrichedObservations.map { it.bssid }.distinct().size
            val qualityMap = enrichedObservations.groupingBy { it.qualityStatus }.eachCount()

            // Signal quality counts
            val sigQuality = mutableMapOf("Excellent" to 0, "Good" to 0, "Fair" to 0, "Poor" to 0)
            for (obs in enrichedObservations) {
                when {
                    obs.rssi >= -50 -> sigQuality["Excellent"] = (sigQuality["Excellent"] ?: 0) + 1
                    obs.rssi >= -65 -> sigQuality["Good"] = (sigQuality["Good"] ?: 0) + 1
                    obs.rssi >= -80 -> sigQuality["Fair"] = (sigQuality["Fair"] ?: 0) + 1
                    else -> sigQuality["Poor"] = (sigQuality["Poor"] ?: 0) + 1
                }
            }

            // Band counts
            val bandMap = mutableMapOf("2.4 GHz" to 0, "5 GHz" to 0, "6 GHz" to 0)
            for (obs in enrichedObservations) {
                when {
                    obs.frequency in 2400..2495 -> bandMap["2.4 GHz"] = (bandMap["2.4 GHz"] ?: 0) + 1
                    obs.frequency in 5150..5895 -> bandMap["5 GHz"] = (bandMap["5 GHz"] ?: 0) + 1
                    obs.frequency in 5925..7125 -> bandMap["6 GHz"] = (bandMap["6 GHz"] ?: 0) + 1
                }
            }

            // Channel distribution
            val channelMap = enrichedObservations.groupingBy { it.channel }.eachCount().toSortedMap()

            // Coverage calculations (~55m grid cell is ~0.003 km^2)
            val cellAreaSqKm = 0.003
            val observedCount = cells.count { !it.isPredicted }
            val predictedCount = cells.count { it.isPredicted }
            val observedSqKm = observedCount * cellAreaSqKm
            val estimatedSqKm = (observedCount + predictedCount) * cellAreaSqKm
            val avgDensity = if (observedSqKm > 0.0) uniqueAps / observedSqKm else 0.0

            _processedObservations.value = enrichedObservations

            // Step 7 & 8: Dispatch to analyzers & store findings
            val findings = engine.processBatch(enrichedObservations)

            val anomalyCount = findings.count { it.analyzerType == AnalyzerType.ANOMALY_DETECTION }

            val updatedMetrics = PipelineMetrics(
                totalObservations = enrichedObservations.size,
                uniqueAccessPoints = uniqueAps,
                anomaliesCount = anomalyCount,
                geographicRegionsCount = cells.size,
                signalQualityCounts = sigQuality,
                bandDistributionCounts = bandMap,
                channelDistribution = channelMap,
                dataQualityCounts = qualityMap,
                observedCoverageSqKm = observedSqKm,
                estimatedCoverageSqKm = estimatedSqKm,
                averageDensityPerSqKm = avgDensity,
                isRunning = false,
                lastUpdated = System.currentTimeMillis()
            )

            _pipelineMetrics.value = updatedMetrics
            updatedMetrics
        } catch (e: Exception) {
            _pipelineMetrics.value = _pipelineMetrics.value.copy(isRunning = false)
            throw e
        }
    }

    /**
     * Reads actual records from Wigle SQLite DB and in-memory cache.
     */
    private fun retrieveObservationsFromDatabase(limit: Int): List<Observation> {
        val list = mutableListOf<Observation>()

        // 1. Ingest from in-memory cache (live scanned networks)
        runCatching {
            val liveCache = ListFragment.lameStatic.networkCache
            if (liveCache != null) {
                for (net in liveCache.values) {
                    val geo = net.latLng
                    val lat = geo?.latitude ?: 0.0
                    val lon = geo?.longitude ?: 0.0
                    list.add(
                        Observation(
                            bssid = net.bssid ?: "",
                            ssid = net.ssid ?: "",
                            latitude = lat,
                            longitude = lon,
                            rssi = net.level,
                            frequency = net.frequency,
                            channel = net.channel,
                            capabilities = net.capabilities ?: "",
                            timestamp = net.lastTime ?: System.currentTimeMillis(),
                            type = when (net.type?.name) {
                                "BT" -> NetworkType.BLE
                                "CELL" -> NetworkType.CELL
                                else -> NetworkType.WIFI
                            }
                        )
                    )
                }
            }
        }

        // 2. Query SQLite database
        var db: SQLiteDatabase? = null
        try {
            if (ListFragment.lameStatic.dbHelper != null) {
                db = ListFragment.lameStatic.dbHelper.db
            } else {
                val dbFile = context.getDatabasePath("wiglewifi.sqlite")
                val altFile = File(FileUtility.getSDPath(), "wiglewifi.sqlite")
                val targetFile = if (dbFile.exists()) dbFile else if (altFile.exists()) altFile else null

                if (targetFile != null && targetFile.exists()) {
                    db = SQLiteDatabase.openDatabase(targetFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                }
            }

            if (db != null && db.isOpen) {
                // Query networks
                db.rawQuery(
                    "SELECT bssid, ssid, frequency, capabilities, lasttime, lastlat, lastlon, bestlevel, type FROM network WHERE lastlat != 0.0 AND lastlon != 0.0 ORDER BY lasttime DESC LIMIT ?",
                    arrayOf(limit.toString())
                ).use { cursor ->
                    val bssidIdx = cursor.getColumnIndex("bssid")
                    val ssidIdx = cursor.getColumnIndex("ssid")
                    val freqIdx = cursor.getColumnIndex("frequency")
                    val capIdx = cursor.getColumnIndex("capabilities")
                    val timeIdx = cursor.getColumnIndex("lasttime")
                    val latIdx = cursor.getColumnIndex("lastlat")
                    val lonIdx = cursor.getColumnIndex("lastlon")
                    val levelIdx = cursor.getColumnIndex("bestlevel")
                    val typeIdx = cursor.getColumnIndex("type")

                    while (cursor.moveToNext()) {
                        val bssid = if (bssidIdx >= 0) cursor.getString(bssidIdx) ?: "" else ""
                        val ssid = if (ssidIdx >= 0) cursor.getString(ssidIdx) ?: "" else ""
                        val freq = if (freqIdx >= 0) cursor.getInt(freqIdx) else 2412
                        val cap = if (capIdx >= 0) cursor.getString(capIdx) ?: "" else ""
                        val time = if (timeIdx >= 0) cursor.getLong(timeIdx) else System.currentTimeMillis()
                        val lat = if (latIdx >= 0) cursor.getDouble(latIdx) else 0.0
                        val lon = if (lonIdx >= 0) cursor.getDouble(lonIdx) else 0.0
                        val level = if (levelIdx >= 0) cursor.getInt(levelIdx) else -75
                        val typeStr = if (typeIdx >= 0) cursor.getString(typeIdx) ?: "W" else "W"

                        list.add(
                            Observation(
                                bssid = bssid,
                                ssid = ssid,
                                latitude = lat,
                                longitude = lon,
                                rssi = level,
                                frequency = freq,
                                channel = frequencyToChannel(freq),
                                capabilities = cap,
                                timestamp = time,
                                type = when (typeStr) {
                                    "B" -> NetworkType.BLE
                                    "C" -> NetworkType.CELL
                                    else -> NetworkType.WIFI
                                }
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Logged passively, proceed with available in-memory data
        }

        return list
    }

    /**
     * Step 2-5: Validates records, normalizes values, handles missing fields, flags duplicates.
     */
    fun processAndValidate(raw: List<Observation>): List<Observation> {
        val now = System.currentTimeMillis()
        val seenSignatures = mutableSetOf<String>()
        val result = mutableListOf<Observation>()

        for (obs in raw) {
            val issues = mutableListOf<String>()
            var quality = DataQualityStatus.NORMAL

            // 1. Coordinate check
            val validCoords = obs.latitude != 0.0 && obs.longitude != 0.0 &&
                    obs.latitude in -90.0..90.0 && obs.longitude in -180.0..180.0
            if (!validCoords) {
                issues.add("Invalid GPS coordinates (${obs.latitude}, ${obs.longitude})")
                quality = DataQualityStatus.INCONSISTENT
            }

            // 2. RSSI check (-115 to 0 dBm)
            val normalizedRssi = if (obs.rssi > 0) -obs.rssi else obs.rssi
            if (normalizedRssi < -115 || normalizedRssi > 0) {
                issues.add("Extreme RSSI value ($normalizedRssi dBm)")
                if (quality == DataQualityStatus.NORMAL) quality = DataQualityStatus.REQUIRES_REVIEW
            }

            // 3. Frequency & Channel validation
            val calcChannel = frequencyToChannel(obs.frequency)
            val resolvedChannel = if (obs.channel > 0) obs.channel else calcChannel
            if (obs.channel > 0 && calcChannel > 0 && obs.channel != calcChannel) {
                issues.add("Channel ${obs.channel} inconsistent with frequency ${obs.frequency} MHz")
                quality = DataQualityStatus.INCONSISTENT
            }

            // 4. Missing fields
            if (obs.bssid.isBlank()) {
                issues.add("Missing hardware BSSID")
                quality = DataQualityStatus.INCOMPLETE
            }

            // 5. Timestamp validation (Not before 2005, not in the future)
            if (obs.timestamp < 1104537600000L || obs.timestamp > now + 60_000L) {
                issues.add("Anomalous timestamp epoch (${obs.timestamp})")
                if (quality == DataQualityStatus.NORMAL) quality = DataQualityStatus.INCONSISTENT
            }

            // 6. Duplicate detection (Exact BSSID + Timestamp + Location)
            val signature = "${obs.bssid.uppercase()}:${obs.timestamp}:${"%.5f".format(obs.latitude)}:${"%.5f".format(obs.longitude)}"
            if (seenSignatures.contains(signature)) {
                issues.add("Duplicate observation record")
                quality = DataQualityStatus.DUPLICATE
            } else {
                seenSignatures.add(signature)
            }

            result.add(
                obs.copy(
                    bssid = obs.bssid.uppercase(),
                    rssi = normalizedRssi,
                    channel = resolvedChannel,
                    qualityStatus = quality,
                    qualityIssues = issues
                )
            )
        }

        return result
    }

    /**
     * Compute spatial grid bins (~55m resolution) and distinguish actual observed coverage from model predictions.
     */
    private fun computeGridBins(observations: List<Observation>): List<GridCell> {
        val resolution = 0.0005 // ~55 meters
        val bins = mutableMapOf<String, MutableList<Observation>>()

        for (obs in observations) {
            if (obs.latitude == 0.0 && obs.longitude == 0.0) continue
            val cx = (obs.latitude / resolution).roundToInt()
            val cy = (obs.longitude / resolution).roundToInt()
            val key = "$cx:$cy"
            bins.getOrPut(key) { mutableListOf() }.add(obs)
        }

        val cells = mutableListOf<GridCell>()
        for ((key, obsList) in bins) {
            val parts = key.split(":")
            val cx = parts[0].toInt()
            val cy = parts[1].toInt()
            val centerLat = cx * resolution
            val centerLon = cy * resolution
            val uniqueAps = obsList.map { it.bssid }.distinct().size
            val avgRssi = obsList.map { it.rssi }.average()

            val level = when {
                uniqueAps >= 15 -> "HIGH"
                uniqueAps >= 5 -> "MEDIUM"
                else -> "LOW"
            }

            cells.add(
                GridCell(
                    key = key,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    apCount = uniqueAps,
                    avgRssi = avgRssi,
                    densityLevel = level,
                    isPredicted = false // Confirmed observation
                )
            )
        }

        // Add Model-Estimated coverage cells for fringe / sparse regions (never presented as confirmed)
        if (cells.size >= 2) {
            val minLat = cells.minOf { it.centerLat }
            val maxLat = cells.maxOf { it.centerLat }
            val minLon = cells.minOf { it.centerLon }
            val maxLon = cells.maxOf { it.centerLon }

            val step = resolution * 2
            var lat = minLat - step
            while (lat <= maxLat + step) {
                var lon = minLon - step
                while (lon <= maxLon + step) {
                    val cx = (lat / resolution).roundToInt()
                    val cy = (lon / resolution).roundToInt()
                    val key = "$cx:$cy"
                    if (!bins.containsKey(key)) {
                        // Check distance to nearest observed cell
                        val nearestDist = cells.minOf {
                            sqrt((it.centerLat - lat).pow(2) + (it.centerLon - lon).pow(2))
                        }
                        if (nearestDist in resolution..(resolution * 3.5)) {
                            // Inverse Distance Weighting estimation
                            var weightedRssi = 0.0
                            var weightSum = 0.0
                            for (c in cells) {
                                val d = sqrt((c.centerLat - lat).pow(2) + (c.centerLon - lon).pow(2))
                                if (d > 0.0) {
                                    val w = 1.0 / (d * d)
                                    weightedRssi += c.avgRssi * w
                                    weightSum += w
                                }
                            }
                            val estRssi = if (weightSum > 0.0) (weightedRssi / weightSum) - 8.0 else -90.0
                            val estAps = max(1, (cells.map { it.apCount }.average() * 0.35).roundToInt())

                            cells.add(
                                GridCell(
                                    key = key,
                                    centerLat = lat,
                                    centerLon = lon,
                                    apCount = estAps,
                                    avgRssi = estRssi,
                                    densityLevel = "LOW",
                                    isPredicted = true // Clearly model-estimated prediction!
                                )
                            )
                        }
                    }
                    lon += step
                }
                lat += step
            }
        }

        return cells
    }

    companion object {
        @Volatile
        private var INSTANCE: ObservationPipeline? = null

        fun getInstance(context: Context): ObservationPipeline {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ObservationPipeline(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun frequencyToChannel(freq: Int): Int {
            return when (freq) {
                in 2412..2484 -> if (freq == 2484) 14 else ((freq - 2407) / 5)
                in 5170..5825 -> ((freq - 5000) / 5)
                in 5925..7125 -> ((freq - 5940) / 5)
                else -> 0
            }
        }
    }
}

