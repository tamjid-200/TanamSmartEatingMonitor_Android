package com.tanam.smarteatingmonitor.camera

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.tanam.smarteatingmonitor.detection.MouthDetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Abstraction the session logic talks to. */
interface CameraHandle {
    fun start()
    fun stop()
}

/**
 * Front camera via CameraX. Bound to the activity lifecycle, so it pauses automatically
 * when the app goes to the background and resumes when it comes back.
 * Frames are throttled to ~12 fps (same as iOS) to save battery.
 */
class CameraController(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val detector: MouthDetector,
    private val onError: (String?) -> Unit,
) : CameraHandle {

    private val appContext = context.applicationContext
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var wanted = false
    @Volatile private var lastFrameAt = 0L

    /** Live preview for the thumbnail; its surface is attached by the UI. */
    val preview: Preview = Preview.Builder().build()

    val hasFrontCamera: Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)

    override fun start() {
        wanted = true
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            if (!wanted) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    onError("No front camera found on this device.")
                    return@addListener
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { image ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastFrameAt < MIN_FRAME_INTERVAL_MS) {
                        image.close()
                    } else {
                        lastFrameAt = now
                        detector.process(image, analysisExecutor)
                    }
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis,
                )
                onError(null)
            } catch (e: Exception) {
                onError("Could not start the front camera: ${e.message ?: e.javaClass.simpleName}")
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    override fun stop() {
        wanted = false
        try { provider?.unbindAll() } catch (_: Exception) { }
    }

    fun release() {
        stop()
        analysisExecutor.shutdown()
    }

    private companion object {
        const val MIN_FRAME_INTERVAL_MS = 1000L / 12L
    }
}
