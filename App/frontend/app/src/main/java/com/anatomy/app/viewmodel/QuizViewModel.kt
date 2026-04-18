package com.anatomy.app.viewmodel

import android.util.Log
import com.anatomy.app.helper.AudioAssistant
import com.anatomy.app.helper.HapticHelper
import com.anatomy.app.network.QuizGameData
import com.anatomy.app.network.QuizQuestionData
import com.anatomy.app.repository.QuizRepository
import com.anatomy.app.utils.UnifiedWebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * QuizViewModel — MVVM state machine for the multiple-choice quiz feature.
 *
 * Manages:
 *  - Quiz status transitions: IDLE → LOADING → PLAYING → FEEDBACK → FINISHED
 *  - Current question tracking, scoring, answer selection
 *  - TTS coordination: reading questions and feedback aloud
 *  - Hybrid input: supports both tap and voice-based answer selection
 *  - WebSocket ping: sends periodic pings to keep the connection alive during quiz
 *  - Dual quiz source: WebSocket (trigger_minigame) and HTTP (generate_quiz endpoint)
 *
 * Not an AndroidX ViewModel — intentionally simple to avoid DI complexity.
 * Instantiated once per MainPagerScreen and shared with QuizScreen.
 */
class QuizViewModel(
    private val quizRepository: QuizRepository
) {
    private val TAG = "QuizViewModel"

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    /** Loading state from repository (HTTP quiz generation in progress) */
    val isLoading: StateFlow<Boolean> = quizRepository.isLoading

    /** Error state from repository (HTTP quiz generation failed) */
    val error: StateFlow<String?> = quizRepository.error

    // ── Ping management ──────────────────────────────────────
    private val pingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pingJob: Job? = null

    // ── Public API ─────────────────────────────────────────────

    /**
     * Start a quiz from the given game data (received from backend trigger_minigame
     * or from HTTP generate_quiz endpoint).
     * Clears any previous quiz state and begins from question 0.
     */
    fun startQuiz(data: QuizGameData) {
        if (data.questions.isEmpty()) {
            Log.w(TAG, "Cannot start quiz: no questions")
            _uiState.value = QuizUiState(
                status = QuizStatus.IDLE,
                statusText = "Tidak ada pertanyaan tersedia."
            )
            return
        }

        Log.d(TAG, "Starting quiz: topic='${data.topic}', ${data.questions.size} questions")
        _uiState.value = QuizUiState(
            status = QuizStatus.PLAYING,
            gameData = data,
            currentIndex = 0,
            score = 0,
            totalQuestions = data.questions.size,
            statusText = "Pertanyaan 1 dari ${data.questions.size}"
        )

        quizRepository.clearPendingQuiz()

        // Start ping to keep WebSocket alive while user is thinking
        startPing()

        // Read the first question via TTS
        readCurrentQuestion()
    }

    /**
     * Called when user selects an answer (via tap or voice).
     * @param selectedIndex The index (0-3) of the chosen option.
     */
    fun selectAnswer(selectedIndex: Int) {
        val state = _uiState.value
        if (state.status != QuizStatus.PLAYING) return
        if (state.selectedAnswer != null) return // Already answered

        val question = state.currentQuestion ?: return
        val isCorrect = selectedIndex == question.correct_answer_index
        val newScore = if (isCorrect) state.score + 1 else state.score

        val correctOption = question.answer_options.getOrElse(question.correct_answer_index) { "?" }
        val feedbackText = if (isCorrect) {
            "Betul! Jawabannya adalah $correctOption."
        } else {
            "Salah. Jawaban yang benar adalah $correctOption."
        }

        _uiState.value = state.copy(
            status = QuizStatus.FEEDBACK,
            selectedAnswer = selectedIndex,
            isCorrect = isCorrect,
            score = newScore,
            feedbackText = feedbackText,
            statusText = if (isCorrect) "Betul! 🎉" else "Salah ✗"
        )

        // Haptic feedback
        if (isCorrect) {
            HapticHelper.longBuzz()
        } else {
            HapticHelper.doubleBuzz()
        }

        // Speak feedback
        AudioAssistant.speak(feedbackText)
    }

    /**
     * Advance to the next question, or finish if all questions answered.
     */
    fun nextQuestion() {
        val state = _uiState.value
        if (state.status != QuizStatus.FEEDBACK) return

        val nextIndex = state.currentIndex + 1
        val data = state.gameData ?: return

        if (nextIndex >= data.questions.size) {
            // Quiz finished — stop ping
            stopPing()
            val finalMsg = "Kuis selesai! Skor Anda: ${state.score} dari ${data.questions.size}."
            _uiState.value = state.copy(
                status = QuizStatus.FINISHED,
                currentIndex = nextIndex,
                selectedAnswer = null,
                isCorrect = null,
                feedbackText = finalMsg,
                statusText = "Quiz selesai!"
            )
            HapticHelper.longBuzz()
            AudioAssistant.speak(finalMsg)
        } else {
            // Next question
            _uiState.value = state.copy(
                status = QuizStatus.PLAYING,
                currentIndex = nextIndex,
                selectedAnswer = null,
                isCorrect = null,
                feedbackText = "",
                statusText = "Pertanyaan ${nextIndex + 1} dari ${data.questions.size}"
            )
            readCurrentQuestion()
        }
    }

    /**
     * Reset quiz to idle state (used when leaving quiz page or retrying).
     */
    fun resetQuiz() {
        Log.d(TAG, "Quiz reset to idle")
        stopPing()
        _uiState.value = QuizUiState()
    }

    /**
     * Generate a new quiz via the HTTP endpoint POST /chat/generate_quiz.
     * Sets UI to LOADING state, calls the repository, and starts the quiz on success.
     */
    suspend fun generateNewQuiz(topic: String) {
        Log.d(TAG, "Generating new quiz for topic: $topic")
        _uiState.value = _uiState.value.copy(
            status = QuizStatus.LOADING,
            statusText = "Membuat kuis tentang \"$topic\"..."
        )

        val result = quizRepository.generateQuiz(topic)

        result.onSuccess { response ->
            Log.d(TAG, "Quiz generated successfully, starting quiz")
            startQuiz(response.quiz)
        }

        result.onFailure { e ->
            Log.e(TAG, "Quiz generation failed", e)
            _uiState.value = _uiState.value.copy(
                status = QuizStatus.IDLE,
                statusText = "Gagal membuat kuis: ${e.message ?: "Error tidak diketahui"}"
            )
        }
    }

    /**
     * Clear error state from repository.
     */
    fun clearError() {
        quizRepository.clearError()
    }

    /**
     * Try to match a voice input string to one of the current answer options.
     * Supports matching by option letter (A/B/C/D), number (1/2/3/4), or partial text match.
     * Returns the matched option index, or -1 if no match.
     */
    fun matchVoiceAnswer(spokenText: String): Int {
        val state = _uiState.value
        val question = state.currentQuestion ?: return -1
        val normalized = spokenText.lowercase().trim()

        // Match by letter: "a", "b", "c", "d"
        val letterIndex = when {
            normalized == "a" || normalized.startsWith("a ") || normalized.startsWith("a.") -> 0
            normalized == "b" || normalized.startsWith("b ") || normalized.startsWith("b.") -> 1
            normalized == "c" || normalized.startsWith("c ") || normalized.startsWith("c.") -> 2
            normalized == "d" || normalized.startsWith("d ") || normalized.startsWith("d.") -> 3
            else -> -1
        }
        if (letterIndex >= 0 && letterIndex < question.answer_options.size) return letterIndex

        // Match by number: "1", "2", "3", "4" or "satu", "dua", "tiga", "empat"
        val numberIndex = when {
            normalized.contains("satu") || normalized == "1" -> 0
            normalized.contains("dua") || normalized == "2" -> 1
            normalized.contains("tiga") || normalized == "3" -> 2
            normalized.contains("empat") || normalized == "4" -> 3
            else -> -1
        }
        if (numberIndex >= 0 && numberIndex < question.answer_options.size) return numberIndex

        // Partial text match: see if spoken text contains any option text
        question.answer_options.forEachIndexed { index, option ->
            if (normalized.contains(option.lowercase().trim())) return index
        }

        return -1
    }

    // ── Ping: keep WebSocket alive during quiz ───────────────

    /**
     * Start sending periodic ping messages to the WebSocket.
     * Called when quiz starts playing to prevent connection drop
     * while user is thinking about answers.
     */
    fun startPing() {
        pingJob?.cancel()
        pingJob = pingScope.launch {
            Log.d(TAG, "Starting quiz ping loop (25s interval)")
            while (isActive) {
                try {
                    val sent = UnifiedWebSocketManager.sendPing()
                    if (!sent) {
                        Log.w(TAG, "Quiz ping send failed")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending quiz ping", e)
                }
                delay(25_000L)
            }
        }
    }

    /**
     * Stop sending periodic ping messages.
     * Called when quiz finishes or when user leaves quiz page.
     */
    fun stopPing() {
        pingJob?.cancel()
        pingJob = null
        Log.d(TAG, "Quiz ping loop stopped")
    }

    // ── Internal ───────────────────────────────────────────────

    private fun readCurrentQuestion() {
        val state = _uiState.value
        val question = state.currentQuestion ?: return
        val qNumber = state.currentIndex + 1

        val optionsText = question.answer_options.mapIndexed { index, option ->
            val letter = ('A' + index)
            "$letter. $option"
        }.joinToString(". ")

        AudioAssistant.speak("Pertanyaan $qNumber. ${question.question_text}. Pilihan jawaban: $optionsText")
    }
}

// ── Data classes ───────────────────────────────────────────

enum class QuizStatus {
    /** No quiz active — waiting for data or user to generate */
    IDLE,
    /** Loading quiz from HTTP endpoint */
    LOADING,
    /** Question is displayed, waiting for answer */
    PLAYING,
    /** Answer selected, showing feedback */
    FEEDBACK,
    /** All questions answered */
    FINISHED
}

data class QuizUiState(
    val status: QuizStatus = QuizStatus.IDLE,
    val gameData: QuizGameData? = null,
    val currentIndex: Int = 0,
    val score: Int = 0,
    val totalQuestions: Int = 0,
    val selectedAnswer: Int? = null,
    val isCorrect: Boolean? = null,
    val feedbackText: String = "",
    val statusText: String = "Menunggu kuis..."
) {
    /** The current question being displayed, or null if quiz is not active. */
    val currentQuestion: QuizQuestionData?
        get() = gameData?.questions?.getOrNull(currentIndex)

    /** Progress fraction (0..1) for progress indicator. */
    val progress: Float
        get() = if (totalQuestions > 0) (currentIndex.toFloat() / totalQuestions) else 0f
}
