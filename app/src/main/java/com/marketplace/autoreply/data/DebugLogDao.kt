package com.marketplace.autoreply.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DebugLogDao {
    @Insert
    suspend fun insert(log: DebugLog)

    @Query("SELECT * FROM debug_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<DebugLog>>

    @Query("DELETE FROM debug_logs")
    suspend fun clearAll()

    @Query("DELETE FROM debug_logs WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
