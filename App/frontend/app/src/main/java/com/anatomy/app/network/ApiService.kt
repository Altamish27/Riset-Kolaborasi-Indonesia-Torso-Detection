package com.anatomy.app.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody

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

    @GET("/chat/sessions")
    suspend fun listSessions(): List<SessionSummary>

    @GET("/sessions")
    suspend fun listSessionsLegacy(): List<SessionSummary>

    @GET("/chat/sessions/{session_id}/history")
    suspend fun getSessionHistory(
        @Path("session_id") sessionId: String
    ): SessionHistoryResponse

    @POST("/chat/generate_quiz")
    suspend fun generateQuiz(
        @Query("topic") topic: String
    ): GenerateQuizResponse

    @Multipart
    @POST("/api/detect")
    suspend fun detectOrgan(
        @Part file: MultipartBody.Part
    ): DetectionApiResponse

}

