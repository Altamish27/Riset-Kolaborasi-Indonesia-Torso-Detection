package com.anatomy.app.network

import kotlinx.serialization.Serializable

/**
 * Quiz data models — match the backend `trigger_minigame` tool-call schema exactly.
 *
 * Backend LLM returns:
 * {
 *   "action": "trigger_minigame",
 *   "answer": "...",
 *   "game_data": {
 *     "topic": "Jantung",
 *     "message": "Mari kita uji pengetahuanmu!",
 *     "questions": [
 *       {
 *         "question_text": "...",
 *         "answer_options": ["A", "B", "C", "D"],
 *         "correct_answer_index": 0
 *       }
 *     ]
 *   }
 * }
 */

@Serializable
data class QuizGameData(
    val topic: String = "",
    val message: String = "",
    val questions: List<QuizQuestionData> = emptyList()
)

@Serializable
data class QuizQuestionData(
    val question_text: String = "",
    val answer_options: List<String> = emptyList(),
    val correct_answer_index: Int = 0
)
