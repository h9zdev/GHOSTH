package net.wigle.wigleandroid.intelligence

import kotlinx.coroutines.runBlocking
import net.wigle.wigleandroid.intelligence.analyzer.PredictiveAnalyticsAnalyzer
import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Observation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PredictiveAnalyticsAnalyzerTest {

    private lateinit var analyzer: PredictiveAnalyticsAnalyzer

    @Before
    fun setup() {
        analyzer = PredictiveAnalyticsAnalyzer()
    }

    @Test
    fun testDensityForecastGenerated() = runBlocking {
        val now = System.currentTimeMillis()
        val batch = listOf(
            Observation("00:01", "Net1", 37.77, -122.41, rssi = -70, timestamp = now - 60000),
            Observation("00:02", "Net2", 37.77, -122.41, rssi = -70, timestamp = now - 45000),
            Observation("00:03", "Net3", 37.77, -122.41, rssi = -70, timestamp = now - 30000),
            Observation("00:04", "Net4", 37.77, -122.41, rssi = -70, timestamp = now - 15000),
            Observation("00:05", "Net5", 37.77, -122.41, rssi = -70, timestamp = now)
        )

        val findings = analyzer.analyze(batch)
        assertFalse("Expected predictive findings", findings.isEmpty())

        val forecast = findings.find { it.analyzerType == AnalyzerType.PREDICTIVE_ANALYTICS }
        assertNotNull("Should generate TinyML Density Forecast", forecast)
        assertTrue(forecast!!.title.contains("Forecast"))
        assertTrue(forecast.details.contains("TinyML"))
        assertTrue(forecast.score > 0.0)
    }
}
