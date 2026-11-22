package net.wigle.wigleandroid.intelligence

import kotlinx.coroutines.runBlocking
import net.wigle.wigleandroid.intelligence.analyzer.SignalIntelligenceAnalyzer
import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Observation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SignalIntelligenceAnalyzerTest {

    private lateinit var analyzer: SignalIntelligenceAnalyzer

    @Before
    fun setup() {
        analyzer = SignalIntelligenceAnalyzer()
    }

    @Test
    fun testChannelCongestionDetected() = runBlocking {
        val batch = mutableListOf<Observation>()
        // 8 networks on channel 6
        for (i in 1..8) {
            batch.add(
                Observation(
                    bssid = "00:11:22:33:00:%02X".format(i),
                    ssid = "CongestedNet_$i",
                    latitude = 37.77,
                    longitude = -122.41,
                    rssi = -65,
                    channel = 6,
                    frequency = 2437
                )
            )
        }

        val findings = analyzer.analyze(batch)
        val congestion = findings.find { it.title.contains("Channel Congestion") }
        assertNotNull("Should detect channel congestion on channel 6", congestion)
        assertEquals(AnalyzerType.SIGNAL_INTELLIGENCE, congestion?.analyzerType)
        assertTrue(congestion!!.summary.contains("Channel 6"))
    }

    @Test
    fun testSpectrumAllocationCalculated() = runBlocking {
        val batch = listOf(
            Observation("AA:01", "24G_Net", 37.77, -122.41, rssi = -60, frequency = 2412),
            Observation("AA:02", "5G_Net", 37.77, -122.41, rssi = -65, frequency = 5180),
            Observation("AA:03", "6G_Net", 37.77, -122.41, rssi = -70, frequency = 6125)
        )

        val findings = analyzer.analyze(batch)
        val spectrum = findings.find { it.title.contains("Spectrum Allocation") }
        assertNotNull("Should compute spectrum allocation", spectrum)
        assertTrue(spectrum!!.title.contains("2.4G"))
        assertTrue(spectrum.title.contains("5G"))
        assertTrue(spectrum.title.contains("6G"))
    }
}

