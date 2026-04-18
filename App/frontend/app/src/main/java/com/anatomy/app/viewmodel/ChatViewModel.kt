package com.anatomy.app.viewmodel

import com.anatomy.app.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatUiMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: String? = null
)

data class ChatSessionItem(
    val sessionId: String,
    val title: String,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class ChatUiState(
    val sessionId: String? = null,
    val chatMessages: List<ChatUiMessage> = emptyList(),
    val sessions: List<ChatSessionItem> = emptyList(),
    val isLoadingSessions: Boolean = false,
    val hasLoadedHistory: Boolean = false,
    val autoListenRequested: Boolean = false
)

class ChatViewModel(
    private val chatRepository: ChatRepository
) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    suspend fun ensureSessionId(forceNew: Boolean = false): String? {
        val sessionId = chatRepository.getOrCreateSessionId(forceNew)
        _uiState.value = _uiState.value.copy(sessionId = sessionId)
        return sessionId
    }

    suspend fun loadSessions() {
        _uiState.value = _uiState.value.copy(isLoadingSessions = true)
        val sessions = chatRepository.fetchSessions()
            .sortedByDescending { it.updated_at ?: it.created_at ?: "" }
            .map {
                ChatSessionItem(
                    sessionId = it.session_id,
                    title = it.title?.takeIf { title -> title.isNotBlank() }
                        ?: "Sesi ${it.session_id.takeLast(6)}",
                    createdAt = it.created_at,
                    updatedAt = it.updated_at
                )
            }

        _uiState.value = _uiState.value.copy(
            sessions = sessions,
            isLoadingSessions = false
        )
    }

    suspend fun resumeLatestSessionOrCreate(): String? {
        val sessionId = chatRepository.resumeLastSessionIdOrCreate()
        _uiState.value = _uiState.value.copy(
            sessionId = sessionId,
            hasLoadedHistory = false
        )
        loadSessions()
        return sessionId
    }

    suspend fun switchToSession(sessionId: String) {
        chatRepository.setActiveSessionId(sessionId)
        _uiState.value = _uiState.value.copy(
            sessionId = sessionId,
            hasLoadedHistory = false
        )
        loadHistoryForCurrentSession(forceReload = true)
        loadSessions()
    }

    suspend fun createNewSession(): String? {
        val newSessionId = chatRepository.createSession()
        if (newSessionId != null) {
            _uiState.value = _uiState.value.copy(
                sessionId = newSessionId,
                chatMessages = emptyList(),
                hasLoadedHistory = true
            )
            loadSessions()
        }
        return newSessionId
    }

    suspend fun loadHistoryForCurrentSession(forceReload: Boolean = false) {
        val currentState = _uiState.value
        val sessionId = currentState.sessionId ?: ensureSessionId() ?: return

        if (!forceReload && currentState.hasLoadedHistory) {
            return
        }

        val history = chatRepository.getSessionHistory(sessionId)
        val mapped = history.map {
            ChatUiMessage(
                text = it.content,
                isUser = it.role.equals("user", ignoreCase = true),
                timestamp = it.timestamp
            )
        }

        _uiState.value = _uiState.value.copy(
            sessionId = sessionId,
            chatMessages = mapped,
            hasLoadedHistory = true
        )
    }

    fun appendUserMessage(text: String) {
        val updated = _uiState.value.chatMessages + ChatUiMessage(
            text = text,
            isUser = true
        )
        _uiState.value = _uiState.value.copy(chatMessages = updated)
    }

    fun appendAssistantMessage(text: String) {
        val updated = _uiState.value.chatMessages + ChatUiMessage(
            text = text,
            isUser = false
        )
        _uiState.value = _uiState.value.copy(chatMessages = updated)
    }

    fun requestAutoListen() {
        _uiState.value = _uiState.value.copy(autoListenRequested = true)
    }

    fun clearAutoListenRequest() {
        _uiState.value = _uiState.value.copy(autoListenRequested = false)
    }

    fun markHistoryNeedsReload() {
        _uiState.value = _uiState.value.copy(hasLoadedHistory = false)
    }

    fun clearSessionState() {
        _uiState.value = ChatUiState()
    }
}
