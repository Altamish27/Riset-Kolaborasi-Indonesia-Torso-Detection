package com.anatomy.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anatomy.app.helper.AudioAssistant
import com.anatomy.app.ui.theme.NeonAmber
import com.anatomy.app.ui.theme.NeonCyan
import com.anatomy.app.ui.theme.NeonGreen
import com.anatomy.app.ui.theme.NeonMagenta
import com.anatomy.app.ui.theme.SurfaceCard
import com.anatomy.app.viewmodel.QuizStatus
import com.anatomy.app.viewmodel.QuizViewModel
import kotlinx.coroutines.launch

/**
 * Page 3 — "Mode Quiz"
 *
 * Dynamic quiz screen powered by backend LLM quiz generation.
 * Supports two quiz sources:
 *   1. WebSocket trigger_minigame (auto-starts from QnaScreen)
 *   2. HTTP POST /chat/generate_quiz (user-initiated via "Generate New Quiz" button)
 *
 * UI States: IDLE → LOADING → PLAYING → FEEDBACK → FINISHED
 */
@Composable
fun QuizScreen(
    isActive: Boolean = true,
    quizViewModel: QuizViewModel
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val uiState by quizViewModel.uiState.collectAsState()
    val isLoading by quizViewModel.isLoading.collectAsState()
    val errorMsg by quizViewModel.error.collectAsState()

    var topicInput by remember { mutableStateOf("") }

    // Stop ping and TTS when leaving quiz page
    DisposableEffect(isActive) {
        onDispose {
            if (!isActive) {
                quizViewModel.stopPing()
                AudioAssistant.stop()
            }
        }
    }

    // ─── Animations ───
    val infiniteTransition = rememberInfiniteTransition(label = "quiz_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "glow"
    )

    val stateColor = when (uiState.status) {
        QuizStatus.IDLE -> NeonCyan
        QuizStatus.LOADING -> NeonAmber
        QuizStatus.PLAYING -> NeonCyan
        QuizStatus.FEEDBACK -> if (uiState.isCorrect == true) NeonGreen else NeonMagenta
        QuizStatus.FINISHED -> NeonGreen
    }

    // ─── UI ───
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        SurfaceCard.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(24.dp)
            .semantics { contentDescription = "Halaman Mode Quiz" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ─── Header ───
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Mode Quiz",
                style = MaterialTheme.typography.headlineLarge,
                color = NeonCyan,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Uji pengetahuan anatomi Anda",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Score + progress (only when quiz is active)
            if (uiState.status == QuizStatus.PLAYING ||
                uiState.status == QuizStatus.FEEDBACK ||
                uiState.status == QuizStatus.FINISHED
            ) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(stateColor.copy(alpha = 0.15f))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.EmojiEvents, null, tint = NeonAmber, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Skor: ${uiState.score} / ${uiState.totalQuestions}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Soal ${(uiState.currentIndex + 1).coerceAtMost(uiState.totalQuestions)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = stateColor,
                    trackColor = stateColor.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round
                )
            }
        }

        // ─── Main content area ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(vertical = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, stateColor.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .background(SurfaceCard.copy(alpha = 0.6f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (uiState.status) {
                // ── IDLE: show generate quiz form ──
                QuizStatus.IDLE -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome, null,
                            tint = NeonCyan.copy(alpha = glowAlpha),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Generate Kuis Baru",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Masukkan topik untuk membuat kuis berdasarkan materi yang sudah dibahas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        Spacer(Modifier.height(20.dp))

                        OutlinedTextField(
                            value = topicInput,
                            onValueChange = { topicInput = it },
                            label = { Text("Topik kuis") },
                            placeholder = { Text("contoh: jantung, paru-paru") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                if (topicInput.isNotBlank()) {
                                    focusManager.clearFocus()
                                    coroutineScope.launch {
                                        quizViewModel.generateNewQuiz(topicInput.trim())
                                    }
                                }
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = NeonCyan.copy(alpha = 0.3f),
                                cursorColor = NeonCyan,
                                focusedLabelColor = NeonCyan,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (topicInput.isNotBlank()) {
                                    focusManager.clearFocus()
                                    coroutineScope.launch {
                                        quizViewModel.generateNewQuiz(topicInput.trim())
                                    }
                                }
                            },
                            enabled = topicInput.isNotBlank() && !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Generate Quiz", fontWeight = FontWeight.Bold)
                        }

                        // Error display
                        AnimatedVisibility(visible = errorMsg != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(Modifier.height(12.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NeonMagenta.copy(alpha = 0.1f))
                                        .border(1.dp, NeonMagenta.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        errorMsg ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NeonMagenta,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // ── LOADING: show progress ──
                QuizStatus.LOADING -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = NeonAmber,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            uiState.statusText,
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonAmber,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "AI sedang menyiapkan pertanyaan...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // ── PLAYING: show question + answer options ──
                QuizStatus.PLAYING -> {
                    val question = uiState.currentQuestion
                    if (question != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Icon(
                                Icons.Default.QuestionMark, null,
                                tint = NeonCyan,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                question.question_text,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 28.sp
                            )
                            Spacer(Modifier.height(20.dp))

                            // Answer options
                            question.answer_options.forEachIndexed { index, option ->
                                val letter = ('A' + index)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(
                                            1.dp,
                                            NeonCyan.copy(alpha = 0.3f),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .background(SurfaceCard.copy(alpha = 0.4f))
                                        .clickable { quizViewModel.selectAnswer(index) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(NeonCyan.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "$letter",
                                                color = NeonCyan,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            option,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── FEEDBACK: show correct/wrong + next button ──
                QuizStatus.FEEDBACK -> {
                    val question = uiState.currentQuestion
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Icon(
                            imageVector = if (uiState.isCorrect == true)
                                Icons.Default.CheckCircle else Icons.Default.Close,
                            contentDescription = null,
                            tint = stateColor,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))

                        // Show the question
                        if (question != null) {
                            Text(
                                question.question_text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp
                            )
                            Spacer(Modifier.height(12.dp))

                            // Show answer options with highlights
                            question.answer_options.forEachIndexed { index, option ->
                                val letter = ('A' + index)
                                val isSelected = uiState.selectedAnswer == index
                                val isCorrectOption = index == question.correct_answer_index

                                val optColor = when {
                                    isCorrectOption -> NeonGreen
                                    isSelected -> NeonMagenta
                                    else -> Color.White.copy(alpha = 0.3f)
                                }
                                val optBg = when {
                                    isCorrectOption -> NeonGreen.copy(alpha = 0.15f)
                                    isSelected -> NeonMagenta.copy(alpha = 0.15f)
                                    else -> Color.Transparent
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, optColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .background(optBg)
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("$letter.", color = optColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(option, style = MaterialTheme.typography.bodyMedium, color = optColor)
                                        if (isCorrectOption) {
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Default.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Feedback text
                        AnimatedVisibility(visible = uiState.feedbackText.isNotEmpty(), enter = fadeIn() + scaleIn()) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(stateColor.copy(alpha = 0.1f))
                                    .border(1.dp, stateColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    uiState.feedbackText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = stateColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { quizViewModel.nextQuestion() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Lanjutkan", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── FINISHED: show final score ──
                QuizStatus.FINISHED -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents, null,
                            tint = NeonAmber,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Kuis Selesai!",
                            style = MaterialTheme.typography.headlineMedium,
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Skor Akhir: ${uiState.score} / ${uiState.totalQuestions}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))

                        AnimatedVisibility(visible = uiState.feedbackText.isNotEmpty(), enter = fadeIn()) {
                            Text(
                                uiState.feedbackText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        // Restart same quiz
                        Button(
                            onClick = {
                                val data = uiState.gameData
                                if (data != null) quizViewModel.startQuiz(data)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Mulai Ulang", fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(12.dp))

                        // Generate new quiz
                        Button(
                            onClick = { quizViewModel.resetQuiz() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonAmber.copy(alpha = 0.2f),
                                contentColor = NeonAmber
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Generate Kuis Baru", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ─── Bottom status ───
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(stateColor.copy(alpha = 0.15f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(stateColor))
                Spacer(Modifier.width(8.dp))
                Text(
                    uiState.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = stateColor,
                    fontSize = 12.sp
                )
            }
        }
    }
}
