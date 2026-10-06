package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkColorScheme = darkColorScheme(
    primary = SalimCyanAccent,
    onPrimary = Color.Black,
    primaryContainer = SalimCyanDark,
    onPrimaryContainer = Color.White,
    secondary = SalimSuccessGreen,
    onSecondary = Color.Black,
    tertiary = SalimEmergencyRed,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = SalimEmergencyRed
)

val LightColorScheme = lightColorScheme(
    primary = SalimCyanPrimary,
    onPrimary = Color.White,
    primaryContainer = SalimCyanLight,
    onPrimaryContainer = Color.Black,
    secondary = SalimSuccessGreen,
    onSecondary = Color.White,
    tertiary = SalimEmergencyRed,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF4A5568),
    error = SalimEmergencyRed
)

@Composable
fun SalimTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = SalimTheme(darkTheme = darkTheme, content = content)
