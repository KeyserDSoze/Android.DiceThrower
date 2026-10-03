package com.keyserdsoze.dicethrower.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.keyserdsoze.dicethrower.model.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD6BAFF),
    onPrimary = Color(0xFF2A1245),
    primaryContainer = Color(0xFF432B5F),
    onPrimaryContainer = Color(0xFFF0DEFF),
    secondary = Color(0xFFB9C8FF),
    background = Color(0xFF0C0B10),
    onBackground = Color(0xFFF2EFF7),
    surface = Color(0xFF15131A),
    onSurface = Color(0xFFF2EFF7),
    surfaceVariant = Color(0xFF211E27),
    onSurfaceVariant = Color(0xFFCAC3D1),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B3FA0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEDBFF),
    onPrimaryContainer = Color(0xFF27103E),
    secondary = Color(0xFF4E5F92),
    background = Color(0xFFFCF8FF),
    onBackground = Color(0xFF1D1A20),
    surface = Color(0xFFFFF9FF),
    onSurface = Color(0xFF1D1A20),
    surfaceVariant = Color(0xFFECE5EF),
    onSurfaceVariant = Color(0xFF4C4650),
)

@Composable
fun DiceThrowerTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
