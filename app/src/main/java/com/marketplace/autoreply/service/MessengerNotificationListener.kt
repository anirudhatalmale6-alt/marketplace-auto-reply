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
 *
 * Uses 3-stage messaging system:
 * Stage 1: Welcome messages (no links/phone numbers) - first contact
 * Stage 2: Follow-up messages (soft intro to move off platform) - after customer replies
 * Stage 3: WhatsApp/Instagram sharing - only after customer interacted twice
 */
class MessengerNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Track senders currently being processed to prevent duplicates
    private val processingSet = mutableSetOf<String>()
    private val processLock = Any()

    companion object {
        private const val TAG = "NotifListener"

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

        // Skip chat head notifications - DO NOT interact with chat heads
        val lowerTitle = title.lowercase()
        val lowerText = text.lowercase()
        if (lowerTitle.contains("chat head") || lowerTitle.contains("chathead") ||
            lowerTitle.contains("bubble") || lowerText.contains("chat head") ||
            lowerText.contains("chathead") || lowerText.contains("active chat") ||
            text.contains("Chat heads active") || title.contains("Chat heads")) {
            AppLogger.info(TAG, "Skipping chat heads notification")
            return
        }

        AppLogger.info(TAG, "Message from: $title", showToast = true)

        // Create identifier for this sender
        val senderId = createSenderId(title, sbn.packageName)

        // Check if currently processing this sender (prevents duplicate concurrent processing)
        synchronized(processLock) {
            if (processingSet.contains(senderId)) {
                AppLogger.info(TAG, "Already processing $title")
                return
            }
            // Mark as processing immediately
            processingSet.add(senderId)
        }

        // Get existing user record to determine current stage
        val existingUser = app.database.repliedUserDao().getUser(senderId)
        val currentStage = existingUser?.currentStage ?: 0 // 0 means new user

        // Determine next stage
        val nextStage = when (currentStage) {
            0 -> 1  // New user -> Stage 1 (Welcome)
            1 -> 2  // Was at Stage 1 -> Stage 2 (Follow-up)
            2 -> 3  // Was at Stage 2 -> Stage 3 (Contact sharing)
            3 -> {
                // Already completed all stages - don't reply again
                AppLogger.info(TAG, "All stages completed for $title")
                synchronized(processLock) { processingSet.remove(senderId) }
                return
            }
            else -> {
                AppLogger.info(TAG, "Invalid stage for $title")
                synchronized(processLock) { processingSet.remove(senderId) }
                return
            }
        }

        AppLogger.info(TAG, "Stage $currentStage -> $nextStage for $title", showToast = true)

        // Check available methods
        val hasReplyActionAvailable = NotificationReplyHelper.hasReplyAction(notification)
        val hasAccessibility = MessengerAccessibilityService.instance != null
        AppLogger.info(TAG, "Reply action: $hasReplyActionAvailable, Accessibility: $hasAccessibility")

        if (!hasReplyActionAvailable && !hasAccessibility) {
            AppLogger.warn(TAG, "No reply method available!", showToast = true)
            synchronized(processLock) { processingSet.remove(senderId) }
            return
        }

        // Get stage-appropriate message
        val stage1Messages = app.preferencesManager.stage1Messages.first()
        val stage2Messages = app.preferencesManager.stage2Messages.first()
        val whatsappNumber = app.preferencesManager.whatsappNumber.first()
        val instagramLink = app.preferencesManager.instagramLink.first()
        val stage3Template = app.preferencesManager.stage3MessageTemplate.first()

        val replyMessage = app.preferencesManager.getMessageForStage(
            stage = nextStage,
            stage1List = stage1Messages,
            stage2List = stage2Messages,
            whatsapp = whatsappNumber,
            instagram = instagramLink,
            template = stage3Template
        )

        AppLogger.info(TAG, "Stage $nextStage msg: ${replyMessage.take(40)}...", showToast = true)

        // Get configurable delay range
        val minDelay = app.preferencesManager.minDelaySeconds.first()
        val maxDelay = app.preferencesManager.maxDelaySeconds.first()
        val delayMs = Random.nextLong(minDelay * 1000L, (maxDelay + 1) * 1000L)
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
                // Direct reply was sent - record success with stage
                recordSuccessfulReply(senderId, title, conversationTitle, sbn.packageName, nextStage, existingUser)
                AppLogger.info(TAG, "Stage $nextStage sent to: $title", showToast = true)
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
            recordSuccessfulReply(senderId, title, conversationTitle, sbn.packageName, nextStage, existingUser)
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
        packageName: String,
        stage: Int,
        existingUser: RepliedUser?
    ) {
        val app = MarketplaceAutoReplyApp.getInstance()

        if (existingUser != null) {
            // Update existing user with new stage
            app.database.repliedUserDao().updateStage(
                identifier = senderId,
                stage = stage,
                timestamp = System.currentTimeMillis()
            )
            AppLogger.info(TAG, "Updated $senderName to stage $stage", showToast = true)
        } else {
            // Insert new user at stage 1
            app.database.repliedUserDao().insert(
                RepliedUser(
                    odentifier = senderId,
                    senderName = senderName,
                    conversationTitle = conversationTitle.ifEmpty { senderName },
                    messengerPackage = packageName,
                    currentStage = stage,
                    interactionCount = 1
                )
            )
            AppLogger.info(TAG, "New user $senderName at stage $stage", showToast = true)
        }
    }

    private fun isMessengerPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()

        // Check exact matches
        if (packageName in MESSENGER_PACKAGES) return true

        // Dynamic detection for ANY Messenger clone
        // Pattern 1: Any package starting with com.facebook.orc (orca, orcb, orcc, orcd, etc.)
        if (pkg.startsWith("com.facebook.orc")) return true

        // Pattern 2: Any package containing "messenger"
        if (pkg.contains("messenger")) return true

        // Pattern 3: Facebook Lite variants
        if (pkg.startsWith("com.facebook.mlite")) return true
        if (pkg.contains("facebook") && pkg.contains("lite")) return true

        // Pattern 4: Clone app patterns (Parallel Space, Dual Space, etc.)
        // These often wrap packages or add suffixes
        if (pkg.contains("facebook.orca")) return true
        if (pkg.contains("facebook.orc")) return true

        // Pattern 5: Common clone app prefixes/suffixes
        if (pkg.contains("clone") && pkg.contains("facebook")) return true
        if (pkg.contains("dual") && pkg.contains("facebook")) return true
        if (pkg.contains("parallel") && pkg.contains("facebook")) return true

        // Pattern 6: Generic - any facebook package with messaging capability
        // Check notification category as backup (handled elsewhere)

        return false
    }

    private fun createSenderId(senderName: String, packageName: String): String {
        return "${senderName.lowercase().trim()}_${packageName.hashCode()}"
    }
}
