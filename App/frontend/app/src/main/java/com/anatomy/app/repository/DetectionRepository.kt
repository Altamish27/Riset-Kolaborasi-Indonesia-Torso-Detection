package com.anatomy.app.repository

import android.content.Context
import com.anatomy.app.network.DetectionApiResponse
import com.anatomy.app.network.HttpClientFactory
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

/**
 * Simple repository to encapsulate image detection network call.
 * Returns RepositoryResult to avoid throwing from UI.
 */
object DetectionRepository {

    sealed class RepositoryResult {
        data class Success(val response: DetectionApiResponse) : RepositoryResult()
        data class Failure(val code: Int? = null, val message: String) : RepositoryResult()
    }

    suspend fun detectImageBytes(context: Context, bytes: ByteArray): RepositoryResult {
        return try {
            val requestBody: RequestBody = RequestBody.create("image/jpeg".toMediaTypeOrNull(), bytes)
            val part = MultipartBody.Part.createFormData("file", "scan_image.jpg", requestBody)
            val api = HttpClientFactory.createApiService(context)
            val resp = api.detectOrgan(part)
            RepositoryResult.Success(resp)
        } catch (e: retrofit2.HttpException) {
            val code = e.code()
            val msg = e.response()?.errorBody()?.string() ?: e.message()
            RepositoryResult.Failure(code, msg)
        } catch (e: Exception) {
            RepositoryResult.Failure(null, e.message ?: "Unknown error")
        }
    }
}
