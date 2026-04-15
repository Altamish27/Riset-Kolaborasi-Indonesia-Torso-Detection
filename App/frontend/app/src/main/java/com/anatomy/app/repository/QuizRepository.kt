package com.anatomy.app.repository

import android.util.Log
import com.anatomy.app.network.QuizGameData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * QuizRepository — Shared state holder for quiz data.
 *
 * Acts as a bridge between QnaScreen (which receives trigger_minigame from WebSocket)
 * and QuizScreen/QuizViewModel (which consumes and renders the quiz).
 *
 * Lifecycle: Created once per MainPagerScreen and shared between QnaScreen and QuizScreen.
 */
class QuizRepository {

    private val TAG = "QuizRepository"

    private val _pendingQuiz = MutableStateFlow<QuizGameData?>(null)

    /** Observable quiz data. QuizViewModel collects this to start the quiz. */
    val pendingQuiz: StateFlow<QuizGameData?> = _pendingQuiz.asStateFlow()

    /**
     * Submit quiz data received from the backend trigger_minigame response.
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
     * Check if there is a pending quiz waiting to be consumed.
     */
    fun hasPendingQuiz(): Boolean = _pendingQuiz.value != null
}
