package com.anatomy.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anatomy.app.data.OrganEntity
import com.anatomy.app.helper.AudioAssistant
import com.anatomy.app.helper.HapticHelper
import com.anatomy.app.helper.VoiceRecognitionHelper
import com.anatomy.app.services.LLMService
import com.anatomy.app.ui.theme.BoundingBoxColor
import com.anatomy.app.ui.theme.NeonAmber
import com.anatomy.app.ui.theme.NeonCyan
import com.anatomy.app.ui.theme.NeonGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Page 1 — "Mode Scan Anatomi"
 *
 * Full-screen CameraX preview with:
 *   - ScanOverlay (scanning wave + bounding box from TFLite or mock)
 *   - TFLite real object detection with mock fallback
 *   - 1.5s detection hold → TTS prompt → voice confirmation
 *   - On "Ya" → Request LLM explanation → Popup slides up + TTS plays explanation
 *   - "Baca Penjelasan" manual button in text-only mode
 *
 * @param isActive Whether this page is currently visible/settled.
 */
@Composable
fun ScanAnatomyScreen(isActive: Boolean = true) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // LLM Service for getting organ explanations from backend
    val llmService = remember { LLMService(context) }

    // Detection state
    var currentDetection by remember { mutableStateOf<DetectionResult?>(null) }
    var confirmedOrgan by remember { mutableStateOf<String?>(null) }
    var isWaitingConfirmation by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Arahkan kamera ke objek") }
    var isLoadingExplanation by remember { mutableStateOf(false) }
    var debugLogs by remember { mutableStateOf(listOf<String>()) }

    // Popup state
    var showPopup by remember { mutableStateOf(false) }
    var popupExplanation by remember { mutableStateOf("") }
    var popupLabel by remember { mutableStateOf("") }

    // Manual read button state
    var showReadButton by remember { mutableStateOf(false) }

    val voiceHelper = remember { VoiceRecognitionHelper(context) }

    fun appendDebugLog(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        debugLogs = (listOf("[$stamp] $message") + debugLogs).take(8)
    }

    // TFLite analyzer
    val analyzer = remember {
        TFLiteObjectAnalyzer(
            context = context,
            onDetection = { detection ->
                if (!isWaitingConfirmation && confirmedOrgan == null && !showPopup) {
                    currentDetection = detection
                }
            },
            onDebugLog = { message ->
                coroutineScope.launch {
                    appendDebugLog(message)
                }
            }
        )
    }

    // 1.5s hold timer for confirmed detection
    LaunchedEffect(currentDetection) {
        if (currentDetection != null && !isWaitingConfirmation && isActive && !showPopup) {
            delay(1500L)
            if (currentDetection != null && !isWaitingConfirmation && !showPopup) {
                val organ = currentDetection!!.organName
                val label = currentDetection!!.mockLabel
                isWaitingConfirmation = true
                statusText = "Terdeteksi: $organ"
                HapticHelper.doubleBuzz()

                if (AudioAssistant.isVoiceOn) {
                    AudioAssistant.speak("Terdeteksi $organ. Mau dengar penjelasan lanjut?")

                    AudioAssistant.onUtteranceCompleted = {
                        voiceHelper.startListening(
                            onResult = { result ->
                                val normalized = result.lowercase().trim()
                                if (normalized.contains("ya") || normalized.contains("mau") ||
                                    normalized.contains("iya") || normalized.contains("oke")
                                ) {
                                    // User confirmed - request LLM explanation
                                    confirmedOrgan = organ
                                    HapticHelper.shortBuzz()
                                    isLoadingExplanation = true
                                    statusText = "Meminta penjelasan dari AI..."
                                    popupLabel = label
                                    
                                    coroutineScope.launch {
                                        // Fetch explanation from LLM backend
                                        val explanation = withContext(Dispatchers.IO) {
                                            llmService.getExplanationText(organ)
                                        }
                                        
                                        isLoadingExplanation = false
                                        
                                        if (explanation.isNotEmpty()) {
                                            // Show popup with LLM explanation
                                            popupExplanation = explanation
                                            showPopup = true
                                            statusText = "Menjelaskan: $organ"
                                            
                                            // Play explanation via TTS
                                            AudioAssistant.speak(explanation)
                                            AudioAssistant.onUtteranceCompleted = {
                                                // Let user dismiss via swipe
                                            }
                                        } else {
                                            AudioAssistant.speak("Maaf, tidak dapat mendapatkan penjelasan untuk $organ saat ini.")
                                            resetScanState(
                                                { isWaitingConfirmation = false },
                                                { confirmedOrgan = null },
                                                { currentDetection = null },
                                                { showReadButton = false },
                                                { statusText = "Arahkan kamera ke objek" }
                                            )
                                        }
                                    }
                                } else {
                                    AudioAssistant.speak("Baik, scan dilanjutkan.")
                                    resetScanState(
                                        { isWaitingConfirmation = false },
                                        { confirmedOrgan = null },
                                        { currentDetection = null },
                                        { showReadButton = false },
                                        { statusText = "Arahkan kamera ke objek" }
                                    )
                                }
                            },
                            onError = { _ ->
                                AudioAssistant.speak("Maaf, tidak dapat mendengar. Scan dilanjutkan.")
                                resetScanState(
                                    { isWaitingConfirmation = false },
                                    { confirmedOrgan = null },
                                    { currentDetection = null },
                                    { showReadButton = false },
                                    { statusText = "Arahkan kamera ke objek" }
                                )
                            }
                        )
                    }
                } else {
                    // Text-only / silent mode → show manual "Read" button
                    showReadButton = true
                    confirmedOrgan = organ
                    popupLabel = label
                }
            }
        }
    }

    // 10-second no-detection fallback
    LaunchedEffect(isActive, currentDetection) {
        if (isActive && currentDetection == null && !isWaitingConfirmation && !showPopup) {
            delay(10_000L)
            // After 10s, if still no detection, give guidance
            if (currentDetection == null && !isWaitingConfirmation && !showPopup && isActive) {
                AudioAssistant.speak("Coba dekatkan atau gerakkan kamera sedikit.")
                HapticHelper.shortBuzz()
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            voiceHelper.destroy()
            analyzer.close()
            AudioAssistant.onUtteranceCompleted = null
        }
    }

    // Pulsing glow for status badge
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
        // Layer 1: Camera preview
        CameraPreview(
            isActive = isActive,
            analyzer = analyzer,
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2: Scan overlay
        ScanOverlay(
            isScanning = isActive && !isWaitingConfirmation && !showPopup,
            detection = currentDetection,
            modifier = Modifier.fillMaxSize()
        )

        // Layer 3: Top status bar
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
                .padding(top = 48.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
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
                        .background(
                            when {
                                confirmedOrgan != null -> NeonGreen.copy(alpha = 0.2f)
                                isWaitingConfirmation -> NeonAmber.copy(alpha = 0.2f)
                                else -> NeonCyan.copy(alpha = 0.15f * statusGlow)
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    confirmedOrgan != null -> NeonGreen
                                    isWaitingConfirmation -> NeonAmber
                                    else -> NeonCyan
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    val confidenceText = if (currentDetection?.confidence ?: -1f > 0f) {
                        " (${(currentDetection!!.confidence * 100).toInt()}%)"
                    } else ""

                    Text(
                        text = statusText + confidenceText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            confirmedOrgan != null -> NeonGreen
                            isWaitingConfirmation -> NeonAmber
                            else -> NeonCyan
                        },
                        modifier = Modifier.semantics { contentDescription = statusText }
                    )
                }
            }
        }

        // Layer 4: Manual "Baca Penjelasan" button
        // NOTE: Detection label with accuracy % is now rendered by ScanOverlay directly
        AnimatedVisibility(
            visible = showReadButton && !showPopup,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Button(
                onClick = {
                    showReadButton = false
                    val organ = confirmedOrgan
                    if (organ != null) {
                        // Request LLM explanation for manual read button
                        isLoadingExplanation = true
                        statusText = "Meminta penjelasan dari AI..."
                        
                        coroutineScope.launch {
                            val explanation = withContext(Dispatchers.IO) {
                                llmService.getExplanationText(organ)
                            }
                            
                            isLoadingExplanation = false
                            
                            if (explanation.isNotEmpty()) {
                                popupExplanation = explanation
                                showPopup = true
                                statusText = "Menjelaskan: $organ"
                                
                                // Play explanation via TTS
    if (AudioAssistant.isVoiceOn) {
                                    AudioAssistant.speak(explanation)
                                }
                            } else {
                                AudioAssistant.speak("Maaf, tidak dapat mendapatkan penjelasan untuk $organ saat ini.")
                                resetScanState(
                                    { isWaitingConfirmation = false },
                                    { confirmedOrgan = null },
                                    { currentDetection = null },
                                    { showReadButton = false },
                                    { statusText = "Arahkan kamera ke objek" }
                                )
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.background
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .height(52.dp)
                    .semantics {
                        contentDescription = "Baca penjelasan tentang ${confirmedOrgan}"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.AutoStories,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Baca Penjelasan",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        // Layer 5: Runtime debug panel
        if (debugLogs.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Debug CV",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonAmber,
                    fontWeight = FontWeight.Bold
                )
                debugLogs.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        fontSize = 10.sp,
                        lineHeight = 12.sp
                    )
                }
            }
        }

        // Layer 6: LLM Explanation Popup
        if (showPopup && popupExplanation.isNotEmpty() && confirmedOrgan != null) {
            LLMExplanationPopupSheet(
                organName = confirmedOrgan!!,
                explanation = popupExplanation,
                onDismiss = {
                    showPopup = false
                    AudioAssistant.stop()
                    AudioAssistant.onUtteranceCompleted = null
                    resetScanState(
                        { isWaitingConfirmation = false },
                        { confirmedOrgan = null },
                        { currentDetection = null },
                        { showReadButton = false },
                        { statusText = "Arahkan kamera ke objek" },
                        { popupExplanation = "" },
                        { popupLabel = "" }
                    )
                }
            )
        }
    }
}

private fun resetScanState(vararg resets: () -> Unit) {
    resets.forEach { it() }
}
