package com.anatomy.app.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import android.content.Context
import com.anatomy.app.utils.TokenManager
import com.anatomy.app.config.AppConfig
import okhttp3.Interceptor

object HttpClientFactory {
    
    fun createApiService(context: Context): ApiService {
        val baseUrl = AppConfig.getBaseUrlDefault() // Use configured base URL
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        
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
        
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(tokenInterceptor)
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
            .build()
    }
    
    fun getBaseUrl(context: Context): String = AppConfig.getBaseUrlDefault()
}
