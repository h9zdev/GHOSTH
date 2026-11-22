package net.wigle.wigleandroid.intelligence

import kotlinx.coroutines.runBlocking
import net.wigle.wigleandroid.intelligence.analyzer.WirelessDensityAnalyzer
import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.intelligence.model.Severity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WirelessDensityAnalyzerTest {

    private lateinit var analyzer: WirelessDensityAnalyzer

    @Before
    fun setup() {
        analyzer = WirelessDensityAnalyzer(
            gridResolution = 0.0005,
            highDensityThreshold = 5,
            criticalDensityThreshold = 10
        )
    }

    @Test
    fun testEmptyBatchReturnsEmpty() = runBlocking {
        val findings = analyzer.analyze(emptyList())
        assertTrue(findings.isEmpty())
    }

    @Test
    fun testHighDensityConcentrationDetected() = runBlocking {
        val baseLat = 37.7749
        val baseLon = -122.4194
        val batch = mutableListOf<Observation>()

        // 12 unique BSSIDs in the same cell
        for (i in 1..12) {
            batch.add(
                Observation(
                    bssid = "00:11:22:33:44:%02X".format(i),
                    ssid = "OfficeNet_$i",
                    latitude = baseLat,
                    longitude = baseLon,
                    rssi = -60 - (i % 10),
                    channel = 6
                )
            )
        }

        val findings = analyzer.analyze(batch)
        assertFalse("Expected findings for dense cluster", findings.isEmpty())

        val highDensityFinding = findings.find { it.title.contains("High Network Concentration") }
        assertNotNull("Should detect High Network Concentration", highDensityFinding)
        assertEquals(AnalyzerType.DENSITY_PREDICTION, highDensityFinding?.analyzerType)
        assertEquals(Severity.CRITICAL, highDensityFinding?.severity)
        assertTrue(highDensityFinding!!.score > 0.4)
    }

    @Test
    fun testHeatmapGeneration() {
        val batch = listOf(
            Observation("AA:BB:CC:DD:EE:01", "NetA", 37.77, -122.41, rssi = -50),
            Observation("AA:BB:CC:DD:EE:02", "NetB", 37.77, -122.41, rssi = -55),
            Observation("AA:BB:CC:DD:EE:03", "NetC", 37.80, -122.45, rssi = -80)
        )

        val heatmap = analyzer.generateHeatmap(batch)
        assertEquals(2, heatmap.size)

        val densePoint = heatmap.find { it.apCount == 2 }
        assertNotNull(densePoint)
        assertEquals(1.0, densePoint!!.weight, 0.01)
    }
}

