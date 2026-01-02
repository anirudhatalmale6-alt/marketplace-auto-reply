package com.marketplace.autoreply

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.marketplace.autoreply.data.AppDatabase
import com.marketplace.autoreply.data.PreferencesManager

class MarketplaceAutoReplyApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var preferencesManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        preferencesManager = PreferencesManager(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Reply Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when auto-reply is active"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "auto_reply_channel"

        @Volatile
        private var instance: MarketplaceAutoReplyApp? = null

        fun getInstance(): MarketplaceAutoReplyApp {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }
}
