package com.flowgallery.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentMuted,
    onPrimaryContainer = AccentHover,
    secondary = AccentHover,
    onSecondary = Color.White,
    background = Bg,
    onBackground = Fg,
    surface = Surface,
    onSurface = Fg,
    surfaceVariant = Surface2,
    onSurfaceVariant = FgSecondary,
    outline = Border,
    error = Error,
    onError = Color.White
)

private val LightColors = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E2FF),
    onPrimaryContainer = Color(0xFF4A2FB0),
    secondary = Color(0xFF6C4DF0),
    onSecondary = Color.White,
    background = LightBg,
    onBackground = Color(0xFF1A1A1F),
    surface = LightSurface,
    onSurface = Color(0xFF1A1A1F),
    surfaceVariant = Color(0xFFF0F0F4),
    onSurfaceVariant = Color(0xFF55555E),
    outline = Color(0xFF8A8A94),
    error = Color(0xFFD32F2F),
    onError = Color.White
)

@Composable
fun FlowGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
