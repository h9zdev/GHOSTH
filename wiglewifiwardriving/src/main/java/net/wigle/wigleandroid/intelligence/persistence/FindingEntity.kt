package net.wigle.wigleandroid.intelligence.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey
import net.wigle.wigleandroid.intelligence.model.AnalyzerType
import net.wigle.wigleandroid.intelligence.model.Finding
import net.wigle.wigleandroid.intelligence.model.Severity

@Entity(tableName = "findings")
data class FindingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val analyzerType: String,
    val title: String,
    val summary: String,
    val severity: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val bssid: String?,
    val ssid: String?,
    val score: Double,
    val details: String
) {
    fun toDomain(): Finding {
        return Finding(
            id = id,
            analyzerType = runCatching { AnalyzerType.valueOf(analyzerType) }.getOrDefault(AnalyzerType.ANOMALY_DETECTION),
            title = title,
            summary = summary,
            severity = runCatching { Severity.valueOf(severity) }.getOrDefault(Severity.INFO),
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            bssid = bssid,
            ssid = ssid,
            score = score,
            details = details
        )
    }

    companion object {
        fun fromDomain(finding: Finding): FindingEntity {
            return FindingEntity(
                id = finding.id,
                analyzerType = finding.analyzerType.name,
                title = finding.title,
                summary = finding.summary,
                severity = finding.severity.name,
                latitude = finding.latitude,
                longitude = finding.longitude,
                timestamp = finding.timestamp,
                bssid = finding.bssid,
                ssid = finding.ssid,
                score = finding.score,
                details = finding.details
            )
        }
    }
}

