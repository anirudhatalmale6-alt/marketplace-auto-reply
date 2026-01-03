package com.marketplace.autoreply.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.marketplace.autoreply.data.AppLogger

/**
 * Helper class to send replies directly via notification actions.
 * This allows replying without opening Messenger - true background operation.
 */
object NotificationReplyHelper {

    private const val TAG = "NotifReplyHelper"

    /**
     * Attempts to send a reply using the notification's direct reply action.
     * Returns true if successful, false if no reply action available.
     */
    fun sendDirectReply(context: Context, sbn: StatusBarNotification, message: String): Boolean {
        val notification = sbn.notification

        // Try standard notification actions FIRST (works better on some devices like Samsung)
        val replyAction = findReplyAction(notification)
        if (replyAction != null) {
            AppLogger.info(TAG, "Trying standard reply action...", showToast = true)
            val success = executeReplyAction(context, replyAction, message)
            if (success) return true
        }

        // Try wearable extender actions as fallback
        val wearableReplyAction = findWearableReplyAction(notification)
        if (wearableReplyAction != null) {
            AppLogger.info(TAG, "Trying wearable reply action...", showToast = true)
            val success = executeReplyAction(context, wearableReplyAction, message)
            if (success) return true
        }

        AppLogger.warn(TAG, "No reply action found in notification")
        return false
    }

    /**
     * Find reply action from wearable extender (used by many messaging apps)
     */
    private fun findWearableReplyAction(notification: Notification): ReplyActionData? {
        try {
            val wearableExtender = Notification.WearableExtender(notification)
            val actions = wearableExtender.actions

            for (action in actions) {
                val remoteInputs = action.remoteInputs
                if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                    // Found an action with remote input (text input)
                    return ReplyActionData(
                        pendingIntent = action.actionIntent,
                        remoteInputs = remoteInputs.toList(),
                        actionTitle = action.title?.toString() ?: "Reply"
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "Error getting wearable actions: ${e.message}")
        }
        return null
    }

    /**
     * Find reply action from standard notification actions
     */
    private fun findReplyAction(notification: Notification): ReplyActionData? {
        val actions = notification.actions ?: return null

        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                // Found an action with remote input (text input)
                AppLogger.info(TAG, "Found action: ${action.title}")
                return ReplyActionData(
                    pendingIntent = action.actionIntent,
                    remoteInputs = remoteInputs.toList(),
                    actionTitle = action.title?.toString() ?: "Reply"
                )
            }
        }

        // Also check for actions with specific titles
        for (action in actions) {
            val title = action.title?.toString()?.lowercase() ?: ""
            if (title.contains("reply") || title.contains("respond") ||
                title.contains("répondre") || title.contains("responder")) {
                AppLogger.info(TAG, "Found reply-titled action: ${action.title}")
                if (action.remoteInputs != null) {
                    return ReplyActionData(
                        pendingIntent = action.actionIntent,
                        remoteInputs = action.remoteInputs.toList(),
                        actionTitle = action.title?.toString() ?: "Reply"
                    )
                }
            }
        }

        return null
    }

    /**
     * Execute the reply action with the given message
     * Uses multiple approaches to ensure compatibility with different devices/apps
     */
    private fun executeReplyAction(context: Context, actionData: ReplyActionData, message: String): Boolean {
        try {
            // Create a new Intent - don't add any extra flags that might interfere
            val intent = Intent()

            // Create bundle with the reply text
            val bundle = Bundle()

            // Fill all remote inputs with the message
            for (remoteInput in actionData.remoteInputs) {
                val key = remoteInput.resultKey
                bundle.putCharSequence(key, message)
                AppLogger.info(TAG, "RemoteInput key: $key")
            }

            // Add the remote input results to the intent
            RemoteInput.addResultsToIntent(actionData.remoteInputs.toTypedArray(), intent, bundle)

            // Set the source as free form input (user typed it)
            RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)

            AppLogger.info(TAG, "Sending PendingIntent...")

            // Send the pending intent with the reply data
            actionData.pendingIntent.send(context, 0, intent)

            AppLogger.info(TAG, "Reply sent via notification!", showToast = true)
            return true

        } catch (e: PendingIntent.CanceledException) {
            AppLogger.error(TAG, "PendingIntent cancelled: ${e.message}", showToast = true)
            return false
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed: ${e.message}", showToast = true)
            return false
        }
    }

    /**
     * Check if a notification has a reply action available
     */
    fun hasReplyAction(notification: Notification): Boolean {
        // Check wearable extender
        try {
            val wearableExtender = Notification.WearableExtender(notification)
            for (action in wearableExtender.actions) {
                if (action.remoteInputs?.isNotEmpty() == true) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Check standard actions
        notification.actions?.forEach { action ->
            if (action.remoteInputs?.isNotEmpty() == true) {
                return true
            }
        }

        return false
    }

    /**
     * Log all available actions in a notification (for debugging)
     */
    fun logNotificationActions(notification: Notification) {
        AppLogger.info(TAG, "=== Notification Actions ===")

        // Log standard actions
        notification.actions?.forEachIndexed { index, action ->
            val hasRemoteInput = action.remoteInputs?.isNotEmpty() == true
            AppLogger.info(TAG, "Action[$index]: ${action.title}, hasRemoteInput=$hasRemoteInput")
            action.remoteInputs?.forEach { ri ->
                AppLogger.info(TAG, "  RemoteInput: key=${ri.resultKey}, label=${ri.label}")
            }
        }

        // Log wearable actions
        try {
            val wearableExtender = Notification.WearableExtender(notification)
            wearableExtender.actions.forEachIndexed { index, action ->
                val hasRemoteInput = action.remoteInputs?.isNotEmpty() == true
                AppLogger.info(TAG, "WearableAction[$index]: ${action.title}, hasRemoteInput=$hasRemoteInput")
            }
        } catch (e: Exception) {
            AppLogger.info(TAG, "No wearable actions")
        }

        AppLogger.info(TAG, "=== End Actions ===")
    }

    data class ReplyActionData(
        val pendingIntent: PendingIntent,
        val remoteInputs: List<RemoteInput>,
        val actionTitle: String
    )
}
