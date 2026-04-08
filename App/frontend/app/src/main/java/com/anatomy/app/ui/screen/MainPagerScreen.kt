package com.anatomy.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.anatomy.app.helper.AudioAssistant
import com.anatomy.app.helper.HapticHelper
import com.anatomy.app.ui.theme.FabTextOnly
import com.anatomy.app.ui.theme.FabVoiceOn
import com.anatomy.app.ui.theme.NeonAmber
import com.anatomy.app.ui.theme.NeonCyan
import com.anatomy.app.ui.theme.NeonGreen
import kotlin.math.absoluteValue

/**
 * MainPagerScreen — Root screen with 3-page HorizontalPager.
 *
 * Pages:
 *   0 — Mode Scan Anatomi (Camera + TFLite detection)
 *   1 — Mode Tanya Jawab (Dynamic chatbot + voice/text input)
 *   2 — Mode Quiz (Blind-friendly voice quiz gamification)
 *
 * Lifecycle:
 *   - Each page's isActive flag depends on settledPage matching
 *   - Mic/voice stops immediately on swipe away
 *   - TTS announces page name on settle, triggering auto-listen chains
 */
@Composable
fun MainPagerScreen() {
    val context = LocalContext.current
    val pageNames = listOf("Mode Scan Anatomi", "Mode Tanya Jawab", "Mode Quiz")
    val pageColors = listOf(NeonCyan, NeonCyan, NeonGreen)
    val pagerState = rememberPagerState(pageCount = { pageNames.size })

    var fabMode by remember { mutableStateOf("voice") }
    var showOnboarding by remember { mutableStateOf(isFirstLaunch(context)) }
    var hasSpokenInitial by remember { mutableStateOf(false) }

    // Welcome announcement — ONLY on first launch (non-onboarding) sessions
    LaunchedEffect(Unit) {
        if (!showOnboarding) {
            // Returning user — speak welcome
            AudioAssistant.speak(
                "Selamat datang di Anatomy Guide. " +
                        "Geser ke kanan untuk Scan, atau geser lagi untuk Tanya Jawab."
            )
            hasSpokenInitial = true
        }
        // First launch users get welcome via onboarding dismissal
    }

    // Observe page changes for TTS + haptic
    // Skip the initial page 0 announcement to avoid double-speak
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (!hasSpokenInitial) {
                hasSpokenInitial = true
                return@collect // Skip first emission (page 0 on startup)
            }
            HapticHelper.shortBuzz()
            AudioAssistant.speak(pageNames[page])
        }
    }

    Scaffold(
        modifier = Modifier.semantics {
            contentDescription = "Layar utama aplikasi Anatomy Guide"
        },
        floatingActionButton = {
            if (!showOnboarding) {
                FloatingActionButton(
                    onClick = {
                        val modeString = AudioAssistant.cycleMode()
                        fabMode = if (AudioAssistant.isTextOnly) "textOnly" else "voice"
                        HapticHelper.doubleBuzz()
                        if (AudioAssistant.isVoiceOn) {
                            AudioAssistant.speak(modeString)
                        }
                    },
                    containerColor = when (fabMode) {
                        "textOnly" -> FabTextOnly
                        else -> FabVoiceOn
                    },
                    shape = CircleShape,
                    modifier = Modifier.semantics {
                        contentDescription = when (fabMode) {
                            "textOnly" -> "Mode teks saja aktif. Ketuk untuk mengaktifkan suara."
                            else -> "Panduan suara aktif. Ketuk untuk beralih ke mode teks saja."
                        }
                    }
                ) {
                    Icon(
                        imageVector = when (fabMode) {
                            "textOnly" -> Icons.Default.TextFields
                            else -> Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.background
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !showOnboarding
            ) { page ->
                val pageOffset = (
                        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        ).absoluteValue

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = lerp(1f, 0.85f, pageOffset.coerceIn(0f, 1f))
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(1f, 0.4f, pageOffset.coerceIn(0f, 1f))
                            translationY = lerp(0f, 30f, pageOffset.coerceIn(0f, 1f))
                        }
                ) {
                    val isSettled = pagerState.settledPage == page && !showOnboarding

                    when (page) {
                        0 -> ScanAnatomyScreen(isActive = isSettled)
                        1 -> QnaScreen(isActive = isSettled)
                        2 -> QuizScreen(isActive = isSettled)
                    }
                }
            }

            // Page indicator (3 dots)
            if (!showOnboarding) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Text(
                            text = pageNames[pagerState.currentPage],
                            style = MaterialTheme.typography.labelLarge,
                            color = pageColors[pagerState.currentPage],
                            modifier = Modifier.semantics {
                                contentDescription =
                                    "Halaman saat ini: ${pageNames[pagerState.currentPage]}"
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        pageNames.forEachIndexed { index, name ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 14.dp else 10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) pageColors[index]
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .semantics {
                                        contentDescription = if (isSelected) {
                                            "Halaman $name, aktif"
                                        } else {
                                            "Halaman $name"
                                        }
                                    }
                            )
                            if (index < pageNames.lastIndex) {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    }
                }
            }

            // Onboarding overlay
            if (showOnboarding) {
                OnboardingOverlay(
                    onDismiss = {
                        showOnboarding = false
                        markOnboardingComplete(context)
                        hasSpokenInitial = true
                        HapticHelper.shortBuzz()
                        AudioAssistant.speak(
                            "Selamat datang di Anatomy Guide. " +
                                    "Geser ke kanan untuk Scan, atau geser lagi untuk Tanya Jawab."
                        )
                    }
                )
            }
        }
    }
}
