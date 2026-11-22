package net.wigle.wigleandroid.intelligence.analyzer

import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.intelligence.model.Severity
import java.util.concurrent.ConcurrentHashMap

/**
 * Temporal Intelligence Analyzer
 * Strictly passive and metadata-only.
 * Tracks:
 * 1. Network emergence (newly observed access points)
 * 2. Network persistence vs transience (churn rate)
 * 3. Changes in network observations over time
 * 4. Temporal shift patterns
 */
class TemporalIntelligenceAnalyzer : Analyzer {

    data class NetworkTemporalRecord(
        val bssid: String,
        val ssid: String,
        val firstSeen: Long,
        var lastSeen: Long,
        var observationCount: Int = 1,
        var lastRssi: Int = -100
    )

    private val temporalHistory = ConcurrentHashMap<String, NetworkTemporalRecord>()

    override suspend fun analyze(batch: List<Observation>): List<Finding> {
        if (batch.isEmpty()) return emptyList()

        val findings = mutableListOf<Finding>()
        val newlyObserved = mutableListOf<Observation>()
        val persistentCount = mutableListOf<Observation>()

        for (obs in batch) {
            val record = temporalHistory[obs.bssid]
            if (record == null) {
                // First time seen in this lifecycle
                temporalHistory[obs.bssid] = NetworkTemporalRecord(
                    bssid = obs.bssid,
                    ssid = obs.ssid,
                    firstSeen = obs.timestamp,
                    lastSeen = obs.timestamp,
                    observationCount = 1,
                    lastRssi = obs.rssi
                )
                newlyObserved.add(obs)
            } else {
                record.lastSeen = obs.timestamp
                record.observationCount++
                record.lastRssi = obs.rssi
                persistentCount.add(obs)
            }
        }

        // 1. Highlight newly emerged networks with strong signal
        val strongNewNets = newlyObserved.filter { it.rssi >= -65 && it.ssid.isNotBlank() }
        for (strong in strongNewNets.take(3)) {
            findings.add(
                Finding(
                    analyzerType = AnalyzerType.TEMPORAL_INTELLIGENCE,
                    title = "Newly Emerged Network: '${strong.ssid}'",
                    summary = "First observation of BSSID ${strong.bssid} with strong local RSSI (${strong.rssi} dBm).",
                    severity = Severity.LOW,
                    latitude = strong.latitude,
                    longitude = strong.longitude,
                    bssid = strong.bssid,
                    ssid = strong.ssid,
                    score = 0.65,
                    details = "First seen timestamp: ${strong.timestamp}. Capabilities: ${strong.capabilities}. Channel: ${strong.channel}."
                )
            )
        }

        // 2. Churn Rate & Macro Temporal Report Finding
        val totalTracked = temporalHistory.size
        val sample = batch.first()
        val newPct = if (batch.isNotEmpty()) (newlyObserved.size * 100) / batch.size else 0

        findings.add(
            Finding(
                analyzerType = AnalyzerType.TEMPORAL_INTELLIGENCE,
                title = "Temporal Churn Rate: $newPct% New / ${100 - newPct}% Persistent",
                summary = "Active scan cycle ingested ${newlyObserved.size} new nodes and ${persistentCount.size} persistent nodes (Historical footprint: $totalTracked total APs).",
                severity = Severity.INFO,
                latitude = sample.latitude,
                longitude = sample.longitude,
                score = (newPct / 100.0).coerceIn(0.1, 1.0),
                details = "Total networks tracked over time: $totalTracked. Current batch size: ${batch.size} observations."
            )
        )

        return findings
    }

    fun reset() {
        temporalHistory.clear()
    }
}

