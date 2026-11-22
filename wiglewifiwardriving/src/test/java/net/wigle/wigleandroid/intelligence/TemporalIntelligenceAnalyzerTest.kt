package net.wigle.wigleandroid.intelligence

import kotlinx.coroutines.runBlocking
import net.wigle.wigleandroid.intelligence.analyzer.TemporalIntelligenceAnalyzer
import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Observation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TemporalIntelligenceAnalyzerTest {

    private lateinit var analyzer: TemporalIntelligenceAnalyzer

    @Before
    fun setup() {
        analyzer = TemporalIntelligenceAnalyzer()
    }

    @Test
    fun testNewlyEmergedNetworkDetected() = runBlocking {
        val batch = listOf(
            Observation(
                bssid = "00:AA:BB:CC:DD:11",
                ssid = "BrandNewAP",
                latitude = 37.77,
                longitude = -122.41,
                rssi = -55 // Strong signal
            )
        )

        val findings = analyzer.analyze(batch)
        val newNet = findings.find { it.title.contains("Newly Emerged Network") }
        assertNotNull("Should identify newly emerged strong AP", newNet)
        assertEquals(AnalyzerType.TEMPORAL_INTELLIGENCE, newNet?.analyzerType)
        assertEquals("BrandNewAP", newNet?.ssid)
    }

    @Test
    fun testChurnRateCalculation() = runBlocking {
        val bssid = "11:22:33:44:55:66"
        // Cycle 1
        analyzer.analyze(listOf(Observation(bssid, "OldNet", 37.77, -122.41, rssi = -70)))

        // Cycle 2: 1 persistent, 1 new
        val findings = analyzer.analyze(listOf(
            Observation(bssid, "OldNet", 37.77, -122.41, rssi = -70),
            Observation("99:88:77:66:55:44", "NewNet", 37.77, -122.41, rssi = -70)
        ))

        val churn = findings.find { it.title.contains("Churn Rate") }
        assertNotNull(churn)
        assertTrue(churn!!.title.contains("50% New"))
    }
}

