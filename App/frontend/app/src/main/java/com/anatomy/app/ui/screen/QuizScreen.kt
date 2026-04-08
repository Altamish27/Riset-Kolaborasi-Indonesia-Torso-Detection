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
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.anatomy.app.ui.theme.NeonAmber
import com.anatomy.app.ui.theme.NeonCyan
import com.anatomy.app.ui.theme.NeonGreen
import com.anatomy.app.ui.theme.NeonMagenta
import com.anatomy.app.ui.theme.SurfaceCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Quiz question data class.
 */
data class QuizQuestion(
    val question: String,
    val answer: String,
    val funFact: String,
    val hint: String,
    val acceptedKeywords: List<String>
)

val quizBank = listOf(
    QuizQuestion(
        question = "Organ mana yang berfungsi memompa darah ke seluruh tubuh?",
        answer = "Jantung",
        funFact = "Tahukah Anda? Jantung manusia berdetak sekitar 100.000 kali per hari!",
        hint = "Petunjuk: Organ ini terletak di rongga dada, sedikit ke kiri.",
        acceptedKeywords = listOf("jantung", "heart")
    ),
    QuizQuestion(
        question = "Organ apa yang berfungsi sebagai tempat pertukaran oksigen dan karbon dioksida?",
        answer = "Paru-paru",
        funFact = "Tahukah Anda? Luas permukaan alveolus di paru-paru mencapai 70 meter persegi!",
        hint = "Petunjuk: Organ ini berpasangan dan terletak di kiri-kanan jantung.",
        acceptedKeywords = listOf("paru", "paru-paru", "lung")
    ),
    QuizQuestion(
        question = "Organ mana yang merupakan organ internal terbesar dan menyaring racun dari darah?",
        answer = "Hati",
        funFact = "Tahukah Anda? Hati bisa meregenerasi dirinya sendiri!",
        hint = "Petunjuk: Organ ini terletak di bagian kanan atas rongga perut.",
        acceptedKeywords = listOf("hati", "liver")
    ),
    QuizQuestion(
        question = "Organ mana yang berfungsi mencerna makanan menggunakan asam klorida?",
        answer = "Lambung",
        funFact = "Tahukah Anda? Asam lambung memiliki pH sekitar 1,5 sampai 3,5!",
        hint = "Petunjuk: Organ berbentuk kantung di bagian kiri atas perut.",
        acceptedKeywords = listOf("lambung", "stomach", "perut")
    ),
    QuizQuestion(
        question = "Organ mana yang berfungsi menyerap nutrisi dari makanan?",
        answer = "Usus",
        funFact = "Tahukah Anda? Usus halus memiliki luas permukaan 250 meter persegi!",
        hint = "Petunjuk: Organ ini terdiri dari dua bagian: halus dan besar.",
        acceptedKeywords = listOf("usus", "intestine")
    ),
    QuizQuestion(
        question = "Organ mana yang berfungsi menyaring darah dan menghasilkan urin?",
        answer = "Ginjal",
        funFact = "Tahukah Anda? Ginjal menyaring sekitar 180 liter darah setiap hari!",
        hint = "Petunjuk: Organ berbentuk kacang, berpasangan, di belakang rongga perut.",
        acceptedKeywords = listOf("ginjal", "kidney")
    ),
    QuizQuestion(
        question = "Sistem apa yang mengendalikan seluruh fungsi tubuh dengan 86 miliar neuron?",
        answer = "Sistem Syaraf",
        funFact = "Tahukah Anda? Sinyal saraf bisa bergerak hingga 120 meter per detik!",
        hint = "Petunjuk: Terdiri dari otak, sumsum tulang belakang, dan saraf tepi.",
        acceptedKeywords = listOf("syaraf", "saraf", "nervous", "otak")
    )
)

/**
 * Page 3 — "Mode Quiz"
 *
 * STABILITY FIX: Uses a simplified state machine with proper TTS→mic chaining.
 * Every voice capture goes through listenForAnswer() which ensures:
 *   1. VoiceRecognitionHelper handles thread safety
 *   2. Proper retry on error (max retries before skipping)
 *   3. "Betul!" or "Salah, coba lagi" immediate audio feedback
 */
@Composable
fun QuizScreen(isActive: Boolean = true) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentIndex by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var attempts by remember { mutableIntStateOf(0) }
    var isListening by remember { mutableStateOf(false) }
    var quizState by remember { mutableStateOf("idle") }
    var feedbackText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Bersiap...") }

    val voiceHelper = remember { VoiceRecognitionHelper(context) }
    val shuffledQuestions = remember { quizBank.shuffled() }

    val currentQuestion = if (currentIndex < shuffledQuestions.size) shuffledQuestions[currentIndex] else null

    fun stopMic() {
        voiceHelper.stopListening()
        isListening = false
    }

    /**
     * Listen for user's answer. This is the core voice capture loop.
     * Called after TTS finishes asking a question (or after giving a hint).
     */
    fun listenForAnswer(question: QuizQuestion) {
        if (!isActive) return

        quizState = "listening"
        isListening = true
        statusText = "🎤 Jawab sekarang..."
        HapticHelper.shortBuzz()

        voiceHelper.startListening(
            onResult = { result ->
                isListening = false
                val normalized = result.lowercase().trim()
                val isCorrect = question.acceptedKeywords.any { normalized.contains(it) }

                if (isCorrect) {
                    // ─── CORRECT ───
                    quizState = "correct"
                    score++
                    feedbackText = question.funFact
                    statusText = "Betul! 🎉"

                    HapticHelper.longBuzz()
                    coroutineScope.launch {
                        delay(200); HapticHelper.shortBuzz()
                        delay(200); HapticHelper.shortBuzz()
                    }

                    AudioAssistant.speak("Betul! Jawabannya ${question.answer}. ${question.funFact}")
                    AudioAssistant.onUtteranceCompleted = {
                        coroutineScope.launch {
                            delay(400)
                            currentIndex++
                            // Ask next question or finish
                            val nextQ = if (currentIndex < shuffledQuestions.size) shuffledQuestions[currentIndex] else null
                            if (nextQ != null) {
                                askQuestionTTS(nextQ, currentIndex + 1, { listenForAnswer(nextQ) })
                            } else {
                                finishQuiz(score, shuffledQuestions.size, { quizState = it }, { feedbackText = it }, { statusText = it })
                            }
                        }
                    }
                } else {
                    // ─── WRONG ───
                    attempts++
                    HapticHelper.doubleBuzz()

                    if (attempts >= 2) {
                        // Give answer and move on
                        quizState = "wrong"
                        feedbackText = "Jawaban: ${question.answer}"
                        statusText = "Jawaban: ${question.answer}"

                        AudioAssistant.speak("Salah. Jawabannya adalah ${question.answer}. ${question.funFact}")
                        AudioAssistant.onUtteranceCompleted = {
                            coroutineScope.launch {
                                delay(400)
                                currentIndex++
                                val nextQ = if (currentIndex < shuffledQuestions.size) shuffledQuestions[currentIndex] else null
                                if (nextQ != null) {
                                    attempts = 0
                                    askQuestionTTS(nextQ, currentIndex + 1, { listenForAnswer(nextQ) })
                                } else {
                                    finishQuiz(score, shuffledQuestions.size, { quizState = it }, { feedbackText = it }, { statusText = it })
                                }
                            }
                        }
                    } else {
                        // Give hint and retry
                        quizState = "wrong"
                        feedbackText = question.hint
                        statusText = "Salah, coba lagi!"

                        AudioAssistant.speak("Salah, coba lagi. ${question.hint}")
                        AudioAssistant.onUtteranceCompleted = {
                            // Re-listen after hint TTS finishes
                            listenForAnswer(question)
                        }
                    }
                }
            },
            onError = { _ ->
                isListening = false
                statusText = "Tidak terdengar"
                HapticHelper.doubleBuzz()

                if (attempts < 2 && isActive) {
                    // Retry listening
                    AudioAssistant.speak("Maaf, tidak terdengar. Coba jawab lagi.")
                    AudioAssistant.onUtteranceCompleted = {
                        listenForAnswer(question)
                    }
                } else {
                    // Skip to next
                    AudioAssistant.speak("Jawabannya adalah ${question.answer}.")
                    AudioAssistant.onUtteranceCompleted = {
                        coroutineScope.launch {
                            delay(400)
                            currentIndex++
                            val nextQ = if (currentIndex < shuffledQuestions.size) shuffledQuestions[currentIndex] else null
                            if (nextQ != null) {
                                attempts = 0
                                askQuestionTTS(nextQ, currentIndex + 1, { listenForAnswer(nextQ) })
                            } else {
                                finishQuiz(score, shuffledQuestions.size, { quizState = it }, { feedbackText = it }, { statusText = it })
                            }
                        }
                    }
                }
            }
        )
    }

    /** Start the quiz from the current question. */
    fun startQuiz() {
        val q = currentQuestion ?: return
        attempts = 0
        feedbackText = ""
        quizState = "asking"
        askQuestionTTS(q, currentIndex + 1) { listenForAnswer(q) }
    }

    // Auto-start quiz when page becomes active
    LaunchedEffect(isActive) {
        if (isActive && quizState == "idle") {
            // Hook into the page-name TTS ("Mode Quiz") finishing
            AudioAssistant.onUtteranceCompleted = {
                startQuiz()
            }
        } else if (!isActive) {
            stopMic()
            AudioAssistant.onUtteranceCompleted = null
        }
    }

    DisposableEffect(isActive) {
        onDispose {
            if (!isActive) {
                voiceHelper.stopListening()
                AudioAssistant.onUtteranceCompleted = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceHelper.destroy()
            AudioAssistant.onUtteranceCompleted = null
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

    val stateColor = when (quizState) {
        "correct" -> NeonGreen
        "wrong" -> NeonMagenta
        "listening" -> NeonAmber
        else -> NeonCyan
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

            Spacer(Modifier.height(16.dp))

            // Score display
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
                    "Skor: $score / ${shuffledQuestions.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Soal ${(currentIndex + 1).coerceAtMost(shuffledQuestions.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ─── Question card ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(vertical = 20.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, stateColor.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .background(SurfaceCard.copy(alpha = 0.6f))
                .padding(24.dp)
                .semantics {
                    contentDescription = currentQuestion?.question ?: "Quiz selesai"
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = when (quizState) {
                        "correct" -> Icons.Default.CheckCircle
                        "wrong" -> Icons.Default.Lightbulb
                        "finished" -> Icons.Default.EmojiEvents
                        else -> Icons.Default.QuestionMark
                    },
                    contentDescription = null,
                    tint = stateColor,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(Modifier.height(16.dp))

                if (quizState != "finished") {
                    Text(
                        currentQuestion?.question ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 32.sp
                    )
                }

                AnimatedVisibility(visible = feedbackText.isNotEmpty(), enter = fadeIn() + scaleIn()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(16.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(stateColor.copy(alpha = 0.1f))
                                .border(1.dp, stateColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                feedbackText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = stateColor,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp
                            )
                        }
                    }
                }

                if (quizState == "finished") {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Skor Akhir: $score / ${shuffledQuestions.size}",
                        style = MaterialTheme.typography.displaySmall,
                        color = NeonGreen, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ─── Bottom controls ───
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
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = stateColor, fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))

            if (isListening) {
                Box(
                    Modifier.size(72.dp).scale(pulseScale).clip(CircleShape)
                        .background(NeonAmber.copy(alpha = glowAlpha * 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Mic, "Mendengarkan", tint = NeonAmber, modifier = Modifier.size(36.dp))
                }
            }

            if (quizState == "finished") {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        currentIndex = 0
                        score = 0
                        attempts = 0
                        quizState = "idle"
                        feedbackText = ""
                        statusText = "Bersiap..."
                        startQuiz()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mulai Ulang", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Helper functions (extracted to avoid deeply nested callbacks) ───

private fun askQuestionTTS(
    question: QuizQuestion,
    questionNumber: Int,
    onTTSDone: () -> Unit
) {
    AudioAssistant.speak("Pertanyaan $questionNumber. ${question.question}")
    AudioAssistant.onUtteranceCompleted = { onTTSDone() }
}

private fun finishQuiz(
    score: Int,
    total: Int,
    setQuizState: (String) -> Unit,
    setFeedback: (String) -> Unit,
    setStatus: (String) -> Unit
) {
    setQuizState("finished")
    setStatus("Quiz selesai!")
    val finalMsg = "Selamat! Quiz selesai. Skor Anda: $score dari $total."
    setFeedback(finalMsg)
    AudioAssistant.speak(finalMsg)
    HapticHelper.longBuzz()
}
