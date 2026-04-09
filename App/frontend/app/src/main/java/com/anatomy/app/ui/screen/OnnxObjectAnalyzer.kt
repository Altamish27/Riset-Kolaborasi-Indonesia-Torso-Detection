package com.anatomy.app.ui.screen

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * OnnxObjectAnalyzer — CameraX ImageAnalysis.Analyzer using ONNX Runtime.
 *
 * Runs the custom YOLO torso detection model exported as .onnx from Ultralytics.
 * Supports both the standard transposed output [1, C, 8400] and NMS-flattened
 * output [1, N, C>=6] produced by some export configurations.
 *
 * @param context Application context for loading the model from assets.
 * @param onDetection Called with the best DetectionResult, or null if nothing found.
 * @param onDebugLog Optional debug sink – defaults to no-op.
 */
class OnnxObjectAnalyzer(
    private val context: Context,
    private val onDetection: (DetectionResult?) -> Unit,
    private val onDebugLog: (String) -> Unit = {}
) : ImageAnalysis.Analyzer {

    private val TAG = "OnnxAnalyzer"

    // ---------------------------------------------------------------------------
    // Model discovery — first match wins
    // ---------------------------------------------------------------------------
    private val MODEL_CANDIDATES = listOf(
        "best_model_torso.onnx",
        "best_model.onnx"
    )

    private val CONFIDENCE_THRESHOLD = 0.25f
    private val THROTTLE_MS = 250L   // max ~4 FPS inference

    // ---------------------------------------------------------------------------
    // Runtime fields
    // ---------------------------------------------------------------------------
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isModelLoaded = false
    private var selectedModelName: String? = null

    /** Input spatial size read from the model's first input tensor shape. */
    private var inputWidth = 640
    private var inputHeight = 640

    private var lastAnalysisTime = 0L
    private var analyzedFrames = 0L
    private var detectionFrames = 0L
    private var lastSummaryLogTime = 0L

    init {
        // Collect .onnx files present in assets
        val assetsOnnx = (context.assets.list("") ?: emptyArray())
            .filter { it.lowercase().endsWith(".onnx") }

        val candidates = (MODEL_CANDIDATES + assetsOnnx).distinct()
        pushDebug("ONNX candidates: ${candidates.joinToString()}")

        ortEnv = OrtEnvironment.getEnvironment()

        for (modelFile in candidates) {
            try {
                val bytes = context.assets.open(modelFile).readBytes()
                val sessionOptions = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(4)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                ortSession = ortEnv!!.createSession(bytes, sessionOptions)

                // Inspect first input to discover H×W
                val inputInfo = ortSession!!.inputInfo
                val firstInputShape = inputInfo.values.firstOrNull()
                    ?.info?.toString() ?: ""
                pushDebug("Input info: $firstInputShape")

                // Parse shape from info string (e.g. "FloatTensor[1, 3, 640, 640]")
                val dims = Regex("\\d+").findAll(firstInputShape)
                    .map { it.value.toLong() }.toList()
                if (dims.size >= 4) {
                    // Shape is [batch, channels, H, W]
                    inputHeight = dims[2].toInt().coerceAtLeast(1)
                    inputWidth  = dims[3].toInt().coerceAtLeast(1)
                }

                isModelLoaded = true
                selectedModelName = modelFile
                pushDebug("Model loaded: $modelFile  input=${inputWidth}×${inputHeight}  conf=$CONFIDENCE_THRESHOLD")
                break
            } catch (e: Exception) {
                pushDebug("Model load failed: $modelFile (${e.message ?: "unknown"})")
            }
        }

        if (!isModelLoaded) {
            pushDebug("No compatible ONNX model found in assets")
        }
    }

    // ---------------------------------------------------------------------------
    // ImageAnalysis.Analyzer
    // ---------------------------------------------------------------------------
    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < THROTTLE_MS) {
            image.close()
            return
        }
        lastAnalysisTime = now
        analyzedFrames++

        if (!isModelLoaded) {
            maybePushSummary("model_not_loaded")
            onDetection(null)
            image.close()
            return
        }

        try {
            val bitmap = imageProxyToBitmap(image) ?: run {
                image.close()
                return
            }

            val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
            val inputTensor = bitmapToOnnxTensor(resized)

            val inputName = ortSession!!.inputNames.first()
            val inputs = mapOf(inputName to inputTensor)
            val results = ortSession!!.run(inputs)

            val outputTensor = results[0].value
            val detection = parseBestDetection(outputTensor)

            inputTensor.close()
            results.close()

            if (detection != null) {
                detectionFrames++
                pushDebug("Detected: ${detection.organName} (${(detection.confidence * 100).toInt()}%)")
                maybePushSummary("detected")
                onDetection(detection)
            } else {
                maybePushSummary("no_objects")
                onDetection(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Analysis error", e)
            pushDebug("Error: ${e.message ?: "unknown"}")
            onDetection(null)
        } finally {
            image.close()
        }
    }

    // ---------------------------------------------------------------------------
    // Bitmap → ONNX float tensor [1, 3, H, W] (NCHW, normalised 0-1)
    // ---------------------------------------------------------------------------
    private fun bitmapToOnnxTensor(bitmap: Bitmap): OnnxTensor {
        val pixels = IntArray(inputWidth * inputHeight)
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        val floatBuf = FloatBuffer.allocate(1 * 3 * inputHeight * inputWidth)

        // Write R plane, then G plane, then B plane (NCHW)
        for (pixel in pixels) floatBuf.put(((pixel shr 16) and 0xFF) / 255f)
        for (pixel in pixels) floatBuf.put(((pixel shr 8)  and 0xFF) / 255f)
        for (pixel in pixels) floatBuf.put(( pixel          and 0xFF) / 255f)

        floatBuf.rewind()

        val shape = longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        return OnnxTensor.createTensor(ortEnv!!, floatBuf, shape)
    }

    // ---------------------------------------------------------------------------
    // Image proxy utilities
    // ---------------------------------------------------------------------------
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val nv21 = yuv420888ToNv21(image)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)
            val bytes = out.toByteArray()
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

            val rotation = image.imageInfo.rotationDegrees
            if (rotation == 0) raw
            else {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap conversion failed", e)
            pushDebug("Frame convert failed: ${e.message ?: "unknown"}")
            null
        }
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val w = image.width
        val h = image.height
        val nv21 = ByteArray(w * h + w * h / 2)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        var out = 0
        for (row in 0 until h)
            for (col in 0 until w)
                nv21[out++] = yBuf[row * yPlane.rowStride + col * yPlane.pixelStride]

        for (row in 0 until h / 2)
            for (col in 0 until w / 2) {
                nv21[out++] = vBuf[row * vPlane.rowStride + col * vPlane.pixelStride]
                nv21[out++] = uBuf[row * uPlane.rowStride + col * uPlane.pixelStride]
            }

        return nv21
    }

    // ---------------------------------------------------------------------------
    // Output parsing
    // ---------------------------------------------------------------------------

    /**
     * Parses the raw ONNX output tensor.
     *
     * YOLO models exported via Ultralytics typically produce one of:
     *  - Shape [1, C, N]  — "transposed" format (C = 4+classes, N = num anchors)
     *  - Shape [1, N, C]  — NMS output (C >= 6: x1,y1,x2,y2,score,classId)
     */
    private fun parseBestDetection(output: Any): DetectionResult? {
        val candidates = mutableListOf<Candidate>()

        when (output) {
            // Most common: float array[][][]  ->  [batch][rows][cols]
            is Array<*> -> {
                if (output.isEmpty()) return null
                val batch = output[0]

                if (batch is Array<*> && batch.isNotEmpty()) {
                    val firstRow = batch[0]

                    if (firstRow is FloatArray) {
                        @Suppress("UNCHECKED_CAST")
                        val matrix = batch as Array<FloatArray>
                        val rows = matrix.size
                        val cols = matrix[0].size

                        // [1, C, N] — transposed YOLO (rows=C, cols=N)
                        if (rows >= 5 && cols > rows) {
                            candidates += parseYoloTransposed(matrix)
                        }
                        // [1, N, C>=6] — NMS rows
                        else if (cols >= 6) {
                            candidates += parseNmsRows(matrix)
                        }
                    }
                } else if (batch is FloatArray) {
                    // Flat 2D [batch=1 squeezed] → treat as single row NMS
                    if (batch.size >= 6) {
                        candidates += parseNmsRows(arrayOf(batch))
                    }
                }
            }
        }

        val best = candidates.maxByOrNull { it.score } ?: return null
        val label = normalizeLabel(best.label) ?: return null

        return DetectionResult(
            mockLabel = label,
            organName = label,
            boundingBox = floatArrayOf(best.left, best.top, best.right, best.bottom),
            confidence = best.score
        )
    }

    /**
     * Parse transposed YOLO output: matrix[C][N]
     * Rows 0-3: cx, cy, w, h (relative to input size, or normalised 0-1)
     * Rows 4+:  class scores
     */
    private fun parseYoloTransposed(matrix: Array<FloatArray>): List<Candidate> {
        val list = mutableListOf<Candidate>()
        val numChannels = matrix.size
        val numAnchors  = matrix[0].size
        if (numChannels < 5) return emptyList()

        for (i in 0 until numAnchors) {
            val cx = matrix[0][i]
            val cy = matrix[1][i]
            val w  = matrix[2][i]
            val h  = matrix[3][i]

            var bestScore = 0f
            var bestClass = -1
            for (c in 4 until numChannels) {
                val s = matrix[c][i]
                if (s > bestScore) { bestScore = s; bestClass = c - 4 }
            }

            if (bestScore < CONFIDENCE_THRESHOLD || bestClass < 0) continue

            val x1 = cx - w / 2f
            val y1 = cy - h / 2f
            val x2 = cx + w / 2f
            val y2 = cy + h / 2f
            val box = normalizeBox(x1, y1, x2, y2) ?: continue

            list += Candidate(box[0], box[1], box[2], box[3], bestScore, "Class $bestClass")
        }
        return list
    }

    /**
     * Parse NMS-style output: matrix[N][C]
     * Columns: x1, y1, x2, y2, score, classId  (C >= 6)
     */
    private fun parseNmsRows(matrix: Array<FloatArray>): List<Candidate> {
        val list = mutableListOf<Candidate>()
        for (row in matrix) {
            if (row.size < 6) continue
            val score = row[4]
            if (score < CONFIDENCE_THRESHOLD) continue
            val cls = row[5].toInt().coerceAtLeast(0)
            val box = normalizeBox(row[0], row[1], row[2], row[3]) ?: continue
            list += Candidate(box[0], box[1], box[2], box[3], score, "Class $cls")
        }
        return list
    }

    /** Normalise pixel-space or already-normalised coordinates to [0, 1]. */
    private fun normalizeBox(x1: Float, y1: Float, x2: Float, y2: Float): FloatArray? {
        val maxAbs = max(
            max(kotlin.math.abs(x1), kotlin.math.abs(y1)),
            max(kotlin.math.abs(x2), kotlin.math.abs(y2))
        )
        val (lN, tN, rN, bN) = if (maxAbs <= 2f) {
            floatArrayOf(x1, y1, x2, y2)
        } else {
            floatArrayOf(
                x1 / inputWidth, y1 / inputHeight,
                x2 / inputWidth, y2 / inputHeight
            )
        }.let { a -> listOf(a[0], a[1], a[2], a[3]) }

        val left   = lN.coerceIn(0f, 1f)
        val top    = tN.coerceIn(0f, 1f)
        val right  = rN.coerceIn(0f, 1f)
        val bottom = bN.coerceIn(0f, 1f)

        if (right <= left || bottom <= top) return null
        if ((right - left) < 0.01f || (bottom - top) < 0.01f) return null

        return floatArrayOf(left, top, right, bottom)
    }

    private fun normalizeLabel(raw: String?): String? {
        val cleaned = raw?.trim()
            ?.replace('_', ' ')
            ?.replace(Regex("\\s+"), " ")
            ?.lowercase()
            ?: return null
        if (cleaned.isBlank()) return null
        if (cleaned.matches(Regex("\\d+"))) return "Class $cleaned"
        return cleaned.split(' ')
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------
    fun close() {
        try { ortSession?.close() } catch (_: Exception) {}
        try { ortEnv?.close()    } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------
    private data class Candidate(
        val left: Float, val top: Float,
        val right: Float, val bottom: Float,
        val score: Float, val label: String
    )

    private fun maybePushSummary(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastSummaryLogTime < 2000L) return
        lastSummaryLogTime = now
        pushDebug("Summary: frames=$analyzedFrames hit=$detectionFrames model=${selectedModelName ?: "none"} state=$reason")
    }

    private fun pushDebug(message: String) {
        Log.d(TAG, message)
        onDebugLog(message)
    }
}
