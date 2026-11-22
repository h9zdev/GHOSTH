package net.wigle.wigleandroid.intelligence.service

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.wigle.wigleandroid.intelligence.analyzer.Analyzer
import net.wigle.wigleandroid.intelligence.analyzer.AnomalyDetectionAnalyzer
import net.wigle.wigleandroid.intelligence.analyzer.WirelessDensityAnalyzer
import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.intelligence.persistence.FindingEntity
import net.wigle.wigleandroid.intelligence.persistence.IntelligenceDatabase

/**
 * Orchestrates intelligence analytics in the background.
 * Strictly passive and off the main scan thread.
 */
class IntelligenceEngine private constructor(context: Context) {

    private val db = IntelligenceDatabase.getInstance(context)
    private val findingDao = db.findingDao()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val densityAnalyzer = WirelessDensityAnalyzer()
    val anomalyAnalyzer = AnomalyDetectionAnalyzer()

    private val activeAnalyzers = listOf<Analyzer>(densityAnalyzer, anomalyAnalyzer)

    fun observeAllFindings(): Flow<List<Finding>> {
        return findingDao.getAllFindings().map { list -> list.map { it.toDomain() } }
    }

    fun observeFindingsByType(type: AnalyzerType): Flow<List<Finding>> {
        return findingDao.getFindingsByType(type.name).map { list -> list.map { it.toDomain() } }
    }

    fun observeFindingsSince(sinceTimestamp: Long): Flow<List<Finding>> {
        return findingDao.getFindingsSince(sinceTimestamp).map { list -> list.map { it.toDomain() } }
    }

    /**
     * Ingest a batch of observations passively and asynchronously.
     */
    fun ingestObservations(batch: List<Observation>) {
        if (batch.isEmpty()) return
        scope.launch {
            processBatch(batch)
        }
    }

    suspend fun processBatch(batch: List<Observation>): List<Finding> = withContext(Dispatchers.Default) {
        val allFindings = mutableListOf<Finding>()
        for (analyzer in activeAnalyzers) {
            val findings = analyzer.analyze(batch)
            allFindings.addAll(findings)
        }

        if (allFindings.isNotEmpty()) {
            val entities = allFindings.map { FindingEntity.fromDomain(it) }
            findingDao.insertFindings(entities)
        }
        allFindings
    }

    suspend fun clearAllFindings() {
        withContext(Dispatchers.IO) {
            findingDao.clearAll()
            anomalyAnalyzer.resetState()
        }
    }

    suspend fun getStats(): Map<String, Int> = withContext(Dispatchers.IO) {
        mapOf(
            "total" to findingDao.getTotalCount(),
            "density" to findingDao.getCountByType(AnalyzerType.DENSITY_PREDICTION.name),
            "anomaly" to findingDao.getCountByType(AnalyzerType.ANOMALY_DETECTION.name)
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: IntelligenceEngine? = null

        fun getInstance(context: Context): IntelligenceEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IntelligenceEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
