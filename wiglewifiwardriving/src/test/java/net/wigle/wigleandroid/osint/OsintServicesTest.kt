package net.wigle.wigleandroid.osint

import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.osint.service.GeospatialOsintService
import net.wigle.wigleandroid.osint.service.WirelessOsintService
import org.junit.Assert.*
import org.junit.Test

class OsintServicesTest {

    @Test
    fun testWirelessOsintEnrichment() {
        val service = WirelessOsintService(null)

        val obs = Observation(
            bssid = "B8:27:EB:11:22:33",
            ssid = "City_Free_WiFi",
            latitude = 37.7749,
            longitude = -122.4194,
            rssi = -65,
            frequency = 2412,
            capabilities = "[OPEN]"
        )

        val finding = service.enrichObservation(obs)
        assertEquals("B8:27:EB:11:22:33", finding.bssid)
        assertEquals("City_Free_WiFi", finding.ssid)
        assertTrue("Expected Raspberry Pi or vendor match", finding.manufacturer.contains("Raspberry Pi") || finding.manufacturer.isNotEmpty())
        assertTrue("Expected public hotspot classification", finding.isPublicHotspot)
        assertTrue(finding.publicCategory.contains("Public") || finding.publicCategory.contains("Municipal"))
    }

    @Test
    fun testGeospatialOsintLayerComputation() {
        val service = GeospatialOsintService()

        val batch = listOf(
            Observation("00:01", "FreeWiFi1", 37.77, -122.41, capabilities = "[OPEN]", rssi = -60),
            Observation("00:02", "Eduroam", 37.78, -122.42, capabilities = "[WPA2-EAP]", rssi = -70),
            Observation("00:03", "PrivateAP", 37.79, -122.40, capabilities = "[WPA3-SAE]", rssi = -80)
        )

        val layer = service.analyzeGeospatial(batch)
        assertEquals(3, layer.totalApCount)
        assertTrue("Tile coord should be valid", layer.osmTileCoord.startsWith("15/"))
        assertTrue("Density should be positive", layer.apDensityPerSqKm > 0.0)
        assertTrue("Administrative boundary should be populated", layer.administrativeWard.isNotEmpty())
    }
}
