# Backend Integration - Quick Reference

## 1. Use Backend Chat in QnaScreen

### Current Implementation (Local)
```kotlin
val answer = withContext(Dispatchers.IO) {
    generateAnswer(database, result)  // ← THIS IS LOCAL
}
```

### Required Changes

**Add imports at top of QnaScreen.kt**:
```kotlin
import com.anatomy.app.network.HttpClientFactory
import com.anatomy.app.repository.ChatRepository
import kotlinx.coroutines.flow.collect
```

**Add to the composable**:
```kotlin
val context = LocalContext.current
val chatRepository = remember { ChatRepository(context) }
var currentSessionId by remember { mutableStateOf<String?>(null) }
var wsConnected by remember { mutableStateOf(false) }
```

**Replace the answer processing section**:
```kotlin
// OLD CODE - REMOVE THIS:
val answer = withContext(Dispatchers.IO) {
    generateAnswer(database, result)
}

// NEW CODE - USE THIS:
// Connect to backend chat if not connected
if (!wsConnected) {
    chatRepository.connectChat()?.let { messageFlow ->
        coroutineScope.launch {
            messageFlow.collect { response ->
                when (response.action) {
                    "session_created" -> {
                        currentSessionId = response.session_id
                    }
                    "answer" -> {
                        val answer = response.answer ?: "No response"
                        chatHistory.add(ChatMessage(text = answer, isUser = false))
                        isProcessing = false
                        AudioAssistant.speak(answer)
                    }
                    "error" -> {
                        statusText = "Error: ${response.error}"
                        isProcessing = false
                    }
                }
            }
        }
    }
    wsConnected = true
}

// Create session if needed
if (currentSessionId == null) {
    chatRepository.createSession()
} else {
    // Send the question to backend
    chatRepository.sendChatMessage(currentSessionId, result)
}
```

## 2. Handle Cleanup on Screen Change

**Add to the disposable effect at bottom**:
```kotlin
DisposableEffect(Unit) {
    onDispose {
        chatRepository.disconnectChat()  // ← Add this
        voiceHelper.destroy()
        AudioAssistant.onUtteranceCompleted = null
    }
}
```

## 3. Add Minimal Error Handling

```kotlin
try {
    chatRepository.sendChatMessage(currentSessionId, result)
} catch (e: Exception) {
    statusText = "Connection error: ${e.message}"
    isProcessing = false
}
```

## 4. Test Backend Connection

**Quick test function** (add to QnaScreen):
```kotlin
fun testBackendConnection() {
    coroutineScope.launch {
        try {
            val apiService = HttpClientFactory.createApiService(context)
            val response = apiService.login("test", "test")
            Log.d("QnaScreen", "Backend response: $response")
        } catch (e: Exception) {
            Log.e("QnaScreen", "Backend connection failed", e)
        }
    }
}
```

## 5. Check Token Before Chat

```kotlin
// Before connecting to chat
val token = TokenManager.getAccessToken(context)
if (token == null) {
    statusText = "Not authenticated. Please login."
    isProcessing = false
    return
}
```

## Full Integration Flow Diagram

```
User speaks/types
      ↓
doStartListening() / processTypedQuestion()
      ↓
chatRepository.sendChatMessage(currentSessionId, message)
      ↓
Backend WebSocket sends message  
to LLM via Groq API
      ↓
Backend sends response with:
{ action: "answer", answer: "..." }
      ↓
messageFlow.collect receives response
      ↓
Display answer + speak via TTS
      ↓
Auto-restart listening
```

## Testing Commands

### Test Registration
```bash
curl -X POST http://43.157.235.115:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test123", "email":"test@test.com", "password":"pass123"}'
```

### Test Login
```bash
curl -X POST http://43.157.235.115:8000/auth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=test123&password=pass123"
```

### Test WebSocket Connection
```bash
# Using websocat tool
websocat ws://43.157.235.115:8000/ws/chat
# Send: {"action":"authenticate","token":"YOUR_TOKEN"}
```

## Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| "No access token" | Ensure user is logged in and token is saved |
| WebSocket connection fails | Check base URL, ensure backend is running |
| "Invalid JSON" from backend | Verify message format matches API spec |
| App crashes on login | Add try-catch and check Logcat |
| Timeout on chat response | Check backend logs, LLM may be slow |

## Key Variables to Monitor

```kotlin
// Check these in Android Studio debugger
currentSessionId      // Should be set after "session_created"
wsConnected           // Should be true after connection
response.action       // Should be "answer" or "error"
TokenManager.getAccessToken(context)  // Should not be null
```

## IDE Quick Actions

1. **Go to definition**: Ctrl + Click on ChatRepository
2. **Find usages**: Right-click → Find Usages
3. **Auto-complete**: Type ChatRepository. → See available methods
4. **Add import**: Alt + Enter on red squiggly
5. **Format code**: Ctrl + Alt + L

---

For complete integration guide, see `INTEGRATION.md`
