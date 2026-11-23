package net.wigle.wigleandroid.intelligence.analyzer

import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.intelligence.model.Severity

/**
 * Signal Intelligence Analyzer
 * Strictly passive and metadata-only.
 *
 * Analyzes:
 * 1. RSSI distribution & signal quality classification (Excellent, Good, Fair, Poor)
 * 2. Cross-band comparison (2.4 GHz, 5 GHz, 6 GHz)
 * 3. Channel congestion analysis & best-channel suggestions
 * 4. Frequency distribution metrics
 *
 * Operational Disclaimer: Clearly distinguishes observed channel counts from actual RF spectrum occupancy.
 * Passive observations alone cannot establish true channel occupancy.
 */
class SignalIntelligenceAnalyzer : Analyzer {

    override suspend fun analyze(batch: List<Observation>): List<Finding> {
        if (batch.isEmpty()) return emptyList()

        val findings = mutableListOf<Finding>()

        // 1. Channel Congestion Analysis
        val channelCounts = mutableMapOf<Int, MutableList<Observation>>()
        for (obs in batch) {
            val ch = if (obs.channel != 0) obs.channel else frequencyToChannel(obs.frequency)
            if (ch > 0) {
                channelCounts.getOrPut(ch) { mutableListOf() }.add(obs)
            }
        }

        // Evaluate 2.4 GHz Congestion (Channels 1, 6, 11)
        val ch1Count = channelCounts[1]?.size ?: 0
        val ch6Count = channelCounts[6]?.size ?: 0
        val ch11Count = channelCounts[11]?.size ?: 0

        val maxCh24 = listOf(Pair(1, ch1Count), Pair(6, ch6Count), Pair(11, ch11Count)).maxByOrNull { it.second }
        if (maxCh24 != null && maxCh24.second >= 4) {
            val congestedChannel = maxCh24.first
            val alternateChannels = listOf(1, 6, 11).filter { it != congestedChannel }
            val leastCongested = alternateChannels.minByOrNull { ch -> channelCounts[ch]?.size ?: 0 } ?: 1
            val sample = channelCounts[congestedChannel]?.first()

            findings.add(
                Finding(
                    analyzerType = AnalyzerType.SIGNAL_INTELLIGENCE,
                    title = "Severe Channel Congestion / Observed Concentration: Ch $congestedChannel",
                    summary = "${maxCh24.second} APs observed on Channel $congestedChannel. Recommended lower-density channel: Ch $leastCongested.",
                    severity = if (maxCh24.second >= 8) Severity.HIGH else Severity.MEDIUM,
                    latitude = sample?.latitude ?: 0.0,
                    longitude = sample?.longitude ?: 0.0,
                    score = (maxCh24.second / 15.0).coerceIn(0.2, 1.0),
                    details = "Observed AP Counts: Ch 1: $ch1Count, Ch 6: $ch6Count, Ch 11: $ch11Count. Note: Observed channel counts represent beacon observations; true RF spectral utilization requires active spectrum analysis.",
                    category = "Channel Utilization",
                    recommendedAction = "Consider migrating high-throughput clients to Ch $leastCongested or 5 GHz band"
                )
            )
        }

        // 2. Cross-Band Distribution (2.4 GHz vs 5 GHz vs 6 GHz)
        var count24G = 0
        var count5G = 0
        var count6G = 0
        val rssi24G = mutableListOf<Int>()
        val rssi5G = mutableListOf<Int>()
        val rssi6G = mutableListOf<Int>()

        for (obs in batch) {
            when {
                obs.frequency in 2400..2495 -> {
                    count24G++
                    rssi24G.add(obs.rssi)
                }
                obs.frequency in 5150..5895 -> {
                    count5G++
                    rssi5G.add(obs.rssi)
                }
                obs.frequency in 5925..7125 -> {
                    count6G++
                    rssi6G.add(obs.rssi)
                }
            }
        }

        val totalBands = count24G + count5G + count6G
        if (totalBands > 0) {
            val pct24 = (count24G * 100) / totalBands
            val pct5 = (count5G * 100) / totalBands
            val pct6 = (count6G * 100) / totalBands

            val avgRssi24 = if (rssi24G.isNotEmpty()) rssi24G.average() else -100.0
            val avgRssi5 = if (rssi5G.isNotEmpty()) rssi5G.average() else -100.0
            val firstSample = batch.first()

            findings.add(
                Finding(
                    analyzerType = AnalyzerType.SIGNAL_INTELLIGENCE,
                    title = "Spectrum Allocation: $pct24% 2.4G / $pct5% 5G / $pct6% 6G",
                    summary = "Tri-band breakdown: 2.4GHz: $count24G (avg ${"%.1f".format(avgRssi24)} dBm), 5GHz: $count5G (avg ${"%.1f".format(avgRssi5)} dBm), 6GHz (Wi-Fi 6E/7): $count6G.",
                    severity = Severity.INFO,
                    latitude = firstSample.latitude,
                    longitude = firstSample.longitude,
                    score = 0.65,
                    details = "Observed spectrum representation across ${batch.size} observations. Passive observations alone cannot establish true channel occupancy.",
                    category = "Band Distribution",
                    recommendedAction = "Expand 5 GHz and 6 GHz infrastructure to offload congested 2.4 GHz spectrum"
                )
            )
        }

        return findings
    }

    private fun frequencyToChannel(freq: Int): Int {
        return when (freq) {
            in 2412..2484 -> if (freq == 2484) 14 else ((freq - 2407) / 5)
            in 5170..5825 -> ((freq - 5000) / 5)
            in 5925..7125 -> ((freq - 5940) / 5)
            else -> 0
        }
    }
}
