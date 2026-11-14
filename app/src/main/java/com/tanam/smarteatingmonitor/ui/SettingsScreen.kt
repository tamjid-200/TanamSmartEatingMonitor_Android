package com.tanam.smarteatingmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tanam.smarteatingmonitor.BuildConfig
import com.tanam.smarteatingmonitor.model.CheckConfig
import com.tanam.smarteatingmonitor.model.MathDifficulty

@Composable
fun SettingsScreen(
    config: CheckConfig,
    onUpdate: ((CheckConfig) -> CheckConfig) -> Unit,
    onSignOut: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirm by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
        ) {
            Column(Modifier.fillMaxSize()) {
                // Top bar
                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Text("Done", fontWeight = FontWeight.SemiBold)
                    }
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
                ) {
                    Section("CHECK-IN SETTINGS") {
                        StepperRow(
                            label = "Check Interval: ${config.intervalSeconds}s",
                            value = config.intervalSeconds,
                            range = CheckConfig.INTERVAL_RANGE,
                            step = CheckConfig.INTERVAL_STEP,
                            onChange = { v -> onUpdate { it.copy(intervalSeconds = v) } },
                        )
                        RowDivider()
                        StepperRow(
                            label = "Grace Period: ${config.gracePeriodSeconds}s",
                            value = config.gracePeriodSeconds,
                            range = CheckConfig.GRACE_RANGE,
                            step = CheckConfig.GRACE_STEP,
                            onChange = { v -> onUpdate { it.copy(gracePeriodSeconds = v) } },
                        )
                        RowDivider()
                        SwitchRow("Require Empty Mouth Check", config.requireEmptyMouth) { v ->
                            onUpdate { it.copy(requireEmptyMouth = v) }
                        }
                        if (config.requireEmptyMouth) {
                            RowDivider()
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Row {
                                    Text("Empty Mouth Sensitivity", modifier = Modifier.weight(1f))
                                    Text("${(config.emptyMouthSensitivity * 100).toInt()}%", color = secondaryText)
                                }
                                Slider(
                                    value = config.emptyMouthSensitivity,
                                    onValueChange = { v -> onUpdate { it.copy(emptyMouthSensitivity = v) } },
                                    valueRange = 0f..1f,
                                )
                                Text(
                                    "Lower = stricter about food in the mouth. Higher = more lenient.",
                                    color = secondaryText,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    Section("LOCK SETTINGS") {
                        Text(
                            "Math Difficulty",
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                        )
                        MathDifficulty.entries.forEach { level ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onUpdate { it.copy(mathDifficulty = level) } }
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = config.mathDifficulty == level,
                                    onClick = { onUpdate { it.copy(mathDifficulty = level) } },
                                )
                                Text(level.label)
                            }
                        }
                        RowDivider()
                        SwitchRow("Re-check on Unlock", config.requireRecheckOnUnlock) { v ->
                            onUpdate { it.copy(requireRecheckOnUnlock = v) }
                        }
                    }

                    Section("YOUTUBE SETTINGS") {
                        SwitchRow("Use YouTube Kids by Default", config.useYouTubeKids) { v ->
                            onUpdate { it.copy(useYouTubeKids = v) }
                        }
                    }

                    Section("KEEP THE CHILD INSIDE THE APP") {
                        SwitchRow("Pin screen during session", config.pinScreenDuringSession) { v ->
                            onUpdate { it.copy(pinScreenDuringSession = v) }
                        }
                        Text(
                            "When a session starts, Android asks to pin TANAM so the child can't switch apps. " +
                                "For a PIN before unpinning, turn on Android Settings → Security → App pinning → " +
                                "\"Ask for PIN before unpinning\". To unpin: hold Back + Overview, or swipe up and hold " +
                                "with gesture navigation.",
                            color = secondaryText,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        )
                    }

                    Section("ABOUT") {
                        InfoRow("Version", BuildConfig.VERSION_NAME)
                        RowDivider()
                        InfoRow("Build", "Android Native")
                    }

                    Section(null) {
                        DestructiveRow("Sign Out of YouTube") { confirm = "signout" }
                        RowDivider()
                        DestructiveRow("Reset All Settings") { confirm = "reset" }
                    }
                }
            }
        }

        when (confirm) {
            "signout" -> ConfirmDialog(
                title = "Sign out of YouTube?",
                message = "Your Google account will be signed out inside TANAM.",
                action = "Sign Out",
                onConfirm = { confirm = null; onSignOut() },
                onDismiss = { confirm = null },
            )
            "reset" -> ConfirmDialog(
                title = "Reset all settings?",
                message = "Every setting goes back to its default value.",
                action = "Reset",
                onConfirm = { confirm = null; onReset() },
                onDismiss = { confirm = null },
            )
        }
    }
}

@Composable
private fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(20.dp))
    if (title != null) {
        Caption(title, Modifier.padding(start = 16.dp, bottom = 6.dp))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
        content = content,
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { onChange((value - step).coerceIn(range)) },
            enabled = value > range.first,
            modifier = Modifier.size(36.dp),
        ) { Icon(Icons.Rounded.Remove, contentDescription = "Decrease") }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(
            onClick = { onChange((value + step).coerceIn(range)) },
            enabled = value < range.last,
            modifier = Modifier.size(36.dp),
        ) { Icon(Icons.Rounded.Add, contentDescription = "Increase") }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, color = secondaryText)
    }
}

@Composable
private fun DestructiveRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = TanamColors.Red,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    action: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action, color = TanamColors.Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
