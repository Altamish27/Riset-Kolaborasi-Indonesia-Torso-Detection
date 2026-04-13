package com.anatomy.app.repository

import android.content.Context
import android.util.Log
import com.anatomy.app.network.ChatResponse
import com.anatomy.app.network.ChatWebSocketClient
import com.anatomy.app.network.HttpClientFactory
import com.anatomy.app.network.VoiceWebSocketClient
import com.anatomy.app.utils.TokenManager
import com.anatomy.app.utils.WebSocketManager
import com.anatomy.app.services.LLMService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay

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
    suspend fun connectChat(): Flow<ChatResponse>? {
        return try {
            val token = TokenManager.getAccessToken(context)
            if (token.isNullOrBlank()) {
                Log.e(TAG, "No access token available for chat connection")
                return null
            }

            // Request exclusive access to WebSocket
            val connectionGranted = WebSocketManager.requestConnection(
                WebSocketManager.ConnectionType.CHATBOT, 
                "ChatRepository"
            )
            
            if (!connectionGranted) {
                val (currentType, currentOwner) = WebSocketManager.getConnectionInfo()
                Log.w(TAG, "WebSocket connection denied - currently used by $currentOwner for $currentType")
                return null
            }

            // Ensure stale socket is closed before opening a new one
            disconnectChat()
            
            chatWebSocket = ChatWebSocketClient(httpClient)
            chatWebSocket?.connect(HttpClientFactory.getBaseUrl(context), token)
            chatWebSocket?.messages
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to chat WebSocket", e)
            null
        }
    }
    
    /**
     * Send a chat message
     */
    fun sendChatMessage(sessionId: String?, content: String): Boolean {
        return try {
            if (content.isBlank()) {
                Log.e(TAG, "Cannot send empty message")
                return false
            }
            
            val message = mutableMapOf<String, String>(
                "action" to "send_message",
                "content" to content
            )
            
            if (sessionId != null) {
                message["session_id"] = sessionId
            }
            
            chatWebSocket?.send(message) == true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending chat message", e)
            false
        }
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
    suspend fun disconnectChat() {
        try {
            chatWebSocket?.disconnect()
            // Release WebSocket connection
            WebSocketManager.releaseConnection("ChatRepository")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting chat websocket", e)
        }
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
