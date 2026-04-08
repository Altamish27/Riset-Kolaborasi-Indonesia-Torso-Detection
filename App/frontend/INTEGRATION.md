# Frontend-Backend Integration Documentation

## Overview
Frontend1 (Android Compose app) has been integrated with the RKI Backend API running at `43.157.235.115:8000`.

## Architecture

### Backend (FastAPI)
- **Base URL**: `43.157.235.115:8000`
- **Features**:
  - Authentication (JWT-based)
  - Chat WebSocket for multi-turn conversations
  - Voice WebSocket for audio-to-text and LLM responses
  - Document ingestion (PDF to Markdown)

### Frontend  (Android/Compose)
- **YOLO v12n** object detection in real-time
- **Voice Recognition** using Android SpeechRecognizer
- **TTS** (Text-to-Speech) in Indonesian
- **Jetpack Compose** UI
- **Room Database** for local organ data
- **Retrofit + OkHttp** for HTTP/WebSocket communication

## Authentication Flow

### Login
1. User enters username and password in LoginScreen
2. Call `POST /auth/token` with OAuth2PasswordRequestForm
3. Receive `access_token` and `refresh_token`
4. Store tokens using TokenManager (encrypted DataStore)
5. Proceed to main app

### Registration
1. User enters username, email, password
2. Call `POST /auth/register` with RegisterRequest
3. On success, show login screen
4. User can then login

### Token Management
- **Storage**: TokenManager uses Android DataStore (encrypted)
- **Refresh**: Token interceptor automatically adds "Authorization: Bearer {token}" header
- **Refresh Token**: Can be used to get new access token via `POST /auth/refresh`

## Integration Points

### 1. Authentication (DONE)
- ✅ LoginScreen.kt - UI for login/registration
- ✅ AuthRepository.kt - Login, register, logout, refresh logic
- ✅ TokenManager.kt - Token persistence & retrieval
- ✅ HttpClientFactory.kt - Retrofit setup with token interceptor
- Location: `/app/src/main/java/com/anatomy/app/`

### 2. Chat WebSocket (PARTIALLY DONE - UI Update Needed)
**File**: ChatRepository.kt, QnaScreen.kt

**Workflow**:
```
1. User types question or uses voice
2. Connect to /ws/chat endpoint with JWT token
3. Send: {"action": "send_message", "session_id": "...", "content": "..."}
4. Receive: {"action": "...", "message": "...", "thinking": "...", "answer": "..."}
5. Display response and speak via TTS
```

**QnaScreen TODO**:
- Replace local `generateAnswer()` function with backend chat
- Integrate ChatRepository
- Flow pattern to listen to WebSocket messages
- Handle session management (create, list, get history)

### 3. Voice WebSocket (NOT YET INTEGRATED)
**File**: VoiceWebSocketClient in WebSocketClient.kt

**Workflow**:
```
1. Connect to /ws/voice with JWT token
2. Send audio chunks as binary data
3. Send special "END_OF_SPEECH" text message
4. Receive: {"stt": "...", "thinking": "...", "answer": "..."}
5. Display STT text, speak answer
```

**Integration Steps**:
- Modify VoiceRecognitionHelper to support WebSocket audio upload
- Or implement separate VoiceAssistantScreen that sends audio to backend
- Parse responses and display/speak

### 4. Document Ingestion (NOT YET INTEGRATED)
**Endpoint**: `POST /ingest/pdf`

**Requirements**:
- File picker to select PDF
- Form fields: category, kelas_akademik, title, description
- Send multipart form data
- Update organ database with ingested content

## API Endpoints Reference

### Authentication
```
POST /auth/register
Body: {"username": "...", "email": "...", "password": "..."}
Response: {"message": "User registered successfully"}

POST /auth/token
Body: {"username": "...", "password": "..."} (form-encoded)
Response: {"access_token": "...", "refresh_token": "...", "token_type": "bearer"}

POST /auth/refresh
Body: {"refresh_token": "..."}
Response: {"access_token": "..."}

POST /auth/logout
Body: {"refresh_token": "..."}
Response: {"message": "Successfully logged out"}
```

### Chat WebSocket (`/ws/chat`)
**Auth Required**: Yes (send authenticate action first)

```json
// Authenticate
{"action": "authenticate", "token": "JWT_TOKEN"}

// Create session
{"action": "create_session"}

// List sessions
{"action": "list_sessions"}

// Get chat history
{"action": "get_history", "session_id": "..."}

// Send message
{"action": "send_message", "session_id": "...", "content": "..."}
```

### Voice WebSocket (`/ws/voice`)
**Auth Required**: Yes (send authenticate action first)

```json
// Authenticate
{"action": "authenticate", "token": "JWT_TOKEN"}

// Create session
{"action": "create_session"}

// End speech
{"text": "END_OF_SPEECH"}
```

## File Structure
```
app/src/main/java/com/anatomy/app/
├── network/
│   ├── ApiModels.kt      # Data classes
│   ├── ApiService.kt     # Retrofit interface
│   ├── HttpClientFactory.kt # OkHttp & Retrofit setup
│   └── WebSocketClient.kt # Chat & Voice WebSocket
├── repository/
│   ├── AuthRepository.kt # Authentication
│   └── ChatRepository.kt # Chat & Voice
├── utils/
│   └── TokenManager.kt   # Token security
└── ui/screen/
    ├── LoginScreen.kt    # Auth UI
    ├── QnaScreen.kt      # Chat UI (needs update)
    └── ...
```

## Configuration

### Base URL
**File**: `HttpClientFactory.kt`
```kotlin
private const val BASE_URL = "http://43.157.235.115:8000"
```

To change, update this constant.

### Network Timeouts
**File**: `HttpClientFactory.kt`
Can add to OkHttpClient:
```kotlin
.connectTimeout(30, TimeUnit.SECONDS)
.readTimeout(30, TimeUnit.SECONDS)
.writeTimeout(30, TimeUnit.SECONDS)
```

## Build & Deployment

### Build
```bash
cd frontend1
gradle build
gradle installDebug  # Install to emulator/device
```

### Dependencies Added
- Retrofit 2.11.0
- OkHttp3 4.12.0
- Kotlinx Serialization
- DataStore

## Testing Checklist

- [ ] Build compiles without errors
- [ ] LoginScreen renders correctly
- [ ] Register new user (test endpoint connectivity)
- [ ] Login with existing user
- [ ] Tokens saved to DataStore
- [ ] Token header added to requests
- [ ] Chat WebSocket connects
- [ ] Send/receive chat messages
- [ ] Voice WebSocket connects
- [ ] Audio upload and response works
- [ ] TTS plays responses

## Known Issues & TODOs

1. **QnaScreen Integration** - Needs update to use ChatRepository instead of local generateAnswer()
2. **Voice WebSocket** - Need to implement audio upload from SpeechRecognizer
3. **Session Management** - UI needs to show/manage multiple chat sessions
4. **Error Handling** - Add better error handling and user feedback
5. **Offline Support** - Cache responses locally when offline
6. **WebSocket Reconnection** - Add auto-reconnect logic

## Contact
Backend: 43.157.235.115:8000
Frontend: Android Compose (Jetpack)

For issues or questions, check logs with:
- Logcat for app logs (tag: "ChatWebSocketClient", "AuthRepository", etc.)
- Backend logs at server
