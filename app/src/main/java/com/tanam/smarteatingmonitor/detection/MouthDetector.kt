package com.tanam.smarteatingmonitor.detection

import android.graphics.PointF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.tanam.smarteatingmonitor.model.DetectionResult
import java.util.concurrent.Executor
import kotlin.math.roundToInt

/**
 * Runs ML Kit face contours on camera frames (Android counterpart of the iOS Vision detector).
 * Mouth openness = inner-lip gap / mouth width, smoothed over the last few frames.
 */
class MouthDetector {

    // Settings (written on main, read on the camera thread)
    @Volatile var openThreshold: Float = 0.35f
    @Volatile var analyzeMouthContent: Boolean = true
    @Volatile var emptyMouthSensitivity: Float = 0.6f

    /** Called on the camera thread after every processed frame. */
    @Volatile var onResult: ((DetectionResult) -> Unit)? = null

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(MIN_FACE_WIDTH_FRACTION)
            .build()
    )
    private val contentAnalyzer = MouthContentAnalyzer()
    private val history = ArrayDeque<Float>()

    /** Takes ownership of [image] and always closes it. */
    @OptIn(ExperimentalGetImage::class)
    fun process(image: ImageProxy, executor: Executor) {
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }
        val rotation = image.imageInfo.rotationDegrees
        val input = try {
            InputImage.fromMediaImage(mediaImage, rotation)
        } catch (t: Throwable) {
            image.close()
            return
        }

        detector.process(input).addOnCompleteListener(executor) { task ->
            val result = try {
                if (task.isSuccessful) {
                    analyse(task.result.orEmpty(), image, rotation)
                } else {
                    history.clear()
                    DetectionResult(error = task.exception?.message)
                }
            } catch (t: Throwable) {
                history.clear()
                DetectionResult(error = t.message)
            } finally {
                image.close()
            }
            onResult?.invoke(result)
        }
    }

    fun reset() {
        history.clear()
    }

    fun close() {
        try { detector.close() } catch (_: Throwable) { }
    }

    private fun analyse(faces: List<Face>, image: ImageProxy, rotation: Int): DetectionResult {
        val uprightWidth = if (rotation % 180 == 0) image.width else image.height
        val face = faces.maxByOrNull { it.boundingBox.width() }
        if (face == null || face.boundingBox.width().toFloat() / uprightWidth < MIN_FACE_WIDTH_FRACTION) {
            history.clear()
            return DetectionResult(faceDetected = false)
        }

        val upperInner = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points.orEmpty()
        val lowerInner = face.getContour(FaceContour.LOWER_LIP_TOP)?.points.orEmpty()
        val upperOuter = face.getContour(FaceContour.UPPER_LIP_TOP)?.points.orEmpty()
        val lowerOuter = face.getContour(FaceContour.LOWER_LIP_BOTTOM)?.points.orEmpty()
        if (upperInner.size < 3 || lowerInner.size < 3) {
            return DetectionResult(faceDetected = true)
        }

        // Inner-lip gap measured at the centre of the mouth
        val upperY = middleAverageY(upperInner)
        val lowerY = middleAverageY(lowerInner)
        val gap = (lowerY - upperY).coerceAtLeast(0f)

        // Mouth width from corner to corner
        val outline = (upperOuter + lowerOuter).ifEmpty { upperInner + lowerInner }
        val minX = outline.minOf { it.x }
        val maxX = outline.maxOf { it.x }
        val mouthWidth = (maxX - minX).coerceAtLeast(1f)
        val ratio = gap / mouthWidth

        // closed ≈ 0.00–0.05, wide open ≈ 0.45+  →  map to 0…1
        val raw = ((ratio - 0.05f) / 0.40f).coerceIn(0f, 1f)
        history.addLast(raw)
        while (history.size > HISTORY_SIZE) history.removeFirst()
        val smoothed = history.sum() / history.size

        val threshold = openThreshold
        val open = smoothed > threshold

        // Empty-mouth check only while the mouth is clearly open
        var empty: Boolean? = null
        if (analyzeMouthContent && open) {
            val inner = upperInner + lowerInner
            contentAnalyzer.sensitivity = emptyMouthSensitivity
            empty = contentAnalyzer.isMouthEmpty(
                image = image,
                rotationDegrees = rotation,
                left = inner.minOf { it.x },
                top = upperY,
                right = inner.maxOf { it.x },
                bottom = lowerY,
            )
        }

        // Round so the UI only recomposes on visible changes
        val rounded = (smoothed * 50f).roundToInt() / 50f
        return DetectionResult(
            faceDetected = true,
            mouthOpenness = rounded,
            isMouthOpen = open,
            mouthLooksEmpty = empty,
        )
    }

    /** Average y of the middle three points of a left-to-right lip contour. */
    private fun middleAverageY(points: List<PointF>): Float {
        val mid = points.size / 2
        val from = (mid - 1).coerceAtLeast(0)
        val to = (mid + 1).coerceAtMost(points.size - 1)
        var sum = 0f
        for (i in from..to) sum += points[i].y
        return sum / (to - from + 1)
    }

    private companion object {
        const val HISTORY_SIZE = 4
        const val MIN_FACE_WIDTH_FRACTION = 0.12f
    }
}
