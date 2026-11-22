package net.wigle.wigleandroid.intelligence

import kotlinx.coroutines.runBlocking
import net.wigle.wigleandroid.intelligence.analyzer.MlClusteringAnalyzer
import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Observation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MlClusteringAnalyzerTest {

    private lateinit var analyzer: MlClusteringAnalyzer

    @Before
    fun setup() {
        analyzer = MlClusteringAnalyzer(maxClusters = 2)
    }

    @Test
    fun testClusteringAndEnvironmentClassification() = runBlocking {
        val baseLat = 37.7749
        val baseLon = -122.4194

        val batch = mutableListOf<Observation>()
        // Cluster 1: Enterprise (WPA3 + 5GHz)
        for (i in 1..4) {
            batch.add(
                Observation(
                    bssid = "00:55:44:33:22:%02X".format(i),
                    ssid = "EnterpriseHub_$i",
                    latitude = baseLat + (i * 0.00001),
                    longitude = baseLon + (i * 0.00001),
                    rssi = -60,
                    frequency = 5240,
                    capabilities = "[WPA3-SAE-CCMP][ESS]"
                )
            )
        }

        val findings = analyzer.analyze(batch)
        assertFalse("Expected cluster findings", findings.isEmpty())

        val clusterFinding = findings.find { it.analyzerType == AnalyzerType.ML_CLUSTERING }
        assertNotNull("Should classify cluster environment", clusterFinding)
        assertTrue(clusterFinding!!.title.contains("Enterprise"))
    }
}

