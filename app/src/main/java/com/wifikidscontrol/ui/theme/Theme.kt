package com.wifikidscontrol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Teal/Emerald theme matching your project
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF009688),       // Teal
    primaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFF00695C),     // Dark Teal
    secondaryContainer = Color(0xFF80CBC4),
    tertiary = Color(0xFF4CAF50),      // Green
    tertiaryContainer = Color(0xFFC8E6C9),
    surface = Color(0xFFFAFAFA),
    surfaceContainerHighest = Color(0xFFE0F2F1),
    error = Color(0xFFD32F2F),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onSurface = Color(0xFF1A1A2E),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    primaryContainer = Color(0xFF00695C),
    secondary = Color(0xFF80CBC4),
    secondaryContainer = Color(0xFF004D40),
    tertiary = Color(0xFF81C784),
    tertiaryContainer = Color(0xFF1B5E20),
    surface = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF1E1E1E),
    error = Color(0xFFEF5350),
    onPrimary = Color(0xFF00251A),
    onSecondary = Color(0xFF00251A),
    onTertiary = Color(0xFF00251A),
    onSurface = Color(0xFFE0E0E0),
    onError = Color(0xFF1A1A2E)
)

@Composable
fun Theme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

object Typography {
    // Using Material 3 default typography
}