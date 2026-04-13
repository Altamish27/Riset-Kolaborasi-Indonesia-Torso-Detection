package com.anatomy.app.utils

import android.content.Context
import android.util.Log
import com.anatomy.app.network.ChatResponse
import com.anatomy.app.network.HttpClientFactory
import com.anatomy.app.network.UnifiedWebSocketClient
import kotlinx.coroutines.flow.Flow

/**
 * UnifiedWebSocketManager - Global WebSocket connection manager
 * 
 * Manages single persistent WebSocket connection for entire app lifecycle
 * Connect once on login, use throughout app session
 */
object UnifiedWebSocketManager {
    
    private val TAG = "UnifiedWebSocketManager"
    private var unifiedClient: UnifiedWebSocketClient? = null
    private val httpClient = HttpClientFactory.createHttpClient()
    
    /**
     * Connect to unified WebSocket (call this on login)
     */
    fun connect(context: Context, token: String): Flow<ChatResponse>? {
        return try {
            if (unifiedClient?.isConnected() == true) {
                Log.d(TAG, "Already connected to unified WebSocket")
                return unifiedClient?.messages
            }
            
            Log.d(TAG, "Connecting to unified WebSocket...")
            unifiedClient = UnifiedWebSocketClient(httpClient)
            unifiedClient?.connect(HttpClientFactory.getBaseUrl(context), token)
            unifiedClient?.messages
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to unified WebSocket", e)
            null
        }
    }
    
    /**
     * Send chat message
     */
    fun sendChatMessage(sessionId: String?, content: String): Boolean {
        return unifiedClient?.sendChatMessage(sessionId, content) ?: false
    }
    
    /**
     * Send scan AI request
     */
    fun sendScanRequest(organ: String, prompt: String): Boolean {
        return unifiedClient?.sendScanRequest(organ, prompt) ?: false
    }
    
    /**
     * Create session
     */
    fun createSession(type: UnifiedWebSocketClient.MessageType = UnifiedWebSocketClient.MessageType.CHAT): Boolean {
        return unifiedClient?.createSession(type) ?: false
    }
    
    /**
     * List sessions
     */
    fun listSessions(): Boolean {
        return unifiedClient?.listSessions() ?: false
    }
    
    /**
     * Check if connected
     */
    fun isConnected(): Boolean {
        return unifiedClient?.isConnected() == true
    }
    
    /**
     * Get message flow
     */
    fun getMessages(): Flow<ChatResponse>? {
        return unifiedClient?.messages
    }
    
    /**
     * Disconnect (call this on logout)
     */
    fun disconnect() {
        try {
            unifiedClient?.disconnect()
            unifiedClient = null
            Log.d(TAG, "Disconnected from unified WebSocket")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting unified WebSocket", e)
        }
    }
}