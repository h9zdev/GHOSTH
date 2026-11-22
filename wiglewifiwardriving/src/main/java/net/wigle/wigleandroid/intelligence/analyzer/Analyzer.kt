package net.wigle.wigleandroid.intelligence.analyzer

import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Observation

/**
 * Common analyzer interface for all Intelligence ML / Analytics modules.
 * Strictly passive and metadata-only.
 */
interface Analyzer {
    suspend fun analyze(batch: List<Observation>): List<Finding>
}

