package com.anatomy.app.services

import android.content.Context
import android.util.Log
import com.anatomy.app.network.ScanWebSocketClient
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
    private var scanWebSocket: ScanWebSocketClient? = null
    private var currentSessionId: String? = null
    
    companion object {
        // Use a separate instance tracker to avoid conflicts with main chatbot
        private var isLLMServiceActive = false
        
        fun isServiceActive(): Boolean = isLLMServiceActive
    }
    
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
            // Important: release /ws/chat so other features (QnA page) can connect reliably.
            disconnect()
        }
    }

    /**
     * Ask a follow-up question about a specific organ in the same chat channel.
     */
    suspend fun askQuestionAboutOrgan(organName: String, question: String): String {
        if (!isValidOrganName(organName)) {
            return "Organ tidak valid untuk pertanyaan lanjutan."
        }
        if (question.isBlank()) {
            return "Pertanyaan masih kosong."
        }

        return try {
            val prompt = "Konteks organ: $organName. " +
                "Jawab pertanyaan berikut secara singkat dan jelas dalam bahasa Indonesia: $question"

            val answer = requestViaWebSocket(prompt)
            if (answer.isBlank()) {
                "Maaf, saya belum bisa menjawab pertanyaan itu saat ini."
            } else {
                answer
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Timeout follow-up question for $organName")
            "Maaf, backend terlalu lama merespons pertanyaan lanjutan."
        } catch (e: Exception) {
            Log.e(TAG, "Error follow-up question for $organName", e)
            "Maaf, terjadi gangguan saat memproses pertanyaan lanjutan."
        } finally {
            // Keep scan LLM channel isolated to avoid interfering with dedicated chat mode.
            disconnect()
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
                
                // Mark service as active
                isLLMServiceActive = true
                
                // Always create fresh WebSocket connection for LLM requests
                // Using dedicated scan endpoint - no conflict with chatbot!
                disconnect()
                scanWebSocket = ScanWebSocketClient(httpClient)
                scanWebSocket?.connect(HttpClientFactory.getBaseUrl(context), token)
                
                // Wait for authentication with shorter timeout
                val authResult = withTimeoutOrNull(10_000L) {
                    scanWebSocket?.messages?.firstOrNull { msg ->
                        msg.action == "authenticated" || msg.error != null
                    }
                }
                
                if (authResult == null) {
                    Log.e(TAG, "WebSocket auth timed out")
                    return@withContext ""
                }
                
                if (authResult.error != null) {
                    Log.e(TAG, "WebSocket auth failed: ${authResult.error}")
                    return@withContext ""
                }
                
                Log.d(TAG, "WebSocket authenticated successfully")
                
                // Create session
                if (!setupSession()) {
                    Log.e(TAG, "Failed to setup session")
                    return@withContext ""
                }
                
                // Send the prompt
                val payload = mutableMapOf(
                    "action" to "send_message",
                    "content" to prompt
                )
                currentSessionId?.let { sid ->
                    payload["session_id"] = sid
                }
                
                val sendResult = scanWebSocket?.send(payload)
                if (sendResult != true) {
                    Log.e(TAG, "Failed to send prompt")
                    return@withContext ""
                }
                
                Log.d(TAG, "Prompt sent via WebSocket, waiting for response...")
                
                // Wait for response with reasonable timeout
                val response = withTimeoutOrNull(25_000L) {
                    scanWebSocket?.messages?.firstOrNull { chatResponse ->
                        chatResponse.action == "chat_response" && !chatResponse.answer.isNullOrBlank()
                    }
                }
                
                if (response == null) {
                    Log.e(TAG, "No response received within timeout")
                    return@withContext ""
                }

                val answer = response.answer?.takeIf { it.isNotBlank() }
                    ?: response.assistant_message
                        ?.jsonObject
                        ?.get("content")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.trim()
                        .orEmpty()
                
                Log.d(TAG, "Received answer: ${answer.take(100)}...")
                return@withContext answer
                
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Timeout in WebSocket request", e)
                ""
            } catch (e: Exception) {
                Log.e(TAG, "Error in WebSocket request", e)
                ""
            } finally {
                // Always mark service as inactive when done
                isLLMServiceActive = false
                // No need to release connection - using dedicated endpoint
            }
        }
    }
    
    /**
     * Setup a new chat session via WebSocket.
     */
    @OptIn(FlowPreview::class)
    private suspend fun setupSession(): Boolean {
        return try {
            val sendResult = scanWebSocket?.send(mapOf("action" to "create_session"))
            if (sendResult != true) {
                Log.e(TAG, "Failed to send create_session")
                return false
            }
            
            // Wait for session_created response
            val sessionResponse = withTimeoutOrNull(8_000L) {
                scanWebSocket?.messages?.firstOrNull { chatResponse ->
                    chatResponse.action == "session_created" && !chatResponse.session_id.isNullOrBlank()
                }
            }
            
            if (sessionResponse == null) {
                Log.e(TAG, "Session creation timed out")
                return false
            }
            
            currentSessionId = sessionResponse.session_id
            Log.d(TAG, "Session created: $currentSessionId")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up session", e)
            false
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
            scanWebSocket?.disconnect()
            scanWebSocket = null
            currentSessionId = null
            isLLMServiceActive = false
            Log.d(TAG, "Disconnected from LLM WebSocket")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
    }
}
