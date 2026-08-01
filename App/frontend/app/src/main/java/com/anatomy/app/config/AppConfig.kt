package com.anatomy.app.config

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.configDataStore by preferencesDataStore(name = "app_config")

object AppConfig {
    // Config keys
    private val BASE_URL_KEY = stringPreferencesKey("base_url")
    private val WS_CHAT_PATH_KEY = stringPreferencesKey("ws_chat_path")
    private val WS_VOICE_PATH_KEY = stringPreferencesKey("ws_voice_path")

    // Default values
    private const val DEFAULT_BASE_URL = "https://backend.rkiprojek.web.id/"
    private const val DEFAULT_WS_CHAT_PATH = "/ws/chat"
    private const val DEFAULT_WS_VOICE_PATH = "/ws/voice"
    private const val DEFAULT_WS_SCAN_PATH = "/ws/scan"

    /**
     * Get Base URL from configuration Stream or use default
     */
    fun getBaseUrlFlow(context: Context): Flow<String> {
        return context.configDataStore.data.map { preferences ->
            preferences[BASE_URL_KEY] ?: DEFAULT_BASE_URL
        }
    }

    /**
     * Get Base URL value (non-blocking, uses default if not configured)
     */
    fun getBaseUrlDefault(): String = DEFAULT_BASE_URL

    /**
     * Set Base URL in configuration
     */
    suspend fun setBaseUrl(context: Context, url: String) {
        context.configDataStore.edit { preferences ->
            preferences[BASE_URL_KEY] = url
        }
    }

    /**
     * Get WebSocket Chat Path from configuration Stream or use default
     */
    fun getWsChatPathFlow(context: Context): Flow<String> {
        return context.configDataStore.data.map { preferences ->
            preferences[WS_CHAT_PATH_KEY] ?: DEFAULT_WS_CHAT_PATH
        }
    }

    /**
     * Get WebSocket Chat Path value (non-blocking, uses default if not configured)
     */
    fun getWsChatPathDefault(): String = DEFAULT_WS_CHAT_PATH

    /**
     * Get WebSocket Voice Path from configuration Stream or use default
     */
    fun getWsVoicePathFlow(context: Context): Flow<String> {
        return context.configDataStore.data.map { preferences ->
            preferences[WS_VOICE_PATH_KEY] ?: DEFAULT_WS_VOICE_PATH
        }
    }

    /**
     * Get WebSocket Voice Path value (non-blocking, uses default if not configured)
     */
    fun getWsVoicePathDefault(): String = DEFAULT_WS_VOICE_PATH

    /**
     * Get WebSocket Scan Path value (for LLM scan AI requests)
     *
     * FALLBACK: If backend doesn't support /ws/scan yet, use /ws/voice
     * TODO: Change back to DEFAULT_WS_SCAN_PATH when backend is ready
     */
    fun getWsScanPathDefault(): String = DEFAULT_WS_VOICE_PATH // Fallback to voice endpoint

    /**
     * Set WebSocket Chat Path in configuration
     */
    suspend fun setWsChatPath(context: Context, path: String) {
        context.configDataStore.edit { preferences ->
            preferences[WS_CHAT_PATH_KEY] = path
        }
    }

    /**
     * Set WebSocket Voice Path in configuration
     */
    suspend fun setWsVoicePath(context: Context, path: String) {
        context.configDataStore.edit { preferences ->
            preferences[WS_VOICE_PATH_KEY] = path
        }
    }
}
