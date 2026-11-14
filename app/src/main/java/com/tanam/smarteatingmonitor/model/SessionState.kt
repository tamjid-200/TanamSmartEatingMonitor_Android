package com.tanam.smarteatingmonitor.model

/** Mirrors the iOS `SessionState` state machine. */
sealed interface SessionState {
    data object Idle : SessionState
    data object Watching : SessionState
    data class CheckInDue(val graceRemaining: Int) : SessionState
    data class Locked(val reason: LockReason) : SessionState
    data class Ended(val summary: SessionSummary) : SessionState
}

val SessionState.isActive: Boolean
    get() = this is SessionState.Watching || this is SessionState.CheckInDue || this is SessionState.Locked

enum class LockReason(val title: String, val message: String) {
    MOUTH_NOT_EMPTY(
        "Mouth Not Empty",
        "Food detected in your mouth. Finish chewing and solve the math to continue."
    ),
    NO_FACE_DETECTED(
        "No Face Detected",
        "Your face wasn't visible. Position yourself in front of the camera and solve the math."
    ),
    DIDNT_OPEN_IN_TIME(
        "Missed Check",
        "You didn't open your mouth in time. Solve the math to continue."
    ),
    BACKGROUNDED(
        "App Backgrounded",
        "You left the app. Solve the math to resume your session."
    ),
}

data class SessionSummary(
    val durationSeconds: Long,
    val passes: Int,
    val locks: Int,
    val startMillis: Long,
    val endMillis: Long,
) {
    /** Same formula as iOS: passes / (passes + locks), or null when there were no passes. */
    val successRatePercent: Int?
        get() = if (passes > 0) (passes * 100) / (passes + locks) else null
}

enum class MathDifficulty(val label: String, val range: IntRange) {
    EASY("Easy (1-10)", 1..10),
    MEDIUM("Medium (10-50)", 10..50),
    HARD("Hard (50-100)", 50..100),
}

data class CheckConfig(
    val intervalSeconds: Int = 50,
    val gracePeriodSeconds: Int = 10,
    val mouthOpenThreshold: Float = 0.35f,
    val mouthOpenDurationMillis: Long = 1_000L,
    val requireEmptyMouth: Boolean = true,
    val emptyMouthSensitivity: Float = 0.6f,
    val mathDifficulty: MathDifficulty = MathDifficulty.MEDIUM,
    val requireRecheckOnUnlock: Boolean = false,
    val useYouTubeKids: Boolean = false,
    /** Android equivalent of iOS Guided Access: pins the app while a session runs. */
    val pinScreenDuringSession: Boolean = false,
) {
    companion object {
        val INTERVAL_RANGE = 20..600
        const val INTERVAL_STEP = 10
        val GRACE_RANGE = 5..60
        const val GRACE_STEP = 5
    }
}

/** One processed camera frame. */
data class DetectionResult(
    val faceDetected: Boolean = false,
    /** 0 = closed, 1 = wide open (smoothed). */
    val mouthOpenness: Float = 0f,
    val isMouthOpen: Boolean = false,
    /** Empty-mouth verdict for this frame; null = not analysed. */
    val mouthLooksEmpty: Boolean? = null,
    val error: String? = null,
) {
    companion object {
        val NONE = DetectionResult()
    }
}
