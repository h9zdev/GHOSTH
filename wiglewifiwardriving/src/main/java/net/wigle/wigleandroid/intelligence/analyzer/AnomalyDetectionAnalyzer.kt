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
 *
 * Detects:
 * 1. Observation Anomalies: Duplicate records, inconsistent metadata, invalid coords, unusual frequency changes, timestamp inconsistencies.
 * 2. Signal Anomalies: Sudden signal measurement shifts, unusual RSSI variations (>2.5σ), cross-band jumps.
 *
 * Note: An unusual observation is not proof of an attack.
 */
class AnomalyDetectionAnalyzer(
    private val alphaEwma: Double = 0.3,
    private val zScoreThreshold: Double = 2.5,
    private val suddenRssiJumpThreshold: Int = 20
) : Analyzer {

    data class BssidStats(
        var ewmaRssi: Double,
        var variance: Double,
        var lastLat: Double,
        var lastLon: Double,
        var lastTime: Long,
        var lastChannel: Int,
        var lastCapabilities: String,
        var observationCount: Int = 1
    )

    private val bssidHistory = ConcurrentHashMap<String, BssidStats>()
    private var anomalyCounter = 0

    override suspend fun analyze(batch: List<Observation>): List<Finding> {
        if (batch.isEmpty()) return emptyList()

        val findings = mutableListOf<Finding>()
        val now = System.currentTimeMillis()

        // 1. Observation Metadata & Duplicate Checks
        val seenSignatures = mutableSetOf<String>()
        for (obs in batch) {
            val sig = "${obs.bssid}:${obs.timestamp}:${"%.5f".format(obs.latitude)}:${"%.5f".format(obs.longitude)}"
            if (!seenSignatures.add(sig)) {
                val aid = "ANOM-DUP-${++anomalyCounter}"
                findings.add(
                    Finding(
                        analyzerType = AnalyzerType.ANOMALY_DETECTION,
                        title = "Duplicate Record Detected: ${obs.bssid}",
                        summary = "Identical BSSID, timestamp, and coordinates detected. Retaining original evidence while flagging duplication.",
                        severity = Severity.LOW,
                        latitude = obs.latitude,
                        longitude = obs.longitude,
                        bssid = obs.bssid,
                        ssid = obs.ssid,
                        score = 0.70,
                        details = "Timestamp: ${obs.timestamp}, Coords: (${obs.latitude}, ${obs.longitude}). Cause: Scanner re-buffering or dual-interface reception.",
                        category = "Duplicate Record",
                        recommendedAction = "Deduplicate observation records while preserving audit log",
                        anomalyId = aid
                    )
                )
            }

            // Invalid Coordinates Check
            if (obs.latitude == 0.0 && obs.longitude == 0.0) {
                val aid = "ANOM-GEO-${++anomalyCounter}"
                findings.add(
                    Finding(
                        analyzerType = AnalyzerType.ANOMALY_DETECTION,
                        title = "Null Coordinate Observation: ${obs.bssid}",
                        summary = "Observation logged without valid GNSS fix (0.0, 0.0).",
                        severity = Severity.LOW,
                        latitude = 0.0,
                        longitude = 0.0,
                        bssid = obs.bssid,
                        ssid = obs.ssid,
                        score = 0.60,
                        details = "GPS satellite lock was pending or obscured during RF capture. Non-malicious.",
                        category = "Coordinate Anomaly",
                        recommendedAction = "Verify GNSS constellation visibility and GPS fix state",
                        anomalyId = aid
                    )
                )
            }

            // Timestamp Disparity
            if (obs.timestamp > now + 60_000L || obs.timestamp < 1104537600000L) {
                val aid = "ANOM-TIME-${++anomalyCounter}"
                findings.add(
                    Finding(
                        analyzerType = AnalyzerType.ANOMALY_DETECTION,
                        title = "Timestamp Disparity: ${obs.bssid}",
                        summary = "Observation timestamp (${obs.timestamp}) lies outside the expected timeline epoch.",
                        severity = Severity.MEDIUM,
                        latitude = obs.latitude,
                        longitude = obs.longitude,
                        bssid = obs.bssid,
                        ssid = obs.ssid,
                        score = 0.80,
                        details = "Likely host system clock synchronization drift or NTP uninitialized state.",
                        category = "Timestamp Disparity",
                        recommendedAction = "Synchronize device system clock via NTP/GPS time",
                        anomalyId = aid
                    )
                )
            }
        }

        // 2. Inconsistent Metadata (Capability Disparity across Same SSID & MAC Mutation)
        val ssidGroups = batch.filter { it.ssid.isNotBlank() }.groupBy { it.ssid }
        for ((ssid, obsList) in ssidGroups) {
            val bssidsForSsid = obsList.map { it.bssid }.distinct()
            if (bssidsForSsid.size > 1) {
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
                        val aid = "ANOM-CAP-${++anomalyCounter}"
                        findings.add(
                            Finding(
                                analyzerType = AnalyzerType.ANOMALY_DETECTION,
                                title = "Potential Rogue AP / Evil Twin: '$ssid'",
                                summary = "Observed matching SSID with contradictory security capabilities: Secure ($secure) vs Open/Legacy ($openOrWep). Note: Unusual observation is not proof of an attack.",
                                severity = Severity.CRITICAL,
                                latitude = rogueObs.latitude,
                                longitude = rogueObs.longitude,
                                bssid = rogueObs.bssid,
                                ssid = ssid,
                                score = 0.95,
                                details = "Conflicting BSSIDs: ${bssidsForSsid.joinToString(", ")}. Note: An unusual observation is not proof of an attack. May be a captive portal, guest subnet, or legacy repeater.",
                                category = "Observation Metadata",
                                recommendedAction = "Review network capability configuration and verify dual-SSID deployments",
                                anomalyId = aid
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
                            details = "Observed BSSIDs: ${bssidsForSsid.take(6).joinToString(", ")} (total: ${bssidsForSsid.size})",
                            category = "MAC Mutation",
                            recommendedAction = "Verify if private MAC address rotation is active on clients or virtual APs",
                            anomalyId = "ANOM-MAC-${++anomalyCounter}"
                        )
                    )
                }
            }
        }

        // 3. Signal Anomalies & Channel Jumps (EWMA Baseline & Z-Score Variance)
        for (obs in batch) {
            val stats = bssidHistory[obs.bssid]
            if (stats == null) {
                bssidHistory[obs.bssid] = BssidStats(
                    ewmaRssi = obs.rssi.toDouble(),
                    variance = 4.0,
                    lastLat = obs.latitude,
                    lastLon = obs.longitude,
                    lastTime = obs.timestamp,
                    lastChannel = obs.channel,
                    lastCapabilities = obs.capabilities
                )
            } else {
                // Cross-band jump check
                if (stats.lastChannel > 0 && obs.channel > 0) {
                    val was24 = stats.lastChannel in 1..14
                    val is5or6 = obs.channel > 14
                    if (was24 && is5or6) {
                        findings.add(
                            Finding(
                                analyzerType = AnalyzerType.ANOMALY_DETECTION,
                                title = "Inconsistent Channel / Band Jump: ${obs.bssid}",
                                summary = "BSSID shifted abruptly across bands from 2.4GHz (Ch ${stats.lastChannel}) to 5GHz/6GHz (Ch ${obs.channel}).",
                                severity = Severity.MEDIUM,
                                latitude = obs.latitude,
                                longitude = obs.longitude,
                                bssid = obs.bssid,
                                ssid = obs.ssid,
                                score = 0.75,
                                details = "Cross-band transition observed for identical hardware address.",
                                category = "Observation Metadata",
                                recommendedAction = "Verify dual-band single-MAC radio architecture",
                                anomalyId = "ANOM-BAND-${++anomalyCounter}"
                            )
                        )
                    }
                }

                val deltaRssi = obs.rssi - stats.ewmaRssi
                val stdDev = sqrt(stats.variance.coerceAtLeast(1.0))
                val zScore = if (stdDev > 0) abs(deltaRssi) / stdDev else 0.0

                // Sudden RSSI Jump or Z-Score threshold exceeded
                if (abs(deltaRssi) >= suddenRssiJumpThreshold || zScore >= zScoreThreshold) {
                    val aid = "ANOM-SIG-${++anomalyCounter}"
                    findings.add(
                        Finding(
                            analyzerType = AnalyzerType.ANOMALY_DETECTION,
                            title = "Sudden Signal Measurement Shift / Unusual RSSI Variation: ${obs.bssid}",
                            summary = "Sudden ${"%.1f".format(deltaRssi)} dBm RSSI deviation (Z=${"%.2f".format(zScore)}σ) from smoothed baseline (${"%.1f".format(stats.ewmaRssi)} dBm).",
                            severity = if (abs(deltaRssi) > 35) Severity.HIGH else Severity.MEDIUM,
                            latitude = obs.latitude,
                            longitude = obs.longitude,
                            bssid = obs.bssid,
                            ssid = obs.ssid,
                            score = (zScore / 5.0).coerceIn(0.5, 0.95),
                            details = "Observed: ${obs.rssi} dBm, Baseline: ${"%.1f".format(stats.ewmaRssi)} dBm, StdDev: ${"%.1f".format(stdDev)}. Multipath reflections or movement are common causes.",
                            category = "Signal Variance",
                            recommendedAction = "Recalibrate signal baseline and check for physical obstructions",
                            anomalyId = aid
                        )
                    )
                }

                // Update EWMA & Variance
                stats.ewmaRssi = (1 - alphaEwma) * stats.ewmaRssi + alphaEwma * obs.rssi
                val newDiff = obs.rssi - stats.ewmaRssi
                stats.variance = (1 - alphaEwma) * stats.variance + alphaEwma * (newDiff * newDiff)
                stats.lastLat = obs.latitude
                stats.lastLon = obs.longitude
                stats.lastTime = obs.timestamp
                stats.lastChannel = obs.channel
                stats.observationCount++
            }
        }

        return findings
    }

    fun resetState() {
        bssidHistory.clear()
        anomalyCounter = 0
    }
}
