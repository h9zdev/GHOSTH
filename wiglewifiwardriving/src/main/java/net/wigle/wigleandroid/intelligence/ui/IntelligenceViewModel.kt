package net.wigle.wigleandroid.intelligence.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.wigle.wigleandroid.intelligence.export.ReportExporter
import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.intelligence.model.Severity
import net.wigle.wigleandroid.intelligence.replay.ObservationReplayer
import net.wigle.wigleandroid.intelligence.service.IntelligenceEngine
import java.io.File

enum class TimeRange(val label: String, val durationMs: Long) {
    LAST_HOUR("1h", 3600_000L),
    LAST_24H("24h", 86400_000L),
    LAST_7D("7d", 7 * 86400_000L),
    ALL("All", Long.MAX_VALUE)
}

enum class BandFilter(val label: String) {
    ALL("All Bands"),
    BAND_2_4GHZ("2.4 GHz"),
    BAND_5GHZ("5 GHz"),
    BAND_6GHZ("6 GHz")
}

enum class SecurityFilter(val label: String) {
    ALL("All Sec"),
    OPEN("Open"),
    WEP("WEP"),
    WPA2("WPA2"),
    WPA3("WPA3")
}

data class FilterState(
    val timeRange: TimeRange = TimeRange.ALL,
    val band: BandFilter = BandFilter.ALL,
    val security: SecurityFilter = SecurityFilter.ALL,
    val searchQuery: String = ""
)

class IntelligenceViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = IntelligenceEngine.getInstance(application)
    private val replayer = ObservationReplayer(engine)

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _selectedModule = MutableStateFlow<AnalyzerType?>(null)
    val selectedModule: StateFlow<AnalyzerType?> = _selectedModule.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _statusNotice = MutableStateFlow<String?>(null)
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    val rawFindings: StateFlow<List<Finding>> = engine.observeAllFindings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredFindings: StateFlow<List<Finding>> = combine(
        rawFindings,
        _filterState,
        _selectedModule
    ) { findings, filter, module ->
        val now = System.currentTimeMillis()
        findings.filter { finding ->
            // Module filter
            val moduleMatch = (module == null || finding.analyzerType == module)

            // Time range filter
            val timeMatch = if (filter.timeRange == TimeRange.ALL) true
            else (now - finding.timestamp) <= filter.timeRange.durationMs

            // Search query filter
            val searchMatch = if (filter.searchQuery.isBlank()) true
            else finding.title.contains(filter.searchQuery, ignoreCase = true) ||
                    finding.summary.contains(filter.searchQuery, ignoreCase = true) ||
                    (finding.ssid?.contains(filter.searchQuery, ignoreCase = true) == true) ||
                    (finding.bssid?.contains(filter.searchQuery, ignoreCase = true) == true)

            moduleMatch && timeMatch && searchMatch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectModule(module: AnalyzerType?) {
        _selectedModule.value = module
    }

    fun setTimeRange(timeRange: TimeRange) {
        _filterState.update { it.copy(timeRange = timeRange) }
    }

    fun setBand(band: BandFilter) {
        _filterState.update { it.copy(band = band) }
    }

    fun setSecurity(security: SecurityFilter) {
        _filterState.update { it.copy(security = security) }
    }

    fun setSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun clearNotice() {
        _statusNotice.value = null
    }

    fun clearFindings() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                engine.clearAllFindings()
                _statusNotice.value = "All findings cleared."
            } catch (e: Exception) {
                _errorMessage.value = "Failed to clear findings: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Replay recorded passive observation batch through analyzers
     */
    fun runReplay(sampleBatch: List<Observation>? = null) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _statusNotice.value = "Replaying passive session..."

                val observations = sampleBatch ?: generateSamplePassiveStream()
                replayer.replay(observations, batchSize = 10, delayMsBetweenBatches = 50) { processed, total, newFindings ->
                    _statusNotice.value = "Processed $processed / $total observations (${newFindings.size} new findings)"
                }
                _statusNotice.value = "Replay complete: ${observations.size} observations evaluated."
            } catch (e: Exception) {
                _errorMessage.value = "Replay error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportReport(context: Context, format: String): File? {
        val list = filteredFindings.value
        if (list.isEmpty()) {
            _statusNotice.value = "No findings to export."
            return null
        }
        return try {
            val file = if (format.equals("pdf", ignoreCase = true)) {
                ReportExporter.exportToPdf(context, list, _selectedModule.value?.displayName ?: "All")
            } else {
                ReportExporter.exportToCsv(context, list, _selectedModule.value?.displayName ?: "All")
            }
            _statusNotice.value = "Exported to: ${file.name}"
            file
        } catch (e: Exception) {
            _errorMessage.value = "Export failed: ${e.message}"
            null
        }
    }

    /**
     * Generates a realistic passive observation stream for verification & testing
     */
    private fun generateSamplePassiveStream(): List<Observation> {
        val list = mutableListOf<Observation>()
        val baseLat = 37.7749
        val baseLon = -122.4194
        val now = System.currentTimeMillis()

        // 1. High density cluster (Downtown transit station)
        for (i in 1..16) {
            list.add(
                Observation(
                    bssid = "00:1A:2B:3C:4D:%02X".format(i),
                    ssid = if (i % 3 == 0) "Muni_Free_WiFi" else "ShopNet_$i",
                    latitude = baseLat + (i * 0.00003),
                    longitude = baseLon + (i * 0.00002),
                    rssi = -55 - (i % 15),
                    frequency = 2437,
                    channel = 6,
                    capabilities = "[WPA2-PSK-CCMP][ESS]",
                    timestamp = now - (i * 1000)
                )
            )
        }

        // 2. Anomaly: Evil Twin with conflicting security capabilities
        list.add(
            Observation(
                bssid = "A0:B1:C2:D3:E4:01",
                ssid = "Metro_Gov_Secure",
                latitude = baseLat + 0.002,
                longitude = baseLon + 0.002,
                rssi = -68,
                frequency = 5180,
                channel = 36,
                capabilities = "[WPA3-SAE-CCMP][ESS]",
                timestamp = now - 5000
            )
        )
        list.add(
            Observation(
                bssid = "F0:E1:D2:C3:B4:99",
                ssid = "Metro_Gov_Secure",
                latitude = baseLat + 0.0021,
                longitude = baseLon + 0.0021,
                rssi = -42,
                frequency = 2412,
                channel = 1,
                capabilities = "[ESS]", // OPEN rogue downgrade
                timestamp = now - 3000
            )
        )

        // 3. Anomaly: Sudden RSSI jump on stationary AP
        val jumpBssid = "CC:DD:EE:11:22:33"
        list.add(Observation(jumpBssid, "IoT_Sensor_Hub", baseLat - 0.001, baseLon - 0.001, rssi = -88, timestamp = now - 8000))
        list.add(Observation(jumpBssid, "IoT_Sensor_Hub", baseLat - 0.001, baseLon - 0.001, rssi = -87, timestamp = now - 6000))
        list.add(Observation(jumpBssid, "IoT_Sensor_Hub", baseLat - 0.001, baseLon - 0.001, rssi = -38, timestamp = now - 2000)) // +49 dBm spike

        // 4. Sparse fringe area for density prediction interpolation
        list.add(Observation("55:66:77:88:99:AA", "Remote_Cabin_Net", baseLat + 0.008, baseLon + 0.008, rssi = -82, timestamp = now - 10000))

        return list
    }
}
