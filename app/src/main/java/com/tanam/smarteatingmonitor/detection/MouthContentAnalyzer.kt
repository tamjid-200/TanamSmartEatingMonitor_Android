package com.tanam.smarteatingmonitor.detection

import androidx.camera.core.ImageProxy
import kotlin.math.max
import kotlin.math.min

/**
 * Best-guess check of whether an open mouth looks empty (port of the iOS analyzer).
 * An empty open mouth is mostly dark cavity, red/pink tongue and white teeth.
 * Anything else (brown, green, yellow, beige mush…) counts as "food-like".
 * This is a colour heuristic, not a perfect detector — tune the sensitivity in Settings.
 *
 * Reads the YUV_420_888 camera frame directly (no bitmap conversion), sampling a grid
 * inside the inner-lip box. Coordinates are in ML Kit's upright image space.
 */
class MouthContentAnalyzer {

    /** 0.0 = strict (little food-like colour allowed), 1.0 = lenient. */
    @Volatile
    var sensitivity: Float = 0.6f

    fun isMouthEmpty(
        image: ImageProxy,
        rotationDegrees: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): Boolean {
        // Shrink a little so the lips themselves are excluded
        val w = right - left
        val h = bottom - top
        val l = left + w * 0.15f
        val r = right - w * 0.15f
        val t = top + h * 0.15f
        val b = bottom - h * 0.15f
        if (r - l < 6f || b - t < 4f) return true // too small to judge

        val bufW = image.width
        val bufH = image.height
        val sideways = rotationDegrees % 180 != 0
        val uprightW = if (sideways) bufH else bufW
        val uprightH = if (sideways) bufW else bufH

        val planes = image.planes
        if (planes.size < 3) return true
        val yBuf = planes[0].buffer
        val yRow = planes[0].rowStride
        val yPix = planes[0].pixelStride
        val uBuf = planes[1].buffer
        val uRow = planes[1].rowStride
        val uPix = planes[1].pixelStride
        val vBuf = planes[2].buffer
        val vRow = planes[2].rowStride
        val vPix = planes[2].pixelStride

        var mouthLike = 0
        var foodLike = 0

        for (row in 0 until GRID_ROWS) {
            val uy = (t + (b - t) * (row + 0.5f) / GRID_ROWS).toInt().coerceIn(0, uprightH - 1)
            for (col in 0 until GRID_COLS) {
                val ux = (l + (r - l) * (col + 0.5f) / GRID_COLS).toInt().coerceIn(0, uprightW - 1)

                // Map upright coordinates back to the sensor buffer
                val bx: Int
                val by: Int
                when (rotationDegrees) {
                    90 -> { bx = uy; by = bufH - 1 - ux }
                    180 -> { bx = bufW - 1 - ux; by = bufH - 1 - uy }
                    270 -> { bx = bufW - 1 - uy; by = ux }
                    else -> { bx = ux; by = uy }
                }
                if (bx !in 0 until bufW || by !in 0 until bufH) continue

                val yIndex = by * yRow + bx * yPix
                val uIndex = (by / 2) * uRow + (bx / 2) * uPix
                val vIndex = (by / 2) * vRow + (bx / 2) * vPix
                if (yIndex >= yBuf.limit() || uIndex >= uBuf.limit() || vIndex >= vBuf.limit()) continue

                val yy = (yBuf.get(yIndex).toInt() and 0xFF).toFloat()
                val uu = ((uBuf.get(uIndex).toInt() and 0xFF) - 128).toFloat()
                val vv = ((vBuf.get(vIndex).toInt() and 0xFF) - 128).toFloat()

                val red = (yy + 1.402f * vv).coerceIn(0f, 255f)
                val green = (yy - 0.344136f * uu - 0.714136f * vv).coerceIn(0f, 255f)
                val blue = (yy + 1.772f * uu).coerceIn(0f, 255f)

                val lum = 0.299f * red + 0.587f * green + 0.114f * blue
                val maxC = max(red, max(green, blue))
                val minC = min(red, min(green, blue))
                val sat = if (maxC > 0f) (maxC - minC) / maxC else 0f

                val isDark = lum < 60f                                   // throat / cavity
                val isRedPink = red > green * 1.25f && red > blue * 1.1f // tongue, gums
                val isWhite = lum > 170f && sat < 0.18f                  // teeth

                if (isDark || isRedPink || isWhite) mouthLike++ else foodLike++
            }
        }

        val total = mouthLike + foodLike
        if (total == 0) return true
        val foodRatio = foodLike.toFloat() / total

        // strict: allow 20% food-like pixels … lenient: allow 70%
        val allowed = 0.2f + sensitivity * 0.5f
        return foodRatio <= allowed
    }

    private companion object {
        const val GRID_COLS = 24
        const val GRID_ROWS = 12
    }
}
