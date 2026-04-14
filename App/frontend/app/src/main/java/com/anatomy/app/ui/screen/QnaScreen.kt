package com.anatomy.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anatomy.app.helper.AudioAssistant
import com.anatomy.app.helper.HapticHelper
import com.anatomy.app.helper.VoiceRecognitionHelper
import com.anatomy.app.network.ChatResponse
import com.anatomy.app.repository.ChatRepository
import com.anatomy.app.utils.TokenManager
import com.anatomy.app.utils.UnifiedWebSocketManager
import com.anatomy.app.ui.theme.MicActive
import com.anatomy.app.ui.theme.MicIdle
import com.anatomy.app.ui.theme.NeonAmber
import com.anatomy.app.ui.theme.NeonCyan
import com.anatomy.app.ui.theme.NeonGreen
import com.anatomy.app.ui.theme.SurfaceCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import android.util.Log
/**
 * ChatMessage — represents one bubble in the conversation.
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Page 2 — "Mode Tanya Jawab"
 *
 * STABILITY FIXES:
 *   1. Auto-listen triggers via LaunchedEffect(isActive) with a delay to ensure
 *      the page TTS finishes, then directly starts mic (no frozen state).
 *   2. stopListening() is always called before starting a new session.
 *   3. Mic button is integrated into the bottom input bar (no overlap with FAB).
 *   4. Speaker toggle is handled by the global FAB in MainPagerScreen (top-level).
 */
@Composable
fun QnaScreen(isActive: Boolean = true) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val chatRepository = remember { ChatRepository(context) }
    val keyboardController = LocalSoftwareKeyboardController.current

    var isListening by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Siap mendengarkan") }
    var textInput by remember { mutableStateOf("") }
    var isAuthenticated by remember { mutableStateOf(false) }
    var isSessionCreating by remember { mutableStateOf(false) }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var pendingQuestion by remember { mutableStateOf<String?>(null) }
    var lastSessionRequestAt by remember { mutableStateOf(0L) }
    var sessionCreateAttempts by remember { mutableStateOf(0) }
    var reconnectNonce by remember { mutableStateOf(0) }
    var reconnectCycles by remember { mutableStateOf(0) }
    var authReconnectAttempts by remember { mutableStateOf(0) }
    var connectionTimeout by remember { mutableStateOf(false) }
    var processingStartedAt by remember { mutableStateOf(0L) }
    val chatHistory = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()

    val voiceHelper = remember { VoiceRecognitionHelper(context) }

    fun requestSessionIfNeeded(force: Boolean = false) {
        if (!isAuthenticated) return
        if (!sessionId.isNullOrBlank()) return

        val now = System.currentTimeMillis()
        if (!force && now - lastSessionRequestAt < 1500L) return

        lastSessionRequestAt = now
        isSessionCreating = true
        sessionCreateAttempts += 1
        statusText = "Menyiapkan sesi chat..."

        // Create session via HTTP asynchronously
        coroutineScope.launch {
            val newSession = try {
                chatRepository.createSession()
            } catch (e: Exception) {
                null
            }

            if (newSession.isNullOrBlank()) {
                statusText = "Koneksi chat belum siap, mencoba sambung ulang..."
                isSessionCreating = false
                reconnectNonce += 1
            } else {
                sessionId = newSession
                isSessionCreating = false
                sessionCreateAttempts = 0
                statusText = "Sesi chat siap"
                val queued = pendingQuestion
                if (!queued.isNullOrBlank()) {
                    pendingQuestion = null
                    statusText = "Mengirim pertanyaan ke backend..."
                    val sent = chatRepository.sendChatMessage(sessionId, queued)
                    if (!sent) {
                        pendingQuestion = queued
                        statusText = "Koneksi chat terputus, menyambung ulang..."
                        reconnectNonce += 1
                    }
                }
            }
        }
    }

    fun failCurrentRequest(message: String) {
        isProcessing = false
        statusText = message
    }

    fun sendQuestionToBackend(question: String) {
        if (!isAuthenticated) {
            pendingQuestion = question
            failCurrentRequest("Menghubungkan ke backend...")
            return
        }

        if (sessionId.isNullOrBlank()) {
            pendingQuestion = question
            requestSessionIfNeeded(force = true)
            return
        }
        val sent = chatRepository.sendChatMessage(sessionId, question)
        if (!sent) {
            pendingQuestion = question
            failCurrentRequest("Koneksi chat terputus, menyambung ulang...")
            reconnectNonce += 1
        }
    }

    /** Start the mic with proper cleanup. */
    fun doStartListening() {
        if (isProcessing || !isActive) return

        try {
            isListening = true
            HapticHelper.shortBuzz()
            statusText = "🎤 Mendengarkan..."

            voiceHelper.startListening(
                onResult = { result ->
                    if (!isActive) return@startListening
                    isListening = false
                    if (result.isBlank()) {
                        if (isActive) statusText = "Tidak terdengar. Tekan mic untuk coba lagi."
                        return@startListening
                    }
                    // Process the recognized question
                    isProcessing = true
                    processingStartedAt = System.currentTimeMillis()
                    if (isActive) statusText = "Memproses..."
                    chatHistory.add(ChatMessage(text = result, isUser = true))

                    coroutineScope.launch {
                        if (isActive) {
                            listState.animateScrollToItem((chatHistory.size - 1).coerceAtLeast(0))
                            sendQuestionToBackend(result)
                        }
                    }
                },
                onError = { code ->
                    if (!isActive) return@startListening
                    isListening = false
                    Log.e("QnaScreen", "Voice recognition error code: $code")
                    if (isActive) statusText = "Gagal mendengar (kode: $code). Coba lagi atau ketik."
                }
            )
        } catch (e: Exception) {
            Log.e("QnaScreen", "Exception in doStartListening", e)
            isListening = false
            statusText = "Error mikrofon. Coba lagi atau ketik pertanyaan."
        }
    }

    /** Process a typed question. */
    fun processTypedQuestion(question: String) {
        if (question.isBlank() || isProcessing) return

        try {
            // Stop mic if active so it doesn't interfere
            if (isListening) {
                voiceHelper.stopListening()
                isListening = false
            }

            isProcessing = true
            processingStartedAt = System.currentTimeMillis()
            statusText = "Memproses..."
            chatHistory.add(ChatMessage(text = question, isUser = true))

            coroutineScope.launch {
                listState.animateScrollToItem((chatHistory.size - 1).coerceAtLeast(0))
                sendQuestionToBackend(question)
            }
        } catch (e: Exception) {
            Log.e("QnaScreen", "Exception in processTypedQuestion", e)
            isProcessing = false
            statusText = "Error memproses pertanyaan. Coba lagi."
        }
    }

    fun stopMic() {
        try {
            voiceHelper.stopListening()
        } catch (e: Exception) {
            Log.e("QnaScreen", "Error stopping mic", e)
        }
        isListening = false
        statusText = "Mikrofon mati"
        AudioAssistant.onUtteranceCompleted = null
    }

    LaunchedEffect(isActive, reconnectNonce) {
        if (!isActive) {
            try {
                chatRepository.disconnectChat()
            } catch (e: Exception) {
                Log.e("QnaScreen", "Error disconnecting chat", e)
            }
            isAuthenticated = false
            isSessionCreating = false
            sessionId = null
            sessionCreateAttempts = 0
            reconnectCycles = 0
            authReconnectAttempts = 0
            isProcessing = false
            return@LaunchedEffect
        }

        statusText = "Menghubungkan ke backend..."
        val flow = try {
            chatRepository.connectChat()
        } catch (e: Exception) {
            Log.e("QnaScreen", "Error connecting to chat", e)
            statusText = "Gagal menghubungkan ke backend. Coba lagi."
            null
        }
        
        if (flow == null) {
            statusText = "Token login tidak ditemukan. Silakan login ulang."
            return@LaunchedEffect
        }
        
        // If WebSocket was already authenticated before we subscribed to the flow,
        // set isAuthenticated immediately so UI doesn't wait
        if (UnifiedWebSocketManager.isAuthenticated()) {
            isAuthenticated = true
            Log.d("QnaScreen", "WebSocket already authenticated, setting isAuthenticated=true")
            requestSessionIfNeeded(force = true)
        }

        try {
            // Add timeout for connection
            withTimeoutOrNull(30_000L) {
                flow.collect { response ->
                    when {
                        response.error != null -> {
                            try {
                                if (response.error.contains("Not authenticated", ignoreCase = true)) {
                                    failCurrentRequest("Menghubungkan ulang ke backend...")
                                    isAuthenticated = false
                                    isSessionCreating = false
                                    sessionId = null
                                    if (authReconnectAttempts < 3) {
                                        authReconnectAttempts += 1
                                        reconnectNonce += 1
                                    } else {
                                        failCurrentRequest("Sesi autentikasi bermasalah. Silakan login ulang.")
                                    }
                                    return@collect
                                }
                                if (response.error.contains("Invalid or expired token", ignoreCase = true)) {
                                    isAuthenticated = false
                                    failCurrentRequest("Sesi login expired. Silakan login ulang.")
                                    return@collect
                                }

                                // If send_message failed due missing session, request a fresh session and retry queued question.
                                if (response.error.contains("session_id and content are required", ignoreCase = true)) {
                                    failCurrentRequest("Sesi chat tidak valid. Menyiapkan sesi baru...")
                                    requestSessionIfNeeded(force = true)
                                    return@collect
                                }

                                failCurrentRequest("Error backend: ${response.error}")
                            } catch (e: Exception) {
                                Log.e("QnaScreen", "Error handling error response", e)
                                failCurrentRequest("Terjadi kesalahan sistem")
                            }
                        }

                    // Some backend builds send chat payload with assistant_message but null action.
                    (response.action == "chat_response") ||
                        (response.assistant_message != null) -> {
                        try {
                            Log.d("QnaScreen", "chat_response handler: raw response=${response.toString().take(500)}")
                            val answer = extractAssistantAnswer(response)
                            Log.d("QnaScreen", "Extracted answer='$answer' isBlank=${answer.isBlank()}")

                            if (answer.isBlank()) {
                                isProcessing = false
                                statusText = "Respons backend kosong."
                                Log.d("QnaScreen", "Answer is blank, setting status")
                                return@collect
                            }

                            Log.d("QnaScreen", "Adding to chatHistory: $answer")
                            chatHistory.add(ChatMessage(text = answer, isUser = false))
                            Log.d("QnaScreen", "chatHistory size now: ${chatHistory.size}")

                            coroutineScope.launch {
                                try {
                                    listState.animateScrollToItem((chatHistory.size - 1).coerceAtLeast(0))
                                } catch (e: Exception) {
                                    Log.e("QnaScreen", "Error scrolling to item", e)
                                }
                            }

                            isProcessing = false
                            statusText = "Menjawab via suara..."

                            try {
                                Log.d("QnaScreen", "Speaking answer: ${answer.take(100)}")
                                AudioAssistant.speak(answer)

                                // Set callback for when speech completes
                                AudioAssistant.onUtteranceCompleted = {
                                    if (isActive) {
                                        Log.d("QnaScreen", "Speech completed via callback")
                                        statusText = "Siap. Tekan mic untuk bertanya lagi."
                                    }
                                }

                                // Timeout fallback: if TTS takes too long or callback doesn't fire,
                                // auto-transition after 15 seconds
                                coroutineScope.launch {
                                    delay(15_000L)
                                    if (isActive && statusText == "Menjawab via suara...") {
                                        Log.d("QnaScreen", "TTS timeout fallback: transitioning to ready")
                                        statusText = "Siap. Tekan mic untuk bertanya lagi."
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("QnaScreen", "Error speaking answer", e)
                                statusText = "Siap. Tekan mic untuk bertanya lagi."
                            }
                        } catch (e: Exception) {
                            Log.e("QnaScreen", "Error processing chat response", e)
                            isProcessing = false
                            statusText = "Error memproses jawaban"
                        }
                    }

                    response.action == "authenticated" -> {
                        isAuthenticated = true
                        authReconnectAttempts = 0
                        reconnectCycles = 0
                        requestSessionIfNeeded(force = true)
                        statusText = "Autentikasi berhasil, membuat sesi..."
                    }

                    response.action == "connected" -> {
                        statusText = "Terhubung ke backend, autentikasi..."
                    }

                    response.action == "session_created" -> {
                        sessionId = response.session_id
                        isSessionCreating = false
                        sessionCreateAttempts = 0
                        val queued = pendingQuestion
                        if (!queued.isNullOrBlank()) {
                            pendingQuestion = null
                            statusText = "Mengirim pertanyaan ke backend..."
                            val sent = chatRepository.sendChatMessage(sessionId, queued)
                            if (!sent) {
                                pendingQuestion = queued
                                statusText = "Koneksi chat terputus, menyambung ulang..."
                                reconnectNonce += 1
                            }
                        } else {
                            statusText = "Siap mendengarkan"
                        }
                    }

                    response.action == "sessions_list" -> {
                        val existingSessionId = response.sessions
                            ?.asSequence()
                            ?.mapNotNull { element ->
                                runCatching {
                                    element.jsonObject["session_id"]?.jsonPrimitive?.contentOrNull
                                }.getOrNull()
                            }
                            ?.firstOrNull { !it.isNullOrBlank() }

                        if (!existingSessionId.isNullOrBlank()) {
                            sessionId = existingSessionId
                            isSessionCreating = false
                            sessionCreateAttempts = 0

                            val queued = pendingQuestion
                            if (!queued.isNullOrBlank()) {
                                pendingQuestion = null
                                statusText = "Mengirim pertanyaan ke backend..."
                                val sent = chatRepository.sendChatMessage(sessionId, queued)
                                if (!sent) {
                                    pendingQuestion = queued
                                    statusText = "Koneksi chat terputus, menyambung ulang..."
                                    reconnectNonce += 1
                                }
                            } else {
                                statusText = "Siap. Tekan mic untuk mulai."
                            }
                        } else if (isAuthenticated && sessionId.isNullOrBlank()) {
                            requestSessionIfNeeded(force = true)
                        }
                    }

            }
                }
            } ?: run {
                // Timeout occurred
                Log.e("QnaScreen", "WebSocket connection timeout")
                isProcessing = false
                isSessionCreating = false
                connectionTimeout = true
                statusText = "Koneksi timeout. Mencoba ulang..."
                delay(1000L)
                reconnectNonce += 1
            }
        } catch (e: Exception) {
            isProcessing = false
            isSessionCreating = false
            statusText = "Koneksi chat bermasalah. Coba buka ulang halaman."
        }
    }

    // Hard timeout so UI cannot remain in processing forever.
    LaunchedEffect(isProcessing, processingStartedAt) {
        if (!isProcessing || processingStartedAt <= 0L) return@LaunchedEffect

        val started = processingStartedAt
        delay(35_000L)

        if (isProcessing && processingStartedAt == started) {
            failCurrentRequest("Respons backend terlalu lama. Coba kirim lagi.")
        }
    }

    // Auto-retry for connection timeout
    LaunchedEffect(connectionTimeout, reconnectNonce) {
        if (!isActive || !connectionTimeout) return@LaunchedEffect
        
        delay(3000L) // Wait before retry
        connectionTimeout = false
        
        if (reconnectCycles < 3) {
            statusText = "Mencoba koneksi ulang... (${reconnectCycles + 1}/3)"
            reconnectCycles += 1
            reconnectNonce += 1
        } else {
            statusText = "Gagal terhubung setelah beberapa percobaan. Periksa koneksi internet."
        }
    }

    LaunchedEffect(isAuthenticated, isSessionCreating, sessionId, isActive, sessionCreateAttempts, reconnectCycles) {
        if (!isActive || !isAuthenticated || !isSessionCreating || !sessionId.isNullOrBlank()) return@LaunchedEffect

        if (sessionCreateAttempts >= 3 && reconnectCycles < 1) {
            statusText = "Menyegarkan koneksi chat..."
            reconnectCycles += 1
                        chatRepository.disconnectChat()
            isAuthenticated = false
            isSessionCreating = false
            sessionId = null
            lastSessionRequestAt = 0L
            sessionCreateAttempts = 0
            reconnectNonce += 1
            return@LaunchedEffect
        }

        if (sessionCreateAttempts >= 5) {
            isSessionCreating = false
            isProcessing = false
            statusText = "Gagal membuat sesi chat. Coba kembali ke halaman ini atau login ulang."
            return@LaunchedEffect
        }

        delay(2500)

        if (isActive && isAuthenticated && isSessionCreating && sessionId.isNullOrBlank()) {
            statusText = "Menyiapkan sesi chat... (${sessionCreateAttempts + 1}/5)"
            requestSessionIfNeeded(force = true)
        }
    }

    // Keep mic fully manual when page becomes active.
    LaunchedEffect(isActive) {
        if (isActive) {
            AudioAssistant.onUtteranceCompleted = {
                if (isActive && !isProcessing && !isListening) {
                    statusText = "Siap. Tekan mic untuk mulai."
                }
            }
            if (!isProcessing && !isListening) {
                statusText = "Siap. Tekan mic untuk mulai."
            }
        } else {
            // Page is no longer active → STOP everything immediately
            stopMic()
        }
    }

    // Cleanup on page deactivation
    DisposableEffect(isActive) {
        onDispose {
            if (!isActive) {
            try {
                chatRepository.disconnectChat()
            } catch (e: Exception) {
                Log.e("QnaScreen", "Error disconnecting chat on dispose", e)
            }
                isAuthenticated = false
                isSessionCreating = false
                sessionId = null
                sessionCreateAttempts = 0
                reconnectCycles = 0
                try {
                    voiceHelper.stopListening()
                } catch (e: Exception) {
                    Log.e("QnaScreen", "Error stopping voice helper on dispose", e)
                }
                AudioAssistant.onUtteranceCompleted = null
                isListening = false
            }
        }
    }

    // Full cleanup on composable dispose
    DisposableEffect(Unit) {
        onDispose {
            try {
                chatRepository.disconnectChat()
            } catch (e: Exception) {
                Log.e("QnaScreen", "Error disconnecting chat on full dispose", e)
            }
            isAuthenticated = false
            isSessionCreating = false
            sessionId = null
            sessionCreateAttempts = 0
            reconnectCycles = 0
            try {
                voiceHelper.destroy()
            } catch (e: Exception) {
                Log.e("QnaScreen", "Error destroying voice helper on full dispose", e)
            }
            AudioAssistant.onUtteranceCompleted = null
            isListening = false
        }
    }

    // ─── Animations ───
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "mic_scale"
    )
    val micGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "mic_glow"
    )

    // ─── UI ───
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        SurfaceCard.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .imePadding()
            .semantics { contentDescription = "Halaman Mode Tanya Jawab" }
    ) {
        // ─── Header ───
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Mode Tanya Jawab",
                style = MaterialTheme.typography.headlineMedium,
                color = NeonCyan,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Tanyakan apa saja tentang anatomi torso",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        // ─── Chat bubble history (scrollable, takes remaining space) ───
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (chatHistory.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SmartToy, null,
                                tint = NeonCyan.copy(alpha = 0.25f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Tanyakan tentang organ tubuh\natau ketik di kolom bawah",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            items(chatHistory) { msg -> ChatBubble(message = msg) }
            item { Spacer(Modifier.height(4.dp)) }
        }

        // ─── Bottom input bar: [Mic] [TextField] [Send] ───
        // All controls in one row. No overlapping with FAB (FAB is for speaker toggle).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Status pill
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isListening -> MicActive
                                isProcessing -> NeonAmber
                                else -> NeonGreen
                            }
                        )
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isListening -> NeonAmber
                        isProcessing -> NeonAmber
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 11.sp
                )
            }

            // Input row: [Mic button] [Text field with send]
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic button (left side, separate from text field)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isListening) MicActive.copy(alpha = micGlow)
                            else MicIdle
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isListening) stopMic() else doStartListening()
                        }
                        .semantics {
                            contentDescription = if (isListening)
                                "Mikrofon aktif. Ketuk untuk berhenti."
                            else "Mikrofon mati. Ketuk untuk mulai."
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = null,
                        tint = if (isListening) Color.White else NeonCyan,
                        modifier = Modifier
                            .size(24.dp)
                            .then(if (isListening) Modifier.scale(micScale) else Modifier)
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Text input field (takes remaining space, with send icon)
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    placeholder = {
                        Text(
                            "Ketik pertanyaan...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White, fontSize = 13.sp
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = NeonCyan.copy(alpha = 0.25f),
                        cursorColor = NeonCyan,
                        focusedContainerColor = SurfaceCard.copy(alpha = 0.5f),
                        unfocusedContainerColor = SurfaceCard.copy(alpha = 0.3f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (textInput.isNotBlank()) {
                                val q = textInput.trim()
                                textInput = ""
                                keyboardController?.hide()
                                processTypedQuestion(q)
                            }
                        }
                    ),
                    trailingIcon = {
                        if (textInput.isNotBlank()) {
                            IconButton(onClick = {
                                val q = textInput.trim()
                                textInput = ""
                                keyboardController?.hide()
                                processTypedQuestion(q)
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    "Kirim", tint = NeonCyan
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * ChatBubble — neon-bordered chat message.
 */
@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.isUser
    val bubbleColor = if (isUser) NeonAmber else NeonCyan
    val borderColor = if (isUser) NeonAmber.copy(alpha = 0.5f) else NeonCyan.copy(alpha = 0.5f)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(NeonCyan.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SmartToy, "AI", tint = NeonCyan, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(6.dp))
        }

        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 })
        ) {
            Box(
                Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isUser) 16.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 16.dp,
                            bottomStart = 16.dp, bottomEnd = 16.dp
                        )
                    )
                    .border(
                        1.dp, borderColor,
                        RoundedCornerShape(
                            topStart = if (isUser) 16.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 16.dp,
                            bottomStart = 16.dp, bottomEnd = 16.dp
                        )
                    )
                    .background(
                        if (isUser) SurfaceCard.copy(alpha = 0.7f)
                        else SurfaceCard.copy(alpha = 0.5f)
                    )
                    .padding(10.dp)
                    .semantics {
                        contentDescription = if (isUser) "Pertanyaan: ${message.text}"
                        else "Jawaban: ${message.text}"
                    }
            ) {
                Column {
                    Text(
                        if (isUser) "Anda" else "Anatomi AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = bubbleColor, fontWeight = FontWeight.Bold, fontSize = 10.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 20.sp, fontSize = 13.sp
                    )
                }
            }
        }

        if (isUser) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(NeonAmber.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, "User", tint = NeonAmber, modifier = Modifier.size(14.dp))
            }
        }
    }
}

private fun extractAssistantAnswer(response: ChatResponse): String {
    Log.d("QnaScreen", "extractAssistantAnswer called: answer=${response.answer?.take(50)} assistant_message=${response.assistant_message}")
    
    response.answer?.takeIf { it.isNotBlank() }?.let { 
        Log.d("QnaScreen", "Extracted from .answer field: ${it.take(100)}")
        return it 
    }

    val content = runCatching {
        response.assistant_message
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull()

    Log.d("QnaScreen", "Extracted from .assistant_message.content: ${content?.take(100)}")
    return content?.trim().orEmpty()
}
