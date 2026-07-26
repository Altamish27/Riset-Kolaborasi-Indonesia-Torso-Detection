package com.anatomy.app.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
// Camera capture handled via TFLiteObjectAnalyzer.requestCapture
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
 
import com.anatomy.app.repository.DetectionRepository
import com.anatomy.app.helper.HapticHelper
import com.anatomy.app.helper.AudioAssistant
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anatomy.app.helper.VoiceRecognitionHelper
import com.anatomy.app.services.LLMService
import com.anatomy.app.ui.theme.NeonAmber
import com.anatomy.app.utils.OrganUtils
import com.anatomy.app.ui.theme.NeonCyan
import com.anatomy.app.ui.theme.NeonGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.util.Locale
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView

@Composable
fun ScanAnatomyScreen(isActive: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val llmService = remember { LLMService(context) }
    val voiceHelper = remember { VoiceRecognitionHelper(context) }

    var currentDetection by remember { mutableStateOf<DetectionResult?>(null) }
    var statusText by remember { mutableStateOf("Layar pemindaian aktif. Arahkan kamera ke model organ, lalu ucapkan 'Pindai' atau tekan tombol untuk mengambil gambar.") }
    var detectionThreshold by remember { mutableStateOf(0.25f) }
    var isLearningActive by remember { mutableStateOf(false) } // kept for compatibility but not used for auto-capture
    var lockedOrgan by remember { mutableStateOf<String?>(null) }
    var isAiBusy by remember { mutableStateOf(false) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf("standby") }
    var startCommandRetry by remember { mutableStateOf(0) }
    var isPageActive by remember { mutableStateOf(isActive) }
    var sessionToken by remember { mutableStateOf(0) }

    // PreviewView reference for single-shot captures (available once CameraPreview binds)
    var previewRef by remember { mutableStateOf<PreviewView?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var isCameraReady by remember { mutableStateOf(false) }

    // Uploading state shared across handlers
    var isUploading by remember { mutableStateOf(false) }

    // Helper function to process captured bytes (validate size, upload, TTS/haptic)
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun processCapturedBytes(bytes: ByteArray) {
        isUploading = true
        try {
            HapticHelper.shortBuzz()
            AudioAssistant.speak("Mengambil gambar, mohon tunggu...")

            val MAX_BYTES = 10L * 1024L * 1024L
            if (bytes.size.toLong() > MAX_BYTES) {
                val warn = "Ukuran gambar terlalu besar. Maksimal 10 megabita. Silakan coba lagi dengan jarak lebih dekat atau gunakan pencahayaan lebih baik."
                statusText = warn
                AudioAssistant.speak(warn)
                HapticHelper.longBuzz()
                return
            }

            val result = withContext(Dispatchers.IO) {
                DetectionRepository.detectImageBytes(context, bytes)
            }

            when (result) {
                is DetectionRepository.RepositoryResult.Success -> {
                    val resp = result.response
                    if (resp.status == "detected") {
                        val rawOrgan = resp.class_id ?: resp.class_name ?: ""
                        val sanitizedOrgan = OrganUtils.sanitizeOrganName(rawOrgan)

                        if (!OrganUtils.isValidOrganName(sanitizedOrgan)) {
                            val invalidMessage = "Organ terdeteksi tetapi tidak dikenali. ${resp.description}"
                            statusText = invalidMessage
                            AudioAssistant.speak(invalidMessage)
                            HapticHelper.shortBuzz()
                            return
                        }

                        lockedOrgan = sanitizedOrgan
                        statusText = "Terdeteksi: $sanitizedOrgan (confidence=${resp.confidence})"
                        AudioAssistant.speak("Model organ terdeteksi: $sanitizedOrgan.")
                        HapticHelper.doubleBuzz()

                        val explanation = try {
                            withContext(Dispatchers.IO) {
                                llmService.getExplanationText(sanitizedOrgan)
                            }
                        } catch (e: Exception) {
                            Log.e("ScanAnatomy", "LLM Error: ${e.message}", e)
                            null
                        }

                        val explanationText = if (!explanation.isNullOrBlank() &&
                            !explanation.contains("tidak tersedia", ignoreCase = true) &&
                            !explanation.contains("invalid", ignoreCase = true) &&
                            !explanation.contains("tidak valid", ignoreCase = true)
                        ) {
                            explanation.trim()
                        } else {
                            null
                        }

                        if (explanationText != null) {
                            val speakText = "Terdeteksi organ $sanitizedOrgan. $explanationText"
                            statusText = explanationText
                            AudioAssistant.speak(speakText)
                            HapticHelper.doubleBuzz()
                        } else {
                            val fallback = "Terdeteksi organ $sanitizedOrgan. Layanan penjelasan detail sedang tidak tersedia, namun kamu bisa menanyakannya di menu QnA."
                            statusText = fallback
                            AudioAssistant.speak(fallback)
                            HapticHelper.shortBuzz()
                        }
                    } else {
                        val speakText = resp.description
                        statusText = resp.description
                        AudioAssistant.speak(speakText)
                        HapticHelper.shortBuzz()
                    }
                }
                is DetectionRepository.RepositoryResult.Failure -> {
                    val msg = result.message ?: "Terjadi kesalahan jaringan"
                    statusText = "Gagal mendeteksi: $msg"
                    AudioAssistant.speak("Gagal mendeteksi. Silakan coba lagi.")
                    HapticHelper.longBuzz()
                }
            }
        } catch (e: Exception) {
            Log.e("ScanAnatomyScreen", "Upload/Detect error", e)
            val err = "Gagal mengunggah atau mendeteksi: ${e.message}"
            statusText = err
            AudioAssistant.speak("Gagal mengunggah atau mendeteksi. Coba lagi.")
            HapticHelper.longBuzz()
        } finally {
            isUploading = false
        }
    }

    // Helper to capture bitmap from PreviewView and pass bytes to processor
    suspend fun captureFromPreviewAndProcess(preview: PreviewView?) {
        if (!isCameraReady || preview == null) {
            val message = "Pratinjau kamera belum siap, silakan tunggu sebentar dan coba lagi."
            statusText = message
            AudioAssistant.speak(message)
            return
        }
        try {
            val bmp: Bitmap? = preview.bitmap
            if (bmp == null) {
                statusText = "Gagal menangkap gambar. Coba lagi."
                AudioAssistant.speak(statusText)
                return
            }
            val bytes = withContext(Dispatchers.IO) {
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                baos.toByteArray()
            }
            processCapturedBytes(bytes)
        } catch (e: Exception) {
            Log.e("ScanAnatomyScreen", "Capture error", e)
            statusText = "Gagal mengambil gambar: ${e.message}"
            AudioAssistant.speak("Gagal mengambil gambar. Coba lagi.")
        }
    }

    fun speak(text: String) {
        if (!AudioAssistant.isVoiceOn) return
        AudioAssistant.speak(text)
    }

    // startLockedLoop kept minimal: allow follow-up QnA while lockedOrgan is set
    fun startLockedLoop() {
        val localSession = sessionToken
        val organ = lockedOrgan
        if (!isPageActive || organ.isNullOrBlank() || isVoiceListening) return

        isVoiceListening = true
        statusText = "Organ $organ terkunci. Silakan bertanya atau ucapkan 'lanjut organ lain'."

        voiceHelper.startListening(
            onResult = { spoken ->
                isVoiceListening = false
                if (!isPageActive || localSession != sessionToken) return@startListening
                val currentOrgan = lockedOrgan ?: return@startListening
                if (spoken.isBlank()) {
                    statusText = "Tidak terdengar. Ulangi pertanyaan atau ucapkan lanjut organ lain."
                    return@startListening
                }

                val norm = spoken.lowercase(Locale.US)
                if (norm.contains("lanjut")) {
                    lockedOrgan = null
                    statusText = "Lanjut ke organ berikutnya."
                    return@startListening
                }

                // Ask LLM for follow-up answer
                scope.launch {
                    isAiBusy = true
                    statusText = "Meminta jawaban AI tentang $currentOrgan..."
                    try {
                        val answer = withContext(Dispatchers.IO) {
                            llmService.askQuestionAboutOrgan(currentOrgan, spoken)
                        }
                        isAiBusy = false
                        if (answer.isBlank()) {
                            statusText = "AI tidak bisa menjawab. Coba lagi."
                            speak("Maaf, saya tidak bisa menjawab itu saat ini.")
                        } else {
                            statusText = "Jawaban tersedia."
                            speak(answer)
                        }
                    } catch (e: Exception) {
                        Log.e("ScanAnatomyScreen", "Error getting AI answer", e)
                        isAiBusy = false
                        statusText = "Error AI. Coba lagi nanti."
                        speak("Ada gangguan AI. Silakan coba lagi nanti.")
                    }
                }
            },
            onError = {
                isVoiceListening = false
                statusText = "Gagal mendengar. Ucapkan lagi."
            }
        )
    }

    fun startLearningCommandLoop() {
        val localSession = sessionToken
        if (!isPageActive || isLearningActive || isAiBusy || isVoiceListening || lockedOrgan != null) return

        isVoiceListening = true
        statusText = "Menunggu perintah suara: mulai belajar"

        voiceHelper.startListening(
            onResult = { spoken ->
                if (!isPageActive || localSession != sessionToken) {
                    isVoiceListening = false
                    return@startListening
                }
                isVoiceListening = false
                if (!isPageActive) return@startListening

                val norm = spoken.lowercase(Locale.US).trim()
                if (norm.contains("mulai belajar")) {
                    startCommandRetry = 0
                    isLearningActive = true
                    currentMode = "detecting"
                    statusText = "Mode belajar aktif. Arahkan kamera ke organ."
                    speak("Mode belajar dimulai. Arahkan kamera ke organ.")
                    return@startListening
                }

                startCommandRetry += 1
                if (startCommandRetry >= 3) {
                    startCommandRetry = 0
                    isLearningActive = true
                    currentMode = "detecting"
                    statusText = "Mode belajar aktif. Arahkan kamera ke organ."
                    speak("Saya mulai mode belajar otomatis.")
                } else {
                    statusText = "Perintah belum dikenali. Ucapkan mulai belajar."
                    scope.launch {
                        delay(500L)
                        if (!isPageActive || localSession != sessionToken) return@launch
                        startLearningCommandLoop()
                    }
                }
            },
            onError = {
                if (!isPageActive || localSession != sessionToken) {
                    isVoiceListening = false
                    return@startListening
                }
                isVoiceListening = false
                if (!isPageActive) return@startListening

                startCommandRetry += 1
                if (startCommandRetry >= 3) {
                    startCommandRetry = 0
                    isLearningActive = true
                    currentMode = "detecting"
                    statusText = "Mode belajar aktif. Arahkan kamera ke organ."
                    speak("Saya mulai mode belajar otomatis.")
                } else {
                    statusText = "Gagal mendengar. Ucapkan mulai belajar."
                    scope.launch {
                        delay(700L)
                        if (!isPageActive || localSession != sessionToken) return@launch
                        startLearningCommandLoop()
                    }
                }
            }
        )
    }

    // Continuous frame analyzer disabled — single-shot capture only.

    LaunchedEffect(detectionThreshold) { /* no-op: threshold retained for compatibility */ }

    LaunchedEffect(isActive) {
        isPageActive = isActive
        if (!isActive) {
            // Increment session token IMMEDIATELY (synchronous) so all in-flight
            // onDetection / onResult callbacks see the stale token and bail out.
            sessionToken += 1
            // Stop voice recognizer right away — no delay.
            voiceHelper.stopListening()
            isVoiceListening = false
            isAiBusy = false
            // Small grace period for any dispatched-IO work to complete, then
            // disconnect LLM so the WebSocket isn't leaked.
            delay(500L)
            llmService.disconnect()
            currentMode = "standby"
            return@LaunchedEffect
        }

        if (!AudioAssistant.isVoiceOn) {
            AudioAssistant.cycleMode()
        }
        // Brief greeting for blind accessibility and start voice listening for capture
        speak(statusText)
        delay(1200L)
        if (!isPageActive) return@LaunchedEffect
        // Start simple voice listening for capture trigger
        isVoiceListening = true
        voiceHelper.startListening(
            onResult = { spoken ->
                isVoiceListening = false
                if (!isPageActive) return@startListening
                val norm = spoken.lowercase(Locale.US)
                val triggers = listOf("pindai", "foto", "ambil", "scan", "jelaskan")
                if (triggers.any { norm.contains(it) }) {
                    scope.launch {
                        if (!isCameraReady) {
                            val notReady = "Pratinjau kamera belum siap, silakan tunggu sebentar dan coba lagi."
                            statusText = notReady
                            AudioAssistant.speak(notReady)
                            return@launch
                        }
                        if (isUploading) return@launch
                        isUploading = true
                        val startMsg = "Mengambil gambar..."
                        statusText = startMsg
                        AudioAssistant.speak(startMsg)
                        HapticHelper.shortBuzz()
                        captureFromPreviewAndProcess(previewRef)
                        isUploading = false
                    }
                } else {
                    statusText = "Perintah tidak dikenali. Tekan tombol atau ucapkan 'pindai'."
                }
            },
            onError = {
                isVoiceListening = false
                statusText = "Gagal mendengar. Tekan tombol untuk mengambil gambar."
            }
        )
    }

    // No continuous learning/auto-capture loops — simplified single-shot flow

    DisposableEffect(Unit) {
        onDispose {
            sessionToken += 1
            isPageActive = false
            voiceHelper.destroy()
            llmService.disconnect()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "status_glow")
    val statusGlow by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { contentDescription = "Halaman Mode Scan Anatomi" }
    ) {
        CameraPreview(
            isActive = isActive,
            analyzer = null,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Pratinjau kamera. Arahkan kamera ke organ yang ingin dipelajari" },
            onPreviewReady = { pv -> previewRef = pv },
            onImageCaptureReady = { capture -> imageCaptureRef = capture },
            onCameraReady = { isCameraReady = true }
        )

        ScanOverlay(
            isScanning = isActive && isLearningActive && lockedOrgan == null,
            detection = currentDetection,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0f)
                        )
                    )
                )
                .padding(top = 48.dp, bottom = 16.dp, start = 20.dp, end = 20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Mode Scan Anatomi",
                    style = MaterialTheme.typography.headlineLarge,
                    color = NeonCyan,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(NeonCyan.copy(alpha = 0.15f * statusGlow))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (currentDetection != null) NeonGreen else NeonAmber)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (currentDetection != null) NeonGreen else NeonAmber,
                        modifier = Modifier.semantics { contentDescription = statusText }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Mode: $currentMode",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Threshold deteksi: ${(detectionThreshold * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonAmber,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = detectionThreshold,
                    onValueChange = { detectionThreshold = it },
                    valueRange = 0.05f..0.95f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Capture snapshot from CameraX preview and upload for backend detection
                // `isUploading` and `processCapturedBytes` are declared above and shared.

                // Manual shutter button for accessible capture
                Button(
                    onClick = {
                        if (!isCameraReady) {
                            val notReady = "Pratinjau kamera belum siap, silakan tunggu sebentar dan coba lagi."
                            statusText = notReady
                            AudioAssistant.speak(notReady)
                            return@Button
                        }
                        if (isUploading) return@Button
                        isUploading = true
                        val startMsg = "Mengambil gambar, mohon tunggu. Sedang menganalisis gambar."
                        statusText = startMsg
                        AudioAssistant.speak(startMsg)
                        HapticHelper.shortBuzz()

                        scope.launch {
                            captureFromPreviewAndProcess(previewRef)
                            isUploading = false
                        }
                    },
                    modifier = Modifier
                        .semantics { contentDescription = "Tombol ambil foto model organ tubuh untuk dideteksi" }
                        .height(48.dp)
                        .widthIn(min = 120.dp)
                ) {
                    Text(text = "Ambil Foto untuk Deteksi")
                }

                if (isUploading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = "Sedang menganalisis gambar" })
                }
            }
        }
    }
}
