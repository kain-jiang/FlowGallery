package com.flowgallery.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    monet: Boolean = false,
    content: @Composable () -> Unit
) {
    // Keep status/navigation bar icons readable in both themes:
    // dark theme -> light (white) icons; light theme -> dark icons.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    // Monet (Material You): pull the color scheme from the system wallpaper
    // on Android 12+ when enabled in Settings. Falls back to the custom
    // accent scheme otherwise.
    val scheme = if (monet && android.os.Build.VERSION.SDK_INT >= 31) {
        if (darkTheme) {
            androidx.compose.material3.dynamicDarkColorScheme(
                androidx.compose.ui.platform.LocalContext.current
            )
        } else {
            androidx.compose.material3.dynamicLightColorScheme(
                androidx.compose.ui.platform.LocalContext.current
            )
        }
    } else {
        if (darkTheme) DarkColors else LightColors
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content
    )
}
