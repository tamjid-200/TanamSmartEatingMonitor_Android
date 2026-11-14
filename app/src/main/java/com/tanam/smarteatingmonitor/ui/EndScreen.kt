package com.tanam.smarteatingmonitor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanam.smarteatingmonitor.model.SessionSummary

@Composable
fun EndScreen(summary: SessionSummary, onNewSession: () -> Unit) {
    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateIn = true }
    val progress by animateFloatAsState(
        if (animateIn) 1f else 0f,
        spring(dampingRatio = 0.8f, stiffness = 120f),
        label = "endIntro",
    )
    val intro = Modifier.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 30.dp.toPx()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = TanamColors.Green,
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer {
                    scaleX = 0.6f + 0.4f * progress
                    scaleY = 0.6f + 0.4f * progress
                },
        )
        Spacer(Modifier.height(24.dp))
        Column(intro, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Session Complete", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Great job staying mindful!", color = secondaryText, fontSize = 15.sp)
        }
        Spacer(Modifier.height(32.dp))

        Column(
            intro.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("Duration", formatDuration(summary.durationSeconds), Icons.Rounded.Schedule, TanamColors.Blue, Modifier.weight(1f))
                StatCard("Passes", "${summary.passes}", Icons.Rounded.CheckCircle, TanamColors.Green, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("Locks", "${summary.locks}", Icons.Rounded.Lock, TanamColors.Orange, Modifier.weight(1f))
                StatCard(
                    "Success Rate",
                    summary.successRatePercent?.let { "$it%" } ?: "—",
                    Icons.Rounded.Insights,
                    TanamColors.Purple,
                    Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        GradientButton(
            text = "New Session",
            icon = Icons.Rounded.Refresh,
            onClick = onNewSession,
            modifier = intro.padding(horizontal = 32.dp).padding(bottom = 40.dp),
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Column(
        modifier
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(10.dp))
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(title, color = secondaryText, fontSize = 12.sp, letterSpacing = 0.5.sp)
    }
}
