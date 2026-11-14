package com.tanam.smarteatingmonitor.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun StartScreen(
    cameraError: String?,
    hasFrontCamera: Boolean,
    kidsByDefault: Boolean,
    onOpenSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onStart: (useKids: Boolean) -> Unit,
) {
    var animateIn by remember { mutableStateOf(false) }
    var showChooser by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateIn = true }

    val progress by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 120f),
        label = "intro",
    )
    val pulse = rememberInfiniteTransition(label = "pulse")
    val logoScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "logoScale",
    )
    val intro = Modifier.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 30.dp.toPx()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = secondaryText)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Logo / title
        Column(intro, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(96.dp)
                    .scale(logoScale)
                    .clip(CircleShape)
                    .background(TanamColors.BrandGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Restaurant, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("TANAM", fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Text("Smart Eating Monitor", color = secondaryText, fontSize = 15.sp, letterSpacing = 2.sp)
        }

        Spacer(Modifier.height(40.dp))

        Column(
            intro.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FeatureRow(Icons.Rounded.SmartDisplay, "Watch YouTube", "Enjoy videos while we monitor your eating cycles")
            FeatureRow(Icons.Rounded.CameraFront, "Face Detection", "Front camera tracks mouth openness in real-time")
            FeatureRow(Icons.Rounded.CheckCircle, "Periodic Checks", "Open your mouth when prompted to continue watching")
            FeatureRow(Icons.Rounded.Lock, "Smart Lock", "Solve a quick math question to unlock if you miss a check")
        }

        Spacer(Modifier.height(40.dp))

        if (cameraError != null) {
            Text(
                cameraError,
                color = TanamColors.Orange,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            TextButton(onClick = onOpenAppSettings) { Text("Open app settings") }
        } else if (!hasFrontCamera) {
            Text("No front camera found on this device.", color = TanamColors.Orange, fontSize = 13.sp)
        }

        GradientButton(
            text = "Start Session",
            icon = Icons.Rounded.PlayArrow,
            onClick = { showChooser = true },
            modifier = intro
                .padding(horizontal = 32.dp)
                .padding(top = 12.dp, bottom = 40.dp),
        )
    }

    if (showChooser) {
        YouTubeChooserSheet(
            kidsByDefault = kidsByDefault,
            onDismiss = { showChooser = false },
            onChoose = { kids ->
                showChooser = false
                onStart(kids)
            },
        )
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = TanamColors.Blue, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(description, color = secondaryText, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeChooserSheet(
    kidsByDefault: Boolean,
    onDismiss: () -> Unit,
    onChoose: (Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun choose(kids: Boolean) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onChoose(kids) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.ChildCare, contentDescription = null, tint = TanamColors.Orange, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text("Which YouTube?", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Choose your viewing experience", color = secondaryText, fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))

            val options = listOf(
                Triple(false, "YouTube", "Standard YouTube (m.youtube.com)"),
                Triple(true, "YouTube Kids", "Child-friendly content (youtubekids.com)"),
            ).sortedByDescending { it.first == kidsByDefault }

            options.forEachIndexed { index, (kids, title, subtitle) ->
                ChooserOption(
                    icon = if (kids) Icons.Rounded.ChildCare else Icons.Rounded.SmartDisplay,
                    title = title,
                    subtitle = subtitle,
                    isDefault = kids == kidsByDefault,
                    onClick = { choose(kids) },
                )
                if (index == 0) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ChooserOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDefault: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isDefault) TanamColors.Blue else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = TanamColors.Blue)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    if (isDefault) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "DEFAULT",
                            color = TanamColors.Blue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TanamColors.Blue.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(subtitle, color = secondaryText, fontSize = 12.sp)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = secondaryText)
        }
    }
}
