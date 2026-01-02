package com.marketplace.autoreply.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a user who has already received an auto-reply.
 * Used to prevent duplicate messages to the same person.
 */
@Entity(tableName = "replied_users")
data class RepliedUser(
    @PrimaryKey
    val odentifier: String, // Unique identifier (sender name + conversation key)
    val senderName: String,
    val conversationTitle: String,
    val repliedAt: Long = System.currentTimeMillis(),
    val messengerPackage: String // Track which Messenger app (original or clone)
)
