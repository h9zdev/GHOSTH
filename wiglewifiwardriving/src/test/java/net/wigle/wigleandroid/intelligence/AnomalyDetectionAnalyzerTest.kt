package net.wigle.wigleandroid.intelligence

import kotlinx.coroutines.runBlocking
import net.wigle.wigleandroid.intelligence.analyzer.AnomalyDetectionAnalyzer
import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.intelligence.model.Severity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AnomalyDetectionAnalyzerTest {

    private lateinit var analyzer: AnomalyDetectionAnalyzer

    @Before
    fun setup() {
        analyzer = AnomalyDetectionAnalyzer(
            alphaEwma = 0.3,
            zScoreThreshold = 2.5,
            suddenRssiJumpThreshold = 20
        )
    }

    @Test
    fun testRogueApEvilTwinDetected() = runBlocking {
        val batch = listOf(
            Observation(
                bssid = "00:11:22:33:44:55",
                ssid = "AirportFreeWiFi",
                latitude = 37.77,
                longitude = -122.41,
                rssi = -65,
                capabilities = "[WPA2-PSK-CCMP][ESS]"
            ),
            Observation(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "AirportFreeWiFi",
                latitude = 37.77,
                longitude = -122.41,
                rssi = -45,
                capabilities = "[ESS]" // Open network, rogue evil twin
            )
        )

        val findings = analyzer.analyze(batch)
        val evilTwinFinding = findings.find { it.title.contains("Potential Rogue AP / Evil Twin") }

        assertNotNull("Should detect evil twin with conflicting security", evilTwinFinding)
        assertEquals(Severity.CRITICAL, evilTwinFinding?.severity)
        assertEquals("AirportFreeWiFi", evilTwinFinding?.ssid)
    }

    @Test
    fun testSuddenRssiJumpAnomaly() = runBlocking {
        val bssid = "11:22:33:44:55:66"

        // Baseline observations at ~ -85 dBm
        val baseline = listOf(
            Observation(bssid, "HomeNet", 37.77, -122.41, rssi = -85),
            Observation(bssid, "HomeNet", 37.77, -122.41, rssi = -84),
            Observation(bssid, "HomeNet", 37.77, -122.41, rssi = -86)
        )
        analyzer.analyze(baseline)

        // Sudden massive surge to -40 dBm (+45 dBm delta)
        val surgeBatch = listOf(
            Observation(bssid, "HomeNet", 37.77, -122.41, rssi = -40)
        )
        val findings = analyzer.analyze(surgeBatch)

        val signalAnomaly = findings.find { it.title.contains("Sudden Signal Measurement Shift") }
        assertNotNull("Should detect sudden signal shift", signalAnomaly)
        assertEquals(AnalyzerType.ANOMALY_DETECTION, signalAnomaly?.analyzerType)
        assertTrue(signalAnomaly!!.severity == Severity.HIGH || signalAnomaly.severity == Severity.MEDIUM)
    }

    @Test
    fun testCrossBandJumpAnomaly() = runBlocking {
        val bssid = "22:33:44:55:66:77"

        // Initial observation on channel 6 (2.4 GHz)
        analyzer.analyze(listOf(
            Observation(bssid, "DualNet", 37.77, -122.41, rssi = -60, channel = 6)
        ))

        // Jump to channel 149 (5 GHz) for same BSSID
        val jumpBatch = listOf(
            Observation(bssid, "DualNet", 37.77, -122.41, rssi = -62, channel = 149)
        )
        val findings = analyzer.analyze(jumpBatch)

        val bandJump = findings.find { it.title.contains("Inconsistent Channel / Band Jump") }
        assertNotNull("Should detect inconsistent cross-band jump", bandJump)
        assertEquals(Severity.MEDIUM, bandJump?.severity)
    }

    @Test
    fun testRapidMacRotationDetected() = runBlocking {
        val batch = mutableListOf<Observation>()
        for (i in 1..6) {
            batch.add(
                Observation(
                    bssid = "00:AA:BB:CC:DD:%02X".format(i),
                    ssid = "RotatingSSID",
                    latitude = 37.77,
                    longitude = -122.41,
                    rssi = -70
                )
            )
        }

        val findings = analyzer.analyze(batch)
        val macMutation = findings.find { it.title.contains("High MAC Address Mutation") }
        assertNotNull("Should detect high MAC mutation anomaly", macMutation)
        assertEquals(Severity.HIGH, macMutation?.severity)
    }
}

