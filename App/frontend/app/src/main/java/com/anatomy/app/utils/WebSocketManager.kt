package com.anatomy.app.utils

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * WebSocketManager - Manages WebSocket connections to prevent conflicts
 * between different features (Scan AI vs Chatbot)
 */
object WebSocketManager {
    
    private val TAG = "WebSocketManager"
    private val mutex = Mutex()
    
    enum class ConnectionType {
        SCAN_AI,
        CHATBOT,
        VOICE
    }
    
    private var activeConnection: ConnectionType? = null
    private var connectionOwner: String? = null
    
    /**
     * Request exclusive access to WebSocket connection
     */
    suspend fun requestConnection(type: ConnectionType, owner: String): Boolean {
        return mutex.withLock {
            when {
                activeConnection == null -> {
                    activeConnection = type
                    connectionOwner = owner
                    Log.d(TAG, "Connection granted to $owner for $type")
                    true
                }
                activeConnection == type && connectionOwner == owner -> {
                    Log.d(TAG, "Connection already owned by $owner for $type")
                    true
                }
                else -> {
                    Log.w(TAG, "Connection denied to $owner for $type. Currently owned by $connectionOwner for $activeConnection")
                    false
                }
            }
        }
    }
    
    /**
     * Release WebSocket connection
     */
    suspend fun releaseConnection(owner: String) {
        mutex.withLock {
            if (connectionOwner == owner) {
                Log.d(TAG, "Connection released by $owner (was $activeConnection)")
                activeConnection = null
                connectionOwner = null
            } else {
                Log.w(TAG, "Release request from $owner ignored. Current owner: $connectionOwner")
            }
        }
    }
    
    /**
     * Force release connection (for cleanup)
     */
    suspend fun forceRelease() {
        mutex.withLock {
            Log.d(TAG, "Force releasing connection (was owned by $connectionOwner for $activeConnection)")
            activeConnection = null
            connectionOwner = null
        }
    }
    
    /**
     * Check if connection is available for specific type
     */
    suspend fun isConnectionAvailable(type: ConnectionType): Boolean {
        return mutex.withLock {
            activeConnection == null || activeConnection == type
        }
    }
    
    /**
     * Get current connection info
     */
    suspend fun getConnectionInfo(): Pair<ConnectionType?, String?> {
        return mutex.withLock {
            Pair(activeConnection, connectionOwner)
        }
    }
}