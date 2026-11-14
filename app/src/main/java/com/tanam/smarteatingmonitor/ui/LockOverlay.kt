package com.tanam.smarteatingmonitor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanam.smarteatingmonitor.model.LockReason
import kotlinx.coroutines.launch

/**
 * Full-screen lock that sits on top of everything and swallows every touch,
 * so the video can't be reached until the maths question is answered.
 */
@Composable
fun LockOverlay(
    reason: LockReason,
    question: String,
    onSubmit: (String) -> Boolean,
) {
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            // Swallow all touches so nothing underneath is reachable
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = 24.dp)
                .graphicsLayer { translationX = shakeOffset.value }
                .shadow(32.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = TanamColors.Orange, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(20.dp))
            Text(reason.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                reason.message,
                color = secondaryText,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                question,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            NumberPad(
                onSubmit = { input ->
                    val correct = onSubmit(input)
                    if (!correct) {
                        scope.launch {
                            for (x in listOf(14f, -14f, 10f, -10f, 5f, 0f)) {
                                shakeOffset.animateTo(x, tween(45))
                            }
                        }
                    }
                    correct
                },
            )
        }
    }
}

@Composable
private fun NumberPad(onSubmit: (String) -> Boolean) {
    var input by remember { mutableStateOf("") }
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("⌫", "0", "✓"),
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(212.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(input, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { key ->
                        NumberKey(key) { input = applyKey(input, key, onSubmit) }
                    }
                }
            }
        }
    }
}

/** Returns the new input after a key press. */
private fun applyKey(input: String, key: String, onSubmit: (String) -> Boolean): String {
    return when (key) {
        "⌫" -> input.dropLast(1)
        "✓" -> {
            if (input.isNotEmpty()) onSubmit(input)
            ""
        }
        else -> if (input.length < 4) input + key else input
    }
}

@Composable
private fun NumberKey(key: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, tween(80), label = "key")
    val background = when (key) {
        "✓" -> TanamColors.Green.copy(alpha = 0.2f)
        "⌫" -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        Modifier
            .size(64.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            key,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (key == "✓") TanamColors.Green else MaterialTheme.colorScheme.onSurface,
        )
    }
}
