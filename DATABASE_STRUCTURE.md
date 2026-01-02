# Database Structure - Replied Users Tracking

## Overview

The app uses SQLite database via Android Room to track users who have already received an auto-reply. This prevents duplicate messages to the same buyer.

## Database Details

- **Database Name:** `marketplace_autoreply.db`
- **Location:** App's private storage (`/data/data/com.marketplace.autoreply/databases/`)
- **ORM:** Android Room 2.6.1

## Table: `replied_users`

| Column | Type | Description |
|--------|------|-------------|
| `odentifier` | TEXT (PK) | Unique identifier combining sender name + Messenger package hash |
| `senderName` | TEXT | Display name of the buyer |
| `conversationTitle` | TEXT | Title of the conversation (usually same as sender name) |
| `repliedAt` | INTEGER | Unix timestamp (milliseconds) when reply was sent |
| `messengerPackage` | TEXT | Package name of Messenger app (e.g., `com.facebook.orca`) |

## Identifier Format

The `odentifier` (primary key) is generated as:
```
{sender_name_lowercase_trimmed}_{messenger_package_hashcode}
```

**Example:**
- Sender: "John Smith"
- Package: "com.facebook.orca"
- Identifier: `john smith_1234567890`

This format ensures:
- Same buyer on same Messenger app = treated as duplicate (no re-reply)
- Same buyer on different Messenger apps (original vs clone) = treated as separate (can receive reply on each)

## Entity Class

```kotlin
@Entity(tableName = "replied_users")
data class RepliedUser(
    @PrimaryKey
    val odentifier: String,
    val senderName: String,
    val conversationTitle: String,
    val repliedAt: Long = System.currentTimeMillis(),
    val messengerPackage: String
)
```

## DAO Operations

| Operation | Method | Description |
|-----------|--------|-------------|
| Insert | `insert(user)` | Add new replied user (replaces if exists) |
| Check | `hasReplied(identifier)` | Returns true if user already replied to |
| Get All | `getAllRepliedUsers()` | Returns Flow of all replied users (sorted by date desc) |
| Count | `getRepliedCount()` | Returns Flow of total count |
| Clear | `clearAll()` | Deletes all records |
| Cleanup | `deleteOlderThan(timestamp)` | Removes old entries |

## Data Flow

1. **New Marketplace notification detected**
2. Sender name extracted from notification
3. Identifier generated from sender name + package
4. Database queried: `hasReplied(identifier)`
5. If false → proceed with auto-reply
6. After successful reply → `insert(RepliedUser(...))`
7. Future messages from same sender → `hasReplied()` returns true → skip

## Preferences Storage

User settings (reply message, enabled state) are stored separately using Android DataStore:

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `auto_reply_enabled` | Boolean | false | Main toggle state |
| `reply_message` | String | "Hi! Thanks for your interest..." | Auto-reply text |

**Location:** `datastore/settings.preferences_pb`

## Clearing Data

**From App:**
- Tap "Clear Reply History" button
- Calls `repliedUserDao.clearAll()`
- All entries deleted, counter resets to 0

**Manual (ADB):**
```bash
adb shell run-as com.marketplace.autoreply rm databases/marketplace_autoreply.db
```

## Export/Backup

The database can be exported for backup using ADB:
```bash
adb backup -f backup.ab -noapk com.marketplace.autoreply
```

Or directly (requires root or debuggable build):
```bash
adb shell run-as com.marketplace.autoreply cat databases/marketplace_autoreply.db > local_backup.db
```

---

**Note:** The database is stored in the app's private directory and is automatically deleted when the app is uninstalled.
