package com.anatomy.app.ui.screen

import android.util.Log
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.anatomy.app.network.QuizGameData
import com.anatomy.app.repository.ChatRepository
import com.anatomy.app.repository.QuizRepository
import com.anatomy.app.ui.theme.MicActive
import com.anatomy.app.ui.theme.MicIdle
import com.anatomy.app.ui.theme.NeonAmber
import com.anatomy.app.ui.theme.NeonCyan
import com.anatomy.app.ui.theme.NeonGreen
import com.anatomy.app.ui.theme.SurfaceCard
import com.anatomy.app.utils.UnifiedWebSocketManager
import com.anatomy.app.viewmodel.ChatSessionItem
import com.anatomy.app.viewmodel.ChatUiMessage
import com.anatomy.app.viewmodel.ChatViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val qnaJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Composable
fun QnaScreen(
    isActive: Boolean = true,
    chatViewModel: ChatViewModel,
    quizRepository: QuizRepository,
    onNavigateToQuiz: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val chatRepository = remember { ChatRepository(context) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val uiState by chatViewModel.uiState.collectAsState()

    var isListening by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Siap. Menyiapkan sesi...") }
    var textInput by remember { mutableStateOf("") }
    var isAuthenticated by remember { mutableStateOf(false) }
    var reconnectNonce by remember { mutableStateOf(0) }
    var hasInitializedVoiceFlow by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val voiceHelper = remember { VoiceRecognitionHelper(context) }

    fun queueGreetingAndAutoListen(greeting: String) {
        AudioAssistant.stop()
        AudioAssistant.speak(greeting)
        AudioAssistant.onUtteranceCompleted = {
            // Hapus @onUtteranceCompleted karena label ini tidak valid untuk assignment variable
            if (isActive && !isListening && !isProcessing) {
                HapticHelper.shortBuzz()
                statusText = "🎤 Mendengarkan..."
                // Jalankan di main thread jika perlu, atau panggil fungsi listening
                chatViewModel.requestAutoListen()
            }
        }
    }

    fun startNewSessionWithVoiceCue() {
        coroutineScope.launch {
            isProcessing = true
            HapticHelper.doubleBuzz()
            statusText = "Membuat sesi baru..."
            val sessionId = chatViewModel.createNewSession()
            isProcessing = false

            if (sessionId.isNullOrBlank()) {
                statusText = "Gagal membuat sesi baru."
                AudioAssistant.stop()
                AudioAssistant.speak("Maaf, sesi baru gagal dibuat.")
                return@launch
            }

            statusText = "Sesi baru siap"
            queueGreetingAndAutoListen("Sesi baru siap. Silakan ajukan pertanyaan.")
        }
    }

    fun sendQuestionToBackend(question: String) {
        coroutineScope.launch {
            val sessionId = uiState.sessionId ?: chatViewModel.resumeLatestSessionOrCreate()
            if (sessionId.isNullOrBlank()) {
                isProcessing = false
                statusText = "Gagal menyiapkan sesi chat. Coba lagi."
                return@launch
            }

            val sent = chatRepository.sendChatMessage(sessionId, question)
            if (!sent) {
                isProcessing = false
                statusText = "Koneksi chat terputus, menyambung ulang..."
                reconnectNonce += 1
            } else {
                statusText = "Memproses jawaban..."
            }
        }
    }

    fun doStartListening() {
        if (isProcessing || !isActive) return

        try {
            // Always interrupt speech before opening mic to avoid overlap.
            AudioAssistant.stop()
            isListening = true
            HapticHelper.shortBuzz()
            statusText = "🎤 Mendengarkan..."

            voiceHelper.startListening(
                onResult = { result ->
                    if (!isActive) return@startListening
                    isListening = false
                    val spoken = result.trim()
                    if (spoken.isBlank()) {
                        statusText = "Tidak terdengar. Tekan mic untuk coba lagi."
                        return@startListening
                    }

                    val normalized = spoken.lowercase()
                    if (normalized.contains("sesi baru")) {
                        startNewSessionWithVoiceCue()
                        return@startListening
                    }

                    isProcessing = true
                    statusText = "Memproses..."
                    chatViewModel.appendUserMessage(spoken)
                    sendQuestionToBackend(spoken)
                },
                onError = { code ->
                    if (!isActive) return@startListening
                    isListening = false
                    statusText = "Gagal mendengar (kode: $code). Coba lagi atau ketik."
                }
            )
        } catch (e: Exception) {
            Log.e("QnaScreen", "Exception in doStartListening", e)
            isListening = false
            statusText = "Error mikrofon. Coba lagi atau ketik pertanyaan."
        }
    }

    fun processTypedQuestion(question: String) {
        if (question.isBlank() || isProcessing) return

        try {
            if (isListening) {
                voiceHelper.stopListening()
                isListening = false
            }
            AudioAssistant.stop()

            isProcessing = true
            statusText = "Memproses..."
            chatViewModel.appendUserMessage(question)
            sendQuestionToBackend(question)
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
        AudioAssistant.onUtteranceCompleted = null
    }

    fun switchToSession(item: ChatSessionItem) {
        coroutineScope.launch {
            HapticHelper.shortBuzz()
            statusText = "Memuat ${item.title}..."
            chatViewModel.switchToSession(item.sessionId)
            drawerState.close()
            statusText = "Sesi dipulihkan"
            queueGreetingAndAutoListen("${item.title} dipulihkan. Silakan lanjut bertanya.")
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            hasInitializedVoiceFlow = false
            stopMic()
            isProcessing = false
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            chatViewModel.loadSessions()
        }
    }

    LaunchedEffect(isActive, reconnectNonce) {
        if (!isActive) return@LaunchedEffect

        statusText = "Menghubungkan ke backend..."
        val flow = chatRepository.connectChat()
        if (flow == null) {
            statusText = "Token login tidak ditemukan. Silakan login ulang."
            return@LaunchedEffect
        }

        if (UnifiedWebSocketManager.isAuthenticated()) {
            isAuthenticated = true
        }

        try {
            flow.collect { response ->
                when {
                    response.error != null -> {
                        isProcessing = false
                        statusText = "Error backend: ${response.error}"
                    }

                    response.action == "trigger_minigame" -> {
                        val gameData = parseQuizGameData(response)
                        if (gameData == null) {
                            statusText = "Kuis gagal dimulai: data tidak valid"
                            return@collect
                        }

                        quizRepository.submitQuizData(gameData)
                        HapticHelper.shortBuzz()
                        statusText = "Kuis siap. Berpindah ke Mode Quiz..."
                        onNavigateToQuiz()
                    }

                    response.action == "chat_response" || response.assistant_message != null -> {
                        val answer = extractAssistantAnswer(response)
                        if (answer.isBlank()) {
                            isProcessing = false
                            statusText = "Respons backend kosong."
                            return@collect // Gunakan @collect untuk keluar dari blok flow
                        }

                        chatViewModel.appendAssistantMessage(answer)
                        isProcessing = false
                        statusText = "Menjawab via suara..."

                        AudioAssistant.speak(answer)
                        AudioAssistant.onUtteranceCompleted = {
                            // Gunakan logika if-check biasa tanpa return label yang bermasalah
                            if (isActive && !isProcessing && !isListening) {
                                HapticHelper.shortBuzz()
                                // Update UI harus dipastikan di Main Thread jika dari callback
                                statusText = "🎤 Mendengarkan..."
                                chatViewModel.requestAutoListen()
                            }
                        }
                    }

                    response.action == "authenticated" -> {
                        isAuthenticated = true
                        statusText = "Autentikasi berhasil"
                    }

                    response.action == "connected" -> {
                        statusText = "Terhubung ke backend, autentikasi..."
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("QnaScreen", "Error collecting chat flow", e)
            isProcessing = false
            statusText = "Koneksi chat bermasalah. Coba buka ulang halaman."
        }
    }

    LaunchedEffect(isActive, isAuthenticated, hasInitializedVoiceFlow) {
        if (!isActive || !isAuthenticated || hasInitializedVoiceFlow) return@LaunchedEffect

        statusText = "Menyiapkan sesi dan riwayat..."
        val sessionId = chatViewModel.resumeLatestSessionOrCreate()
        if (sessionId.isNullOrBlank()) {
            statusText = "Gagal menyiapkan sesi."
            return@LaunchedEffect
        }

        chatViewModel.loadHistoryForCurrentSession(forceReload = true)
        hasInitializedVoiceFlow = true

        val latestState = chatViewModel.uiState.value

        val greeting = if (latestState.chatMessages.isEmpty()) {
            "Halo, saya asisten anatomi Anda. Silakan ajukan pertanyaan."
        } else {
            "Sesi terakhir berhasil dipulihkan. Silakan lanjutkan percakapan."
        }

        HapticHelper.shortBuzz()
        statusText = "Menyapa pengguna..."
        queueGreetingAndAutoListen(greeting)
    }

    LaunchedEffect(uiState.autoListenRequested, isActive, isProcessing, isListening) {
        if (!isActive || !uiState.autoListenRequested) return@LaunchedEffect
        if (isProcessing || isListening) return@LaunchedEffect

        chatViewModel.clearAutoListenRequest()
        doStartListening()
    }

    LaunchedEffect(uiState.chatMessages.size) {
        if (uiState.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem((uiState.chatMessages.size - 1).coerceAtLeast(0))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopMic()
            try {
                voiceHelper.destroy()
            } catch (e: Exception) {
                Log.e("QnaScreen", "Error destroying voice helper", e)
            }
            AudioAssistant.onUtteranceCompleted = null
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "mic_scale"
    )
    val micGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "mic_glow"
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.82f),
                drawerContainerColor = SurfaceCard
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Riwayat Sesi",
                        style = MaterialTheme.typography.titleLarge,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(NeonGreen.copy(alpha = 0.16f))
                            .border(1.dp, NeonGreen.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .clickable {
                                HapticHelper.doubleBuzz()
                                startNewSessionWithVoiceCue()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = NeonGreen)
                        Spacer(Modifier.width(8.dp))
                        Text("Sesi Baru", color = Color.White)
                    }

                    Spacer(Modifier.height(12.dp))

                    if (uiState.isLoadingSessions) {
                        Text(
                            text = "Memuat daftar sesi...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }

                    if (uiState.sessions.isEmpty() && !uiState.isLoadingSessions) {
                        Text(
                            text = "Belum ada sesi tersimpan.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.sessions) { session ->
                            val isSelected = uiState.sessionId == session.sessionId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) NeonCyan.copy(alpha = 0.16f)
                                        else SurfaceCard.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonCyan.copy(alpha = 0.5f)
                                        else Color.White.copy(alpha = 0.2f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { switchToSession(session) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.SmartToy, contentDescription = null, tint = NeonCyan)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(session.title, color = Color.White, fontSize = 13.sp)
                                    Text(
                                        session.updatedAt ?: session.createdAt ?: "Tanpa timestamp",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 12.dp, end = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    HapticHelper.shortBuzz()
                    coroutineScope.launch {
                        drawerState.open()
                    }
                }) {
                    Icon(Icons.Default.Menu, contentDescription = "Buka menu sesi", tint = NeonCyan)
                }

                Column(
                    modifier = Modifier.weight(1f),
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
                        "Voice-first untuk pengguna tunanetra",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.width(48.dp))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.chatMessages.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    null,
                                    tint = NeonCyan.copy(alpha = 0.25f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Katakan pertanyaan Anda\natau ketik di kolom bawah",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                items(uiState.chatMessages) { msg ->
                    ChatBubble(message = msg)
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
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

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                                // Manual interrupt is always allowed to force-stop current speech.
                                if (isListening) {
                                    stopMic()
                                    statusText = "Mikrofon dihentikan"
                                } else {
                                    doStartListening()
                                }
                            }
                            .semantics {
                                contentDescription = if (isListening) {
                                    "Mikrofon aktif. Ketuk untuk berhenti"
                                } else {
                                    "Mikrofon mati. Ketuk untuk mulai"
                                }
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
                            color = Color.White,
                            fontSize = 13.sp
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
                                        contentDescription = "Kirim",
                                        tint = NeonCyan
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatUiMessage) {
    val bubbleColor = if (message.isUser) NeonAmber else NeonCyan
    val borderColor = if (message.isUser) NeonAmber.copy(alpha = 0.5f) else NeonCyan.copy(alpha = 0.5f)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!message.isUser) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.12f)),
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
                            topStart = if (message.isUser) 16.dp else 4.dp,
                            topEnd = if (message.isUser) 4.dp else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .border(
                        1.dp,
                        borderColor,
                        RoundedCornerShape(
                            topStart = if (message.isUser) 16.dp else 4.dp,
                            topEnd = if (message.isUser) 4.dp else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(
                        if (message.isUser) SurfaceCard.copy(alpha = 0.7f)
                        else SurfaceCard.copy(alpha = 0.5f)
                    )
                    .padding(10.dp)
                    .semantics {
                        contentDescription = if (message.isUser) "Pertanyaan: ${message.text}"
                        else "Jawaban: ${message.text}"
                    }
            ) {
                Column {
                    Text(
                        if (message.isUser) "Anda" else "Anatomi AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = bubbleColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 20.sp,
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (message.isUser) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(NeonAmber.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, "User", tint = NeonAmber, modifier = Modifier.size(14.dp))
            }
        }
    }
}

private fun extractAssistantAnswer(response: ChatResponse): String {
    response.answer?.takeIf { it.isNotBlank() }?.let { return it }

    val content = runCatching {
        response.assistant_message
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull()

    return content?.trim().orEmpty()
}

private fun parseQuizGameData(response: ChatResponse): QuizGameData? {
    val payload = response.game_data ?: return null
    return runCatching {
        qnaJson.decodeFromJsonElement<QuizGameData>(payload)
    }.getOrNull()
}
