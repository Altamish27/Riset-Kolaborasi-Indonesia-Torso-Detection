package com.anatomy.app.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anatomy.app.helper.AudioAssistant
import com.anatomy.app.helper.HapticHelper
import com.anatomy.app.helper.VoiceRecognitionHelper
import com.anatomy.app.ui.theme.MicActive
import com.anatomy.app.ui.theme.MicIdle
import com.anatomy.app.ui.theme.NeonAmber
import com.anatomy.app.ui.theme.NeonGreen
import com.anatomy.app.ui.theme.NeonMagenta
import com.anatomy.app.ui.theme.SurfaceCard
import com.anatomy.app.viewmodel.QuizEntryState
import com.anatomy.app.viewmodel.QuizStatus
import com.anatomy.app.viewmodel.QuizViewModel
import kotlinx.coroutines.launch

@Composable
fun QuizScreen(
    isActive: Boolean = true,
    quizViewModel: QuizViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by quizViewModel.uiState.collectAsState()
    val isLoading by quizViewModel.isLoading.collectAsState()
    val errorMsg by quizViewModel.error.collectAsState()

    val voiceHelper = remember { VoiceRecognitionHelper(context) }

    var isListening by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("Mode suara siap") }
    var promptedEntryState by remember { mutableStateOf<QuizEntryState?>(null) }

    fun startListeningAnswer() {
        if (!isActive || uiState.status != QuizStatus.PLAYING || isListening) return

        AudioAssistant.stop()
        isListening = true
        HapticHelper.shortBuzz()
        voiceStatus = "🎤 Sebutkan jawaban Anda"

        voiceHelper.startListening(
            onResult = { spokenText ->
                isListening = false
                val answer = spokenText.trim()
                if (answer.isBlank()) {
                    voiceStatus = "Tidak terdengar"
                    HapticHelper.doubleBuzz()
                    return@startListening
                }

                val match = quizViewModel.matchVoiceAnswer(answer)
                if (match >= 0) {
                    HapticHelper.shortBuzz()
                    voiceStatus = "Jawaban diterima"
                    quizViewModel.selectAnswer(match)
                } else {
                    HapticHelper.doubleBuzz()
                    voiceStatus = "Jawaban belum dikenali"
                    AudioAssistant.speak("Jawaban belum dikenali. Ulangi dengan A, B, C, D, angka, atau teks opsi.")
                    AudioAssistant.onUtteranceCompleted = {
                        if (isActive && uiState.status == QuizStatus.PLAYING) {
                            startListeningAnswer()
                        }
                    }
                }
            },
            onError = { code ->
                isListening = false
                voiceStatus = "Error mikrofon: $code"
                HapticHelper.doubleBuzz()
            }
        )
    }

    fun startListeningModeSelection() {
        if (!isActive || uiState.entryState != QuizEntryState.CHOOSING_MODE || isListening || isLoading) {
            return
        }

        AudioAssistant.stop()
        isListening = true
        HapticHelper.shortBuzz()
        voiceStatus = "🎤 Ucapkan: obrolan tadi atau topik baru"

        voiceHelper.startListening(
            onResult = { spokenText ->
                isListening = false
                val spoken = spokenText.lowercase().trim()
                when {
                    spoken.contains("obrolan tadi") ||
                        spoken.contains("chat") ||
                        spoken.contains("obrolan") -> {
                        HapticHelper.longBuzz()
                        coroutineScope.launch {
                            quizViewModel.startQuizFromChatHistory()
                        }
                    }

                    spoken.contains("topik baru") || spoken.contains("topik") -> {
                        HapticHelper.doubleBuzz()
                        quizViewModel.chooseCustomTopicEntry()
                    }

                    else -> {
                        HapticHelper.doubleBuzz()
                        AudioAssistant.speak("Pilihan belum dikenali. Ucapkan obrolan tadi atau topik baru.")
                        AudioAssistant.onUtteranceCompleted = {
                            if (isActive && uiState.entryState == QuizEntryState.CHOOSING_MODE) {
                                startListeningModeSelection()
                            }
                        }
                    }
                }
            },
            onError = { code ->
                isListening = false
                voiceStatus = "Error mikrofon: $code"
                HapticHelper.doubleBuzz()
            }
        )
    }

    fun startListeningNewTopic() {
        if (!isActive || uiState.entryState != QuizEntryState.LISTENING_TOPIC || isListening || isLoading) {
            return
        }

        AudioAssistant.stop()
        isListening = true
        HapticHelper.shortBuzz()
        voiceStatus = "🎤 Sebutkan topik kuis"

        voiceHelper.startListening(
            onResult = { spokenText ->
                isListening = false
                val topic = spokenText.trim()
                if (topic.isBlank()) {
                    HapticHelper.doubleBuzz()
                    AudioAssistant.speak("Topik belum terdengar. Silakan sebutkan topik baru.")
                    AudioAssistant.onUtteranceCompleted = {
                        if (isActive && uiState.entryState == QuizEntryState.LISTENING_TOPIC) {
                            startListeningNewTopic()
                        }
                    }
                    return@startListening
                }

                HapticHelper.longBuzz()
                coroutineScope.launch {
                    quizViewModel.generateNewQuiz(topic)
                }
            },
            onError = { code ->
                isListening = false
                voiceStatus = "Kendala suara. Memuat kuis Anatomi Torso..."
                HapticHelper.doubleBuzz()
                AudioAssistant.speak("Kendala mikrofon terdeteksi. Memuat kuis Anatomi Torso...")
                coroutineScope.launch {
                    quizViewModel.generateNewQuiz("Anatomi Torso")
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        quizViewModel.observePendingQuiz(coroutineScope)
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            quizViewModel.evaluateEntryDecision()
        } else {
            isListening = false
            promptedEntryState = null
            AudioAssistant.onUtteranceCompleted = null
        }
    }

    LaunchedEffect(uiState.entryState, isActive) {
        if (!isActive) return@LaunchedEffect
        if (promptedEntryState == uiState.entryState) return@LaunchedEffect

        when (uiState.entryState) {
            QuizEntryState.CHOOSING_MODE -> {
                promptedEntryState = QuizEntryState.CHOOSING_MODE
                val prompt = "Selamat datang di kuis. Ingin memulai kuis berdasarkan obrolan tadi, atau memilih topik baru?"
                AudioAssistant.speak(prompt)
                AudioAssistant.onUtteranceCompleted = {
                    if (isActive && uiState.entryState == QuizEntryState.CHOOSING_MODE) {
                        startListeningModeSelection()
                    }
                }
            }

            QuizEntryState.LISTENING_TOPIC -> {
                promptedEntryState = QuizEntryState.LISTENING_TOPIC
                val prompt = "Silakan sebutkan topik baru yang Anda inginkan."
                AudioAssistant.speak(prompt)
                AudioAssistant.onUtteranceCompleted = {
                    if (isActive && uiState.entryState == QuizEntryState.LISTENING_TOPIC) {
                        startListeningNewTopic()
                    }
                }
            }

            QuizEntryState.NONE -> {
                promptedEntryState = null
            }
        }
    }

    LaunchedEffect(uiState.status, uiState.currentIndex, isActive) {
        if (!isActive) return@LaunchedEffect

        when (uiState.status) {
            QuizStatus.PLAYING -> {
                HapticHelper.longBuzz()
                AudioAssistant.onUtteranceCompleted = {
                    if (isActive && uiState.status == QuizStatus.PLAYING && !isListening) {
                        startListeningAnswer()
                    }
                }
            }

            QuizStatus.FEEDBACK -> {
                AudioAssistant.onUtteranceCompleted = {
                    if (isActive) {
                        quizViewModel.nextQuestion()
                    }
                }
            }

            QuizStatus.FINISHED,
            QuizStatus.IDLE -> {
                if (uiState.entryState == QuizEntryState.NONE) {
                    AudioAssistant.onUtteranceCompleted = null
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                voiceHelper.destroy()
            } catch (e: Exception) {
                Log.e("QuizScreen", "Error destroying voice helper", e)
            }
            AudioAssistant.onUtteranceCompleted = null
        }
    }

    val stateColor = when (uiState.status) {
        QuizStatus.IDLE -> NeonGreen
        QuizStatus.PLAYING -> NeonGreen
        QuizStatus.FEEDBACK -> if (uiState.isCorrect == true) NeonGreen else NeonMagenta
        QuizStatus.FINISHED -> NeonGreen
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        NeonGreen.copy(alpha = 0.05f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blur(if (isLoading) 8.dp else 0.dp)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .semantics { contentDescription = "Halaman Mode Quiz" },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Mode Quiz",
                style = MaterialTheme.typography.headlineMedium,
                color = NeonGreen,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(Modifier.height(8.dp))

            Text(
                uiState.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = stateColor,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                voiceStatus,
                style = MaterialTheme.typography.labelSmall,
                color = if (isListening) NeonAmber else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (errorMsg != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorMsg.orEmpty(),
                    color = NeonMagenta,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, stateColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .background(SurfaceCard.copy(alpha = 0.5f))
                    .padding(18.dp)
            ) {
                when (uiState.status) {
                    QuizStatus.IDLE -> {
                        val infoText = when (uiState.entryState) {
                            QuizEntryState.CHOOSING_MODE -> "Menunggu pilihan mode kuis lewat suara."
                            QuizEntryState.LISTENING_TOPIC -> "Menunggu topik baru dari suara Anda."
                            QuizEntryState.NONE -> "Menyiapkan kuis..."
                        }
                        Text(
                            infoText,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    QuizStatus.PLAYING -> {
                        val question = uiState.currentQuestion
                        if (question == null) {
                            Text("Pertanyaan tidak tersedia", color = Color.White)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.QuestionMark, null, tint = NeonGreen)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Soal ${uiState.currentIndex + 1}/${uiState.totalQuestions}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                Text(
                                    question.question_text,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    lineHeight = 26.sp
                                )

                                question.answer_options.forEachIndexed { index, option ->
                                    val letter = ('A' + index)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SurfaceCard.copy(alpha = 0.45f))
                                            .border(
                                                width = 1.dp,
                                                color = NeonGreen.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { quizViewModel.selectAnswer(index) }
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            "$letter. $option",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(if (isListening) MicActive else MicIdle)
                                            .clickable {
                                                AudioAssistant.stop()
                                                if (isListening) {
                                                    HapticHelper.shortBuzz()
                                                    voiceHelper.stopListening()
                                                    isListening = false
                                                    voiceStatus = "Mikrofon dihentikan"
                                                } else {
                                                    startListeningAnswer()
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                                            contentDescription = null,
                                            tint = if (isListening) Color.White else NeonGreen
                                        )
                                    }

                                    Spacer(Modifier.width(10.dp))

                                    Text(
                                        "Mode hands-free aktif. Jawab dengan suara.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    QuizStatus.FEEDBACK -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isCorrect == true) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.QuestionMark
                                },
                                contentDescription = null,
                                tint = stateColor
                            )

                            Text(
                                uiState.feedbackText,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp
                            )
                        }
                    }

                    QuizStatus.FINISHED -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.EmojiEvents, null, tint = NeonAmber)
                            Text(
                                "Skor ${uiState.score}/${uiState.totalQuestions}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                uiState.feedbackText,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp
                            )
                            Button(
                                onClick = {
                                    HapticHelper.shortBuzz()
                                    quizViewModel.resetQuiz()
                                    coroutineScope.launch {
                                        quizViewModel.evaluateEntryDecision()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonGreen,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Default.Refresh, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Selesai")
                            }
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard.copy(alpha = 0.82f))
                        .border(1.dp, NeonGreen.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = NeonGreen, strokeWidth = 3.dp)
                        Text(
                            "Membuat kuis berdasarkan pilihan suara Anda...",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
