package com.anatomy.app.services

import android.content.Context
import android.util.Log
import com.anatomy.app.network.ChatWebSocketClient
import com.anatomy.app.network.HttpClientFactory
import com.anatomy.app.utils.TokenManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * LLMService - Manages communication with backend LLM for organ explanations.
 *
 * Uses WebSocket /ws/chat endpoint (already deployed in backend).
 * Flow: Connect → Authenticate → Create Session → Send prompt → Get explanation
 *
 * This service is responsible for:
 * 1. Connecting to backend chat WebSocket
 * 2. Sending organ name as a chat prompt via LLM
 * 3. Receiving AI-generated explanations
 * 4. Handling errors gracefully
 *
 * All explanations come from the deployed backend LLM - no hardcoded content.
 */
class LLMService(private val context: Context) {
    
    private val TAG = "LLMService"
    private val httpClient = HttpClientFactory.createHttpClient()
    private var chatWebSocket: ChatWebSocketClient? = null
    private var currentSessionId: String? = null
    
    /**
     * Get AI explanation for an organ from backend LLM via WebSocket.
     *
     * @param organName The name of the organ (e.g., "Jantung", "Paru-paru")
     * @return Explanation text from AI, or error message if request fails
     */
    suspend fun getExplanationText(organName: String): String {
        if (!isValidOrganName(organName)) {
            Log.e(TAG, "Invalid organ name: $organName")
            return "Organ tidak valid untuk penjelasan."
        }

        return try {
            // Build a natural prompt for the LLM
            val prompt = "Jelaskan organ $organName secara singkat, maksimal 3-4 kalimat. " +
                    "Fokus pada fungsi utama organ ini dalam bahasa Indonesia yang mudah dipahami."
            
            Log.d(TAG, "Requesting LLM explanation for: $organName")
            
            // Connect and get explanation via WebSocket
            val explanation = requestViaWebSocket(prompt)
            
            if (explanation.isBlank()) {
                Log.e(TAG, "Empty explanation received for $organName")
                "Penjelasan tidak tersedia untuk $organName."
            } else {
                Log.d(TAG, "LLM explanation received: ${explanation.take(50)}...")
                explanation
            }
            
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Timeout waiting for LLM response for $organName")
            "Maaf, backend terlalu lama merespons. Coba lagi."
        } catch (e: Exception) {
            Log.e(TAG, "Error getting LLM explanation for $organName", e)
            "Maaf, tidak dapat mendapatkan penjelasan saat ini."
        } finally {
            // Optionally disconnect after getting explanation
            // (can keep connection open for performance)
        }
    }
    
    /**
     * Send request via WebSocket chat and wait for response.
     * 
     * @param prompt The prompt to send to LLM
     * @return The assistant's answer/explanation
     */
    @OptIn(FlowPreview::class)
    private suspend fun requestViaWebSocket(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Get token
                val token = TokenManager.getAccessToken(context)
                if (token == null) {
                    Log.e(TAG, "No access token available")
                    return@withContext ""
                }
                
                // Create or reuse WebSocket connection
                if (chatWebSocket == null) {
                    chatWebSocket = ChatWebSocketClient(httpClient)
                    chatWebSocket?.connect(HttpClientFactory.getBaseUrl(context), token)
                    val authResult = withTimeoutOrNull(12_000L) {
                        chatWebSocket?.messages?.firstOrNull { msg ->
                            msg.action == "authenticated" || msg.error != null
                        }
                    }
                    if (authResult == null || authResult.error != null) {
                        Log.e(TAG, "WebSocket auth failed or timed out: ${authResult?.error}")
                        return@withContext ""
                    }
                }
                
                // Create session if needed
                if (currentSessionId == null) {
                    setupSession()
                }
                
                // Send the prompt
                val payload = mutableMapOf(
                    "action" to "send_message",
                    "content" to prompt
                )
                currentSessionId?.let { sid ->
                    payload["session_id"] = sid
                }
                chatWebSocket?.send(payload)
                
                Log.d(TAG, "Prompt sent via WebSocket")
                
                // Wait for response (max 30 seconds)
                val response = withTimeoutOrNull(30_000L) {
                    chatWebSocket?.messages?.firstOrNull { chatResponse ->
                        chatResponse.action == "chat_response"
                    }
                } ?: return@withContext ""

                response.answer?.takeIf { it.isNotBlank() }
                    ?: response.assistant_message
                        ?.jsonObject
                        ?.get("content")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.trim()
                        .orEmpty()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in WebSocket request", e)
                ""
            }
        }
    }
    
    /**
     * Setup a new chat session via WebSocket.
     */
    @OptIn(FlowPreview::class)
    private suspend fun setupSession() {
        try {
            chatWebSocket?.send(mapOf("action" to "create_session"))
            
            // Wait for session_created response
            val sessionResponse = withTimeoutOrNull(10_000L) {
                chatWebSocket?.messages?.firstOrNull { chatResponse ->
                    chatResponse.action == "session_created"
                }
            } ?: return
            
            currentSessionId = sessionResponse.session_id
            Log.d(TAG, "Session created: $currentSessionId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up session", e)
        }
    }
    
    /**
     * Validate organ name is suitable for LLM query.
     * 
     * @param organName Name to validate
     * @return true if organ name is valid, false otherwise
     */
    fun isValidOrganName(organName: String): Boolean {
        return organName.isNotBlank() && 
               organName.length > 1 && 
               organName.length < 100 &&
               !organName.contains(Regex("[0-9!@#$%^&*()]"))
    }
    
    /**
     * Disconnect from WebSocket (optional cleanup).
     */
    fun disconnect() {
        try {
            chatWebSocket?.disconnect()
            chatWebSocket = null
            currentSessionId = null
            Log.d(TAG, "Disconnected from LLM WebSocket")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
    }
}
