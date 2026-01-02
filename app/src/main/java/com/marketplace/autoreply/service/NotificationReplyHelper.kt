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

        // Try to find reply action in wearable extender first (more reliable)
        val wearableReplyAction = findWearableReplyAction(notification)
        if (wearableReplyAction != null) {
            AppLogger.info(TAG, "Found wearable reply action", showToast = true)
            return executeReplyAction(context, wearableReplyAction, message)
        }

        // Try to find reply action in notification actions
        val replyAction = findReplyAction(notification)
        if (replyAction != null) {
            AppLogger.info(TAG, "Found notification reply action", showToast = true)
            return executeReplyAction(context, replyAction, message)
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
     */
    private fun executeReplyAction(context: Context, actionData: ReplyActionData, message: String): Boolean {
        try {
            // Create intent and bundle for the reply
            val intent = Intent()
            val bundle = Bundle()

            // Fill all remote inputs with the message
            for (remoteInput in actionData.remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, message)
                AppLogger.info(TAG, "Setting remote input key: ${remoteInput.resultKey}")
            }

            // Add the remote input bundle to the intent
            RemoteInput.addResultsToIntent(actionData.remoteInputs.toTypedArray(), intent, bundle)

            // Use FLAG_UPDATE_CURRENT to allow the RemoteInput to be added
            // Send with the fill-in intent containing the reply data
            actionData.pendingIntent.send(context, 0, intent, null, null)

            AppLogger.info(TAG, "Reply sent via notification action!", showToast = true)
            return true

        } catch (e: PendingIntent.CanceledException) {
            AppLogger.error(TAG, "PendingIntent was cancelled: ${e.message}", showToast = true)
            return false
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to send reply: ${e.message}", showToast = true)
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
