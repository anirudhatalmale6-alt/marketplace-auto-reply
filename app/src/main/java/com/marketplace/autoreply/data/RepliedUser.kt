package com.marketplace.autoreply.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a user who has already received an auto-reply.
 * Tracks the conversation stage for 3-stage messaging system:
 * - Stage 1: Welcome message (no links/numbers)
 * - Stage 2: Follow-up message (soft intro to move off platform)
 * - Stage 3: WhatsApp/Instagram sharing (only after customer interacted twice)
 */
@Entity(tableName = "replied_users")
data class RepliedUser(
    @PrimaryKey
    val odentifier: String, // Unique identifier (sender name + conversation key)
    val senderName: String,
    val conversationTitle: String,
    val repliedAt: Long = System.currentTimeMillis(),
    val messengerPackage: String, // Track which Messenger app (original or clone)
    val currentStage: Int = 1, // 1 = Welcome sent, 2 = Follow-up sent, 3 = Contact shared
    val interactionCount: Int = 1 // How many times customer has messaged
)
