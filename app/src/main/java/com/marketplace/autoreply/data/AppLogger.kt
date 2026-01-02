package com.marketplace.autoreply.data

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.marketplace.autoreply.MarketplaceAutoReplyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AppLogger {

    fun log(tag: String, message: String, level: String = "INFO", showToast: Boolean = false) {
        // Log to Android logcat
        when (level) {
            "ERROR" -> Log.e(tag, message)
            "WARN" -> Log.w(tag, message)
            else -> Log.d(tag, message)
        }

        // Save to database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = MarketplaceAutoReplyApp.getInstance()
                app.database.debugLogDao().insert(
                    DebugLog(
                        tag = tag,
                        message = message,
                        level = level
                    )
                )
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to save log", e)
            }
        }

        // Show toast on main thread if requested
        if (showToast) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val app = MarketplaceAutoReplyApp.getInstance()
                    Toast.makeText(app, "[$tag] $message", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    // Ignore toast errors
                }
            }
        }
    }

    fun info(tag: String, message: String, showToast: Boolean = false) {
        log(tag, message, "INFO", showToast)
    }

    fun warn(tag: String, message: String, showToast: Boolean = false) {
        log(tag, message, "WARN", showToast)
    }

    fun error(tag: String, message: String, showToast: Boolean = false) {
        log(tag, message, "ERROR", showToast)
    }
}
