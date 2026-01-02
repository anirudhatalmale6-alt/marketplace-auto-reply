package com.marketplace.autoreply.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.marketplace.autoreply.MarketplaceAutoReplyApp
import com.marketplace.autoreply.data.AppLogger
import com.marketplace.autoreply.data.RepliedUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Listens for Messenger notifications and sends auto-replies directly
 * via notification actions - fully in background without opening Messenger.
 */
class MessengerNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Track senders currently being processed to prevent duplicates
    private val processingSet = mutableSetOf<String>()
    private val processLock = Any()

    companion object {
        private const val TAG = "NotifListener"

        // Delay range in milliseconds (8-12 seconds)
        private const val MIN_DELAY_MS = 8000L
        private const val MAX_DELAY_MS = 12000L

        // Package names for Messenger (original and common clones)
        val MESSENGER_PACKAGES = setOf(
            "com.facebook.orca",           // Original Messenger
            "com.facebook.mlite",          // Messenger Lite
        )

        var instance: MessengerNotificationListener? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLogger.info(TAG, "Service STARTED", showToast = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scope.cancel()
        AppLogger.info(TAG, "Service STOPPED", showToast = true)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        AppLogger.info(TAG, "Listener connected - ready!", showToast = true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        AppLogger.warn(TAG, "Listener disconnected", showToast = true)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        // Log notifications for debugging
        AppLogger.info(TAG, "Notif: $packageName")

        // Check if this is from Messenger (original or clone)
        if (!isMessengerPackage(packageName)) {
            return
        }

        AppLogger.info(TAG, "MESSENGER detected!", showToast = true)

        // Log available actions for debugging
        NotificationReplyHelper.logNotificationActions(sbn.notification)

        scope.launch {
            try {
                processNotification(sbn)
            } catch (e: Exception) {
                AppLogger.error(TAG, "Error: ${e.message}", showToast = true)
            }
        }
    }

    private suspend fun processNotification(sbn: StatusBarNotification) {
        val app = MarketplaceAutoReplyApp.getInstance()

        // Check if auto-reply is enabled
        val isEnabled = app.preferencesManager.isAutoReplyEnabled.first()
        if (!isEnabled) {
            AppLogger.info(TAG, "Auto-reply disabled")
            return
        }

        val notification = sbn.notification
        val extras = notification.extras

        // Extract notification details
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: ""

        val fullText = "$title $text $bigText $conversationTitle".lowercase()

        AppLogger.info(TAG, "From: $title | Msg: $text")

        // Skip system/group notifications without a clear sender
        if (title.isEmpty() || title == "Messenger" || title.contains("conversation")) {
            AppLogger.info(TAG, "Skipping system notification")
            return
        }

        AppLogger.info(TAG, "Message from: $title", showToast = true)

        // Create identifier for this sender
        val senderId = createSenderId(title, sbn.packageName)

        // Check if we've already replied to this user (database check)
        val hasReplied = app.database.repliedUserDao().hasReplied(senderId)
        if (hasReplied) {
            AppLogger.info(TAG, "Already replied to $title")
            return
        }

        // Check if currently processing this sender (prevents duplicate concurrent processing)
        synchronized(processLock) {
            if (processingSet.contains(senderId)) {
                AppLogger.info(TAG, "Already processing $title")
                return
            }
            // Mark as processing immediately
            processingSet.add(senderId)
        }

        // Check available methods
        val hasReplyActionAvailable = NotificationReplyHelper.hasReplyAction(notification)
        val hasAccessibility = MessengerAccessibilityService.instance != null
        AppLogger.info(TAG, "Reply action: $hasReplyActionAvailable, Accessibility: $hasAccessibility")

        if (!hasReplyActionAvailable && !hasAccessibility) {
            AppLogger.warn(TAG, "No reply method available!", showToast = true)
            synchronized(processLock) { processingSet.remove(senderId) }
            return
        }

        // Get reply message
        val replyMessage = app.preferencesManager.replyMessage.first()

        // Random delay (8-12 seconds)
        val delayMs = Random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1)
        AppLogger.info(TAG, "Waiting ${delayMs/1000}s...", showToast = true)
        delay(delayMs)

        // Check if notification has direct reply action
        val hasReplyAction = NotificationReplyHelper.hasReplyAction(notification)
        AppLogger.info(TAG, "Has reply action: $hasReplyAction", showToast = true)

        // Try Method 1: Direct Reply (TRUE BACKGROUND - no opening Messenger)
        if (hasReplyAction) {
            AppLogger.info(TAG, "Trying direct reply (background)...", showToast = true)
            val directReplySuccess = NotificationReplyHelper.sendDirectReply(
                this@MessengerNotificationListener, sbn, replyMessage
            )

            if (directReplySuccess) {
                // Direct reply was sent - record success
                // Note: We can't verify if Messenger actually received it
                recordSuccessfulReply(senderId, title, conversationTitle, sbn.packageName)
                AppLogger.info(TAG, "Direct reply sent (background)!", showToast = true)
                synchronized(processLock) { processingSet.remove(senderId) }
                return
            }
        }

        // Method 1 failed or not available - try Method 2: Accessibility Service
        AppLogger.info(TAG, "Using accessibility method...", showToast = true)

        val accessibilityService = MessengerAccessibilityService.instance
        if (accessibilityService == null) {
            AppLogger.error(TAG, "Enable Accessibility Service!", showToast = true)
            synchronized(processLock) { processingSet.remove(senderId) }
            return
        }

        // Open the notification to launch Messenger
        val contentIntent = sbn.notification.contentIntent
        if (contentIntent == null) {
            AppLogger.error(TAG, "No content intent!", showToast = true)
            synchronized(processLock) { processingSet.remove(senderId) }
            return
        }

        // Queue the reply for accessibility service
        AppLogger.info(TAG, "Queuing reply...", showToast = true)
        var accessibilitySuccess = false
        accessibilityService.requestSendReply(
            message = replyMessage,
            targetPackage = sbn.packageName,
            senderName = title
        ) { result ->
            accessibilitySuccess = result
        }

        // Open Messenger by clicking notification
        try {
            contentIntent.send()
            AppLogger.info(TAG, "Opening Messenger...", showToast = true)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to open: ${e.message}", showToast = true)
            MessengerAccessibilityService.pendingReply = null
            synchronized(processLock) { processingSet.remove(senderId) }
            return
        }

        // Wait for accessibility to complete (max 25 seconds for cloned apps)
        var waitTime = 0L
        while (MessengerAccessibilityService.pendingReply != null && waitTime < 25000) {
            delay(500)
            waitTime += 500
        }

        if (accessibilitySuccess) {
            recordSuccessfulReply(senderId, title, conversationTitle, sbn.packageName)
        } else {
            AppLogger.error(TAG, "Reply failed - check Accessibility", showToast = true)
        }

        // Always remove from processing set when done
        synchronized(processLock) { processingSet.remove(senderId) }
    }

    private suspend fun recordSuccessfulReply(
        senderId: String,
        senderName: String,
        conversationTitle: String,
        packageName: String
    ) {
        val app = MarketplaceAutoReplyApp.getInstance()
        app.database.repliedUserDao().insert(
            RepliedUser(
                odentifier = senderId,
                senderName = senderName,
                conversationTitle = conversationTitle.ifEmpty { senderName },
                messengerPackage = packageName
            )
        )
        AppLogger.info(TAG, "SUCCESS! Replied to: $senderName", showToast = true)
    }

    private fun isMessengerPackage(packageName: String): Boolean {
        // Check exact matches
        if (packageName in MESSENGER_PACKAGES) return true

        // Check for clone/dual app patterns
        if (packageName.startsWith("com.facebook.orca")) return true
        if (packageName.startsWith("com.facebook.orcb")) return true  // Clone app variant
        if (packageName.startsWith("com.facebook.orcc")) return true  // Another clone variant
        if (packageName.startsWith("com.facebook.mlite")) return true
        if (packageName.contains("facebook") && packageName.contains("orca")) return true
        if (packageName.contains("facebook") && packageName.contains("orcb")) return true
        if (packageName.contains("messenger")) return true

        return false
    }

    private fun createSenderId(senderName: String, packageName: String): String {
        return "${senderName.lowercase().trim()}_${packageName.hashCode()}"
    }
}
