package com.anatomy.app.network

import android.util.Log
import com.anatomy.app.config.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * UnifiedWebSocketClient - Single WebSocket connection for all features
 * 
 * Concept: Connect once on login, use message routing for different features
 * - Chat messages: {"type": "chat", "action": "send_message", ...}
 * - Scan requests: {"type": "scan", "action": "get_explanation", ...}
 * - Voice requests: {"type": "voice", "action": "...", ...}
 */
class UnifiedWebSocketClient(private val httpClient: OkHttpClient) {
    
    private val TAG = "UnifiedWebSocketClient"
    private var webSocket: WebSocket? = null
    private val messageFlow = MutableSharedFlow<ChatResponse>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val decodeJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val encodeJson = Json { explicitNulls = false }
    
    val messages: Flow<ChatResponse> = messageFlow.asSharedFlow()
    
    enum class MessageType {
        CHAT,
        SCAN,
        VOICE
    }
    
    private var isConnected = false
    private var isAuthenticated = false
    private val pingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pingJob: Job? = null
    
    fun connect(baseUrl: String, token: String) {
        try {
            if (isConnected) {
                Log.d(TAG, "Already connected to unified WebSocket")
                return
            }
            
            // Close existing connection first
            disconnect()
            
            val wsUrl = baseUrl.replace("http", "ws") + AppConfig.getWsChatPathDefault()
            val request = Request.Builder().url(wsUrl).build()
            
            webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    Log.d(TAG, "Unified WebSocket connected to: $wsUrl")
                    isConnected = true
                    safeChannelSend(ChatResponse(action = "connected"))
                    
                    // Immediately authenticate
                    val authMessage = mapOf(
                        "action" to "authenticate",
                        "token" to token
                    )
                    val authJson = encodeJson.encodeToString(authMessage)
                    val sent = webSocket.send(authJson)
                    if (!sent) {
                        Log.e(TAG, "Failed to send unified auth payload on open")
                    } else {
                        Log.d(TAG, "Unified authentication message sent")
                    }
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        Log.d(TAG, "Received unified WebSocket message: ${text.take(200)}...")
                        val response = decodeJson.decodeFromString<ChatResponse>(text)
                        
                        // Handle authentication
                        if (response.action == "authenticated") {
                            isAuthenticated = true
                            Log.d(TAG, "Unified WebSocket authenticated successfully")
                            startPing()
                        }
                        
                        Log.d(TAG, "Parsed unified response action: ${response.action}")
                        safeChannelSend(response)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing unified WebSocket message: $text", e)
                        safeChannelSend(
                            ChatResponse(
                                action = "error",
                                error = "Invalid server message format"
                            )
                        )
                    }
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "Unified WebSocket closing: $code $reason")
                    isConnected = false
                    isAuthenticated = false
                    stopPing()
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    Log.e(TAG, "Unified WebSocket failure", t)
                    isConnected = false
                    isAuthenticated = false
                    stopPing()
                    safeChannelSend(
                        ChatResponse(
                            action = "error",
                            error = t.message ?: "Connection failed"
                        )
                    )
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting unified WebSocket", e)
            isConnected = false
            isAuthenticated = false
            safeChannelSend(
                ChatResponse(
                    action = "error",
                    error = e.message ?: "Connection error"
                )
            )
        }
    }
    
    private fun safeChannelSend(response: ChatResponse) {
        try {
            val emitted = messageFlow.tryEmit(response)
            if (!emitted) {
                Log.w(TAG, "Dropping unified message due to backpressure: action=${response.action}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending to channel", e)
        }
    }
    
    /**
     * Send chat message
     */
    fun sendChatMessage(sessionId: String?, content: String): Boolean {
        val message = mutableMapOf<String, String>(
            "type" to "chat",
            "action" to "send_message",
            "content" to content
        )
        
        if (sessionId != null) {
            message["session_id"] = sessionId
        }
        
        return send(message)
    }
    
    /**
     * Send scan AI request
     */
    fun sendScanRequest(organ: String, prompt: String): Boolean {
        val message = mapOf(
            "type" to "scan",
            "action" to "get_explanation", 
            "organ" to organ,
            "prompt" to prompt
        )
        
        return send(message)
    }
    
    /**
     * Create session (works for both chat and scan)
     */
    fun createSession(type: MessageType = MessageType.CHAT): Boolean {
        val message = mapOf(
            "type" to type.name.lowercase(),
            "action" to "create_session"
        )
        
        return send(message)
    }
    
    /**
     * List sessions
     */
    fun listSessions(): Boolean {
        val message = mapOf(
            "action" to "list_sessions"
        )
        
        return send(message)
    }
    
    private fun send(message: Map<String, String>): Boolean {
        try {
            if (!isConnected || webSocket == null) {
                Log.e(TAG, "WebSocket not connected, cannot send message")
                return false
            }
            
            if (!isAuthenticated) {
                Log.e(TAG, "WebSocket not authenticated, cannot send message")
                return false
            }
            
            val jsonString = encodeJson.encodeToString(message)
            val sent = webSocket?.send(jsonString) == true
            if (!sent) {
                Log.e(TAG, "WebSocket send failed (unified). Socket may be closed.")
                safeChannelSend(
                    ChatResponse(
                        action = "error",
                        error = "Failed to send message: websocket not ready"
                    )
                )
            }
            return sent
        } catch (e: Exception) {
            Log.e(TAG, "Error sending unified message", e)
            safeChannelSend(
                ChatResponse(
                    action = "error",
                    error = "Failed to send message: ${e.message ?: "unknown error"}"
                )
            )
            return false
        }
    }
    
    fun isConnected(): Boolean = isConnected && isAuthenticated
    
    fun isWebSocketAuthenticated(): Boolean = isAuthenticated
    
    fun disconnect() {
        try {
            webSocket?.close(1000, "Client disconnect")
            webSocket = null
            isConnected = false
            isAuthenticated = false
            stopPing()
        } catch (e: Exception) {
            Log.e(TAG, "Error during unified disconnect", e)
        }
    }

    private fun startPing() {
        try {
            // cancel any existing job
            pingJob?.cancel()
            pingJob = pingScope.launch {
                while (isConnected && isAuthenticated) {
                    try {
                        val sent = send(mapOf("action" to "ping"))
                        if (!sent) {
                            Log.w(TAG, "Ping send failed")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending ping", e)
                    }
                    delay(5_000L)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ping job", e)
        }
    }

    private fun stopPing() {
        try {
            pingJob?.cancel()
            pingJob = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ping job", e)
        }
    }
}