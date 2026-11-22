package net.wigle.wigleandroid.intelligence.analyzer

import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.intelligence.model.Severity
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Anomaly Detection Analyzer (HIGH PRIORITY)
 * Strictly passive and metadata-only.
 * Uses classical EWMA (Exponentially Weighted Moving Average), Z-score thresholds,
 * and multi-dimensional consistency checking to flag:
 * 1. Sudden signal-strength changes (Z-score > 2.5, EWMA divergence > 25 dBm)
 * 2. Duplicate or inconsistent access point records (Evil Twin / Rogue AP / Encryption downgrades)
 * 3. Channel/band inconsistencies
 * 4. Unusual MAC address observation patterns (spoofing / rapid rotation)
 */
class AnomalyDetectionAnalyzer(
    private val alphaEwma: Double = 0.3,
    private val zScoreThreshold: Double = 2.5,
    private val suddenRssiJumpThreshold: Int = 24
) : Analyzer {

    data class BssidStats(
        var ewmaRssi: Double,
        var variance: Double,
        var lastLat: Double,
        var lastLon: Double,
        var lastChannel: Int,
        var lastCapabilities: String,
        var observationCount: Int = 1
    )

    // In-memory EWMA state tracker for baseline smoothing across observations
    private val bssidHistory = ConcurrentHashMap<String, BssidStats>()

    override suspend fun analyze(batch: List<Observation>): List<Finding> {
        if (batch.isEmpty()) return emptyList()

        val findings = mutableListOf<Finding>()

        // 1. Inconsistent & Duplicate AP Records (Evil Twin / Capability Downgrade)
        // Group by SSID to detect rogue duplicate APs with conflicting capabilities
        val ssidGroups = batch.filter { it.ssid.isNotBlank() }.groupBy { it.ssid }
        for ((ssid, obsList) in ssidGroups) {
            val bssidsForSsid = obsList.map { it.bssid }.distinct()
            if (bssidsForSsid.size > 1) {
                // Check if capabilities are conflicting across different BSSIDs sharing the same SSID
                val capsMap = obsList.groupBy { it.capabilities }
                if (capsMap.size > 1) {
                    val openOrWep = capsMap.keys.find { caps ->
                        caps.isEmpty() || caps.contains("WEP", ignoreCase = true) || (!caps.contains("WPA", ignoreCase = true) && !caps.contains("RSN", ignoreCase = true))
                    }
                    val secure = capsMap.keys.find { caps ->
                        caps.contains("WPA2", ignoreCase = true) || caps.contains("WPA3", ignoreCase = true) || caps.contains("SAE", ignoreCase = true)
                    }

                    if (openOrWep != null && secure != null) {
                        val rogueObs = obsList.first { it.capabilities == openOrWep }
                        findings.add(
                            Finding(
                                analyzerType = AnalyzerType.ANOMALY_DETECTION,
                                title = "Potential Rogue AP / Evil Twin: '$ssid'",
                                summary = "Observed matching SSID '$ssid' with contradictory security. Secure ($secure) vs Downgraded ($openOrWep).",
                                severity = Severity.CRITICAL,
                                latitude = rogueObs.latitude,
                                longitude = rogueObs.longitude,
                                bssid = rogueObs.bssid,
                                ssid = ssid,
                                score = 0.95,
                                details = "Conflicting BSSIDs: ${bssidsForSsid.joinToString(", ")}. Capabilities disparity: '$secure' vs '$openOrWep'."
                            )
                        )
                    }
                }

                // Rapid MAC Rotation / Spoofing Anomaly
                if (bssidsForSsid.size >= 5) {
                    val sample = obsList.first()
                    findings.add(
                        Finding(
                            analyzerType = AnalyzerType.ANOMALY_DETECTION,
                            title = "High MAC Address Mutation: '$ssid'",
                            summary = "Detected ${bssidsForSsid.size} distinct BSSIDs broadcasting the same SSID in a single session.",
                            severity = Severity.HIGH,
                            latitude = sample.latitude,
                            longitude = sample.longitude,
                            bssid = sample.bssid,
                            ssid = ssid,
                            score = 0.85,
                            details = "Observed BSSIDs: ${bssidsForSsid.take(6).joinToString(", ")} (total: ${bssidsForSsid.size})"
                        )
                    )
                }
            }
        }

        // 2. Sudden Signal Measurement Shifts & Inconsistent Channel Records
        for (obs in batch) {
            val stats = bssidHistory[obs.bssid]
            if (stats == null) {
                bssidHistory[obs.bssid] = BssidStats(
                    ewmaRssi = obs.rssi.toDouble(),
                    variance = 4.0, // Initial nominal variance
                    lastLat = obs.latitude,
                    lastLon = obs.longitude,
                    lastChannel = obs.channel,
                    lastCapabilities = obs.capabilities,
                    observationCount = 1
                )
            } else {
                // Channel / Band Jump Inconsistency Check
                if (stats.lastChannel != 0 && obs.channel != 0 && stats.lastChannel != obs.channel) {
                    val bandA = if (stats.lastChannel <= 14) "2.4GHz" else "5GHz/6GHz"
                    val bandB = if (obs.channel <= 14) "2.4GHz" else "5GHz/6GHz"
                    if (bandA != bandB) {
                        findings.add(
                            Finding(
                                analyzerType = AnalyzerType.ANOMALY_DETECTION,
                                title = "Inconsistent Channel / Band Jump",
                                summary = "BSSID ${obs.bssid} jumped cross-band from ch ${stats.lastChannel} ($bandA) to ch ${obs.channel} ($bandB).",
                                severity = Severity.MEDIUM,
                                latitude = obs.latitude,
                                longitude = obs.longitude,
                                bssid = obs.bssid,
                                ssid = obs.ssid,
                                score = 0.70,
                                details = "Cross-band transition without re-association delay. Prior channel: ${stats.lastChannel}, current: ${obs.channel}."
                            )
                        )
                    }
                }

                // EWMA & Z-Score Sudden RSSI Shift Check
                val diff = obs.rssi - stats.ewmaRssi
                val stdDev = sqrt(stats.variance).coerceAtLeast(1.0)
                val zScore = diff / stdDev

                if (abs(diff) >= suddenRssiJumpThreshold || abs(zScore) >= zScoreThreshold) {
                    val direction = if (diff > 0) "surge" else "drop"
                    val severity = if (abs(diff) >= 30) Severity.HIGH else Severity.MEDIUM
                    findings.add(
                        Finding(
                            analyzerType = AnalyzerType.ANOMALY_DETECTION,
                            title = "Sudden Signal Measurement Shift (${obs.rssi} dBm)",
                            summary = "BSSID ${obs.bssid} ('${obs.ssid}') exhibited sudden $direction of ${"%.1f".format(abs(diff))} dBm (z-score: ${"%.2f".format(zScore)}).",
                            severity = severity,
                            latitude = obs.latitude,
                            longitude = obs.longitude,
                            bssid = obs.bssid,
                            ssid = obs.ssid,
                            score = (abs(zScore) / 4.0).coerceIn(0.5, 1.0),
                            details = "Current RSSI: ${obs.rssi} dBm, Prior EWMA Baseline: ${"%.1f".format(stats.ewmaRssi)} dBm, StdDev: ${"%.2f".format(stdDev)}."
                        )
                    )
                }

                // Update EWMA and Variance
                val oldMean = stats.ewmaRssi
                stats.ewmaRssi = (alphaEwma * obs.rssi) + ((1.0 - alphaEwma) * stats.ewmaRssi)
                val error = obs.rssi - oldMean
                stats.variance = ((1.0 - alphaEwma) * stats.variance) + (alphaEwma * error * error)
                stats.lastLat = obs.latitude
                stats.lastLon = obs.longitude
                stats.lastChannel = obs.channel
                stats.lastCapabilities = obs.capabilities
                stats.observationCount++
            }
        }

        return findings
    }

    /**
     * Clear cached EWMA state (e.g. for fresh replay or unit tests)
     */
    fun resetState() {
        bssidHistory.clear()
    }
}
