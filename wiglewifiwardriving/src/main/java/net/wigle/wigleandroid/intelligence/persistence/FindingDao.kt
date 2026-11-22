package net.wigle.wigleandroid.intelligence.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FindingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFindings(findings: List<FindingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinding(finding: FindingEntity): Long

    @Query("SELECT * FROM findings ORDER BY timestamp DESC")
    fun getAllFindings(): Flow<List<FindingEntity>>

    @Query("SELECT * FROM findings WHERE analyzerType = :type ORDER BY timestamp DESC")
    fun getFindingsByType(type: String): Flow<List<FindingEntity>>

    @Query("SELECT * FROM findings WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getFindingsSince(since: Long): Flow<List<FindingEntity>>

    @Query("SELECT * FROM findings WHERE analyzerType = :type AND timestamp >= :since ORDER BY timestamp DESC")
    fun getFindingsByTypeSince(type: String, since: Long): Flow<List<FindingEntity>>

    @Query("SELECT COUNT(*) FROM findings WHERE analyzerType = :type")
    suspend fun getCountByType(type: String): Int

    @Query("SELECT COUNT(*) FROM findings")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM findings")
    suspend fun clearAll()
}
