package com.anatomy.app.repository

import android.content.Context
import android.util.Log
import com.anatomy.app.network.DetectionApiResponse
import com.anatomy.app.network.HttpClientFactory
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Simple repository to encapsulate image detection network call.
 * Returns RepositoryResult to avoid throwing from UI.
 */
object DetectionRepository {

    sealed class RepositoryResult {
        data class Success(val response: DetectionApiResponse) : RepositoryResult()
        data class Failure(val code: Int? = null, val message: String, val throwable: Throwable? = null) : RepositoryResult()
    }

    suspend fun detectImageBytes(context: Context, bytes: ByteArray): RepositoryResult {
        return try {
            Log.d("ScanAnatomy", "Sending image bytes to backend, size: ${bytes.size} bytes")
            val requestBody: RequestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull(), 0, bytes.size)
            val part = MultipartBody.Part.createFormData("file", "scan_image.jpg", requestBody)
            val api = HttpClientFactory.createApiService(context)
            val response = api.detectOrgan(part)
            val rawResponse = response.errorBody()?.string()
                ?: response.body()?.toString()
                ?: "empty response"
            Log.d("ScanAnatomy", "detectOrgan response code=${response.code()} raw_response=$rawResponse")
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    RepositoryResult.Success(body)
                } else {
                    RepositoryResult.Failure(response.code(), "Response body was null", null)
                }
            } else {
                RepositoryResult.Failure(response.code(), rawResponse, null)
            }
        } catch (e: Exception) {
            Log.e("ScanAnatomy", "Backend Detection Network Call Failed: ${e.localizedMessage}", e)
            RepositoryResult.Failure(null, e.message ?: "Unknown error", e)
        }
    }
}
