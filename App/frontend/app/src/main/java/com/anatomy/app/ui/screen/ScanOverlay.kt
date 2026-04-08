package com.anatomy.app.ui.screen

import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.anatomy.app.ui.theme.BoundingBoxColor
import com.anatomy.app.ui.theme.BoundingBoxGlow
import com.anatomy.app.ui.theme.NeonCyan
import com.anatomy.app.ui.theme.NeonGreen
import com.anatomy.app.ui.theme.ScanWaveColor
import com.anatomy.app.ui.theme.TextOnNeon

/**
 * ScanOverlay — Canvas-based composable drawn ON TOP of the camera preview.
 *
 * Features:
 *   1. Animated scanning wave line that sweeps vertically
 *   2. Dynamic bounding box that uses REAL coordinates from TFLite/mock detection
 *   3. Pulsing glow effect on the bounding box
 *   4. Neon accuracy percentage label (e.g., "Jantung - 98%") above the bounding box
 *
 * @param isScanning Whether the scanning wave animation is active.
 * @param detection Current detection result (or null if nothing detected).
 */
@Composable
fun ScanOverlay(
    isScanning: Boolean,
    detection: DetectionResult?,
    modifier: Modifier = Modifier
) {
    // Scanning wave animation
    val infiniteTransition = rememberInfiniteTransition(label = "scan_wave")
    val wavePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_y"
    )

    // Bounding box glow pulse
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Label text paint (native Android Paint for Canvas drawText)
    val labelPaint = Paint().apply {
        color = TextOnNeon.toArgb()
        textSize = 40f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD
        )
    }

    val confidencePaint = Paint().apply {
        color = NeonGreen.toArgb()
        textSize = 36f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD
        )
        setShadowLayer(8f, 0f, 0f, NeonGreen.toArgb())
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = when {
                    detection != null -> {
                        val pct = if (detection.confidence > 0f) {
                            "${(detection.confidence * 100).toInt()} persen"
                        } else ""
                        "Objek terdeteksi: ${detection.organName} $pct"
                    }
                    isScanning -> "Sedang memindai"
                    else -> "Kamera siap"
                }
            }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // ─── Scanning wave line ───
        if (isScanning) {
            val waveY = wavePosition * canvasHeight
            val gradient = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    ScanWaveColor.copy(alpha = 0.6f),
                    ScanWaveColor,
                    ScanWaveColor.copy(alpha = 0.6f),
                    Color.Transparent
                )
            )
            drawLine(
                brush = gradient,
                start = Offset(0f, waveY),
                end = Offset(canvasWidth, waveY),
                strokeWidth = 4f
            )
            // Glow trail
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        NeonCyan.copy(alpha = 0.08f),
                        NeonCyan.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    startY = (waveY - 80f).coerceAtLeast(0f),
                    endY = waveY
                ),
                topLeft = Offset(0f, (waveY - 80f).coerceAtLeast(0f)),
                size = Size(canvasWidth, 80f)
            )
        }

        // ─── Dynamic bounding box from real detection coordinates ───
        if (detection != null) {
            val bbox = detection.boundingBox
            // Real normalized coordinates → pixel coordinates
            val left = bbox[0] * canvasWidth
            val top = bbox[1] * canvasHeight
            val right = bbox[2] * canvasWidth
            val bottom = bbox[3] * canvasHeight
            val bboxWidth = right - left
            val bboxHeight = bottom - top

            // Outer glow (pulsing)
            drawRect(
                color = BoundingBoxGlow.copy(alpha = glowAlpha * 0.5f),
                topLeft = Offset(left - 8f, top - 8f),
                size = Size(bboxWidth + 16f, bboxHeight + 16f),
                style = Stroke(width = 14f)
            )

            // Main bounding box with dashed stroke
            drawRect(
                color = BoundingBoxColor,
                topLeft = Offset(left, top),
                size = Size(bboxWidth, bboxHeight),
                style = Stroke(
                    width = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f))
                )
            )

            // Corner accents (L-shapes)
            val cornerLen = 28f
            val cornerStroke = 5f
            val cc = BoundingBoxColor

            // Top-left
            drawLine(cc, Offset(left, top), Offset(left + cornerLen, top), cornerStroke)
            drawLine(cc, Offset(left, top), Offset(left, top + cornerLen), cornerStroke)
            // Top-right
            drawLine(cc, Offset(right, top), Offset(right - cornerLen, top), cornerStroke)
            drawLine(cc, Offset(right, top), Offset(right, top + cornerLen), cornerStroke)
            // Bottom-left
            drawLine(cc, Offset(left, bottom), Offset(left + cornerLen, bottom), cornerStroke)
            drawLine(cc, Offset(left, bottom), Offset(left, bottom - cornerLen), cornerStroke)
            // Bottom-right
            drawLine(cc, Offset(right, bottom), Offset(right - cornerLen, bottom), cornerStroke)
            drawLine(cc, Offset(right, bottom), Offset(right, bottom - cornerLen), cornerStroke)

            // ─── Accuracy % label above bounding box ───
            val labelY = top - 14f
            val confidencePct = if (detection.confidence > 0f) {
                "${(detection.confidence * 100).toInt()}%"
            } else "mock"
            val labelText = "${detection.organName} — $confidencePct"

            // Measure text width for background
            val textWidth = confidencePaint.measureText(labelText)
            val labelPadH = 16f
            val labelPadV = 8f
            val labelHeight = 44f
            val bgLeft = left
            val bgTop = labelY - labelHeight
            val bgWidth = (textWidth + labelPadH * 2).coerceAtLeast(bboxWidth)

            // Label background
            drawRect(
                color = BoundingBoxColor.copy(alpha = 0.9f),
                topLeft = Offset(bgLeft, bgTop),
                size = Size(bgWidth, labelHeight)
            )

            // Draw neon text using native Canvas
            drawContext.canvas.nativeCanvas.drawText(
                labelText,
                bgLeft + labelPadH,
                bgTop + labelHeight - labelPadV - 2f,
                confidencePaint
            )
        }
    }
}
