package com.anatomy.app.repository

import android.content.Context
import android.util.Log
import com.anatomy.app.network.ChatResponse
import com.anatomy.app.network.ChatWebSocketClient
import com.anatomy.app.network.HttpClientFactory
import com.anatomy.app.network.VoiceWebSocketClient
import com.anatomy.app.utils.TokenManager
import com.anatomy.app.utils.UnifiedWebSocketManager
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val context: Context
) {
    
    private val TAG = "ChatRepository"
    private var chatWebSocket: ChatWebSocketClient? = null
    private var voiceWebSocket: VoiceWebSocketClient? = null
    private val httpClient = HttpClientFactory.createHttpClient()
    
    /**
     * Connect to chat WebSocket (now uses unified connection)
     */
    fun connectChat(): Flow<ChatResponse>? {
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

            // Connect to unified WebSocket
            Log.d(TAG, "Connecting to unified WebSocket for chat")
            UnifiedWebSocketManager.connect(context, token)
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
            
            UnifiedWebSocketManager.sendChatMessage(sessionId, content)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending chat message", e)
            false
        }
    }
    
    /**
     * Create a new chat session (now uses unified connection)
     */
    fun createSession(): Boolean {
        return UnifiedWebSocketManager.createSession()
    }
    
    /**
     * List all sessions (now uses unified connection)
     */
    fun listSessions(): Boolean {
        return UnifiedWebSocketManager.listSessions()
    }
    
    /**
     * Get chat history for a session
     */
    fun getHistory(sessionId: String): Boolean {
        return chatWebSocket?.send(mapOf(
            "action" to "get_history",
            "session_id" to sessionId
        )) == true
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
