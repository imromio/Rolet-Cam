package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PrecisionAmber,
    onPrimary = OnAmberDark,
    primaryContainer = SlateContainerHigh,
    onPrimaryContainer = PrecisionAmberLight,
    secondary = SignalEmerald,
    onSecondary = ObsidianVoid,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = SignalEmerald,
    tertiary = SignalCyan,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateContainer,
    onSurfaceVariant = TextSecondary,
    outline = SlateOutline,
    error = SignalCrimson,
    onError = TextPrimary,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightSurface,
    secondary = SignalEmerald,
    onSecondary = LightSurface,
    tertiary = SignalCyan,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightTextSecondary,
    error = SignalCrimson,
    onError = LightSurface,
)

private val GeoLensShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = GeoLensShapes,
        content = content,
    )
}
