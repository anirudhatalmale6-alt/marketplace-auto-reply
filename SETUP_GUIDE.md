# Marketplace Auto-Reply - Setup Guide

## Installation

1. **Install the APK**
   - Transfer `app-release.apk` to your Android device
   - Open the file and allow installation from unknown sources if prompted
   - Complete the installation

2. **Open the App**
   - Find "Marketplace Auto-Reply" in your app drawer
   - Launch the app

## Required Permissions

The app needs two special permissions to function:

### 1. Notification Access
- In the app, tap "Setup" next to "Notification Access"
- This opens Android settings
- Find "Marketplace Auto-Reply" in the list
- Toggle it ON
- Confirm when prompted

**Why needed:** This allows the app to see Messenger notifications and detect Marketplace messages.

### 2. Accessibility Service
- In the app, tap "Setup" next to "Accessibility Service"
- This opens Android Accessibility settings
- Find "Marketplace Auto-Reply" under "Downloaded services" or "Installed services"
- Tap on it and toggle it ON
- Confirm when prompted

**Why needed:** This allows the app to type messages and tap the send button in Messenger.

## Configuration

### Setting Your Reply Message
1. In the main app screen, find the "Reply Message" section
2. Enter your desired auto-reply message
3. Tap "Save Message"

**Example messages:**
- "Hi! Thanks for your interest. I'll get back to you shortly."
- "Hello! Yes, this item is available. What questions do you have?"
- "Thanks for reaching out! I'll respond with more details soon."

### Enabling Auto-Reply
1. Toggle the main "Auto-Reply" switch ON
2. A persistent notification will appear showing the service is active
3. The app will now monitor for Marketplace messages

## Using with Cloned/Dual Messenger Apps

The app automatically detects and works with:
- **Facebook Messenger** (original)
- **Messenger Lite**
- **Samsung Dual Messenger** (Messenger clone)
- **Other dual-app solutions** (Xiaomi, Huawei, etc.)

No additional configuration needed - the app monitors all Messenger variants.

## How It Works

1. When a Marketplace buyer messages you, the app detects it via notification
2. It waits a random 8-12 seconds (to avoid Facebook detection)
3. Opens the conversation
4. Types and sends your pre-set message
5. Records the buyer so they won't receive duplicate auto-replies

## Statistics & History

- **Users replied to:** Shows how many unique buyers have received auto-replies
- **Clear Reply History:** Resets the database (use if you want to re-reply to previous buyers)

## Troubleshooting

### App not detecting messages
1. Verify Notification Access is enabled
2. Make sure Messenger notifications are not muted
3. Check that Auto-Reply toggle is ON

### Messages not being sent
1. Verify Accessibility Service is enabled
2. Make sure Messenger is not in "Restricted" background mode
3. Try restarting the app

### Service stops after a while
1. Disable battery optimization for this app
   - Go to Settings > Apps > Marketplace Auto-Reply > Battery
   - Select "Unrestricted" or "Don't optimize"
2. Lock the app in recent apps (if your device supports it)

### Messages going to wrong conversations
1. Make sure you're not actively using Messenger when auto-reply triggers
2. Close other Messenger conversations before enabling auto-reply

## Battery Usage

The app is designed for minimal battery impact:
- Uses efficient notification monitoring
- Only activates when Marketplace messages arrive
- No continuous background scanning

## Stopping Auto-Reply

Simply toggle OFF the "Auto-Reply" switch in the app. The persistent notification will disappear.

## Uninstalling

1. First disable Accessibility Service in Android settings
2. Then disable Notification Access
3. Uninstall the app normally

---

**Note:** This app is designed for personal use on Facebook Marketplace. Always ensure your auto-reply messages are professional and helpful to potential buyers.
