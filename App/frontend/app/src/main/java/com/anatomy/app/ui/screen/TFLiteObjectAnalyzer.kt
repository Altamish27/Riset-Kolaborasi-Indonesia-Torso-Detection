package com.anatomy.app.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

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
    private val onDebugLog: (String) -> Unit = {},
    private val onFpsUpdate: (Float) -> Unit = {},
    initialConfidenceThreshold: Float = 0.25f
) : ImageAnalysis.Analyzer {

    private val TAG = "TFLiteAnalyzer"
    private val MODEL_CANDIDATES = listOf(
        "best_model_torso_float16_nms.tflite",
        "best_model_torso_float16_416_nms.tflite",
        "best_model_torso_float16.tflite",
        "best_model_torso_416_fp16_nms.tflite",
        "best_model_float16.tflite"
    )
    @Volatile
    private var confidenceThreshold = initialConfidenceThreshold.coerceIn(0.05f, 0.95f)

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var isModelLoaded = false
    private var analyzedFrames = 0L
    private var detectionFrames = 0L
    private var lastSummaryLogTime = 0L
    private var fpsWindowStartMs = System.currentTimeMillis()
    private var fpsWindowFrames = 0L
    private var currentFps = 0f
    private var selectedModelName: String? = null
    private var inputWidth = 416
    private var inputHeight = 416
    private var inputType: DataType = DataType.FLOAT32
    private var outputShape: IntArray = intArrayOf()
    private val classNameById = mutableMapOf<Int, String>()
    private var imageProcessor: ImageProcessor? = null
    private val tensorImage = TensorImage(DataType.UINT8)
    private var rgbFrameBitmap: Bitmap? = null
    private var rgbaScratch: ByteArray = ByteArray(0)
    private var letterboxBitmap: Bitmap? = null
    private var letterboxCanvas: Canvas? = null
    private var letterboxScale = 1f
    private var letterboxPadX = 0f
    private var letterboxPadY = 0f
    private var sourceFrameWidth = 1
    private var sourceFrameHeight = 1

    init {
        classNameById.putAll(loadClassNamesFromMetadata())

        val discoveredModels = (context.assets.list("") ?: emptyArray())
            .filter { it.lowercase(Locale.US).endsWith(".tflite") }

        val allCandidates = (MODEL_CANDIDATES + discoveredModels)
            .distinct()
            .sortedByDescending { scoreModelName(it) }

        pushDebug("Candidates model: ${allCandidates.joinToString()}")

        for (modelFile in allCandidates) {
            try {
                val mappedModel = FileUtil.loadMappedFile(context, modelFile)
                val gpuSupported = runCatching {
                    CompatibilityList().isDelegateSupportedOnThisDevice
                }.getOrDefault(false)

                val loadedWithGpu = if (gpuSupported) {
                    runCatching {
                        val gpuOptions = Interpreter.Options().apply {
                            gpuDelegate = GpuDelegate()
                            addDelegate(gpuDelegate)
                        }
                        interpreter = Interpreter(mappedModel, gpuOptions)
                        pushDebug("Model loaded via GPU: $modelFile")
                        true
                    }.getOrElse { gpuErr ->
                        pushDebug("GPU gagal untuk $modelFile (${gpuErr.message ?: "unknown"}), fallback CPU")
                        gpuDelegate?.close()
                        gpuDelegate = null
                        false
                    }
                } else {
                    pushDebug("GPU delegate tidak tersedia, langsung CPU")
                    false
                }

                if (!loadedWithGpu) {
                    val cpuOptions = Interpreter.Options().apply {
                        setNumThreads(4)
                    }
                    interpreter = Interpreter(mappedModel, cpuOptions)
                }

                val inputTensor = interpreter?.getInputTensor(0)
                val inputShape = inputTensor?.shape() ?: intArrayOf(1, 416, 416, 3)
                inputType = inputTensor?.dataType() ?: DataType.FLOAT32
                if (inputShape.size >= 4) {
                    inputHeight = inputShape[1].coerceAtLeast(1)
                    inputWidth = inputShape[2].coerceAtLeast(1)
                }

                outputShape = interpreter?.getOutputTensor(0)?.shape() ?: intArrayOf()
                imageProcessor = buildImageProcessor()

                isModelLoaded = true
                selectedModelName = modelFile
                pushDebug(
                    "Model loaded: $modelFile, in=${inputShape.contentToString()} $inputType, out=${outputShape.contentToString()}, conf=$confidenceThreshold"
                )
                break
            } catch (e: Throwable) {
                pushDebug("Model gagal load: $modelFile (${e.message ?: "unknown"})")
                interpreter?.close()
                interpreter = null
                gpuDelegate?.close()
                gpuDelegate = null
            }
        }

        if (!isModelLoaded) {
            pushDebug("Tidak ada model TFLite yang kompatibel di assets")
        }
    }

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        fpsWindowFrames += 1
        val fpsElapsed = (now - fpsWindowStartMs).coerceAtLeast(1L)
        if (fpsElapsed >= 1000L) {
            currentFps = fpsWindowFrames * 1000f / fpsElapsed.toFloat()
            onFpsUpdate(currentFps)
            fpsWindowFrames = 0L
            fpsWindowStartMs = now
        }

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

            val letterboxedBitmap = letterboxToInput(bitmap) ?: run {
                image.close()
                return
            }

            tensorImage.load(letterboxedBitmap)
            val processedImage = imageProcessor?.process(tensorImage) ?: tensorImage
            val inputBuffer = processedImage.buffer.apply { rewind() }
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
        } catch (e: Throwable) {
            Log.e(TAG, "Analysis error", e)
            pushDebug("Error analyze: ${e.message ?: "unknown"}")
            onDetection(null)
        } finally {
            image.close()
        }
    }

    /**
     * Convert RGBA_8888 ImageProxy into Bitmap without JPEG transcoding.
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            if (image.planes.isEmpty()) return null
            val width = image.width
            val height = image.height

            ensureFrameBuffers(width, height)
            val plane = image.planes[0]
            val srcBuffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            val expectedSize = width * height * 4
            if (rgbaScratch.size != expectedSize) {
                rgbaScratch = ByteArray(expectedSize)
            }

            if (pixelStride == 4 && rowStride == width * 4) {
                srcBuffer.rewind()
                srcBuffer.get(rgbaScratch, 0, expectedSize)
            } else {
                for (row in 0 until height) {
                    val rowStart = row * rowStride
                    val outRowStart = row * width * 4
                    var outCol = 0
                    for (col in 0 until width) {
                        val srcIndex = rowStart + col * pixelStride
                        val dstIndex = outRowStart + outCol
                        rgbaScratch[dstIndex] = srcBuffer.get(srcIndex)
                        rgbaScratch[dstIndex + 1] = srcBuffer.get(srcIndex + 1)
                        rgbaScratch[dstIndex + 2] = srcBuffer.get(srcIndex + 2)
                        rgbaScratch[dstIndex + 3] = srcBuffer.get(srcIndex + 3)
                        outCol += 4
                    }
                }
            }

            val bitmap = rgbFrameBitmap ?: return null
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(rgbaScratch))

            val rotation = image.imageInfo.rotationDegrees
            if (rotation == 0) {
                bitmap
            } else {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Bitmap conversion failed", e)
            pushDebug("Konversi frame gagal: ${e.message ?: "unknown"}")
            null
        }
    }

    private fun ensureFrameBuffers(width: Int, height: Int) {
        val current = rgbFrameBitmap
        if (current == null || current.width != width || current.height != height) {
            rgbFrameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            rgbaScratch = ByteArray(width * height * 4)
        }
    }

    private fun ensureLetterboxBuffer() {
        val current = letterboxBitmap
        if (current == null || current.width != inputWidth || current.height != inputHeight) {
            letterboxBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
            letterboxCanvas = Canvas(letterboxBitmap!!)
        }
    }

    private fun letterboxToInput(source: Bitmap): Bitmap? {
        ensureLetterboxBuffer()
        val dstBitmap = letterboxBitmap ?: return null
        val canvas = letterboxCanvas ?: return null

        val srcW = source.width.coerceAtLeast(1)
        val srcH = source.height.coerceAtLeast(1)
        sourceFrameWidth = srcW
        sourceFrameHeight = srcH

        val scale = minOf(inputWidth / srcW.toFloat(), inputHeight / srcH.toFloat())
        val scaledW = (srcW * scale).roundToInt().coerceAtLeast(1)
        val scaledH = (srcH * scale).roundToInt().coerceAtLeast(1)
        val left = ((inputWidth - scaledW) / 2f)
        val top = ((inputHeight - scaledH) / 2f)

        letterboxScale = scale
        letterboxPadX = left
        letterboxPadY = top

        canvas.drawColor(Color.BLACK)
        val srcRect = Rect(0, 0, srcW, srcH)
        val dstRect = Rect(
            left.roundToInt(),
            top.roundToInt(),
            (left + scaledW).roundToInt(),
            (top + scaledH).roundToInt()
        )
        canvas.drawBitmap(source, srcRect, dstRect, null)
        return dstBitmap
    }

    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        interpreter = null
        gpuDelegate = null
    }

    fun updateConfidenceThreshold(newThreshold: Float) {
        val clamped = newThreshold.coerceIn(0.05f, 0.95f)
        if (kotlin.math.abs(clamped - confidenceThreshold) < 0.001f) return
        confidenceThreshold = clamped
        pushDebug("Threshold diubah ke ${"%.2f".format(Locale.US, confidenceThreshold)}")
    }

    private fun normalizeModelLabel(rawLabel: String?): String? {
        val classPattern = Regex("^\\s*class\\s+(\\d+)\\s*$", RegexOption.IGNORE_CASE)
        val classMatch = rawLabel?.let { classPattern.find(it) }
        if (classMatch != null) {
            val classId = classMatch.groupValues[1].toIntOrNull()
            if (classId != null) {
                val mapped = classNameById[classId]
                if (!mapped.isNullOrBlank()) return mapped
            }
        }

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

    private fun loadClassNamesFromMetadata(): Map<Int, String> {
        val metadataCandidates = listOf("metadata.yaml", "metadata.yml")
        val metadataText = metadataCandidates.firstNotNullOfOrNull { candidate ->
            runCatching {
                context.assets.open(candidate).bufferedReader().use { it.readText() }
            }.getOrNull()
        } ?: return emptyMap()

        val result = mutableMapOf<Int, String>()
        var insideNames = false

        for (line in metadataText.lineSequence()) {
            val trimmed = line.trimEnd()
            if (!insideNames) {
                if (trimmed == "names:") {
                    insideNames = true
                }
                continue
            }

            if (trimmed.isBlank()) continue
            if (!line.startsWith(" ") && !line.startsWith("\t")) break

            val match = Regex("^\\s*(\\d+)\\s*:\\s*(.+?)\\s*$").find(line) ?: continue
            val id = match.groupValues[1].toIntOrNull() ?: continue
            val label = normalizeModelLabel(match.groupValues[2]) ?: continue
            result[id] = label
        }

        if (result.isNotEmpty()) {
            pushDebug("Metadata loaded: ${result.size} kelas")
        }

        return result
    }

    private fun isFloat16ModelName(name: String): Boolean {
        return "float16" in name || "fp16" in name
    }

    private fun scoreModelName(name: String): Int {
        val lower = name.lowercase(Locale.US)
        var score = 0
        if ("416" in lower) score += 8
        if ("nms" in lower) score += 6
        if ("fp16" in lower || "float16" in lower) score += 4
        if ("torso" in lower) score += 3
        if ("best" in lower) score += 1
        return score
    }

    private fun buildImageProcessor(): ImageProcessor {
        val builder = ImageProcessor.Builder()

        if (inputType == DataType.FLOAT32) {
            // Match the old pipeline normalization: [0,255] -> [0,1].
            builder.add(NormalizeOp(0f, 255f))
        }

        return builder.build()
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
        var best: Candidate? = null
        val classCount = classNameById.size

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
                    val denseColsNoObj = classCount + 4
                    val denseColsWithObj = classCount + 5

                    // Prefer deterministic mode selection from metadata class count.
                    when {
                        classCount > 0 && (colCount == denseColsNoObj || colCount == denseColsWithObj) -> {
                            best = maxCandidate(best, parseNmsRowsBest(matrix))
                        }
                        classCount > 0 && (rowCount == denseColsNoObj || rowCount == denseColsWithObj) -> {
                            best = maxCandidate(best, parseYoloTransposedBest(matrix))
                        }
                        colCount == 6 -> {
                            best = maxCandidate(best, parseNmsRowsBest(matrix))
                        }
                        rowCount == 6 -> {
                            best = maxCandidate(best, parseYoloTransposedBest(matrix))
                        }
                        else -> {
                            // Fallback heuristics when output shape is uncommon.
                            if (colCount > rowCount) {
                                best = maxCandidate(best, parseNmsRowsBest(matrix))
                            } else {
                                best = maxCandidate(best, parseYoloTransposedBest(matrix))
                            }
                        }
                    }

                    maybePushShapeSummary("shape=[1,$rowCount,$colCount], classes=$classCount")
                } else if (first is FloatArray) {
                    @Suppress("UNCHECKED_CAST")
                    val matrix = output as Array<FloatArray>
                    best = maxCandidate(best, parseNmsRowsBest(matrix))
                }
            }
        }

        best ?: return null
        val label = normalizeModelLabel(best.label) ?: return null

        return DetectionResult(
            mockLabel = label,
            organName = label,
            boundingBox = floatArrayOf(best.left, best.top, best.right, best.bottom),
            confidence = best.score
        )
    }

    private fun parseNmsRowsBest(rows: Array<FloatArray>): Candidate? {
        var best: Candidate? = null
        for (row in rows) {
            val candidate = parseRowCandidate(row) ?: continue
            best = maxCandidate(best, candidate)
        }
        return best
    }

    private fun parseRowCandidate(row: FloatArray): Candidate? {
        if (row.size < 6) return null
        val classCount = classNameById.size

        // Classic NMS row format: [x1, y1, x2, y2, score, class_id]
        if (row.size == 6) {
            val score = row[4]
            if (!score.isFinite() || score < confidenceThreshold || score > 1.2f) return null

            val cls = row[5].toInt().coerceAtLeast(0)
            if (classCount > 0 && cls !in 0 until classCount) return null
            val box = normalizeBox(row[0], row[1], row[2], row[3]) ?: return null
            return Candidate(
                left = box[0],
                top = box[1],
                right = box[2],
                bottom = box[3],
                score = score,
                label = classNameById[cls] ?: "Class $cls"
            )
        }

        // Dense YOLO row format can be [cx,cy,w,h,class...] or [cx,cy,w,h,obj,class...]
        val hasObjectness = classCount > 0 && row.size == classCount + 5
        val classStart = if (hasObjectness) 5 else 4
        if (classStart >= row.size) return null

        val objectness = if (hasObjectness) row[4].coerceAtLeast(0f) else 1f
        var bestScore = 0f
        var bestClass = -1

        for (c in classStart until row.size) {
            val classProb = row[c].coerceAtLeast(0f)
            val score = classProb * objectness
            if (score > bestScore) {
                bestScore = score
                bestClass = c - classStart
            }
        }

        if (bestScore < confidenceThreshold || bestClass < 0) return null
        if (classCount > 0 && bestClass !in 0 until classCount) return null

        val cx = row[0]
        val cy = row[1]
        val w = row[2]
        val h = row[3]

        val box = normalizeBox(cx - (w / 2f), cy - (h / 2f), cx + (w / 2f), cy + (h / 2f))
            ?: return null

        return Candidate(
            left = box[0],
            top = box[1],
            right = box[2],
            bottom = box[3],
            score = bestScore,
            label = classNameById[bestClass] ?: "Class $bestClass"
        )
    }

    private fun parseYoloTransposedBest(matrix: Array<FloatArray>): Candidate? {
        var best: Candidate? = null
        val channels = matrix.size
        val numPred = matrix[0].size

        if (channels < 6) return null

        val classCount = classNameById.size
        val hasObjectness = classCount > 0 && channels == classCount + 5
        val classStart = if (hasObjectness) 5 else 4
        if (classStart >= channels) return null

        for (i in 0 until numPred) {
            val cx = matrix[0][i]
            val cy = matrix[1][i]
            val w = matrix[2][i]
            val h = matrix[3][i]

            var bestScore = 0f
            var bestClass = -1
            val objectness = if (hasObjectness) matrix[4][i].coerceAtLeast(0f) else 1f
            for (c in classStart until channels) {
                val score = matrix[c][i].coerceAtLeast(0f) * objectness
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c - classStart
                }
            }

            if (bestScore < confidenceThreshold || bestClass < 0) continue
            if (classCount > 0 && bestClass !in 0 until classCount) continue

            val x1 = cx - (w / 2f)
            val y1 = cy - (h / 2f)
            val x2 = cx + (w / 2f)
            val y2 = cy + (h / 2f)

            val box = normalizeBox(x1, y1, x2, y2)
            if (box == null) continue

            val candidate = Candidate(
                left = box[0],
                top = box[1],
                right = box[2],
                bottom = box[3],
                score = bestScore,
                label = classNameById[bestClass] ?: "Class $bestClass"
            )
            best = maxCandidate(best, candidate)
        }

        return best
    }

    private fun maxCandidate(current: Candidate?, incoming: Candidate?): Candidate? {
        if (incoming == null) return current
        if (current == null) return incoming
        return if (incoming.score > current.score) incoming else current
    }

    private fun normalizeBox(x1: Float, y1: Float, x2: Float, y2: Float): FloatArray? {
        val maxAbs = max(max(kotlin.math.abs(x1), kotlin.math.abs(y1)), max(kotlin.math.abs(x2), kotlin.math.abs(y2)))
        val inputBox = if (maxAbs <= 2f) {
            floatArrayOf(
                x1 * inputWidth.toFloat(),
                y1 * inputHeight.toFloat(),
                x2 * inputWidth.toFloat(),
                y2 * inputHeight.toFloat()
            )
        } else {
            floatArrayOf(
                x1,
                y1,
                x2,
                y2
            )
        }

        val srcW = sourceFrameWidth.coerceAtLeast(1).toFloat()
        val srcH = sourceFrameHeight.coerceAtLeast(1).toFloat()
        val scale = letterboxScale.coerceAtLeast(1e-6f)

        val srcLeftPx = (inputBox[0] - letterboxPadX) / scale
        val srcTopPx = (inputBox[1] - letterboxPadY) / scale
        val srcRightPx = (inputBox[2] - letterboxPadX) / scale
        val srcBottomPx = (inputBox[3] - letterboxPadY) / scale

        var left = (srcLeftPx / srcW).coerceIn(0f, 1f)
        var top = (srcTopPx / srcH).coerceIn(0f, 1f)
        var right = (srcRightPx / srcW).coerceIn(0f, 1f)
        var bottom = (srcBottomPx / srcH).coerceIn(0f, 1f)

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
        pushDebug("Summary: frames=$analyzedFrames, hit=$detectionFrames, fps=${"%.1f".format(Locale.US, currentFps)}, model=$model, thr=${"%.2f".format(Locale.US, confidenceThreshold)}, state=$reason")
    }

    private fun maybePushShapeSummary(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastSummaryLogTime < 2000L) return
        pushDebug("Tensor: $message")
    }

    private fun pushDebug(message: String) {
        Log.d(TAG, message)
        onDebugLog(message)
    }
}
