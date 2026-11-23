package net.wigle.wigleandroid.intelligence.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.wigle.wigleandroid.intelligence.export.ReportExporter
import net.wigle.wigleandroid.intelligence.model.*
import net.wigle.wigleandroid.intelligence.replay.ObservationReplayer
import net.wigle.wigleandroid.intelligence.service.GridCell
import net.wigle.wigleandroid.intelligence.service.IntelligenceEngine
import net.wigle.wigleandroid.intelligence.service.ObservationPipeline
import net.wigle.wigleandroid.intelligence.service.PipelineMetrics
import net.wigle.wigleandroid.intelligence.ui.components.MapVisualizationMode
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
    val searchQuery: String = "",
    val analysisRadiusMeters: Int = 100
)

class IntelligenceViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = IntelligenceEngine.getInstance(application)
    private val pipeline = ObservationPipeline.getInstance(application)
    private val replayer = ObservationReplayer(engine)

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _selectedModule = MutableStateFlow<AnalyzerType?>(null)
    val selectedModule: StateFlow<AnalyzerType?> = _selectedModule.asStateFlow()

    private val _mapMode = MutableStateFlow(MapVisualizationMode.DENSITY_HEATMAP)
    val mapMode: StateFlow<MapVisualizationMode> = _mapMode.asStateFlow()

    private val _selectedSectionIndex = MutableStateFlow(0)
    val selectedSectionIndex: StateFlow<Int> = _selectedSectionIndex.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _statusNotice = MutableStateFlow<String?>(null)
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    val pipelineMetrics: StateFlow<PipelineMetrics> = pipeline.pipelineMetrics
    val gridCells: StateFlow<List<GridCell>> = pipeline.gridCells
    val processedObservations: StateFlow<List<Observation>> = pipeline.processedObservations

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
                    (finding.bssid?.contains(filter.searchQuery, ignoreCase = true) == true) ||
                    finding.category.contains(filter.searchQuery, ignoreCase = true)

            moduleMatch && timeMatch && searchMatch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Automatically load and process actual application database records on start
        refreshActualData()
    }

    /**
     * Executes the pipeline against the application's actual SQLite database and live network cache.
     */
    fun refreshActualData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                _statusNotice.value = "Analyzing wireless database records..."
                val metrics = pipeline.executePipeline(maxRecords = 5000)
                _statusNotice.value = "Analysis complete: ${metrics.totalObservations} records, ${metrics.uniqueAccessPoints} APs evaluated."
            } catch (e: Exception) {
                _errorMessage.value = "Database analysis notice: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setMapMode(mode: MapVisualizationMode) {
        _mapMode.value = mode
    }

    fun selectSection(index: Int) {
        _selectedSectionIndex.value = index
    }

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

    fun setAnalysisRadius(radiusMeters: Int) {
        _filterState.update { it.copy(analysisRadiusMeters = radiusMeters) }
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

                val observations = sampleBatch ?: processedObservations.value.ifEmpty {
                    emptyList()
                }
                if (observations.isEmpty()) {
                    _statusNotice.value = "No recorded observations found in database to replay."
                    return@launch
                }

                replayer.replay(observations, batchSize = 10, delayMsBetweenBatches = 40) { processed, total, newFindings ->
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
}
