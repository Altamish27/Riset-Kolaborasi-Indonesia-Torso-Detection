package com.anatomy.app.repository

import android.content.Context
import android.util.Log
import com.anatomy.app.network.ChatResponse
import com.anatomy.app.network.HttpClientFactory
import com.anatomy.app.network.SessionHistoryMessage
import com.anatomy.app.network.SessionSummary
import com.anatomy.app.network.VoiceWebSocketClient
import com.anatomy.app.utils.TokenManager
import com.anatomy.app.utils.UnifiedWebSocketManager
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val context: Context
) {
    
    private val TAG = "ChatRepository"
    private var voiceWebSocket: VoiceWebSocketClient? = null
    private val httpClient = HttpClientFactory.createHttpClient()
    private val apiService by lazy { HttpClientFactory.createApiService(context) }
    
    /**
     * Connect to chat WebSocket (now uses unified connection)
     */
    suspend fun connectChat(): Flow<ChatResponse>? {
        return try {
            if (UnifiedWebSocketManager.isConnected()) {
                Log.d(TAG, "Using existing unified WebSocket connection")
                return UnifiedWebSocketManager.getMessages()
            }

            val token = TokenManager.getAccessToken(context)
            if (token.isNullOrBlank()) {
                Log.e(TAG, "No access token available for chat connection")
                return null
            }

            Log.d(TAG, "Connecting to unified WebSocket for chat")
            UnifiedWebSocketManager.connect(context, token)
            UnifiedWebSocketManager.getMessages()
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to unified WebSocket", e)
            null
        }
    }
    
    /**
     * Send a chat message (now uses unified connection)
     */
    fun sendChatMessage(sessionId: String?, content: String): Boolean {
        return try {
            if (content.isBlank()) {
                Log.e(TAG, "Cannot send empty message")
                return false
            }

            val resolvedSessionId = sessionId ?: TokenManager.getChatSessionId(context)
            if (resolvedSessionId.isNullOrBlank()) {
                Log.e(TAG, "Cannot send message: missing session id")
                return false
            }
            
            UnifiedWebSocketManager.sendChatMessage(resolvedSessionId, content)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending chat message", e)
            false
        }
    }
    
    /**
     * Create a new chat session via HTTP and return session_id
     */
    suspend fun createSession(): String? {
        return try {
            val resp = apiService.createSession()
            TokenManager.saveChatSessionId(context, resp.session_id)
            resp.session_id
        } catch (e: Exception) {
            Log.e(TAG, "Error creating session via HTTP", e)
            null
        }
    }

    suspend fun getOrCreateSessionId(forceNew: Boolean = false): String? {
        if (forceNew) {
            TokenManager.clearChatSessionId(context)
        }

        val savedSessionId = TokenManager.getChatSessionId(context)
        if (!savedSessionId.isNullOrBlank()) {
            return savedSessionId
        }

        return createSession()
    }

    suspend fun getSessionHistory(sessionId: String): List<SessionHistoryMessage> {
        return try {
            apiService.getSessionHistory(sessionId).messages
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chat history", e)
            emptyList()
        }
    }

    suspend fun fetchSessions(): List<SessionSummary> {
        return try {
            apiService.listSessions()
        } catch (e: Exception) {
            Log.w(TAG, "Primary sessions endpoint failed, trying legacy path", e)
            try {
                apiService.listSessionsLegacy()
            } catch (legacyError: Exception) {
                Log.e(TAG, "Error fetching sessions list", legacyError)
                emptyList()
            }
        }
    }

    suspend fun resumeLastSessionIdOrCreate(): String? {
        val sessions = fetchSessions()
        val latest = sessions.maxByOrNull {
            it.updated_at ?: it.created_at ?: ""
        }

        if (latest != null) {
            setActiveSessionId(latest.session_id)
            return latest.session_id
        }

        return createSession()
    }

    fun setActiveSessionId(sessionId: String) {
        TokenManager.saveChatSessionId(context, sessionId)
    }

    fun clearPersistedSessionId() {
        TokenManager.clearChatSessionId(context)
    }
    
    /**
     * List all sessions via WebSocket (legacy helper).
     */
    fun listSessionsWs(): Boolean {
        return UnifiedWebSocketManager.listSessions()
    }
    
    /**
     * Get chat history for a session
     */
    fun getHistory(sessionId: String): Boolean {
        return !sessionId.isBlank()
    }
    
    /**
     * Disconnect from chat WebSocket (now managed globally)
     */
    fun disconnectChat() {
        // Note: Don't disconnect unified WebSocket here as it's shared
        // It will be disconnected on app destroy or logout
        Log.d(TAG, "Chat disconnect requested - unified connection remains active")
    }
    
    /**
     * Connect to voice WebSocket
     */
    fun connectVoice(): Flow<ChatResponse>? {
        val token = TokenManager.getAccessToken(context)
        if (token.isNullOrBlank()) {
            Log.e(TAG, "No access token available for voice connection")
            return null
        }

        disconnectVoice()
        
        voiceWebSocket = VoiceWebSocketClient(httpClient)
        voiceWebSocket?.connect(HttpClientFactory.getBaseUrl(context), token)
        return voiceWebSocket?.messages
    }
    
    /**
     * Send audio chunk to voice WebSocket
     */
    fun sendAudioChunk(audioData: ByteArray): Boolean {
        return voiceWebSocket?.sendAudio(audioData) == true
    }
    
    /**
     * Signal end of speech to voice WebSocket
     */
    fun sendEndOfSpeech(): Boolean {
        return voiceWebSocket?.sendJson(mapOf("action" to "end_of_speech")) == true
    }
    
    /**
     * Create a session in voice mode
     */
    fun createVoiceSession(): Boolean {
        return voiceWebSocket?.sendJson(mapOf("action" to "create_session")) == true
    }
    
    /**
     * Disconnect from voice WebSocket
     */
    fun disconnectVoice() {
        try {
            voiceWebSocket?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting voice websocket", e)
        }
        voiceWebSocket = null
    }
    
    /**
     * Disconnect all connections
     */
    fun disconnectAll() {
        disconnectChat()
        disconnectVoice()
    }
}
