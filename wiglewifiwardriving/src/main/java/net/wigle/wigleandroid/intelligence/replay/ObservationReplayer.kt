package net.wigle.wigleandroid.intelligence.replay

import kotlinx.coroutines.delay
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Observation
import net.wigle.wigleandroid.intelligence.service.IntelligenceEngine

/**
 * Replay mode that feeds recorded observations through the intelligence analyzers.
 */
class ObservationReplayer(
    private val engine: IntelligenceEngine
) {
    suspend fun replay(
        observations: List<Observation>,
        batchSize: Int = 20,
        delayMsBetweenBatches: Long = 100,
        onProgress: (processed: Int, total: Int, newFindings: List<Finding>) -> Unit = { _, _, _ -> }
    ) {
        val chunks = observations.chunked(batchSize)
        var processedCount = 0

        for (chunk in chunks) {
            val findings = engine.processBatch(chunk)
            processedCount += chunk.size
            onProgress(processedCount, observations.size, findings)
            if (delayMsBetweenBatches > 0) {
                delay(delayMsBetweenBatches)
            }
        }
    }
}

