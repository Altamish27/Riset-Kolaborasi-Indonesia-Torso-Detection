package com.anatomy.app.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "auth_tokens")

object TokenManager {
    
    private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
    private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val CHAT_SESSION_ID_KEY = stringPreferencesKey("chat_session_id")
    
    fun saveTokens(
        context: Context,
        accessToken: String,
        refreshToken: String,
        username: String
    ) {
        runBlocking(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences[ACCESS_TOKEN_KEY] = accessToken
                preferences[REFRESH_TOKEN_KEY] = refreshToken
                preferences[USERNAME_KEY] = username
            }
        }
    }
    
    fun getAccessToken(context: Context): String? {
        return runBlocking(Dispatchers.IO) {
            try {
                context.dataStore.data.first()[ACCESS_TOKEN_KEY]
            } catch (e: Exception) {
                null
            }
        }
    }
    
    fun getRefreshToken(context: Context): String? {
        return runBlocking(Dispatchers.IO) {
            try {
                context.dataStore.data.first()[REFRESH_TOKEN_KEY]
            } catch (e: Exception) {
                null
            }
        }
    }
    
    fun getUsername(context: Context): String? {
        return runBlocking(Dispatchers.IO) {
            try {
                context.dataStore.data.first()[USERNAME_KEY]
            } catch (e: Exception) {
                null
            }
        }
    }
    
    fun clearTokens(context: Context) {
        runBlocking(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences.clear()
            }
        }
    }
    
    fun isLoggedIn(context: Context): Boolean {
        return getAccessToken(context) != null
    }

    fun saveChatSessionId(context: Context, sessionId: String) {
        runBlocking(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences[CHAT_SESSION_ID_KEY] = sessionId
            }
        }
    }

    fun getChatSessionId(context: Context): String? {
        return runBlocking(Dispatchers.IO) {
            try {
                context.dataStore.data.first()[CHAT_SESSION_ID_KEY]
            } catch (e: Exception) {
                null
            }
        }
    }

    fun clearChatSessionId(context: Context) {
        runBlocking(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences.remove(CHAT_SESSION_ID_KEY)
            }
        }
    }
}
