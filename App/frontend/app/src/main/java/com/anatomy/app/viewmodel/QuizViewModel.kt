package com.anatomy.app.viewmodel

import android.util.Log
import com.anatomy.app.helper.AudioAssistant
import com.anatomy.app.helper.HapticHelper
import com.anatomy.app.network.QuizGameData
import com.anatomy.app.network.QuizQuestionData
import com.anatomy.app.network.SessionHistoryMessage
import com.anatomy.app.repository.ChatRepository
import com.anatomy.app.repository.QuizRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class QuizViewModel(
    private val quizRepository: QuizRepository,
    private val chatRepository: ChatRepository
) {
    private val TAG = "QuizViewModel"

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()
    private var pendingQuizJob: Job? = null
    val isLoading: StateFlow<Boolean> = quizRepository.isLoading
    val error: StateFlow<String?> = quizRepository.error

    fun observePendingQuiz(scope: CoroutineScope) {
        if (pendingQuizJob != null) return
        pendingQuizJob = scope.launch {
            quizRepository.pendingQuiz.collectLatest { pending ->
                if (pending != null) {
                    startQuiz(pending)
                }
            }
        }
    }

    fun stopObservingPendingQuiz() {
        pendingQuizJob?.cancel()
        pendingQuizJob = null
    }

    suspend fun evaluateEntryDecision() {
        val state = _uiState.value
        if (state.status != QuizStatus.IDLE) return
        if (state.entryState == QuizEntryState.CHOOSING_MODE || state.entryState == QuizEntryState.LISTENING_TOPIC) return

        _uiState.value = state.copy(
            entryState = QuizEntryState.CHOOSING_MODE,
            statusText = "Memeriksa sesi tanya jawab aktif..."
        )

        val history = loadActiveChatHistory()
        val hasHistory = history.any { it.content.isNotBlank() }

        _uiState.value = _uiState.value.copy(
            hasChatHistory = hasHistory,
            suggestedTopic = inferTopicFromHistory(history),
            entryState = QuizEntryState.CHOOSING_MODE,
            statusText = "Menunggu pilihan mode kuis lewat suara."
        )
    }

    fun chooseCustomTopicEntry() {
        _uiState.value = _uiState.value.copy(
            entryState = QuizEntryState.LISTENING_TOPIC,
            statusText = "Sebutkan topik baru untuk memulai kuis."
        )
    }

    suspend fun startQuizFromChatHistory() {
        val history = loadActiveChatHistory()
        val topic = if (history.none { it.content.isNotBlank() }) {
            _uiState.value = _uiState.value.copy(
                hasChatHistory = false,
                statusText = "History kosong. Menggunakan topik Anatomi Torso Umum."
            )
            "Anatomi Torso Umum"
        } else {
            inferTopicFromHistory(history)
        }
        generateNewQuiz(topic)
    }

    fun startQuiz(data: QuizGameData) {
        if (data.questions.isEmpty()) {
            Log.w(TAG, "Cannot start quiz: no questions")
            _uiState.value = QuizUiState(
                status = QuizStatus.IDLE,
                entryState = QuizEntryState.LISTENING_TOPIC,
                statusText = "Tidak ada pertanyaan tersedia."
            )
            return
        }

        Log.d(TAG, "Starting quiz: topic='${data.topic}', ${data.questions.size} questions")
        _uiState.value = QuizUiState(
            status = QuizStatus.PLAYING,
            entryState = QuizEntryState.NONE,
            hasChatHistory = _uiState.value.hasChatHistory,
            suggestedTopic = _uiState.value.suggestedTopic,
            gameData = data,
            currentIndex = 0,
            score = 0,
            totalQuestions = data.questions.size,
            statusText = "Pertanyaan 1 dari ${data.questions.size}"
        )

        quizRepository.clearPendingQuiz()
        readCurrentQuestion()
    }

    fun selectAnswer(selectedIndex: Int) {
        val state = _uiState.value
        if (state.status != QuizStatus.PLAYING) return
        if (state.selectedAnswer != null) return

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
            statusText = if (isCorrect) "Betul!" else "Salah"
        )

        if (isCorrect) {
            HapticHelper.longBuzz()
        } else {
            HapticHelper.doubleBuzz()
        }

        AudioAssistant.speak(feedbackText)
    }

    fun nextQuestion() {
        val state = _uiState.value
        if (state.status != QuizStatus.FEEDBACK) return

        val nextIndex = state.currentIndex + 1
        val data = state.gameData ?: return

        if (nextIndex >= data.questions.size) {
            val finalMsg = "Kuis selesai! Skor Anda: ${state.score} dari ${data.questions.size}."
            _uiState.value = state.copy(
                status = QuizStatus.FINISHED,
                currentIndex = nextIndex,
                selectedAnswer = null,
                isCorrect = null,
                feedbackText = finalMsg,
                statusText = "Quiz selesai"
            )
            HapticHelper.longBuzz()
            AudioAssistant.speak(finalMsg)
        } else {
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

    fun resetQuiz() {
        Log.d(TAG, "Quiz reset to entry gate")
        _uiState.value = QuizUiState(
            status = QuizStatus.IDLE,
            entryState = QuizEntryState.CHOOSING_MODE,
            statusText = "Menyiapkan halaman kuis..."
        )
    }

    suspend fun generateNewQuiz(topic: String) {
        val trimmedTopic = topic.trim()
        if (trimmedTopic.isBlank()) {
            _uiState.value = _uiState.value.copy(
                status = QuizStatus.IDLE,
                entryState = QuizEntryState.LISTENING_TOPIC,
                statusText = "Topik kuis tidak boleh kosong"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            status = QuizStatus.IDLE,
            entryState = QuizEntryState.NONE,
            statusText = "Membuat kuis tentang $trimmedTopic..."
        )

        val result = quizRepository.generateQuiz(trimmedTopic)
        result.onSuccess { response ->
            startQuiz(response.quiz)
        }.onFailure { e ->
            _uiState.value = _uiState.value.copy(
                status = QuizStatus.IDLE,
                entryState = QuizEntryState.LISTENING_TOPIC,
                statusText = e.message ?: "Gagal membuat kuis"
            )
        }
    }

    fun clearError() {
        quizRepository.clearError()
    }

    fun matchVoiceAnswer(spokenText: String): Int {
        val question = _uiState.value.currentQuestion ?: return -1
        val normalized = spokenText
            .lowercase()
            .replace(".", " ")
            .replace(",", " ")
            .replace(";", " ")
            .trim()

        val byLetter = when {
            normalized == "a" || normalized.startsWith("a ") || normalized.contains("opsi a") || normalized.contains("pilihan a") -> 0
            normalized == "b" || normalized.startsWith("b ") || normalized.contains("opsi b") || normalized.contains("pilihan b") -> 1
            normalized == "c" || normalized.startsWith("c ") || normalized.contains("opsi c") || normalized.contains("pilihan c") -> 2
            normalized == "d" || normalized.startsWith("d ") || normalized.contains("opsi d") || normalized.contains("pilihan d") -> 3
            else -> -1
        }
        if (byLetter in question.answer_options.indices) return byLetter

        val byNumber = when {
            normalized == "1" || normalized.contains("nomor satu") || normalized.contains("jawaban satu") || normalized.contains("pertama") || normalized.contains("satu") -> 0
            normalized == "2" || normalized.contains("nomor dua") || normalized.contains("jawaban dua") || normalized.contains("kedua") || normalized.contains("dua") -> 1
            normalized == "3" || normalized.contains("nomor tiga") || normalized.contains("jawaban tiga") || normalized.contains("ketiga") || normalized.contains("tiga") -> 2
            normalized == "4" || normalized.contains("nomor empat") || normalized.contains("jawaban empat") || normalized.contains("keempat") || normalized.contains("empat") -> 3
            else -> -1
        }
        if (byNumber in question.answer_options.indices) return byNumber

        question.answer_options.forEachIndexed { index, option ->
            if (normalized.contains(option.lowercase().trim())) return index
        }

        return -1
    }

    private suspend fun loadActiveChatHistory(): List<SessionHistoryMessage> {
        val sessionId = chatRepository.getActiveSessionId() ?: return emptyList()
        return chatRepository.getSessionHistory(sessionId)
    }

    private fun inferTopicFromHistory(history: List<SessionHistoryMessage>): String {
        val lastUserMessage = history
            .asReversed()
            .firstOrNull { it.role.equals("user", ignoreCase = true) && it.content.isNotBlank() }
            ?.content
            ?.trim()

        if (!lastUserMessage.isNullOrBlank()) {
            return lastUserMessage.take(120)
        }

        return "anatomi torso"
    }

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

enum class QuizEntryState {
    NONE,
    CHOOSING_MODE,
    LISTENING_TOPIC
}

enum class QuizStatus {
    IDLE,
    PLAYING,
    FEEDBACK,
    FINISHED
}

data class QuizUiState(
    val status: QuizStatus = QuizStatus.IDLE,
    val entryState: QuizEntryState = QuizEntryState.CHOOSING_MODE,
    val hasChatHistory: Boolean = false,
    val suggestedTopic: String? = null,
    val gameData: QuizGameData? = null,
    val currentIndex: Int = 0,
    val score: Int = 0,
    val totalQuestions: Int = 0,
    val selectedAnswer: Int? = null,
    val isCorrect: Boolean? = null,
    val feedbackText: String = "",
    val statusText: String = "Menunggu kuis..."
) {
    val currentQuestion: QuizQuestionData?
        get() = gameData?.questions?.getOrNull(currentIndex)

    val progress: Float
        get() = if (totalQuestions > 0) (currentIndex.toFloat() / totalQuestions) else 0f
}
