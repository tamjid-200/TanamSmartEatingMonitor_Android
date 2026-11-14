package com.tanam.smarteatingmonitor.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tanam.smarteatingmonitor.SessionViewModel
import com.tanam.smarteatingmonitor.model.SessionState
import com.tanam.smarteatingmonitor.web.YouTubeController

@Composable
fun ActiveSessionScreen(
    vm: SessionViewModel,
    youtube: YouTubeController,
    cameraPreview: Preview,
) {
    var showEndConfirm by remember { mutableStateOf(false) }
    val state = vm.state
    val fullscreen = youtube.fullscreenView

    // System back: leave full screen → go back in YouTube → ask to end. Ignored while locked.
    BackHandler {
        when {
            state is SessionState.Locked -> Unit
            fullscreen != null -> youtube.exitFullscreen()
            youtube.canGoBack() -> youtube.goBack()
            else -> showEndConfirm = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            StatusBar(vm, cameraPreview)

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                // The same WebView for the whole app run (recreated only if its renderer crashes)
                key(youtube.webView) {
                    AndroidView(
                        factory = {
                            val view = youtube.webView
                            (view.parent as? ViewGroup)?.removeView(view)
                            view
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                val checkIn = state as? SessionState.CheckInDue
                CheckInBannerVisibility(
                    checkIn = checkIn,
                    gracePeriod = vm.config.gracePeriodSeconds,
                    faceDetected = vm.detection.faceDetected,
                    openness = vm.detection.mouthOpenness,
                    isOpen = vm.detection.isMouthOpen,
                )
            }

            SessionInfoBar(
                durationSeconds = vm.sessionDurationSeconds,
                passes = vm.passes,
                locks = vm.locks,
                onEnd = { showEndConfirm = true },
            )
        }

        // YouTube's own full-screen video mode
        if (fullscreen != null) {
            key(fullscreen) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            (fullscreen.parent as? ViewGroup)?.removeView(fullscreen)
                            addView(
                                fullscreen,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                ),
                            )
                        }
                    },
                    onRelease = { it.removeAllViews() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showEndConfirm) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text("Finished eating?") },
            text = { Text("This stops the camera and the video.") },
            confirmButton = {
                TextButton(onClick = {
                    showEndConfirm = false
                    vm.stopSession()
                }) { Text("End Session", color = TanamColors.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = false }) { Text("Keep Watching") }
            },
        )
    }
}

// region Status bar

@Composable
private fun StatusBar(vm: SessionViewModel, cameraPreview: Preview) {
    val detection = vm.detection
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Live camera thumbnail so the child can see they're in frame
        CameraThumbnail(
            preview = cameraPreview,
            borderColor = if (detection.faceDetected) TanamColors.Green else TanamColors.Red,
        )
        Spacer(Modifier.width(14.dp))

        Column {
            Caption("NEXT CHECK")
            Text(
                "${vm.timeUntilNextCheck}s",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(14.dp))
        VerticalDivider(Modifier.height(32.dp))
        Spacer(Modifier.width(14.dp))

        val openColor = if (detection.isMouthOpen) TanamColors.Green else secondaryText
        Icon(
            if (detection.isMouthOpen) Icons.Rounded.SentimentVerySatisfied else Icons.Rounded.SentimentNeutral,
            contentDescription = null,
            tint = openColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Caption(if (detection.isMouthOpen) "OPEN" else "CLOSED", color = openColor)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun CameraThumbnail(preview: Preview, borderColor: Color) {
    val shape = RoundedCornerShape(10.dp)
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE // clips to rounded corners
                scaleType = PreviewView.ScaleType.FILL_CENTER
                setBackgroundColor(android.graphics.Color.BLACK)
                preview.setSurfaceProvider(surfaceProvider)
            }
        },
        onRelease = { preview.setSurfaceProvider(null) },
        modifier = Modifier
            .size(width = 44.dp, height = 58.dp)
            .clip(shape)
            .border(2.dp, borderColor, shape),
    )
}

// endregion

// region Check-in banner

@Composable
private fun CheckInBannerVisibility(
    checkIn: SessionState.CheckInDue?,
    gracePeriod: Int,
    faceDetected: Boolean,
    openness: Float,
    isOpen: Boolean,
) {
    AnimatedVisibility(
        visible = checkIn != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        if (checkIn != null) {
            CheckInBanner(
                graceRemaining = checkIn.graceRemaining,
                gracePeriod = gracePeriod,
                faceDetected = faceDetected,
                openness = openness,
                isOpen = isOpen,
            )
        }
    }
}

@Composable
private fun CheckInBanner(
    graceRemaining: Int,
    gracePeriod: Int,
    faceDetected: Boolean,
    openness: Float,
    isOpen: Boolean,
) {
    val fraction by animateFloatAsState(
        targetValue = if (gracePeriod > 0) graceRemaining.toFloat() / gracePeriod else 0f,
        animationSpec = tween(1000, easing = LinearEasing),
        label = "grace",
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = TanamColors.Orange)
                .clip(RoundedCornerShape(16.dp))
                .background(TanamColors.OrangeGradient)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Face, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Caption("MOUTH CHECK", color = Color.White)
                Text(
                    "Open your mouth wide to continue watching",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    strokeWidth = 4.dp,
                )
                Text("$graceRemaining", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(visible = faceDetected) {
            MouthOpennessIndicator(openness = openness, isOpen = isOpen)
        }
    }
}

@Composable
private fun MouthOpennessIndicator(openness: Float, isOpen: Boolean) {
    val animated by animateFloatAsState(openness, tween(150), label = "openness")
    val color = if (isOpen) TanamColors.Green else TanamColors.Orange
    Column(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Caption("MOUTH OPENNESS")
            Spacer(Modifier.weight(1f))
            Text("${(openness * 100).toInt()}%", color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Caption("CLOSED")
            Spacer(Modifier.weight(1f))
            Caption("OPEN")
        }
    }
}

// endregion

// region Session info bar

@Composable
private fun SessionInfoBar(durationSeconds: Long, passes: Int, locks: Int, onEnd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(12.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Stat("SESSION", formatDuration(durationSeconds), MaterialTheme.colorScheme.onSurface)
        VerticalDivider(Modifier.height(36.dp))
        Stat("PASSES", "$passes", TanamColors.Green)
        VerticalDivider(Modifier.height(36.dp))
        Stat("LOCKS", "$locks", TanamColors.Orange)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onEnd,
            colors = ButtonDefaults.buttonColors(containerColor = TanamColors.Red, contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("END", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column {
        Caption(label, Modifier.padding(bottom = 2.dp))
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// endregion
