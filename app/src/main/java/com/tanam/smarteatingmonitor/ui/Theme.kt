package com.tanam.smarteatingmonitor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** iOS-matching palette. */
object TanamColors {
    val Blue = Color(0xFF007AFF)
    val Purple = Color(0xFFAF52DE)
    val Orange = Color(0xFFFF9500)
    val OrangeDeep = Color(0xFFFF6A00)
    val Green = Color(0xFF34C759)
    val Red = Color(0xFFFF3B30)

    val BrandGradient = Brush.linearGradient(listOf(Blue, Purple))
    val BrandHorizontal = Brush.horizontalGradient(listOf(Blue, Purple))
    val OrangeGradient = Brush.linearGradient(listOf(Orange, OrangeDeep))
}

private val LightScheme = lightColorScheme(
    primary = TanamColors.Blue,
    secondary = TanamColors.Purple,
    tertiary = TanamColors.Orange,
    error = TanamColors.Red,
    background = Color(0xFFF2F2F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFF2F2F7),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    onSurfaceVariant = Color(0xFF6C6C70),
    outline = Color(0xFFC6C6C8),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    secondary = Color(0xFFBF5AF2),
    tertiary = Color(0xFFFF9F0A),
    error = Color(0xFFFF453A),
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF98989F),
    outline = Color(0xFF38383A),
)

@Composable
fun TanamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}

/** Secondary label colour (like iOS `.secondary`). */
val secondaryText: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
