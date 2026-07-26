package com.anatomy.app.ui.screen

import android.util.Log
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/**
 * CameraPreview — Composable wrapping CameraX PreviewView.
 *
 * Binds Preview + ImageAnalysis use cases to the lifecycle.
 * Camera starts when [isActive] is true and unbinds when false.
 *
 * @param isActive Whether the camera should be running.
 * @param analyzer Optional ImageAnalysis.Analyzer for frame processing.
 * @param modifier Compose modifier for layout.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    analyzer: ImageAnalysis.Analyzer? = null,
    onPreviewReady: ((PreviewView) -> Unit)? = null,
    onCameraReady: (() -> Unit)? = null,
    onImageCaptureReady: ((ImageCapture) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    // Bind/unbind camera based on isActive
    LaunchedEffect(isActive) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

                    if (isActive) {
                try {
                    // Unbind all first
                    cameraProvider.unbindAll()

                    // Preview use case
                    val preview = Preview.Builder()
                        .build()
                        .apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }

                    // Image capture use case for readiness tracking and future capture.
                    val imageCapture = ImageCapture.Builder()
                        .setTargetRotation(Surface.ROTATION_0)
                        .build()

                    // Image analysis use case
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setTargetRotation(Surface.ROTATION_0)
                        .build()
                        .apply {
                            analyzer?.let {
                                setAnalyzer(cameraExecutor, it)
                            }
                        }

                    // Use back camera
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    // Bind to lifecycle
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                        imageAnalysis
                    )

                    onPreviewReady?.invoke(previewView)
                    onImageCaptureReady?.invoke(imageCapture)
                    onCameraReady?.invoke()

                    Log.d("CameraPreview", "Camera bound successfully.")
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Camera bind failed", e)
                }
            } else {
                cameraProvider.unbindAll()
                Log.d("CameraPreview", "Camera unbound.")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            // Unbind camera use-cases first (must run on main thread via its own executor),
            // then shut down the background analysis executor to avoid
            // RejectedExecutionException if the future fires after shutdown.
            try {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    try { future.get().unbindAll() } catch (e: Exception) {
                        Log.e("CameraPreview", "unbindAll on dispose failed", e)
                        // Notify caller that preview view is ready so they can
                        // capture the current frame via `previewView.bitmap`.
                        onPreviewReady?.invoke(previewView)
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                Log.e("CameraPreview", "Failed to schedule unbind on dispose", e)
            }
            // Shut down the analysis thread only after scheduling the unbind.
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
