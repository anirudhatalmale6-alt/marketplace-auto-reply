package com.marketplace.autoreply.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.marketplace.autoreply.data.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Accessibility Service for automating reply input in Messenger.
 * Used as fallback when notification direct reply is not available.
 */
class MessengerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val TAG = "AccessibilityService"

        @Volatile
        var instance: MessengerAccessibilityService? = null
            private set

        // Pending reply to send
        @Volatile
        var pendingReply: PendingReplyData? = null
    }

    data class PendingReplyData(
        val message: String,
        val targetPackage: String,
        val senderName: String,
        val onComplete: (Boolean) -> Unit
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLogger.info(TAG, "Accessibility Service STARTED", showToast = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scope.cancel()
        AppLogger.info(TAG, "Accessibility Service STOPPED", showToast = true)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLogger.info(TAG, "Accessibility connected", showToast = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pending = pendingReply ?: return

        // Check if we're in the right app
        if (event.packageName?.toString() != pending.targetPackage) {
            return
        }

        // Window state changed or content changed - try to find and fill input
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            scope.launch {
                tryToSendReply(pending)
            }
        }
    }

    override fun onInterrupt() {
        AppLogger.warn(TAG, "Service interrupted")
    }

    private var retryCount = 0
    private val MAX_RETRIES = 20  // Increased retries for cloned apps
    private var triedClickingConversation = false

    private suspend fun tryToSendReply(pending: PendingReplyData) {
        val rootNode = rootInActiveWindow ?: return

        try {
            // Longer delay on first try to let UI fully load
            val delayTime = if (retryCount == 0) 2000L else 500L
            delay(delayTime)

            // Find text input field
            var inputField = findInputField(rootNode)

            // If no input field found, maybe we're on chat list - try to find and click conversation
            // But NEVER try to find/click chat heads
            if (inputField == null && !triedClickingConversation && retryCount >= 3) {
                // Skip if sender name contains chat heads related text
                val senderLower = pending.senderName.lowercase()
                if (senderLower.contains("chat head") || senderLower.contains("chathead") ||
                    senderLower.contains("bubble") || senderLower.contains("active")) {
                    AppLogger.info(TAG, "Skipping chat heads - not a real conversation")
                    pendingReply = null
                    pending.onComplete(false)
                    retryCount = 0
                    triedClickingConversation = false
                    return
                }
                AppLogger.info(TAG, "Trying to find conversation: ${pending.senderName}")
                val conversationItem = findConversationBySender(rootNode, pending.senderName)
                if (conversationItem != null) {
                    AppLogger.info(TAG, "Clicking on conversation...", showToast = true)
                    conversationItem.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    triedClickingConversation = true
                    retryCount = 0  // Reset retry count after clicking conversation
                    return
                }
            }

            if (inputField == null) {
                retryCount++
                if (retryCount < MAX_RETRIES) {
                    AppLogger.info(TAG, "Waiting for input... ($retryCount)")
                } else {
                    AppLogger.error(TAG, "Input not found after $MAX_RETRIES tries", showToast = true)
                    pendingReply = null
                    pending.onComplete(false)
                    retryCount = 0
                    triedClickingConversation = false
                }
                return
            }

            AppLogger.info(TAG, "Found input field!", showToast = true)

            // Focus on input first
            inputField.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            delay(200)

            // Set the text
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, pending.message)
            val textSet = inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            AppLogger.info(TAG, "Text set: $textSet")

            delay(500)

            // Refresh root node to find updated send button
            val freshRoot = rootInActiveWindow
            if (freshRoot != null) {
                // Find and click send button
                val sendButton = findSendButton(freshRoot)
                if (sendButton != null) {
                    AppLogger.info(TAG, "Clicking send...", showToast = true)
                    sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                    // Clear pending and notify success
                    pendingReply = null
                    pending.onComplete(true)
                    retryCount = 0
                    triedClickingConversation = false
                    AppLogger.info(TAG, "SENT via Accessibility!", showToast = true)

                    // Press back to close Messenger after a short delay
                    delay(1500)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(500)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                } else {
                    AppLogger.warn(TAG, "Send button not found, retrying...")
                    retryCount++
                }
                freshRoot.recycle()
            }

        } catch (e: Exception) {
            AppLogger.error(TAG, "Error: ${e.message}")
            retryCount++
        } finally {
            rootNode.recycle()
        }
    }

    private fun findInputField(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Look for EditText or text input
        val className = node.className?.toString() ?: ""
        if (className.contains("EditText") || className.contains("edit", ignoreCase = true)) {
            if (node.isEditable) {
                AppLogger.info(TAG, "Found EditText input")
                return node
            }
        }

        // Check by resource ID patterns (expanded for Messenger)
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (viewId.contains("composer") || viewId.contains("input") ||
            viewId.contains("edit") || viewId.contains("message") ||
            viewId.contains("text_input") || viewId.contains("entry") ||
            viewId.contains("comment") || viewId.contains("write") ||
            viewId.contains("type") || viewId.contains("reply")) {
            if (node.isEditable) {
                AppLogger.info(TAG, "Found input by ID: $viewId")
                return node
            }
        }

        // Check for hint text indicating message input
        val hint = node.hintText?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        if ((hint.contains("message") || hint.contains("type") || hint.contains("write") ||
            hint.contains("aa") || hint.contains("إكتب") || hint.contains("ecris") ||
            contentDesc.contains("message") || contentDesc.contains("compose")) &&
            node.isEditable) {
            AppLogger.info(TAG, "Found input by hint/desc: $hint / $contentDesc")
            return node
        }

        // Check for focusable editable nodes
        if (node.isEditable && node.isFocusable) {
            AppLogger.info(TAG, "Found editable focusable node")
            return node
        }

        // Check for any editable node (last resort)
        if (node.isEditable) {
            AppLogger.info(TAG, "Found any editable node: class=$className")
            return node
        }

        // Check children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findInputField(child)
            if (result != null) {
                return result
            }
            child.recycle()
        }

        return null
    }

    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""

        // Check for send button by various identifiers
        val isSendButton = viewId.contains("send") ||
            contentDesc.contains("send") ||
            contentDesc.contains("enviar") ||
            contentDesc.contains("envoyer") ||
            contentDesc.contains("gửi") ||
            contentDesc.contains("kirim") ||
            text.contains("send") ||
            text == "›" ||
            text == ">" ||
            viewId.contains("submit") ||
            contentDesc.contains("submit")

        if (isSendButton && node.isClickable) {
            AppLogger.info(TAG, "Found send button: $viewId / $contentDesc")
            return node
        }

        // Also check for ImageButton/ImageView that might be send icon
        if ((className.contains("imagebutton") || className.contains("imageview")) &&
            node.isClickable &&
            (contentDesc.contains("send") || viewId.contains("send"))) {
            AppLogger.info(TAG, "Found send image button")
            return node
        }

        // Check children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findSendButton(child)
            if (result != null) {
                return result
            }
            child.recycle()
        }

        return null
    }

    /**
     * Find a conversation item in chat list by sender name
     */
    private fun findConversationBySender(node: AccessibilityNodeInfo, senderName: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""
        val nodeDesc = node.contentDescription?.toString() ?: ""
        val senderLower = senderName.lowercase()

        // Check if this node contains the sender name
        if (nodeText.lowercase().contains(senderLower) ||
            nodeDesc.lowercase().contains(senderLower)) {
            // Find clickable parent
            var clickable = node
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    clickable = parent
                    break
                }
                parent = parent.parent
            }
            if (clickable.isClickable) {
                AppLogger.info(TAG, "Found conversation for: $senderName")
                return clickable
            }
        }

        // Check children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findConversationBySender(child, senderName)
            if (result != null) {
                return result
            }
            child.recycle()
        }

        return null
    }

    /**
     * Request to send a reply via accessibility automation
     */
    fun requestSendReply(
        message: String,
        targetPackage: String,
        senderName: String,
        onComplete: (Boolean) -> Unit
    ) {
        retryCount = 0
        triedClickingConversation = false
        pendingReply = PendingReplyData(message, targetPackage, senderName, onComplete)
        AppLogger.info(TAG, "Reply queued, waiting for Messenger", showToast = true)
    }
}
