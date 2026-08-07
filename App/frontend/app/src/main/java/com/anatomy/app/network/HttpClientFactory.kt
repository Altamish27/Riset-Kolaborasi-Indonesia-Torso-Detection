package com.anatomy.app.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import android.content.Context
import com.anatomy.app.utils.TokenManager
import com.anatomy.app.config.AppConfig
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

object HttpClientFactory {

    private val TAG = "HttpClientFactory"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }
    
    fun createApiService(context: Context): ApiService {
        val baseUrl = getBaseUrl(context)
        
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        // Token Interceptor to add Authorization header
        val tokenInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val token = TokenManager.getAccessToken(context)
            
            val request = if (token != null) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }
            
            chain.proceed(request)
        }

        // Authenticator: automatically refresh token on 401 responses
        val tokenAuthenticator = Authenticator { _: Route?, response: Response ->
            // Only attempt refresh once per request chain to avoid infinite loops
            if (response.request.header("X-Token-Refreshed") != null) {
                Log.w(TAG, "Token already refreshed for this request, giving up")
                return@Authenticator null
            }

            Log.d(TAG, "Got 401, attempting token refresh...")
            val newAccessToken = refreshTokenSync(baseUrl, context)

            if (newAccessToken != null) {
                Log.d(TAG, "Token refreshed successfully, retrying request")
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .header("X-Token-Refreshed", "true")
                    .build()
            } else {
                Log.e(TAG, "Token refresh failed, cannot retry request")
                null
            }
        }
        
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(tokenInterceptor)
            .authenticator(tokenAuthenticator)
            .build()
        
        val contentType = "application/json".toMediaType()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
        
        return retrofit.create(ApiService::class.java)
    }
    
    fun createHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    fun getBaseUrl(context: Context): String = AppConfig.getBaseUrlDefault()

    /**
     * Synchronously refresh the access token using /auth/refresh.
     * Uses a bare OkHttpClient (no interceptors/authenticator) to avoid loops.
     * Returns the new access_token on success, or null on failure.
     */
    private fun refreshTokenSync(baseUrl: String, context: Context): String? {
        val refreshToken = TokenManager.getRefreshToken(context)
        if (refreshToken == null) {
            Log.e(TAG, "No refresh token available for token refresh")
            return null
        }

        return try {
            val bareClient = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val body = json.encodeToString(
                RefreshRequest.serializer(),
                RefreshRequest(refreshToken)
            ).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/auth/refresh")
                .post(body)
                .build()

            val response = bareClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val authResponse = json.decodeFromString(
                        AuthResponse.serializer(),
                        responseBody
                    )
                    val newAccessToken = authResponse.access_token
                    if (newAccessToken != null) {
                        val username = TokenManager.getUsername(context) ?: ""
                        TokenManager.saveTokens(
                            context,
                            newAccessToken,
                            refreshToken, // Keep existing refresh token
                            username
                        )
                        Log.d(TAG, "Token refresh successful, new access token saved")
                        newAccessToken
                    } else {
                        Log.e(TAG, "Refresh response missing access_token")
                        null
                    }
                } else {
                    Log.e(TAG, "Refresh response body is null")
                    null
                }
            } else {
                Log.e(TAG, "Token refresh HTTP failed: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh exception", e)
            null
        }
    }
}
