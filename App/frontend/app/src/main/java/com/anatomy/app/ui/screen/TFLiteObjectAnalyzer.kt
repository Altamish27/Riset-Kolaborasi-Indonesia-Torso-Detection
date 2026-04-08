package com.anatomy.app.ui.screen

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
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * TFLiteObjectAnalyzer — CameraX ImageAnalysis.Analyzer using direct TFLite Interpreter.
 *
 * Uses YOLO-style tensor parsing so exported Ultralytics TFLite models can run similarly
 * to Python `YOLO(...).predict(...)` flow.
 *
 * @param context Application context for loading models.
 * @param onDetection Called with the mapped DetectionResult or null.
 */
class TFLiteObjectAnalyzer(
    private val context: Context,
    private val onDetection: (DetectionResult?) -> Unit,
    private val onDebugLog: (String) -> Unit = {}
) : ImageAnalysis.Analyzer {

    private val TAG = "TFLiteAnalyzer"
    private val MODEL_CANDIDATES = listOf(
        "best_model_torso_float16_nms.tflite",
        "best_model_torso_float16_416_nms.tflite",
        "best_model_torso_float16.tflite",
        "best_model_torso_416_fp16_nms.tflite",
        "best_model_float16.tflite",
        "best_model.tflite",
        "best_model_torso_float32.tflite"
    )
    private val CONFIDENCE_THRESHOLD = 0.25f
    private val THROTTLE_MS = 250L // Process at most 4 frames/second

    private var interpreter: Interpreter? = null
    private var isModelLoaded = false
    private var lastAnalysisTime = 0L
    private var analyzedFrames = 0L
    private var detectionFrames = 0L
    private var lastSummaryLogTime = 0L
    private var selectedModelName: String? = null
    private var inputWidth = 416
    private var inputHeight = 416
    private var inputType: DataType = DataType.FLOAT32
    private var outputShape: IntArray = intArrayOf()

    init {
        val discoveredModels = (context.assets.list("") ?: emptyArray())
            .filter { it.lowercase().endsWith(".tflite") }

        val allCandidates = (MODEL_CANDIDATES + discoveredModels)
            .distinct()
            .sortedByDescending { scoreModelName(it) }

        pushDebug("Candidates model: ${allCandidates.joinToString()}")

        for (modelFile in allCandidates) {
            try {
                val mappedModel = FileUtil.loadMappedFile(context, modelFile)
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                }
                interpreter = Interpreter(mappedModel, options)

                val inputTensor = interpreter?.getInputTensor(0)
                val inputShape = inputTensor?.shape() ?: intArrayOf(1, 416, 416, 3)
                inputType = inputTensor?.dataType() ?: DataType.FLOAT32
                if (inputShape.size >= 4) {
                    inputHeight = inputShape[1].coerceAtLeast(1)
                    inputWidth = inputShape[2].coerceAtLeast(1)
                }

                outputShape = interpreter?.getOutputTensor(0)?.shape() ?: intArrayOf()

                isModelLoaded = true
                selectedModelName = modelFile
                pushDebug(
                    "Model loaded: $modelFile, in=${inputShape.contentToString()} $inputType, out=${outputShape.contentToString()}, conf=$CONFIDENCE_THRESHOLD"
                )
                break
            } catch (e: Exception) {
                pushDebug("Model gagal load: $modelFile (${e.message ?: "unknown"})")
            }
        }

        if (!isModelLoaded) {
            pushDebug("Tidak ada model TFLite yang kompatibel di assets")
        }
    }

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < THROTTLE_MS) {
            image.close()
            return
        }
        lastAnalysisTime = now
        analyzedFrames += 1

        if (!isModelLoaded) {
            maybePushSummary("model_not_loaded")
            onDetection(null)
            image.close()
            return
        }

        try {
            val bitmap = imageProxyToBitmap(image)
            if (bitmap == null) {
                image.close()
                return
            }

            val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
            val inputBuffer = bitmapToInputBuffer(resized)
            val outputBuffer = createOutputBuffer()

            interpreter?.run(inputBuffer, outputBuffer)
            val detection = parseBestDetection(outputBuffer)

            if (detection != null) {
                detectionFrames += 1
                pushDebug("Deteksi: ${detection.organName} (${(detection.confidence * 100).toInt()}%)")
                maybePushSummary("detected")
                onDetection(detection)
            } else {
                maybePushSummary("no_objects")
                onDetection(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Analysis error", e)
            pushDebug("Error analyze: ${e.message ?: "unknown"}")
            onDetection(null)
        } finally {
            image.close()
        }
    }

    /**
     * Convert ImageProxy (YUV_420_888) to Bitmap for TFLite.
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val nv21 = yuv420888ToNv21(image)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)
            val imageBytes = out.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null

            val rotation = image.imageInfo.rotationDegrees
            if (rotation == 0) {
                bitmap
            } else {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap conversion failed", e)
            pushDebug("Konversi frame gagal: ${e.message ?: "unknown"}")
            null
        }
    }

    /** Convert YUV_420_888 ImageProxy into NV21 byte array with row/pixel stride handling. */
    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        // Copy Y plane taking row stride into account.
        var outIndex = 0
        for (row in 0 until height) {
            val yRowStart = row * yPlane.rowStride
            for (col in 0 until width) {
                val yIndex = yRowStart + col * yPlane.pixelStride
                nv21[outIndex++] = yBuffer.get(yIndex)
            }
        }

        // NV21 expects interleaved VU for each 2x2 block.
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        for (row in 0 until chromaHeight) {
            val uRowStart = row * uPlane.rowStride
            val vRowStart = row * vPlane.rowStride
            for (col in 0 until chromaWidth) {
                val uIndex = uRowStart + col * uPlane.pixelStride
                val vIndex = vRowStart + col * vPlane.pixelStride
                nv21[outIndex++] = vBuffer.get(vIndex)
                nv21[outIndex++] = uBuffer.get(uIndex)
            }
        }

        return nv21
    }

    fun close() {
        interpreter?.close()
    }

    private fun normalizeModelLabel(rawLabel: String?): String? {
        val cleaned = rawLabel
            ?.trim()
            ?.replace('_', ' ')
            ?.replace(Regex("\\s+"), " ")
            ?.lowercase()
            ?: return null

        if (cleaned.isBlank()) return null
        if (cleaned.matches(Regex("\\d+"))) {
            // Keep numeric class IDs as valid labels if metadata names are unavailable.
            return "Class $cleaned"
        }

        return cleaned.split(' ')
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }

    private fun scoreModelName(name: String): Int {
        val lower = name.lowercase()
        var score = 0
        if ("416" in lower) score += 8
        if ("nms" in lower) score += 6
        if ("fp16" in lower || "float16" in lower) score += 4
        if ("torso" in lower) score += 3
        if ("best" in lower) score += 1
        return score
    }

    private fun bitmapToInputBuffer(bitmap: Bitmap): ByteBuffer {
        val channels = 3
        val bytesPerChannel = when (inputType) {
            DataType.FLOAT32 -> 4
            else -> 1
        }
        val buffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * channels * bytesPerChannel)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputWidth * inputHeight)
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF)
            val g = (pixel shr 8 and 0xFF)
            val b = (pixel and 0xFF)

            when (inputType) {
                DataType.FLOAT32 -> {
                    buffer.putFloat(r / 255f)
                    buffer.putFloat(g / 255f)
                    buffer.putFloat(b / 255f)
                }
                else -> {
                    buffer.put(r.toByte())
                    buffer.put(g.toByte())
                    buffer.put(b.toByte())
                }
            }
        }
        buffer.rewind()
        return buffer
    }

    private fun createOutputBuffer(): Any {
        val shape = outputShape
        if (shape.isEmpty()) return Array(1) { FloatArray(6) }

        return when (shape.size) {
            3 -> Array(shape[0]) { Array(shape[1]) { FloatArray(shape[2]) } }
            2 -> Array(shape[0]) { FloatArray(shape[1]) }
            else -> {
                val flatSize = shape.fold(1) { acc, dim -> acc * dim.coerceAtLeast(1) }
                FloatArray(flatSize)
            }
        }
    }

    private fun parseBestDetection(output: Any): DetectionResult? {
        val candidates = mutableListOf<Candidate>()

        when (output) {
            is Array<*> -> {
                if (output.isEmpty()) return null
                val first = output[0]

                if (first is Array<*>) {
                    @Suppress("UNCHECKED_CAST")
                    val batch = output as Array<Array<FloatArray>>
                    val matrix = batch.firstOrNull() ?: return null
                    if (matrix.isEmpty()) return null

                    val rowCount = matrix.size
                    val colCount = matrix[0].size

                    // Format A: [1, N, C] where C>=6 (x1,y1,x2,y2,score,class)
                    if (colCount >= 6 && rowCount >= 1) {
                        candidates += parseNmsRows(matrix)
                    } else if (rowCount >= 6 && colCount >= 1) {
                        // Format B: [1, C, N] YOLO-like (cx,cy,w,h,class...)
                        candidates += parseYoloTransposed(matrix)
                    }
                } else if (first is FloatArray) {
                    @Suppress("UNCHECKED_CAST")
                    val matrix = output as Array<FloatArray>
                    candidates += parseNmsRows(matrix)
                }
            }
        }

        val best = candidates.maxByOrNull { it.score } ?: return null
        val label = normalizeModelLabel(best.label) ?: return null

        return DetectionResult(
            mockLabel = label,
            organName = label,
            boundingBox = floatArrayOf(best.left, best.top, best.right, best.bottom),
            confidence = best.score
        )
    }

    private fun parseNmsRows(rows: Array<FloatArray>): List<Candidate> {
        val list = mutableListOf<Candidate>()
        for (row in rows) {
            if (row.size < 6) continue
            val score = row[4]
            if (score < CONFIDENCE_THRESHOLD) continue

            val cls = row[5].toInt().coerceAtLeast(0)
            val box = normalizeBox(row[0], row[1], row[2], row[3])
            if (box == null) continue

            list += Candidate(
                left = box[0],
                top = box[1],
                right = box[2],
                bottom = box[3],
                score = score,
                label = "Class $cls"
            )
        }
        return list
    }

    private fun parseYoloTransposed(matrix: Array<FloatArray>): List<Candidate> {
        val list = mutableListOf<Candidate>()
        val channels = matrix.size
        val numPred = matrix[0].size

        if (channels < 6) return emptyList()

        for (i in 0 until numPred) {
            val cx = matrix[0][i]
            val cy = matrix[1][i]
            val w = matrix[2][i]
            val h = matrix[3][i]

            var bestScore = 0f
            var bestClass = -1
            for (c in 4 until channels) {
                val score = matrix[c][i]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c - 4
                }
            }

            if (bestScore < CONFIDENCE_THRESHOLD || bestClass < 0) continue

            val x1 = cx - (w / 2f)
            val y1 = cy - (h / 2f)
            val x2 = cx + (w / 2f)
            val y2 = cy + (h / 2f)

            val box = normalizeBox(x1, y1, x2, y2)
            if (box == null) continue

            list += Candidate(
                left = box[0],
                top = box[1],
                right = box[2],
                bottom = box[3],
                score = bestScore,
                label = "Class $bestClass"
            )
        }

        return list
    }

    private fun normalizeBox(x1: Float, y1: Float, x2: Float, y2: Float): FloatArray? {
        val maxAbs = max(max(kotlin.math.abs(x1), kotlin.math.abs(y1)), max(kotlin.math.abs(x2), kotlin.math.abs(y2)))
        val normalized = if (maxAbs <= 2f) {
            floatArrayOf(x1, y1, x2, y2)
        } else {
            floatArrayOf(
                x1 / inputWidth.toFloat(),
                y1 / inputHeight.toFloat(),
                x2 / inputWidth.toFloat(),
                y2 / inputHeight.toFloat()
            )
        }

        var left = normalized[0].coerceIn(0f, 1f)
        var top = normalized[1].coerceIn(0f, 1f)
        var right = normalized[2].coerceIn(0f, 1f)
        var bottom = normalized[3].coerceIn(0f, 1f)

        if (right <= left || bottom <= top) return null

        // Keep minimum visible box size to avoid degenerate overlays.
        if ((right - left) < 0.01f || (bottom - top) < 0.01f) return null

        return floatArrayOf(left, top, right, bottom)
    }

    private data class Candidate(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val score: Float,
        val label: String
    )

    private fun maybePushSummary(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastSummaryLogTime < 2000L) return
        lastSummaryLogTime = now
        val model = selectedModelName ?: "none"
        pushDebug("Summary: frames=$analyzedFrames, hit=$detectionFrames, model=$model, state=$reason")
    }

    private fun pushDebug(message: String) {
        Log.d(TAG, message)
        onDebugLog(message)
    }
}
