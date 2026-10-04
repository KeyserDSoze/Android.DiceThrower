package com.keyserdsoze.dicethrower.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.keyserdsoze.dicethrower.model.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF71C8FF),
    onPrimary = Color(0xFF001E2D),
    primaryContainer = Color(0xFF103C61),
    onPrimaryContainer = Color(0xFFD1ECFF),
    secondary = Color(0xFFC7B4FF),
    onSecondary = Color(0xFF2B1758),
    secondaryContainer = Color(0xFF3C2867),
    onSecondaryContainer = Color(0xFFE8DEFF),
    tertiary = Color(0xFFFFD37A),
    onTertiary = Color(0xFF3D2B00),
    tertiaryContainer = Color(0xFF554000),
    onTertiaryContainer = Color(0xFFFFE4A6),
    background = Color(0xFF070A17),
    onBackground = Color(0xFFF0F2FF),
    surface = Color(0xFF101426),
    onSurface = Color(0xFFF0F2FF),
    surfaceVariant = Color(0xFF181C33),
    onSurfaceVariant = Color(0xFFC6C8DD),
    outline = Color(0xFF8E90A6),
    outlineVariant = Color(0xFF3F4359),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00639A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBFF),
    onPrimaryContainer = Color(0xFF001D32),
    secondary = Color(0xFF6750A4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DDFF),
    onSecondaryContainer = Color(0xFF22105D),
    tertiary = Color(0xFF765A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08D),
    onTertiaryContainer = Color(0xFF241A00),
    background = Color(0xFFF8F8FF),
    onBackground = Color(0xFF191B24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191B24),
    surfaceVariant = Color(0xFFE9E8F2),
    onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC8C6D0),
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
