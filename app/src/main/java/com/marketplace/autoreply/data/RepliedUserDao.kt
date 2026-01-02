package com.marketplace.autoreply.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RepliedUserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: RepliedUser)

    @Query("SELECT EXISTS(SELECT 1 FROM replied_users WHERE odentifier = :identifier)")
    suspend fun hasReplied(identifier: String): Boolean

    @Query("SELECT * FROM replied_users ORDER BY repliedAt DESC")
    fun getAllRepliedUsers(): Flow<List<RepliedUser>>

    @Query("SELECT COUNT(*) FROM replied_users")
    fun getRepliedCount(): Flow<Int>

    @Query("DELETE FROM replied_users")
    suspend fun clearAll()

    @Query("DELETE FROM replied_users WHERE repliedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
