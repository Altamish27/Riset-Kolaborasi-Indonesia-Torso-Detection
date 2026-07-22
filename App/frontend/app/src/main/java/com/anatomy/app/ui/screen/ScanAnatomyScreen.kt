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
import com.anatomy.app.ui.theme.NeonCyan
import com.anatomy.app.ui.theme.NeonGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import android.util.Log

@Composable
fun ScanAnatomyScreen(isActive: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val llmService = remember { LLMService(context) }
    val voiceHelper = remember { VoiceRecognitionHelper(context) }

    var currentDetection by remember { mutableStateOf<DetectionResult?>(null) }
    var statusText by remember { mutableStateOf("Ucapkan mulai belajar untuk memulai") }
    var detectionThreshold by remember { mutableStateOf(0.25f) }
    var isLearningActive by remember { mutableStateOf(false) }
    var lockedOrgan by remember { mutableStateOf<String?>(null) }
    var isAiBusy by remember { mutableStateOf(false) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf("standby") }
    var startCommandRetry by remember { mutableStateOf(0) }
    var isPageActive by remember { mutableStateOf(isActive) }
    var sessionToken by remember { mutableStateOf(0) }

    fun speak(text: String) {
        if (!AudioAssistant.isVoiceOn) return
        AudioAssistant.speak(text)
    }

    fun startLockedLoop() {
        val localSession = sessionToken
        val organ = lockedOrgan
        if (!isPageActive || organ.isNullOrBlank() || isVoiceListening) return

        isVoiceListening = true
        statusText = "Organ $organ terkunci. Silakan bertanya atau ucapkan lanjut organ lain."

        voiceHelper.startListening(
            onResult = { spoken ->
                if (!isPageActive || localSession != sessionToken) {
                    isVoiceListening = false
                    return@startListening
                }
                isVoiceListening = false
                if (!isPageActive) return@startListening

                val currentOrgan = lockedOrgan
                if (currentOrgan.isNullOrBlank()) return@startListening
                val norm = spoken.lowercase(Locale.US).trim()

                if (norm.contains("lanjut organ lain")) {
                    statusText = "Baik, lanjut ke organ berikutnya."
                    speak("Baik, lanjut organ lain.")
                    lockedOrgan = null
                    currentDetection = null
                    currentMode = "detecting"
                    return@startListening
                }

                if (spoken.isBlank()) {
                    statusText = "Tidak terdengar. Ulangi pertanyaan atau ucapkan lanjut organ lain."
                    scope.launch {
                        delay(500L)
                        if (!isPageActive || localSession != sessionToken) return@launch
                        startLockedLoop()
                    }
                    return@startListening
                }

                isAiBusy = true
                statusText = "Meminta jawaban AI tentang $currentOrgan..."
                scope.launch {
                    try {
                        val answer = withContext(Dispatchers.IO) {
                            llmService.askQuestionAboutOrgan(currentOrgan, spoken)
                        }
                        isAiBusy = false
                        if (!isPageActive || localSession != sessionToken || lockedOrgan == null) return@launch
                        
                        if (answer.isBlank() || answer.contains("Maaf", ignoreCase = true) || 
                            answer.contains("tidak dapat", ignoreCase = true)) {
                            statusText = "AI tidak bisa menjawab. Coba pertanyaan lain atau lanjut organ lain."
                            speak("Maaf, saya tidak bisa menjawab itu. Tanya yang lain atau lanjut organ lain.")
                        } else {
                            statusText = "Silakan tanya lagi atau ucapkan lanjut organ lain."
                            speak(answer)
                        }
                        delay(1200L)
                        if (!isPageActive || localSession != sessionToken) return@launch
                        startLockedLoop()
                    } catch (e: Exception) {
                        Log.e("ScanAnatomyScreen", "Error getting AI answer", e)
                        isAiBusy = false
                        if (!isPageActive || localSession != sessionToken) return@launch
                        statusText = "Error AI: Coba lagi atau lanjut organ lain."
                        speak("Ada gangguan AI. Coba lagi atau lanjut organ lain.")
                        delay(1200L)
                        if (!isPageActive || localSession != sessionToken) return@launch
                        startLockedLoop()
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
                statusText = "Gagal mendengar. Ucapkan lagi atau lanjut organ lain."
                scope.launch {
                    delay(600L)
                    if (!isPageActive || localSession != sessionToken) return@launch
                    startLockedLoop()
                }
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

    val analyzer = remember {
        TFLiteObjectAnalyzer(
            context = context,
            onDetection = { detection ->
                scope.launch {
                    val localSession = sessionToken
                    if (!isPageActive || !isLearningActive || lockedOrgan != null || isAiBusy) return@launch

                    currentDetection = detection
                    if (detection == null) {
                        statusText = "Mencari organ..."
                        return@launch
                    }

                    val organ = detection.organName
                    lockedOrgan = organ
                    currentMode = "locked"
                    isAiBusy = true
                    statusText = "Organ terkunci: $organ. Meminta penjelasan AI..."

                    try {
                        val explanation = withContext(Dispatchers.IO) {
                            llmService.getExplanationText(organ)
                        }
                        isAiBusy = false
                        if (!isPageActive || localSession != sessionToken || lockedOrgan == null) return@launch

                        if (explanation.isBlank() || explanation.contains("Maaf", ignoreCase = true) || 
                            explanation.contains("tidak dapat", ignoreCase = true)) {
                            statusText = "Tidak ada penjelasan untuk $organ. Deteksi organ lain."
                            speak("Tidak ada penjelasan. Silakan sorot organ lain.")
                            // Auto-unlock after failed explanation
                            delay(3000L)
                            if (!isPageActive || localSession != sessionToken) return@launch
                            lockedOrgan = null
                            currentDetection = null
                            currentMode = "detecting"
                            statusText = "Mencari organ..."
                        } else {
                            statusText = "Organ $organ terkunci. Dengarkan penjelasan."
                            speak(explanation)
                            delay(1200L)
                            if (!isPageActive || localSession != sessionToken) return@launch
                            startLockedLoop()
                        }
                    } catch (e: Exception) {
                        Log.e("ScanAnatomyScreen", "Error getting AI explanation", e)
                        isAiBusy = false
                        if (!isPageActive || localSession != sessionToken || lockedOrgan == null) return@launch
                        statusText = "Error AI. Deteksi organ lain atau coba lagi."
                        speak("Ada gangguan AI. Silakan coba organ lain.")
                        // Auto-unlock after error
                        delay(3000L)
                        if (!isPageActive || localSession != sessionToken) return@launch
                        lockedOrgan = null
                        currentDetection = null
                        currentMode = "detecting"
                        statusText = "Mencari organ..."
                    }
                }
            },
            onDebugLog = {},
            initialConfidenceThreshold = detectionThreshold
        )
    }

    LaunchedEffect(detectionThreshold) {
        analyzer.updateConfidenceThreshold(detectionThreshold)
    }

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
        speak("Mode scan siap. Ucapkan mulai belajar untuk memulai.")
        delay(1000L)
        if (!isPageActive) return@LaunchedEffect  // guard: page may have deactivated during delay
        if (!isLearningActive && lockedOrgan == null) {
            startLearningCommandLoop()
        }
    }

    LaunchedEffect(isActive, isLearningActive, lockedOrgan, isAiBusy, isVoiceListening) {
        if (!isPageActive) return@LaunchedEffect
        if (!isLearningActive && lockedOrgan == null && !isAiBusy && !isVoiceListening) {
            startLearningCommandLoop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Invalidate session token immediately so any lingering coroutine
            // callbacks that captured `localSession` will see a mismatch and
            // return early without touching disposed Compose state.
            sessionToken += 1
            isPageActive = false
            voiceHelper.destroy()
            llmService.disconnect()
            analyzer.close()
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
            analyzer = analyzer,
            modifier = Modifier.fillMaxSize()
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
                var isUploading by remember { mutableStateOf(false) }
                var lastAutoCaptureMs by remember { mutableStateOf(0L) }
                var detectionFirstSeenMs by remember { mutableStateOf(0L) }
                val AUTO_CAPTURE_COOLDOWN_MS = 3500L
                val DETECTION_STABILITY_MS = 900L

                // Auto-capture when analyzer reports a stable detection
                LaunchedEffect(currentDetection, isLearningActive) {
                    if (!isPageActive) return@LaunchedEffect
                    if (!isLearningActive) return@LaunchedEffect

                    val now = System.currentTimeMillis()
                    if (currentDetection != null) {
                        if (detectionFirstSeenMs == 0L) detectionFirstSeenMs = now
                    } else {
                        detectionFirstSeenMs = 0L
                    }

                    if (currentDetection != null && lockedOrgan == null && !isUploading) {
                        if (detectionFirstSeenMs > 0L && now - detectionFirstSeenMs >= DETECTION_STABILITY_MS) {
                            if (now - lastAutoCaptureMs > AUTO_CAPTURE_COOLDOWN_MS) {
                                lastAutoCaptureMs = now
                                // Request a one-shot capture from analyzer
                                try {
                                    isUploading = true
                                    val startMsg = "Mengambil foto otomatis, mohon tunggu. Sedang menganalisis gambar."
                                    statusText = startMsg
                                    AudioAssistant.speak(startMsg)
                                    HapticHelper.shortBuzz()

                                    (analyzer as? TFLiteObjectAnalyzer)?.requestCapture { bytes ->
                                        scope.launch {
                                            processCapturedBytes(bytes)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("ScanAnatomyScreen", "Auto-capture error", e)
                                    isUploading = false
                                }
                            }
                        }
                    }
                }

                // Manual shutter button for accessible capture
                Button(
                    onClick = {
                        if (isUploading) return@Button
                        try {
                            isUploading = true
                            val startMsg = "Mengambil foto, mohon tunggu. Sedang menganalisis gambar."
                            statusText = startMsg
                            AudioAssistant.speak(startMsg)
                            HapticHelper.shortBuzz()

                            (analyzer as? TFLiteObjectAnalyzer)?.requestCapture { bytes ->
                                scope.launch {
                                    processCapturedBytes(bytes)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ScanAnatomyScreen", "Capture request failed", e)
                            isUploading = false
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = "Tombol ambil foto model organ tubuh untuk dideteksi" }
                ) {
                    Text(text = "Ambil Foto untuk Deteksi")
                }

                if (isUploading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = "Sedang menganalisis gambar" })
                }

                // Helper function to process captured bytes (validate size, upload, TTS/haptic)
                suspend fun processCapturedBytes(bytes: ByteArray) {
                    try {
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
                                    val speakText = "Model organ terdeteksi: ${resp.class_name ?: resp.class_id}."
                                    statusText = "Terdeteksi: ${resp.class_name} (confidence=${resp.confidence})"
                                    lockedOrgan = resp.class_name ?: resp.class_id
                                    AudioAssistant.speak(speakText)
                                    HapticHelper.doubleBuzz()
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
            }
        }
    }
}
