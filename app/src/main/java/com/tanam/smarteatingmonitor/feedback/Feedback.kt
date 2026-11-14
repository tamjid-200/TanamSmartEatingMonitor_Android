package com.tanam.smarteatingmonitor.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Sounds and haptics (Android counterpart of the iOS NotificationService). */
class Feedback(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val tones: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    } catch (_: RuntimeException) {
        null
    }

    fun success() {
        playTone(ToneGenerator.TONE_PROP_ACK, 180)
        vibrate(longArrayOf(0, 40, 60, 40))
    }

    fun error() {
        playTone(ToneGenerator.TONE_SUP_ERROR, 350)
        vibrate(longArrayOf(0, 220, 90, 220))
    }

    fun warning() {
        vibrate(longArrayOf(0, 35))
    }

    fun release() {
        tones?.release()
    }

    private fun playTone(tone: Int, durationMs: Int) {
        try { tones?.startTone(tone, durationMs) } catch (_: RuntimeException) { }
    }

    private fun vibrate(pattern: LongArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: RuntimeException) { }
    }
}
