package com.tanam.smarteatingmonitor.data

import android.content.Context
import androidx.core.content.edit
import com.tanam.smarteatingmonitor.model.CheckConfig
import com.tanam.smarteatingmonitor.model.MathDifficulty

/** Persists settings so they survive app restarts. */
class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tanam_settings", Context.MODE_PRIVATE)

    fun load(): CheckConfig {
        val d = CheckConfig()
        return CheckConfig(
            intervalSeconds = prefs.getInt(K_INTERVAL, d.intervalSeconds)
                .coerceIn(CheckConfig.INTERVAL_RANGE),
            gracePeriodSeconds = prefs.getInt(K_GRACE, d.gracePeriodSeconds)
                .coerceIn(CheckConfig.GRACE_RANGE),
            requireEmptyMouth = prefs.getBoolean(K_EMPTY, d.requireEmptyMouth),
            emptyMouthSensitivity = prefs.getFloat(K_SENSITIVITY, d.emptyMouthSensitivity).coerceIn(0f, 1f),
            mathDifficulty = MathDifficulty.entries.firstOrNull { it.name == prefs.getString(K_DIFFICULTY, null) }
                ?: d.mathDifficulty,
            requireRecheckOnUnlock = prefs.getBoolean(K_RECHECK, d.requireRecheckOnUnlock),
            useYouTubeKids = prefs.getBoolean(K_KIDS, d.useYouTubeKids),
            pinScreenDuringSession = prefs.getBoolean(K_PIN, d.pinScreenDuringSession),
        )
    }

    fun save(config: CheckConfig) {
        prefs.edit {
            putInt(K_INTERVAL, config.intervalSeconds)
            putInt(K_GRACE, config.gracePeriodSeconds)
            putBoolean(K_EMPTY, config.requireEmptyMouth)
            putFloat(K_SENSITIVITY, config.emptyMouthSensitivity)
            putString(K_DIFFICULTY, config.mathDifficulty.name)
            putBoolean(K_RECHECK, config.requireRecheckOnUnlock)
            putBoolean(K_KIDS, config.useYouTubeKids)
            putBoolean(K_PIN, config.pinScreenDuringSession)
        }
    }

    private companion object {
        const val K_INTERVAL = "interval"
        const val K_GRACE = "grace"
        const val K_EMPTY = "require_empty_mouth"
        const val K_SENSITIVITY = "empty_mouth_sensitivity"
        const val K_DIFFICULTY = "math_difficulty"
        const val K_RECHECK = "recheck_on_unlock"
        const val K_KIDS = "use_youtube_kids"
        const val K_PIN = "pin_screen"
    }
}
