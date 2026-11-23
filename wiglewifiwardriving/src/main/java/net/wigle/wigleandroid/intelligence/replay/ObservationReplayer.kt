package net.wigle.wigleandroid.intelligence.replay

import kotlinx.coroutines.delay
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.NetworkType
import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.intelligence.service.IntelligenceEngine

/**
 * Replay mode that feeds recorded observations through the intelligence analyzers.
 */
class ObservationReplayer(
    private val engine: IntelligenceEngine
) {
    suspend fun replay(
        observations: List<Observation>,
        batchSize: Int = 20,
        delayMsBetweenBatches: Long = 100,
        onProgress: (processed: Int, total: Int, newFindings: List<Finding>) -> Unit = { _, _, _ -> }
    ) {
        val chunks = observations.chunked(batchSize)
        var processedCount = 0

        for (chunk in chunks) {
            val findings = engine.processBatch(chunk)
            processedCount += chunk.size
            onProgress(processedCount, observations.size, findings)
            if (delayMsBetweenBatches > 0) {
                delay(delayMsBetweenBatches)
            }
        }
    }

    companion object {
        fun generateSamplePassiveStream(): List<Observation> {
            val list = mutableListOf<Observation>()
            val baseLat = 37.7749
            val baseLon = -122.4194
            val now = System.currentTimeMillis()

            // Tri-band spectrum & multiple AP nodes
            val sampleDefs = listOf(
                Triple("CorporateHQ_Secure", 5180, "[WPA3-SAE-CCMP][RSN]"),
                Triple("Guest_Portal_Open", 2412, "[OPEN]"),
                Triple("Lab_UltraFast_6G", 6155, "[WPA3-SAE-CCMP][WIFI6E]"),
                Triple("Muni_Metro_Bus_42", 2437, "[WPA2-PSK-CCMP]"),
                Triple("Starbucks_WiFi", 2462, "[OPEN]"),
                Triple("City_Free_WiFi", 5220, "[OPEN]"),
                Triple("Eduroam_Campus", 5240, "[WPA2-EAP-CCMP]"),
                Triple("IoT_Sensor_Hub", 2422, "[WPA2-PSK-CCMP]")
            )

            sampleDefs.forEachIndexed { i, (ssid, freq, caps) ->
                val mac = "00:11:22:33:44:%02X".format(i)
                val channel = when {
                    freq in 2400..2500 -> (freq - 2407) / 5
                    freq in 5000..5900 -> (freq - 5000) / 5
                    else -> 1
                }
                list.add(
                    Observation(
                        bssid = mac,
                        ssid = ssid,
                        latitude = baseLat + (i * 0.0003),
                        longitude = baseLon + (i * 0.0004),
                        rssi = -50 - (i * 5),
                        frequency = freq,
                        channel = channel,
                        capabilities = caps,
                        timestamp = now - (sampleDefs.size - i) * 5000L,
                        type = NetworkType.WIFI
                    )
                )
            }

            return list
        }
    }
}
