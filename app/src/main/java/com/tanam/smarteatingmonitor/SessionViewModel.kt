package com.tanam.smarteatingmonitor

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanam.smarteatingmonitor.camera.CameraHandle
import com.tanam.smarteatingmonitor.data.SettingsRepository
import com.tanam.smarteatingmonitor.detection.MouthDetector
import com.tanam.smarteatingmonitor.feedback.Feedback
import com.tanam.smarteatingmonitor.model.CheckConfig
import com.tanam.smarteatingmonitor.model.DetectionResult
import com.tanam.smarteatingmonitor.model.LockReason
import com.tanam.smarteatingmonitor.model.SessionState
import com.tanam.smarteatingmonitor.model.SessionSummary
import com.tanam.smarteatingmonitor.model.isActive
import com.tanam.smarteatingmonitor.web.VideoHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Session logic — a faithful port of the iOS `SessionManager`:
 * watching → (interval) → check-in with grace period → pass / lock → maths unlock.
 */
class SessionViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)
    private val feedback = Feedback(app)
    private val mainHandler = Handler(Looper.getMainLooper())

    val mouthDetector = MouthDetector()

    // Attached by the activity (views must not outlive it)
    private var video: VideoHandle? = null
    private var camera: CameraHandle? = null

    // region Observable state

    var state: SessionState by mutableStateOf(SessionState.Idle)
        private set
    var timeUntilNextCheck by mutableIntStateOf(0)
        private set
    var sessionDurationSeconds by mutableLongStateOf(0L)
        private set
    var passes by mutableIntStateOf(0)
        private set
    var locks by mutableIntStateOf(0)
        private set
    var lockQuestion by mutableStateOf("")
        private set
    var detection by mutableStateOf(DetectionResult.NONE)
        private set
    var cameraError by mutableStateOf<String?>(null)
    var isVideoPlaying by mutableStateOf(false)
    var config by mutableStateOf(settings.load())
        private set

    // endregion

    private var lockAnswer = 0
    private var graceRemaining = 0
    private var tickJob: Job? = null
    private var sessionStartMillis = 0L
    private var sessionUsesKids = false
    private var lastFaceSeenAt = 0L
    private var mouthOpenSince = 0L
    private var emptyVotes = 0
    private var foodVotes = 0

    init {
        mouthDetector.onResult = { result -> mainHandler.post { handleDetection(result) } }
        applyDetectorSettings()
    }

    fun attach(video: VideoHandle, camera: CameraHandle) {
        this.video = video
        this.camera = camera
        // Activity was recreated mid-session: bring everything back, then require the maths.
        if (state.isActive) {
            camera.start()
            video.load(sessionUsesKids)
            if (state !is SessionState.Locked) lock(LockReason.BACKGROUNDED)
            else video.setLocked(true)
        }
    }

    fun detach() {
        video = null
        camera = null
    }

    // region Settings

    fun updateConfig(transform: (CheckConfig) -> CheckConfig) {
        config = transform(config)
        settings.save(config)
        applyDetectorSettings()
    }

    fun resetSettings() = updateConfig { CheckConfig() }

    private fun applyDetectorSettings() {
        mouthDetector.openThreshold = config.mouthOpenThreshold
        mouthDetector.emptyMouthSensitivity = config.emptyMouthSensitivity
        mouthDetector.analyzeMouthContent = config.requireEmptyMouth
    }

    fun signOutOfYouTube() {
        video?.signOut(reloadKids = config.useYouTubeKids)
    }

    // endregion

    // region Session control

    fun startSession(useYouTubeKids: Boolean) {
        if (state != SessionState.Idle) return

        sessionUsesKids = useYouTubeKids
        passes = 0
        locks = 0
        sessionDurationSeconds = 0
        sessionStartMillis = System.currentTimeMillis()
        lastFaceSeenAt = SystemClock.elapsedRealtime()
        resetOpenTracking()
        mouthDetector.reset()
        detection = DetectionResult.NONE
        cameraError = null

        camera?.start()
        video?.setLocked(false)
        video?.load(useYouTubeKids)

        timeUntilNextCheck = config.intervalSeconds
        state = SessionState.Watching
        startTicking()
    }

    fun stopSession() {
        if (!state.isActive) return
        val summary = SessionSummary(
            durationSeconds = sessionDurationSeconds,
            passes = passes,
            locks = locks,
            startMillis = sessionStartMillis,
            endMillis = System.currentTimeMillis(),
        )
        cleanup()
        state = SessionState.Ended(summary)
    }

    fun returnHome() {
        state = SessionState.Idle
    }

    private fun cleanup() {
        tickJob?.cancel()
        tickJob = null
        video?.pause()
        video?.setLocked(false)
        camera?.stop()
        mouthDetector.reset()
        resetOpenTracking()
        detection = DetectionResult.NONE
        isVideoPlaying = false
        timeUntilNextCheck = 0
        graceRemaining = 0
    }

    // endregion

    // region 1-second tick

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            var next = SystemClock.elapsedRealtime() + 1_000L
            while (true) { // delay() is cancellable, so cancel() ends this loop
                delay((next - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                next += 1_000L
                tick()
            }
        }
    }

    private fun tick() {
        val current = state
        if (!current.isActive) return
        sessionDurationSeconds += 1
        video?.ensureInjected()

        when (current) {
            SessionState.Watching -> {
                if (timeUntilNextCheck > 1) {
                    timeUntilNextCheck -= 1
                } else {
                    timeUntilNextCheck = 0
                    triggerCheckIn()
                }
            }
            is SessionState.CheckInDue -> {
                video?.pause() // keep the video still until the check passes
                if (graceRemaining > 1) {
                    graceRemaining -= 1
                    state = SessionState.CheckInDue(graceRemaining)
                    feedback.warning()
                } else {
                    lock(if (detection.faceDetected) LockReason.DIDNT_OPEN_IN_TIME else LockReason.NO_FACE_DETECTED)
                }
            }
            is SessionState.Locked -> {
                video?.pause()
            }
            else -> Unit
        }
    }

    // endregion

    // region Check-in

    private fun triggerCheckIn() {
        if (state != SessionState.Watching) return
        video?.pause()
        video?.setLocked(true)
        resetOpenTracking()
        graceRemaining = config.gracePeriodSeconds
        state = SessionState.CheckInDue(graceRemaining)
        feedback.warning()
    }

    /** Called on the main thread after every processed camera frame. */
    private fun handleDetection(result: DetectionResult) {
        if (!state.isActive) return
        detection = result
        val now = SystemClock.elapsedRealtime()
        if (result.faceDetected) lastFaceSeenAt = now

        when (state) {
            SessionState.Watching -> {
                if (now - lastFaceSeenAt > NO_FACE_LOCK_MILLIS) {
                    lock(LockReason.NO_FACE_DETECTED)
                }
            }
            is SessionState.CheckInDue -> {
                if (!result.faceDetected || !result.isMouthOpen) {
                    resetOpenTracking() // must hold the mouth open continuously
                    return
                }
                if (mouthOpenSince == 0L) mouthOpenSince = now
                result.mouthLooksEmpty?.let { empty -> if (empty) emptyVotes++ else foodVotes++ }
                if (now - mouthOpenSince >= config.mouthOpenDurationMillis) {
                    evaluateCheckIn()
                }
            }
            else -> Unit
        }
    }

    private fun evaluateCheckIn() {
        if (config.requireEmptyMouth && foodVotes > emptyVotes) {
            lock(LockReason.MOUTH_NOT_EMPTY)
        } else {
            checkInPassed()
        }
    }

    private fun checkInPassed() {
        passes += 1
        resetOpenTracking()
        feedback.success()
        video?.setLocked(false)
        video?.play()
        timeUntilNextCheck = config.intervalSeconds
        lastFaceSeenAt = SystemClock.elapsedRealtime()
        state = SessionState.Watching
    }

    private fun resetOpenTracking() {
        mouthOpenSince = 0L
        emptyVotes = 0
        foodVotes = 0
    }

    // endregion

    // region Lock

    private fun lock(reason: LockReason) {
        if (!state.isActive) return
        if (state is SessionState.Locked) return
        locks += 1
        resetOpenTracking()
        video?.pause()
        video?.setLocked(true)
        feedback.error()
        generateMathQuestion()
        state = SessionState.Locked(reason)
    }

    private fun generateMathQuestion() {
        val range = config.mathDifficulty.range
        val a = Random.nextInt(range.first, range.last + 1)
        val b = Random.nextInt(range.first, range.last + 1)
        if (Random.nextBoolean()) {
            lockQuestion = "$a + $b = ?"
            lockAnswer = a + b
        } else {
            lockQuestion = "${maxOf(a, b)} − ${minOf(a, b)} = ?"
            lockAnswer = maxOf(a, b) - minOf(a, b)
        }
    }

    /** Returns true if the answer was correct. A wrong answer gets a new question (as on iOS). */
    fun submitLockAnswer(answer: String): Boolean {
        if (state !is SessionState.Locked) return false
        val value = answer.toIntOrNull()
        if (value == null || value != lockAnswer) {
            feedback.warning()
            generateMathQuestion()
            return false
        }
        unlock()
        return true
    }

    private fun unlock() {
        lastFaceSeenAt = SystemClock.elapsedRealtime()
        resetOpenTracking()
        if (config.requireRecheckOnUnlock) {
            // Video stays paused until the mouth check passes again
            graceRemaining = config.gracePeriodSeconds
            state = SessionState.CheckInDue(graceRemaining)
        } else {
            video?.setLocked(false)
            video?.play()
            timeUntilNextCheck = config.intervalSeconds
            state = SessionState.Watching
        }
    }

    // endregion

    // region App lifecycle

    fun onAppBackgrounded() {
        if (state.isActive) lock(LockReason.BACKGROUNDED)
    }

    fun onAppForegrounded() {
        if (state.isActive) lastFaceSeenAt = SystemClock.elapsedRealtime()
    }

    // endregion

    override fun onCleared() {
        tickJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        mouthDetector.onResult = null
        mouthDetector.close()
        feedback.release()
    }

    private companion object {
        /** How long without a face (while watching) before locking. */
        const val NO_FACE_LOCK_MILLIS = 10_000L
    }
}
