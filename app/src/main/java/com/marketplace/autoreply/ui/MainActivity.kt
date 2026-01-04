package com.marketplace.autoreply.ui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.marketplace.autoreply.MarketplaceAutoReplyApp
import com.marketplace.autoreply.databinding.ActivityMainBinding
import com.marketplace.autoreply.service.MessengerAccessibilityService
import com.marketplace.autoreply.service.MessengerNotificationListener
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val app by lazy { MarketplaceAutoReplyApp.getInstance() }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observePreferences()
        observeDebugLogs()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun setupUI() {
        // Toggle switch for enabling/disabling auto-reply
        binding.switchAutoReply.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                app.preferencesManager.setAutoReplyEnabled(isChecked)
            }
        }

        // Save button for Stage 1 messages
        binding.btnSaveStage1.setOnClickListener {
            val messages = listOf(
                binding.editStage1Msg1.text.toString().trim(),
                binding.editStage1Msg2.text.toString().trim(),
                binding.editStage1Msg3.text.toString().trim(),
                binding.editStage1Msg4.text.toString().trim(),
                binding.editStage1Msg5.text.toString().trim()
            ).filter { it.isNotEmpty() }

            if (messages.isNotEmpty()) {
                lifecycleScope.launch {
                    app.preferencesManager.setStage1Messages(messages)
                    Toast.makeText(this@MainActivity, "Stage 1: ${messages.size} message(s) saved", Toast.LENGTH_SHORT).show()
                    binding.textStage1Count.text = "Active: ${messages.size}/5"
                }
            } else {
                Toast.makeText(this, "Please enter at least one welcome message", Toast.LENGTH_SHORT).show()
            }
        }

        // Save button for Stage 2 messages
        binding.btnSaveStage2.setOnClickListener {
            val messages = listOf(
                binding.editStage2Msg1.text.toString().trim(),
                binding.editStage2Msg2.text.toString().trim(),
                binding.editStage2Msg3.text.toString().trim(),
                binding.editStage2Msg4.text.toString().trim(),
                binding.editStage2Msg5.text.toString().trim()
            ).filter { it.isNotEmpty() }

            if (messages.isNotEmpty()) {
                lifecycleScope.launch {
                    app.preferencesManager.setStage2Messages(messages)
                    Toast.makeText(this@MainActivity, "Stage 2: ${messages.size} message(s) saved", Toast.LENGTH_SHORT).show()
                    binding.textStage2Count.text = "Active: ${messages.size}/5"
                }
            } else {
                Toast.makeText(this, "Please enter at least one follow-up message", Toast.LENGTH_SHORT).show()
            }
        }

        // Save button for Stage 3 (contact info)
        binding.btnSaveStage3.setOnClickListener {
            val whatsapp = binding.editWhatsapp.text.toString().trim()
            val instagram = binding.editInstagram.text.toString().trim()
            val template = binding.editStage3Template.text.toString().trim()

            if (whatsapp.isEmpty() && instagram.isEmpty()) {
                Toast.makeText(this, "Please enter WhatsApp number or Instagram", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                app.preferencesManager.setWhatsappNumber(whatsapp)
                app.preferencesManager.setInstagramLink(instagram)
                if (template.isNotEmpty()) {
                    app.preferencesManager.setStage3MessageTemplate(template)
                }
                Toast.makeText(this@MainActivity, "Contact info saved!", Toast.LENGTH_SHORT).show()
            }
        }

        // Save button for delay settings
        binding.btnSaveDelay.setOnClickListener {
            val minDelay = binding.editMinDelay.text.toString().toIntOrNull() ?: 8
            val maxDelay = binding.editMaxDelay.text.toString().toIntOrNull() ?: 12

            if (minDelay < 1) {
                Toast.makeText(this, "Minimum delay must be at least 1 second", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (maxDelay < minDelay) {
                Toast.makeText(this, "Maximum delay must be >= minimum", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                app.preferencesManager.setDelayRange(minDelay, maxDelay)
                Toast.makeText(this@MainActivity, "Delay set: $minDelay-$maxDelay seconds", Toast.LENGTH_SHORT).show()
            }
        }

        // Permission setup buttons
        binding.btnSetupNotification.setOnClickListener {
            openNotificationListenerSettings()
        }

        binding.btnSetupAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        // Clear history button
        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset All Conversations")
                .setMessage("This will reset all conversation stages, allowing the app to start from Stage 1 with all users again. Are you sure?")
                .setPositiveButton("Reset") { _, _ ->
                    lifecycleScope.launch {
                        app.database.repliedUserDao().clearAll()
                        Toast.makeText(this@MainActivity, "All conversations reset", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Clear logs button
        binding.btnClearLogs.setOnClickListener {
            lifecycleScope.launch {
                app.database.debugLogDao().clearAll()
                Toast.makeText(this@MainActivity, "Logs cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observePreferences() {
        // Observe auto-reply enabled state
        lifecycleScope.launch {
            app.preferencesManager.isAutoReplyEnabled.collectLatest { enabled ->
                binding.switchAutoReply.isChecked = enabled
            }
        }

        // Observe Stage 1 messages
        lifecycleScope.launch {
            app.preferencesManager.stage1Messages.collectLatest { messages ->
                val stage1Boxes = listOf(
                    binding.editStage1Msg1,
                    binding.editStage1Msg2,
                    binding.editStage1Msg3,
                    binding.editStage1Msg4,
                    binding.editStage1Msg5
                )
                stage1Boxes.forEachIndexed { index, editText ->
                    val message = messages.getOrNull(index) ?: ""
                    if (editText.text.toString() != message) {
                        editText.setText(message)
                    }
                }
                binding.textStage1Count.text = "Active: ${messages.size}/5"
            }
        }

        // Observe Stage 2 messages
        lifecycleScope.launch {
            app.preferencesManager.stage2Messages.collectLatest { messages ->
                val stage2Boxes = listOf(
                    binding.editStage2Msg1,
                    binding.editStage2Msg2,
                    binding.editStage2Msg3,
                    binding.editStage2Msg4,
                    binding.editStage2Msg5
                )
                stage2Boxes.forEachIndexed { index, editText ->
                    val message = messages.getOrNull(index) ?: ""
                    if (editText.text.toString() != message) {
                        editText.setText(message)
                    }
                }
                binding.textStage2Count.text = "Active: ${messages.size}/5"
            }
        }

        // Observe WhatsApp number
        lifecycleScope.launch {
            app.preferencesManager.whatsappNumber.collectLatest { number ->
                if (binding.editWhatsapp.text.toString() != number) {
                    binding.editWhatsapp.setText(number)
                }
            }
        }

        // Observe Instagram link
        lifecycleScope.launch {
            app.preferencesManager.instagramLink.collectLatest { link ->
                if (binding.editInstagram.text.toString() != link) {
                    binding.editInstagram.setText(link)
                }
            }
        }

        // Observe Stage 3 template
        lifecycleScope.launch {
            app.preferencesManager.stage3MessageTemplate.collectLatest { template ->
                if (binding.editStage3Template.text.toString() != template) {
                    binding.editStage3Template.setText(template)
                }
            }
        }

        // Observe delay settings
        lifecycleScope.launch {
            app.preferencesManager.minDelaySeconds.collectLatest { minDelay ->
                if (binding.editMinDelay.text.toString() != minDelay.toString()) {
                    binding.editMinDelay.setText(minDelay.toString())
                }
            }
        }

        lifecycleScope.launch {
            app.preferencesManager.maxDelaySeconds.collectLatest { maxDelay ->
                if (binding.editMaxDelay.text.toString() != maxDelay.toString()) {
                    binding.editMaxDelay.setText(maxDelay.toString())
                }
            }
        }

        // Observe replied count
        lifecycleScope.launch {
            app.database.repliedUserDao().getRepliedCount().collectLatest { count ->
                binding.textRepliedCount.text = "Conversations: $count"
            }
        }
    }

    private fun observeDebugLogs() {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        lifecycleScope.launch {
            app.database.debugLogDao().getRecentLogs().collectLatest { logs ->
                val logText = if (logs.isEmpty()) {
                    "No logs yet. Enable auto-reply and receive a message to see activity."
                } else {
                    logs.take(20).joinToString("\n") { log ->
                        val time = dateFormat.format(Date(log.timestamp))
                        "[$time] ${log.tag}: ${log.message}"
                    }
                }
                binding.textDebugLogs.text = logText
            }
        }
    }

    private fun updateServiceStatus() {
        val notificationEnabled = isNotificationListenerEnabled()
        val accessibilityEnabled = isAccessibilityServiceEnabled()

        binding.textNotificationStatus.text = if (notificationEnabled) {
            "Notification Access: ENABLED"
        } else {
            "Notification Access: DISABLED"
        }
        binding.btnSetupNotification.isEnabled = !notificationEnabled

        binding.textAccessibilityStatus.text = if (accessibilityEnabled) {
            "Accessibility Service: ENABLED"
        } else {
            "Accessibility Service: DISABLED"
        }
        binding.btnSetupAccessibility.isEnabled = !accessibilityEnabled

        // Show warning if notification not enabled (required)
        binding.textWarning.visibility = if (notificationEnabled) {
            android.view.View.GONE
        } else {
            android.view.View.VISIBLE
        }
    }

    private fun checkPermissions() {
        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val componentName = ComponentName(this, MessengerNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(componentName.flattenToString())
    }

    private fun openNotificationListenerSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        val myServiceName = ComponentName(this, MessengerAccessibilityService::class.java)

        return enabledServices.any { serviceInfo ->
            val enabledServiceName = ComponentName.unflattenFromString(serviceInfo.id)
            enabledServiceName == myServiceName
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
}
