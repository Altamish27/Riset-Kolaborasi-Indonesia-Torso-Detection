package com.anatomy.app.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Auth Models
 */
@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val message: String? = null,
    val access_token: String? = null,
    val refresh_token: String? = null,
    val token_type: String? = null
)

@Serializable
data class RefreshRequest(
    val refresh_token: String
)

@Serializable
data class SessionResponse(
    val session_id: String
)

@Serializable
data class SessionHistoryMessage(
    val role: String,
    val content: String,
    val timestamp: String? = null
)

@Serializable
data class SessionHistoryResponse(
    val session_id: String,
    val messages: List<SessionHistoryMessage> = emptyList()
)

/**
 * Chat Models
 */
@Serializable
data class ChatMessage(
    val action: String,
    val token: String? = null,
    val content: String? = null,
    val session_id: String? = null
)

@Serializable
data class ChatResponse(
    val action: String? = null,
    val username: String? = null,
    val session_id: String? = null,
    val message: String? = null,
    val thinking: String? = null,
    val thoughts: String? = null,
    val answer: String? = null,
    val user_message: JsonElement? = null,
    val assistant_message: JsonElement? = null,
    val sessions: List<JsonElement>? = null,
    val messages: List<JsonElement>? = null,
    val error: String? = null,
    val game_data: JsonElement? = null
)

/**
 * Document Ingestion Models
 */
@Serializable
data class IngestMetadata(
    val category: String,
    val kelas_akademik: Int,
    val title: String,
    val description: String
)

@Serializable
data class GenerateQuizResponse(
    val session_id: String,
    val quiz: QuizGameData
)

/**
 * LLM explanations now use WebSocket /ws/chat endpoint
 * No REST endpoint needed - handled by ChatWebSocketClient
 */
