# Hardcoded Data Removal - Frontend1 Chatbot App

## Summary of Changes

This document describes all the hardcoded data that has been removed from the frontend1 Android app and how they are now managed through a centralized configuration system.

## Hardcoded Values Removed

### 1. **Base URL** (`HttpClientFactory.kt`)
- **Before:** `private const val BASE_URL = "http://43.157.235.115:8000"`
- **After:** Moved to `AppConfig.kt` as `DEFAULT_BASE_URL`
- **Impact:** All HTTP requests now use the configured base URL from `AppConfig`

### 2. **WebSocket Chat Path** (`WebSocketClient.kt`)
- **Before:** `val wsUrl = baseUrl.replace("http", "ws") + "/ws/chat"`
- **After:** `val wsUrl = baseUrl.replace("http", "ws") + AppConfig.getWsChatPathDefault()`
- **Impact:** Chat WebSocket connections use the configured path

### 3. **WebSocket Voice Path** (`WebSocketClient.kt`)
- **Before:** `val wsUrl = baseUrl.replace("http", "ws") + "/ws/voice"`
- **After:** `val wsUrl = baseUrl.replace("http", "ws") + AppConfig.getWsVoicePathDefault()`
- **Impact:** Voice WebSocket connections use the configured path

## New Configuration System

### AppConfig.kt
A centralized configuration object that manages all API and WebSocket settings.

**Location:** `app/src/main/java/com/anatomy/app/config/AppConfig.kt`

**Features:**
- Stores configuration in Android DataStore (secure, persistent storage)
- Provides both Flow-based (reactive) and direct (default) access methods
- Easy to override configuration at runtime

**Default Values:**
```kotlin
DEFAULT_BASE_URL = "http://43.157.235.115:8000"
DEFAULT_WS_CHAT_PATH = "/ws/chat"
DEFAULT_WS_VOICE_PATH = "/ws/voice"
```

## Using the Configuration System

### Reading Configuration

1. **Get default values (non-blocking):**
   ```kotlin
   val baseUrl = AppConfig.getBaseUrlDefault()
   val chatPath = AppConfig.getWsChatPathDefault()
   val voicePath = AppConfig.getWsVoicePathDefault()
   ```

2. **Get reactive values (Flow-based):**
   ```kotlin
   AppConfig.getBaseUrlFlow(context).collect { baseUrl ->
       // Use baseUrl
   }
   ```

### Updating Configuration

```kotlin
// In a coroutine scope:
AppConfig.setBaseUrl(context, "http://new-server:8000")
AppConfig.setWsChatPath(context, "/ws/chat-v2")
AppConfig.setWsVoicePath(context, "/ws/voice-v2")
```

## Files Modified

1. **`network/HttpClientFactory.kt`**
   - Removed hardcoded `BASE_URL` constant
   - Updated `createApiService()` to read from `AppConfig`
   - Updated `getBaseUrl(context)` method signature

2. **`network/WebSocketClient.kt`**
   - Added import for `AppConfig`
   - Updated `ChatWebSocketClient.connect()` to use `AppConfig.getWsChatPathDefault()`
   - Updated `VoiceWebSocketClient.connect()` to use `AppConfig.getWsVoicePathDefault()`

3. **`repository/ChatRepository.kt`**
   - Updated `connectChat()` to pass context to `HttpClientFactory.getBaseUrl()`
   - Updated `connectVoice()` to pass context to `HttpClientFactory.getBaseUrl()`

4. **`config/AppConfig.kt`** (NEW)
   - Created new configuration management system
   - Centralized all hardcoded API/WebSocket values

## Benefits

1. **Centralized Configuration:** All API settings in one place
2. **Runtime Updates:** Configuration can be changed without recompiling
3. **Persistent Storage:** Settings survive app restarts using DataStore
4. **Type-Safe:** Kotlin-based, type-safe configuration access
5. **Easy Maintenance:** Future changes only require updates to AppConfig.kt
6. **Testing-Friendly:** Can override configuration per test scenario

## Environment Variables/Build Variants

For development/staging/production environments, you can:

1. **In `build.gradle.kts`:** Define build variants with different default configurations
2. **At Runtime:** Call `AppConfig.setBaseUrl(context, buildVariantUrl)` during app initialization
3. **Via Remote Config:** Integrate Firebase Remote Config to fetch settings from backend

## Example: Multi-Environment Setup

```kotlin
// In Application.onCreate() or MainActivity
launch {
    val baseUrl = when (BuildConfig.FLAVOR) {
        "development" -> "http://192.168.1.100:8000"
        "staging" -> "http://staging-server:8000"
        "production" -> "http://43.157.235.115:8000"
        else -> AppConfig.getBaseUrlDefault()
    }
    AppConfig.setBaseUrl(applicationContext, baseUrl)
}
```

## Testing Checklist

- [ ] Verify app connects to correct API endpoint
- [ ] Verify WebSocket connections use correct paths
- [ ] Test configuration persistence across app restarts
- [ ] Test configuration updates at runtime
- [ ] Verify no hardcoded URLs in app code
