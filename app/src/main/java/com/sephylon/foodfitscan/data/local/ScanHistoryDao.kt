package com.sephylon.foodfitscan.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Insert
    suspend fun insertScanHistory(entity: ScanHistoryEntity)

    @Query("SELECT * FROM scan_history ORDER BY scannedAtMillis DESC LIMIT :limit")
    suspend fun getRecentScanHistory(limit: Int): List<ScanHistoryEntity>

    @Query("SELECT * FROM scan_history ORDER BY scannedAtMillis DESC LIMIT :limit")
    fun observeRecentScanHistory(limit: Int): Flow<List<ScanHistoryEntity>>

    @Query("DELETE FROM scan_history")
    suspend fun clearScanHistory()

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteScanHistoryItem(id: Long)
}
