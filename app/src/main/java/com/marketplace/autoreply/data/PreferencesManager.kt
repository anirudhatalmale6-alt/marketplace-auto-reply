package com.marketplace.autoreply.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val AUTO_REPLY_ENABLED = booleanPreferencesKey("auto_reply_enabled")
        private val REPLY_MESSAGE = stringPreferencesKey("reply_message")
        private val DEFAULT_MESSAGE = "Hi! Thanks for your interest. I'll get back to you shortly."
    }

    val isAutoReplyEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[AUTO_REPLY_ENABLED] ?: false }

    val replyMessage: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[REPLY_MESSAGE] ?: DEFAULT_MESSAGE }

    suspend fun setAutoReplyEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_REPLY_ENABLED] = enabled
        }
    }

    suspend fun setReplyMessage(message: String) {
        context.dataStore.edit { preferences ->
            preferences[REPLY_MESSAGE] = message
        }
    }
}
