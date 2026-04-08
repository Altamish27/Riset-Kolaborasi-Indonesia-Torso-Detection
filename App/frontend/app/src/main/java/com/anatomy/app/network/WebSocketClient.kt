package com.anatomy.app.network

import android.util.Log
import com.anatomy.app.config.AppConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class ChatWebSocketClient(private val httpClient: OkHttpClient) {
    
    private val TAG = "ChatWebSocketClient"
    private var webSocket: WebSocket? = null
    private val messageChannel = Channel<ChatResponse>(Channel.BUFFERED)
    private val decodeJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val encodeJson = Json { explicitNulls = false }
    
    val messages: Flow<ChatResponse> = messageChannel.receiveAsFlow()
    
    fun connect(baseUrl: String, token: String) {
        try {
            val wsUrl = baseUrl.replace("http", "ws") + AppConfig.getWsChatPathDefault()
            val request = Request.Builder().url(wsUrl).build()
            
            webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    Log.d(TAG, "Chat WebSocket connected")
                    messageChannel.trySend(ChatResponse(action = "connected"))
                    // Immediately authenticate
                    val authMessage = mapOf(
                        "action" to "authenticate",
                        "token" to token
                    )
                    val authJson = encodeJson.encodeToString(authMessage)
                    val sent = webSocket.send(authJson)
                    if (!sent) {
                        Log.e(TAG, "Failed to send chat auth payload on open")
                    }
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val response = decodeJson.decodeFromString<ChatResponse>(text)
                        val result = messageChannel.trySend(response)
                        if (result.isFailure) {
                            Log.e(TAG, "Failed to send message to channel: ${result.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing WebSocket message: $text", e)
                        messageChannel.trySend(
                            ChatResponse(
                                action = "error",
                                error = "Invalid server message format"
                            )
                        )
                    }
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "Chat WebSocket closing: $code $reason")
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    Log.e(TAG, "Chat WebSocket failure", t)
                    val result = messageChannel.trySend(
                        ChatResponse(
                            action = "error",
                            error = t.message ?: "Connection failed"
                        )
                    )
                    if (result.isFailure) {
                        Log.e(TAG, "Failed to send error message", result.exceptionOrNull())
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting WebSocket", e)
            val result = messageChannel.trySend(
                ChatResponse(
                    action = "error",
                    error = e.message ?: "Connection error"
                )
            )
            if (result.isFailure) {
                Log.e(TAG, "Failed to send error", result.exceptionOrNull())
            }
        }
    }
    
    fun send(message: Map<String, String>): Boolean {
        try {
            val jsonString = encodeJson.encodeToString(message)
            val sent = webSocket?.send(jsonString) == true
            if (!sent) {
                Log.e(TAG, "WebSocket send failed (chat). Socket may be closed.")
                messageChannel.trySend(
                    ChatResponse(
                        action = "error",
                        error = "Failed to send message: websocket not ready"
                    )
                )
            }
            return sent
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            messageChannel.trySend(
                ChatResponse(
                    action = "error",
                    error = "Failed to send message: ${e.message ?: "unknown error"}"
                )
            )
            return false
        }
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        messageChannel.close()
    }
}

class VoiceWebSocketClient(private val httpClient: OkHttpClient) {
    
    private val TAG = "VoiceWebSocketClient"
    private var webSocket: WebSocket? = null
    private val messageChannel = Channel<ChatResponse>(Channel.BUFFERED)
    private val decodeJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val encodeJson = Json { explicitNulls = false }
    
    val messages: Flow<ChatResponse> = messageChannel.receiveAsFlow()
    
    fun connect(baseUrl: String, token: String) {
        try {
            val wsUrl = baseUrl.replace("http", "ws") + AppConfig.getWsVoicePathDefault()
            val request = Request.Builder().url(wsUrl).build()
            
            webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    Log.d(TAG, "Voice WebSocket connected")
                    messageChannel.trySend(ChatResponse(action = "connected"))
                    // Immediately authenticate
                    val authMessage = mapOf(
                        "action" to "authenticate",
                        "token" to token
                    )
                    val authJson = encodeJson.encodeToString(authMessage)
                    val sent = webSocket.send(authJson)
                    if (!sent) {
                        Log.e(TAG, "Failed to send voice auth payload on open")
                    }
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val response = decodeJson.decodeFromString<ChatResponse>(text)
                        val result = messageChannel.trySend(response)
                        if (result.isFailure) {
                            Log.e(TAG, "Failed to send message to channel: ${result.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing WebSocket message: $text", e)
                        messageChannel.trySend(
                            ChatResponse(
                                action = "error",
                                error = "Invalid server message format"
                            )
                        )
                    }
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "Voice WebSocket closing: $code $reason")
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    Log.e(TAG, "Voice WebSocket failure", t)
                    val result = messageChannel.trySend(
                        ChatResponse(
                            action = "error",
                            error = t.message ?: "Connection failed"
                        )
                    )
                    if (result.isFailure) {
                        Log.e(TAG, "Failed to send error message", result.exceptionOrNull())
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting WebSocket", e)
            val result = messageChannel.trySend(
                ChatResponse(
                    action = "error",
                    error = e.message ?: "Connection error"
                )
            )
            if (result.isFailure) {
                Log.e(TAG, "Failed to send error", result.exceptionOrNull())
            }
        }
    }
    
    fun sendJson(message: Map<String, String>): Boolean {
        try {
            val jsonString = encodeJson.encodeToString(message)
            val sent = webSocket?.send(jsonString) == true
            if (!sent) {
                Log.e(TAG, "WebSocket send failed (voice json). Socket may be closed.")
                messageChannel.trySend(
                    ChatResponse(
                        action = "error",
                        error = "Failed to send voice json: websocket not ready"
                    )
                )
            }
            return sent
        } catch (e: Exception) {
            Log.e(TAG, "Error sending JSON message", e)
            messageChannel.trySend(
                ChatResponse(
                    action = "error",
                    error = "Failed to send voice json: ${e.message ?: "unknown error"}"
                )
            )
            return false
        }
    }
    
    fun sendAudio(audioData: ByteArray): Boolean {
        try {
            // Convert byte array to hex string for transmission
            val hexString = audioData.joinToString("") { "%02x".format(it) }
            val sent = webSocket?.send(hexString) == true
            if (!sent) {
                Log.e(TAG, "WebSocket send failed (voice audio). Socket may be closed.")
            }
            return sent
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio data", e)
            return false
        }
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        messageChannel.close()
    }
}
