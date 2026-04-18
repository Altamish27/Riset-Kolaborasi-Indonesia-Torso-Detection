package com.anatomy.app.repository

import android.content.Context
import android.util.Log
import com.anatomy.app.network.ApiService
import com.anatomy.app.network.GenerateQuizResponse
import com.anatomy.app.network.QuizGameData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * QuizRepository — Shared state holder for quiz data + HTTP quiz generation.
 *
 * Acts as a bridge between:
 *   - QnaScreen (which receives trigger_minigame from WebSocket)
 *   - HTTP endpoint POST /chat/generate_quiz (user-initiated quiz generation)
 *   - QuizScreen/QuizViewModel (which consumes and renders the quiz)
 *
 * Lifecycle: Created once per MainPagerScreen and shared between QnaScreen and QuizScreen.
 */
class QuizRepository(
    private val apiService: ApiService,
    private val context: Context
) {

    private val TAG = "QuizRepository"

    private val _pendingQuiz = MutableStateFlow<QuizGameData?>(null)

    /** Observable quiz data. QuizViewModel collects this to start the quiz. */
    val pendingQuiz: StateFlow<QuizGameData?> = _pendingQuiz.asStateFlow()

    /** Loading state for HTTP quiz generation. */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Error message from last failed quiz generation. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Generate a quiz via HTTP endpoint POST /chat/generate_quiz?topic={topic}.
     *
     * The backend uses the user's latest chat session + LangChain to generate
     * 3-5 multiple-choice questions focused on the given topic.
     *
     * Authorization header is handled by the OkHttp tokenInterceptor.
     * Token refresh on 401 is handled by the OkHttp Authenticator.
     *
     * @param topic The quiz topic (e.g. "jantung", "paru-paru")
     * @return Result containing the GenerateQuizResponse (session_id + quiz)
     */
    suspend fun generateQuiz(topic: String): Result<GenerateQuizResponse> {
        _isLoading.value = true
        _error.value = null
        return try {
            Log.d(TAG, "Generating quiz for topic: $topic")
            val response = apiService.generateQuiz(topic)
            Log.d(TAG, "Quiz generated: session=${response.session_id}, " +
                    "topic=${response.quiz.topic}, questions=${response.quiz.questions.size}")
            _pendingQuiz.value = response.quiz
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate quiz for topic: $topic", e)
            _error.value = e.message ?: "Gagal membuat kuis"
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Submit quiz data received from the backend trigger_minigame response (WebSocket).
     * This will notify QuizViewModel to start the quiz.
     */
    fun submitQuizData(gameData: QuizGameData) {
        Log.d(TAG, "Quiz submitted: topic='${gameData.topic}', questions=${gameData.questions.size}")
        _pendingQuiz.value = gameData
    }

    /**
     * Clear the pending quiz after it has been consumed by QuizViewModel.
     */
    fun clearPendingQuiz() {
        Log.d(TAG, "Pending quiz cleared")
        _pendingQuiz.value = null
    }

    /**
     * Clear any error state (e.g. after user acknowledges error).
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Check if there is a pending quiz waiting to be consumed.
     */
    fun hasPendingQuiz(): Boolean = _pendingQuiz.value != null
}
