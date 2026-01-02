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

        // Save button for reply message
        binding.btnSaveMessage.setOnClickListener {
            val message = binding.editReplyMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                lifecycleScope.launch {
                    app.preferencesManager.setReplyMessage(message)
                    Toast.makeText(this@MainActivity, "Message saved", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
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
                .setTitle("Clear Reply History")
                .setMessage("This will allow the app to reply to users who have already received a message. Are you sure?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        app.database.repliedUserDao().clearAll()
                        Toast.makeText(this@MainActivity, "History cleared", Toast.LENGTH_SHORT).show()
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

        // Observe reply message
        lifecycleScope.launch {
            app.preferencesManager.replyMessage.collectLatest { message ->
                if (binding.editReplyMessage.text.toString() != message) {
                    binding.editReplyMessage.setText(message)
                }
            }
        }

        // Observe replied count
        lifecycleScope.launch {
            app.database.repliedUserDao().getRepliedCount().collectLatest { count ->
                binding.textRepliedCount.text = "Users replied to: $count"
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
