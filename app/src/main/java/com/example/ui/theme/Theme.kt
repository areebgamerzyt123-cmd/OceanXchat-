package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = OceanPrimary,
    onPrimary = Color.White,
    secondary = OceanSecondary,
    onSecondary = Color.White,
    background = OceanBackgroundDark,
    surface = OceanContainerDark,
    surfaceVariant = OceanCardDark,
    outline = OceanBorderDark,
    onBackground = OceanTextLight,
    onSurface = OceanTextLight,
    onSurfaceVariant = OceanTextMuted,
    error = OceanDanger
)

private val LightColorScheme = lightColorScheme(
    primary = OceanPrimary,
    onPrimary = Color.White,
    secondary = OceanSecondary,
    onSecondary = Color.White,
    background = Color(0xFFF2F4F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFE5E7EB),
    outline = Color(0xFFD1D5DB),
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF4B5563),
    error = OceanDanger
)

@Composable
fun OceanXChatTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
