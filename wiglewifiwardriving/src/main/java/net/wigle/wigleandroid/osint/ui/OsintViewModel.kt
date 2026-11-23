package net.wigle.wigleandroid.osint.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.wigle.wigleandroid.intelligence.replay.ObservationReplayer
import net.wigle.wigleandroid.osint.model.GeospatialOsintLayer
import net.wigle.wigleandroid.osint.model.WirelessOsintFinding
import net.wigle.wigleandroid.osint.service.GeospatialOsintService
import net.wigle.wigleandroid.osint.service.WirelessOsintService

class OsintViewModel(application: Application) : AndroidViewModel(application) {

    private val wirelessService = WirelessOsintService(application)
    private val geospatialService = GeospatialOsintService()

    private val _wirelessFindings = MutableStateFlow<List<WirelessOsintFinding>>(emptyList())
    val wirelessFindings: StateFlow<List<WirelessOsintFinding>> = _wirelessFindings.asStateFlow()

    private val _geospatialLayer = MutableStateFlow<GeospatialOsintLayer?>(null)
    val geospatialLayer: StateFlow<GeospatialOsintLayer?> = _geospatialLayer.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedFilter = MutableStateFlow("ALL")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            val sampleObs = ObservationReplayer.generateSamplePassiveStream()
            val enriched = wirelessService.enrichBatch(sampleObs)
            val geo = geospatialService.analyzeGeospatial(sampleObs)

            _wirelessFindings.value = enriched
            _geospatialLayer.value = geo
            _isLoading.value = false
        }
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }
}
