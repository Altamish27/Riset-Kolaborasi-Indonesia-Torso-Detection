package com.anatomy.app.repository

import android.content.Context
import android.util.Log
import com.anatomy.app.network.ApiService
import com.anatomy.app.network.RegisterRequest
import com.anatomy.app.utils.TokenManager

class AuthRepository(
    private val apiService: ApiService,
    private val context: Context
) {
    
    private val TAG = "AuthRepository"
    
    suspend fun register(username: String, email: String, password: String): Result<String> {
        return try {
            val request = RegisterRequest(username, email, password)
            val response = apiService.register(request)
            if (response.message != null) {
                Result.success(response.message)
            } else {
                Result.failure(Exception("Registration response missing message"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Registration failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun login(username: String, password: String): Result<Unit> {
        return try {
            val response = apiService.login(username, password)
            
            if (response.access_token != null && response.refresh_token != null) {
                TokenManager.saveTokens(
                    context,
                    response.access_token,
                    response.refresh_token,
                    username
                )
                Result.success(Unit)
            } else {
                Result.failure(Exception("Login response missing tokens"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun refresh(): Result<Unit> {
        return try {
            val refreshToken = TokenManager.getRefreshToken(context)
            if (refreshToken == null) {
                return Result.failure(Exception("No refresh token available"))
            }
            
            val response = apiService.refresh(
                com.anatomy.app.network.RefreshRequest(refreshToken)
            )
            
            if (response.access_token != null) {
                val username = TokenManager.getUsername(context) ?: return Result.failure(
                    Exception("Username not found")
                )
                TokenManager.saveTokens(
                    context,
                    response.access_token,
                    refreshToken, // Keep the same refresh token if not provided
                    username
                )
                Result.success(Unit)
            } else {
                Result.failure(Exception("Refresh response missing access token"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun logout(): Result<Unit> {
        return try {
            val refreshToken = TokenManager.getRefreshToken(context)
            if (refreshToken != null) {
                apiService.logout(
                    com.anatomy.app.network.RefreshRequest(refreshToken)
                )
            }
            TokenManager.clearTokens(context)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Logout failed", e)
            // Still clear tokens even if logout fails
            TokenManager.clearTokens(context)
            Result.failure(e)
        }
    }
    
    fun isLoggedIn(): Boolean {
        return TokenManager.isLoggedIn(context)
    }
    
    fun getCurrentUsername(): String? {
        return TokenManager.getUsername(context)
    }
}
