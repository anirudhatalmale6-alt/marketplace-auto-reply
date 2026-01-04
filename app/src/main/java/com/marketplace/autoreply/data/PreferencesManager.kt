package com.marketplace.autoreply.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val AUTO_REPLY_ENABLED = booleanPreferencesKey("auto_reply_enabled")
        private val REPLY_MESSAGES = stringPreferencesKey("reply_messages")
        private val MIN_DELAY_SECONDS = intPreferencesKey("min_delay_seconds")
        private val MAX_DELAY_SECONDS = intPreferencesKey("max_delay_seconds")

        // Stage 1: Welcome messages (no links/phone numbers)
        private val STAGE1_MESSAGES = stringPreferencesKey("stage1_messages")
        // Stage 2: Follow-up messages (soft intro to move off platform)
        private val STAGE2_MESSAGES = stringPreferencesKey("stage2_messages")
        // Stage 3: Contact info
        private val WHATSAPP_NUMBER = stringPreferencesKey("whatsapp_number")
        private val INSTAGRAM_LINK = stringPreferencesKey("instagram_link")
        // Stage 3 message template
        private val STAGE3_MESSAGE_TEMPLATE = stringPreferencesKey("stage3_message_template")

        private val DEFAULT_MESSAGE = "Hi! Thanks for your interest. I'll get back to you shortly."
        private const val DEFAULT_MIN_DELAY = 8
        private const val DEFAULT_MAX_DELAY = 12

        // Default Stage 1 messages (welcome - no links/numbers)
        private val DEFAULT_STAGE1 = listOf(
            "Hello, thank you for your message!",
            "Hi there! The product is currently available.",
            "Hello! Thanks for reaching out.",
            "Hi! Yes, this item is still for sale.",
            "Hello! Thanks for your interest in this product.",
            "Hi there! I'm glad you're interested.",
            "Hello! Would you like more details about this item?",
            "Hi! Do you prefer pickup or delivery?",
            "Hello! Feel free to ask any questions.",
            "Hi there! This is still available."
        )

        // Default Stage 2 messages (follow-up - soft intro)
        private val DEFAULT_STAGE2 = listOf(
            "For quicker details, we can continue on WhatsApp if you like.",
            "I can send you my WhatsApp number to share more photos.",
            "Would you prefer to continue our chat on WhatsApp?",
            "I have more photos and details I can share on WhatsApp.",
            "For faster communication, shall we move to WhatsApp?",
            "I can provide more information through WhatsApp if convenient.",
            "Would WhatsApp work better for you? I can share videos there.",
            "I usually respond faster on WhatsApp, would that work for you?",
            "I have additional photos to share, WhatsApp would be easier.",
            "For a quicker response, we could chat on WhatsApp."
        )

        // Default Stage 3 template
        private val DEFAULT_STAGE3_TEMPLATE = "Great! Here's my contact:\nWhatsApp: {whatsapp}\nInstagram: {instagram}\nLooking forward to hearing from you!"
    }

    val isAutoReplyEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[AUTO_REPLY_ENABLED] ?: false }

    // Legacy: Multiple messages separated by ||| delimiter (kept for backward compatibility)
    val replyMessages: Flow<List<String>> = context.dataStore.data
        .map { preferences ->
            val messagesStr = preferences[REPLY_MESSAGES] ?: DEFAULT_MESSAGE
            messagesStr.split("|||").map { it.trim() }.filter { it.isNotEmpty() }
        }

    // For backward compatibility - returns first message
    val replyMessage: Flow<String> = context.dataStore.data
        .map { preferences ->
            val messagesStr = preferences[REPLY_MESSAGES] ?: DEFAULT_MESSAGE
            messagesStr.split("|||").firstOrNull()?.trim() ?: DEFAULT_MESSAGE
        }

    // Stage 1: Welcome messages
    val stage1Messages: Flow<List<String>> = context.dataStore.data
        .map { preferences ->
            val messagesStr = preferences[STAGE1_MESSAGES]
            if (messagesStr.isNullOrEmpty()) {
                DEFAULT_STAGE1
            } else {
                messagesStr.split("|||").map { it.trim() }.filter { it.isNotEmpty() }
            }
        }

    // Stage 2: Follow-up messages
    val stage2Messages: Flow<List<String>> = context.dataStore.data
        .map { preferences ->
            val messagesStr = preferences[STAGE2_MESSAGES]
            if (messagesStr.isNullOrEmpty()) {
                DEFAULT_STAGE2
            } else {
                messagesStr.split("|||").map { it.trim() }.filter { it.isNotEmpty() }
            }
        }

    // WhatsApp number
    val whatsappNumber: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[WHATSAPP_NUMBER] ?: "" }

    // Instagram link
    val instagramLink: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[INSTAGRAM_LINK] ?: "" }

    // Stage 3 message template
    val stage3MessageTemplate: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[STAGE3_MESSAGE_TEMPLATE] ?: DEFAULT_STAGE3_TEMPLATE }

    val minDelaySeconds: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[MIN_DELAY_SECONDS] ?: DEFAULT_MIN_DELAY }

    val maxDelaySeconds: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[MAX_DELAY_SECONDS] ?: DEFAULT_MAX_DELAY }

    suspend fun setAutoReplyEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_REPLY_ENABLED] = enabled
        }
    }

    suspend fun setReplyMessages(messages: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[REPLY_MESSAGES] = messages.joinToString("|||")
        }
    }

    // For backward compatibility
    suspend fun setReplyMessage(message: String) {
        context.dataStore.edit { preferences ->
            preferences[REPLY_MESSAGES] = message
        }
    }

    suspend fun setStage1Messages(messages: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[STAGE1_MESSAGES] = messages.joinToString("|||")
        }
    }

    suspend fun setStage2Messages(messages: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[STAGE2_MESSAGES] = messages.joinToString("|||")
        }
    }

    suspend fun setWhatsappNumber(number: String) {
        context.dataStore.edit { preferences ->
            preferences[WHATSAPP_NUMBER] = number
        }
    }

    suspend fun setInstagramLink(link: String) {
        context.dataStore.edit { preferences ->
            preferences[INSTAGRAM_LINK] = link
        }
    }

    suspend fun setStage3MessageTemplate(template: String) {
        context.dataStore.edit { preferences ->
            preferences[STAGE3_MESSAGE_TEMPLATE] = template
        }
    }

    suspend fun setDelayRange(minSeconds: Int, maxSeconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[MIN_DELAY_SECONDS] = minSeconds
            preferences[MAX_DELAY_SECONDS] = maxSeconds
        }
    }

    /**
     * Get the appropriate message for a given stage
     * @param stage 1 = Welcome, 2 = Follow-up, 3 = Contact sharing
     * @param whatsapp WhatsApp number (for stage 3)
     * @param instagram Instagram link (for stage 3)
     */
    fun getMessageForStage(
        stage: Int,
        stage1List: List<String>,
        stage2List: List<String>,
        whatsapp: String,
        instagram: String,
        template: String
    ): String {
        return when (stage) {
            1 -> stage1List.randomOrNull() ?: DEFAULT_STAGE1.random()
            2 -> stage2List.randomOrNull() ?: DEFAULT_STAGE2.random()
            3 -> {
                // Build stage 3 message with contact info
                var message = template.ifEmpty { DEFAULT_STAGE3_TEMPLATE }
                message = message.replace("{whatsapp}", whatsapp.ifEmpty { "Not set" })
                message = message.replace("{instagram}", instagram.ifEmpty { "Not set" })
                message
            }
            else -> DEFAULT_MESSAGE
        }
    }
}
