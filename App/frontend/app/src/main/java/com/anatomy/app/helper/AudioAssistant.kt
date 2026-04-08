package com.anatomy.app.helper

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * AudioAssistant — Centralized singleton for all voice output.
 *
 * Supports three modes:
 *   - VOICE_ON: Full TTS + text display
 *   - TEXT_ONLY: No TTS, only text display (for sighted users / quiet environments)
 *
 * Uses Indonesian locale (id-ID) by default.
 */
object AudioAssistant {

    private const val TAG = "AudioAssistant"

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    /** Whether voice guidance is active (TTS speaks aloud). */
    var isVoiceOn = true
        private set

    /** Whether text-only mode is active (no TTS but text is still displayed). */
    var isTextOnly = false
        private set

    /** True when voice OR text-only is enabled — i.e. the assistant is "active". */
    val isEnabled: Boolean get() = isVoiceOn || isTextOnly

    /**
     * The most recent text passed to [speak] or [speakQueued].
     * UI can observe this to display text for sighted users even in text-only mode.
     */
    var lastSpokenText: String = ""
        private set

    /**
     * Callback invoked when an utterance finishes speaking.
     * Screens can set this to react after TTS completes.
     */
    var onUtteranceCompleted: (() -> Unit)? = null

    /**
     * Initialize the TTS engine. Should be called once from Application.onCreate().
     */
    fun init(context: Context) {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale("id", "ID")
                val result = tts?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.w(TAG, "Indonesian locale not available, using default.")
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.0f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        onUtteranceCompleted?.invoke()
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })

                isInitialized = true
                Log.d(TAG, "TTS initialized successfully with id-ID locale.")
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    /**
     * Speak the given [text]. Interrupts any currently playing speech.
     * In text-only mode, stores the text but does not speak.
     */
    fun speak(text: String) {
        lastSpokenText = text
        if (isTextOnly) {
            // In text-only mode, fire completion callback immediately
            onUtteranceCompleted?.invoke()
            return
        }
        if (!isVoiceOn) return
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet. Ignoring speak request.")
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Speak the given [text], queued after current speech.
     * In text-only mode, stores the text but does not speak.
     */
    fun speakQueued(text: String) {
        lastSpokenText = text
        if (isTextOnly) return
        if (!isVoiceOn) return
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet. Ignoring speak request.")
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    /**
     * Stop any currently playing speech immediately.
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * Cycle mode: VOICE_ON → TEXT_ONLY → VOICE_ON.
     * Returns a description string for the new mode.
     */
    fun cycleMode(): String {
        return if (isVoiceOn && !isTextOnly) {
            // Switch to text-only
            isVoiceOn = false
            isTextOnly = true
            stop()
            "Mode teks saja"
        } else {
            // Switch back to voice on
            isVoiceOn = true
            isTextOnly = false
            "Panduan suara diaktifkan"
        }
    }

    /**
     * Shutdown the TTS engine.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
