package net.wigle.wigleandroid.intelligence

import kotlinx.coroutines.runBlocking
import net.wigle.wigleandroid.intelligence.analyzer.GeospatialMlAnalyzer
import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Observation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GeospatialMlAnalyzerTest {

    private lateinit var analyzer: GeospatialMlAnalyzer

    @Before
    fun setup() {
        analyzer = GeospatialMlAnalyzer(epsMeters = 150.0, minPts = 3)
    }

    @Test
    fun testMobileApDetected() = runBlocking {
        val bssid = "AA:BB:CC:DD:EE:99"
        val batch = listOf(
            Observation(bssid, "Transit_Bus_WiFi", 37.7700, -122.4100, rssi = -65),
            Observation(bssid, "Transit_Bus_WiFi", 37.7750, -122.4150, rssi = -66) // ~700m away
        )

        val findings = analyzer.analyze(batch)
        val mobileFinding = findings.find { it.title.contains("Mobile / In-Transit") }
        assertNotNull("Should detect mobile AP with large geographic dispersion", mobileFinding)
        assertEquals(AnalyzerType.GEOSPATIAL_ML, mobileFinding?.analyzerType)
        assertEquals(bssid, mobileFinding?.bssid)
    }

    @Test
    fun testDbscanClustering() = runBlocking {
        val batch = mutableListOf<Observation>()
        // 4 points within 50 meters
        for (i in 1..4) {
            batch.add(
                Observation(
                    bssid = "00:11:22:33:44:%02X".format(i),
                    ssid = "CoreAP_$i",
                    latitude = 37.7749 + (i * 0.0001),
                    longitude = -122.4194 + (i * 0.0001),
                    rssi = -70
                )
            )
        }

        val findings = analyzer.analyze(batch)
        val cluster = findings.find { it.title.contains("DBSCAN Spatial Cluster") }
        assertNotNull("Should detect DBSCAN spatial core cluster", cluster)
    }
}

