package com.anatomy.app.repository

import android.content.Context
import android.util.Log
import com.anatomy.app.network.ChatResponse
import com.anatomy.app.network.ChatWebSocketClient
import com.anatomy.app.network.HttpClientFactory
import com.anatomy.app.network.VoiceWebSocketClient
import com.anatomy.app.utils.TokenManager
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val context: Context
) {
    
    private val TAG = "ChatRepository"
    private var chatWebSocket: ChatWebSocketClient? = null
    private var voiceWebSocket: VoiceWebSocketClient? = null
    private val httpClient = HttpClientFactory.createHttpClient()
    
    /**
     * Connect to chat WebSocket
     */
    fun connectChat(): Flow<ChatResponse>? {
        val token = TokenManager.getAccessToken(context)
        if (token.isNullOrBlank()) {
            Log.e(TAG, "No access token available for chat connection")
            return null
        }

        // Ensure stale socket is closed before opening a new one
        disconnectChat()
        
        chatWebSocket = ChatWebSocketClient(httpClient)
        chatWebSocket?.connect(HttpClientFactory.getBaseUrl(context), token)
        return chatWebSocket?.messages
    }
    
    /**
     * Send a chat message
     */
    fun sendChatMessage(sessionId: String?, content: String): Boolean {
        val message = mutableMapOf<String, String>(
            "action" to "send_message",
            "content" to content
        )
        
        if (sessionId != null) {
            message["session_id"] = sessionId
        }
        
        return chatWebSocket?.send(message) == true
    }
    
    /**
     * Create a new chat session
     */
    fun createSession(): Boolean {
        return chatWebSocket?.send(mapOf("action" to "create_session")) == true
    }
    
    /**
     * List all sessions
     */
    fun listSessions(): Boolean {
        return chatWebSocket?.send(mapOf("action" to "list_sessions")) == true
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
     * Disconnect from chat WebSocket
     */
    fun disconnectChat() {
        chatWebSocket?.disconnect()
        chatWebSocket = null
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
        voiceWebSocket?.disconnect()
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
