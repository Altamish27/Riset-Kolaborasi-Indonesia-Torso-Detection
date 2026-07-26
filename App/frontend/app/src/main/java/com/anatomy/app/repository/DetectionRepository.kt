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
            val requestBody: RequestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "scan_image.jpg", requestBody)
            val api = HttpClientFactory.createApiService(context)
            val resp = api.detectOrgan(part)
            RepositoryResult.Success(resp)
        } catch (e: retrofit2.HttpException) {
            val code = e.code()
            val msg = e.response()?.errorBody()?.string() ?: e.message()
            Log.e("ScanAnatomy", "Backend Detection HTTP Failed: $code / $msg", e)
            RepositoryResult.Failure(code, msg, e)
        } catch (e: Exception) {
            Log.e("ScanAnatomy", "Backend Detection Network Call Failed: ${e.localizedMessage}", e)
            RepositoryResult.Failure(null, e.message ?: "Unknown error", e)
        }
    }
}
