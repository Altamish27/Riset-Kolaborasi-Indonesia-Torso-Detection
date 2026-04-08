package com.anatomy.app.helper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * VoiceRecognitionHelper — Thread-safe wrapper around Android SpeechRecognizer.
 *
 * KEY FIX: SpeechRecognizer MUST be created and operated on the main thread.
 * This helper ensures all calls are dispatched to the main thread via Handler.
 * It also calls cancel+destroy before every new session to clear frozen states.
 */
class VoiceRecognitionHelper(private val context: Context) {

    private val TAG = "VoiceRecognition"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isCurrentlyListening = false

    /**
     * Start listening for voice input.
     * Always cancels any previous session first to avoid frozen states.
     *
     * @param onResult Called with the recognized text string (on main thread).
     * @param onError Called with the SpeechRecognizer error code (on main thread).
     */
    fun startListening(
        onResult: (String) -> Unit,
        onError: (Int) -> Unit
    ) {
        mainHandler.post {
            // Check availability
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.e(TAG, "Speech recognition is not available on this device.")
                onError(-1)
                return@post
            }

            // CRITICAL: Always destroy previous recognizer to clear frozen states
            internalDestroy()

            isCurrentlyListening = true

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Ready for speech...")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Speech started.")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "Speech ended.")
                    }

                    override fun onError(error: Int) {
                        Log.e(TAG, "Recognition error: $error")
                        isCurrentlyListening = false
                        // Dispatch callback on main thread
                        mainHandler.post { onError(error) }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val bestResult = matches?.firstOrNull() ?: ""
                        Log.d(TAG, "Result: $bestResult")
                        isCurrentlyListening = false
                        // Dispatch callback on main thread
                        mainHandler.post { onResult(bestResult) }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            try {
                speechRecognizer?.startListening(intent)
                Log.d(TAG, "Started listening.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start listening", e)
                isCurrentlyListening = false
                onError(-2)
            }
        }
    }

    /**
     * Stop listening gracefully.
     */
    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                isCurrentlyListening = false
                Log.d(TAG, "Stopped listening.")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recognizer", e)
            }
        }
    }

    /**
     * Destroy the recognizer and free resources (internal, call from main thread only).
     */
    private fun internalDestroy() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying recognizer", e)
        }
        speechRecognizer = null
        isCurrentlyListening = false
    }

    /**
     * Destroy the recognizer and free resources.
     * Safe to call from any thread.
     */
    fun destroy() {
        mainHandler.post {
            internalDestroy()
            Log.d(TAG, "VoiceRecognitionHelper destroyed.")
        }
    }

    /** Whether the recognizer is currently listening. */
    val isListening: Boolean get() = isCurrentlyListening
}
