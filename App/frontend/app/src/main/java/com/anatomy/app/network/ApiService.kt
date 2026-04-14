package com.anatomy.app.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded

/**
 * API Service Interface for HTTP endpoints
 */
interface ApiService {
    
    @POST("/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse
    
    @FormUrlEncoded
    @POST("/auth/token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): AuthResponse
    
    @POST("/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): AuthResponse
    
    @POST("/auth/logout")
    suspend fun logout(@Body request: RefreshRequest): AuthResponse
    
    // LLM explanations are now fetched via WebSocket /ws/chat (ChatWebSocketClient)
    // This keeps the connection stateful and integrates with backend chat infrastructure

    // Create a new chat session (server will return session_id)
    @POST("/chat/sessions")
    suspend fun createSession(): SessionResponse

}

