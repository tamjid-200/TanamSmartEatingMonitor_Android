package com.tanam.smarteatingmonitor

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.tanam.smarteatingmonitor.camera.CameraController
import com.tanam.smarteatingmonitor.model.LockReason
import com.tanam.smarteatingmonitor.model.SessionState
import com.tanam.smarteatingmonitor.model.isActive
import com.tanam.smarteatingmonitor.ui.ActiveSessionScreen
import com.tanam.smarteatingmonitor.ui.EndScreen
import com.tanam.smarteatingmonitor.ui.LockOverlay
import com.tanam.smarteatingmonitor.ui.SettingsScreen
import com.tanam.smarteatingmonitor.ui.StartScreen
import com.tanam.smarteatingmonitor.ui.TanamTheme
import com.tanam.smarteatingmonitor.web.YouTubeController

class MainActivity : ComponentActivity() {

    private val vm: SessionViewModel by viewModels()
    private lateinit var youtube: YouTubeController
    private lateinit var camera: CameraController

    private var pendingUseKids = false
    private var pinnedByUs = false

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                beginSession(pendingUseKids)
            } else {
                vm.cameraError = "Camera access denied. Allow it in Settings → Apps → TANAM → Permissions."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        youtube = YouTubeController(this) { playing -> vm.isVideoPlaying = playing }
        camera = CameraController(this, this, vm.mouthDetector) { error -> vm.cameraError = error }
        vm.attach(youtube, camera)

        setContent {
            TanamTheme {
                val active = vm.state.isActive

                // Keep the screen awake while a session runs; release pinning when it ends
                LaunchedEffect(active) {
                    if (active) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        unpinIfNeeded()
                        youtube.stopPage()
                    }
                }

                TanamApp(
                    vm = vm,
                    youtube = youtube,
                    camera = camera,
                    onStartRequested = ::requestStart,
                    onOpenAppSettings = ::openAppSettings,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onAppForegrounded()
    }

    override fun onStop() {
        // Leaving the app (home, app switcher, screen off) locks the session — same as iOS.
        if (!isChangingConfigurations) vm.onAppBackgrounded()
        youtube.onAppStopped()
        super.onStop()
    }

    override fun onDestroy() {
        vm.detach()
        camera.release()
        youtube.destroy()
        super.onDestroy()
    }

    private fun requestStart(useKids: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            beginSession(useKids)
        } else {
            pendingUseKids = useKids
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun beginSession(useKids: Boolean) {
        vm.startSession(useKids)
        if (vm.config.pinScreenDuringSession && vm.state.isActive) {
            try {
                startLockTask() // Android asks the user to confirm pinning
                pinnedByUs = true
            } catch (_: Exception) { }
        }
    }

    private fun unpinIfNeeded() {
        if (!pinnedByUs) return
        pinnedByUs = false
        try {
            val am = getSystemService(ActivityManager::class.java)
            if (am?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) stopLockTask()
        } catch (_: Exception) { }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun TanamApp(
    vm: SessionViewModel,
    youtube: YouTubeController,
    camera: CameraController,
    onStartRequested: (Boolean) -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    val state = vm.state

    // Which top-level screen to show (the session's sub-states share one screen)
    val screen = when (state) {
        SessionState.Idle -> Screen.START
        is SessionState.Ended -> Screen.END
        else -> Screen.SESSION
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Crossfade(targetState = screen, label = "screen") { target ->
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                when (target) {
                    Screen.START -> StartScreen(
                        cameraError = vm.cameraError,
                        hasFrontCamera = camera.hasFrontCamera,
                        kidsByDefault = vm.config.useYouTubeKids,
                        onOpenSettings = { showSettings = true },
                        onOpenAppSettings = onOpenAppSettings,
                        onStart = onStartRequested,
                    )
                    Screen.SESSION -> ActiveSessionScreen(vm, youtube, camera.preview)
                    Screen.END -> {
                        val summary = (vm.state as? SessionState.Ended)?.summary
                        if (summary != null) EndScreen(summary, onNewSession = vm::returnHome)
                    }
                }
            }
        }

        // Lock overlay sits ON TOP of everything (blocks all touches on the video)
        val locked = state as? SessionState.Locked
        // Remember the last reason so the fade-out still shows the right text
        val lastReason = remember { arrayOf(LockReason.DIDNT_OPEN_IN_TIME) }
        if (locked != null) lastReason[0] = locked.reason
        AnimatedVisibility(visible = locked != null, enter = fadeIn(), exit = fadeOut()) {
            LockOverlay(
                reason = locked?.reason ?: lastReason[0],
                question = vm.lockQuestion,
                onSubmit = vm::submitLockAnswer,
            )
        }
    }

    if (showSettings && state == SessionState.Idle) {
        SettingsScreen(
            config = vm.config,
            onUpdate = vm::updateConfig,
            onSignOut = vm::signOutOfYouTube,
            onReset = vm::resetSettings,
            onDismiss = { showSettings = false },
        )
    }
}

private enum class Screen { START, SESSION, END }
