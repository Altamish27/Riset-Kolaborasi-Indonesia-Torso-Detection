package com.anatomy.app.ui.screen

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicLong

/**
 * Data class representing a detection result (mock or real TFLite).
 *
 * @param mockLabel The detected object label (e.g., "Bottle")
 * @param organName The mapped anatomy organ name (e.g., "Jantung")
 * @param boundingBox Normalized bounding box [left, top, right, bottom] in 0..1 range
 * @param confidence Detection confidence score (0..1). -1 for mock detections.
 */
data class DetectionResult(
    val mockLabel: String,
    val organName: String,
    val boundingBox: FloatArray, // [left, top, right, bottom] normalized 0..1
    val confidence: Float = -1f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetectionResult) return false
        return mockLabel == other.mockLabel && organName == other.organName
    }

    override fun hashCode(): Int {
        return 31 * mockLabel.hashCode() + organName.hashCode()
    }
}

/**
 * MockObjectAnalyzer — CameraX ImageAnalysis.Analyzer that simulates object detection.
 *
 * Used as the fallback when the TFLite model is not available.
 * Cycles through mock labels every ~3s, mapping each to an anatomy organ.
 *
 * @param onDetection Called with the detection result.
 */
class MockObjectAnalyzer(
    private val onDetection: (DetectionResult?) -> Unit
) : ImageAnalysis.Analyzer {

    private val TAG = "MockObjectAnalyzer"

    private val mockMappings = listOf(
        DetectionResult("Bottle", "Jantung", floatArrayOf(0.25f, 0.20f, 0.75f, 0.70f)),
        DetectionResult("Cell phone", "Paru-paru", floatArrayOf(0.20f, 0.15f, 0.80f, 0.75f)),
        DetectionResult("Person", "Sistem Syaraf", floatArrayOf(0.10f, 0.10f, 0.90f, 0.90f)),
        DetectionResult("Book", "Hati", floatArrayOf(0.15f, 0.25f, 0.85f, 0.65f)),
        DetectionResult("Cup", "Lambung", floatArrayOf(0.30f, 0.30f, 0.70f, 0.80f)),
        DetectionResult("Remote", "Usus", floatArrayOf(0.20f, 0.20f, 0.80f, 0.60f)),
        DetectionResult("Mouse", "Ginjal", floatArrayOf(0.25f, 0.35f, 0.75f, 0.75f))
    )

    private var currentIndex = 0
    private val lastDetectionTime = AtomicLong(0L)
    private val detectionIntervalMs = 3000L

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastDetectionTime.get()

        if (elapsed >= detectionIntervalMs) {
            lastDetectionTime.set(now)
            val detection = mockMappings[currentIndex % mockMappings.size]
            currentIndex++
            Log.d(TAG, "Mock detection: ${detection.mockLabel} → ${detection.organName}")
            onDetection(detection)
        }

        image.close()
    }

    fun reset() {
        currentIndex = 0
        lastDetectionTime.set(0L)
    }
}
