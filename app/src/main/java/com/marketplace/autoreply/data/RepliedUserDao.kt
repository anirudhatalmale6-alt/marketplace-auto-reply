package com.marketplace.autoreply.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RepliedUserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: RepliedUser)

    @Update
    suspend fun update(user: RepliedUser)

    @Query("SELECT EXISTS(SELECT 1 FROM replied_users WHERE odentifier = :identifier)")
    suspend fun hasReplied(identifier: String): Boolean

    @Query("SELECT * FROM replied_users WHERE odentifier = :identifier LIMIT 1")
    suspend fun getUser(identifier: String): RepliedUser?

    @Query("SELECT currentStage FROM replied_users WHERE odentifier = :identifier")
    suspend fun getCurrentStage(identifier: String): Int?

    @Query("UPDATE replied_users SET currentStage = :stage, interactionCount = interactionCount + 1, repliedAt = :timestamp WHERE odentifier = :identifier")
    suspend fun updateStage(identifier: String, stage: Int, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM replied_users ORDER BY repliedAt DESC")
    fun getAllRepliedUsers(): Flow<List<RepliedUser>>

    @Query("SELECT COUNT(*) FROM replied_users")
    fun getRepliedCount(): Flow<Int>

    @Query("DELETE FROM replied_users")
    suspend fun clearAll()

    @Query("DELETE FROM replied_users WHERE repliedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
