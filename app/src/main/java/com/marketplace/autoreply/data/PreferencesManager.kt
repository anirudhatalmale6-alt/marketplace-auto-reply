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

        private val DEFAULT_MESSAGE = "Hi! Thanks for your interest. I'll get back to you shortly."
        private const val DEFAULT_MIN_DELAY = 8
        private const val DEFAULT_MAX_DELAY = 12
    }

    val isAutoReplyEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[AUTO_REPLY_ENABLED] ?: false }

    // Multiple messages separated by ||| delimiter
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

    suspend fun setDelayRange(minSeconds: Int, maxSeconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[MIN_DELAY_SECONDS] = minSeconds
            preferences[MAX_DELAY_SECONDS] = maxSeconds
        }
    }
}
