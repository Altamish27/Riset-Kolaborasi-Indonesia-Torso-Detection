package com.anatomy.app.ui.screen

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.anatomy.app.ui.theme.NeonCyan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// DataStore extension for the app
val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding_prefs")
val IS_FIRST_LAUNCH_KEY = booleanPreferencesKey("is_first_launch")

/**
 * Check if this is the first launch (synchronous, for composable initial state).
 */
fun isFirstLaunch(context: Context): Boolean {
    return try {
        runBlocking {
            context.onboardingDataStore.data.map { prefs ->
                prefs[IS_FIRST_LAUNCH_KEY] ?: true
            }.first()
        }
    } catch (e: Exception) {
        true
    }
}

/**
 * Mark the onboarding as completed.
 */
fun markOnboardingComplete(context: Context) {
    CoroutineScope(Dispatchers.IO).launch {
        context.onboardingDataStore.edit { prefs ->
            prefs[IS_FIRST_LAUNCH_KEY] = false
        }
    }
}

/**
 * OnboardingOverlay — First-launch overlay with animated swipe arrows.
 *
 * Shown only on the first launch. Dismissed by double-tap.
 * - Semi-transparent dark scrim
 * - Animated left/right chevron arrows on the sides
 * - Center: "Geser untuk berpindah menu"
 * - Bottom: "Ketuk dua kali untuk memulai"
 *
 * @param onDismiss Called when the user double-taps to dismiss.
 */
@Composable
fun OnboardingOverlay(
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arrows")

    // Left arrow oscillation
    val leftOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -16f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "left_arrow"
    )

    // Right arrow oscillation
    val rightOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "right_arrow"
    )

    // Pulsing glow for text
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "text_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onDismiss()
                    }
                )
            }
            .semantics {
                contentDescription = "Layar selamat datang. Ketuk dua kali untuk memulai."
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // App title
            Text(
                text = "Anatomy Guide",
                style = MaterialTheme.typography.displayLarge,
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Animated arrows row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Left arrows
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = NeonCyan.copy(alpha = 0.4f),
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = leftOffset.dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = NeonCyan.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = leftOffset.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Center instruction
                Text(
                    text = "GESER",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White.copy(alpha = textAlpha),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Right arrows
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = NeonCyan.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = rightOffset.dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = NeonCyan.copy(alpha = 0.4f),
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = rightOffset.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Geser ke kiri atau kanan\nuntuk berpindah antar fitur",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Double-tap instruction
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                NeonCyan.copy(alpha = 0.15f),
                                NeonCyan.copy(alpha = 0.25f),
                                NeonCyan.copy(alpha = 0.15f)
                            )
                        )
                    )
                    .padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Ketuk dua kali untuk memulai",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonCyan.copy(alpha = textAlpha),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
