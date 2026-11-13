package com.keyserdsoze.dicethrower.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.model.ThemeMode

/** Classical inscriptional capitals for titles, names and every number a die can show. */
val Cinzel = FontFamily(
    Font(R.font.cinzel_semibold, FontWeight.Medium),
    Font(R.font.cinzel_semibold, FontWeight.SemiBold),
    Font(R.font.cinzel_bold, FontWeight.Bold),
    Font(R.font.cinzel_black, FontWeight.ExtraBold),
    Font(R.font.cinzel_black, FontWeight.Black),
)

/** "Arcane Codex" night: ink-black table, gilded accents, arcane violet and aether cyan. */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8C170),
    onPrimary = Color(0xFF2B1E00),
    primaryContainer = Color(0xFF3B2C0B),
    onPrimaryContainer = Color(0xFFFFE2A8),
    inversePrimary = Color(0xFF7A5A12),
    secondary = Color(0xFFB7A8FF),
    onSecondary = Color(0xFF23154F),
    secondaryContainer = Color(0xFF2F2663),
    onSecondaryContainer = Color(0xFFE6DEFF),
    tertiary = Color(0xFF7FD8E8),
    onTertiary = Color(0xFF00363E),
    tertiaryContainer = Color(0xFF0E4A55),
    onTertiaryContainer = Color(0xFFB7F1FB),
    background = Color(0xFF0A0B14),
    onBackground = Color(0xFFEFE9DC),
    surface = Color(0xFF12141F),
    onSurface = Color(0xFFEFE9DC),
    surfaceVariant = Color(0xFF1C1F2E),
    onSurfaceVariant = Color(0xFFB4AFBF),
    surfaceTint = Color(0xFFE8C170),
    inverseSurface = Color(0xFFEFE9DC),
    inverseOnSurface = Color(0xFF1B1C25),
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF5F1410),
    errorContainer = Color(0xFF7A231B),
    onErrorContainer = Color(0xFFFFDAD4),
    outline = Color(0xFF726D80),
    outlineVariant = Color(0xFF2E3143),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2B2F42),
    surfaceDim = Color(0xFF0A0B14),
    surfaceContainerLowest = Color(0xFF07080F),
    surfaceContainerLow = Color(0xFF10121C),
    surfaceContainer = Color(0xFF161926),
    surfaceContainerHigh = Color(0xFF1D2030),
    surfaceContainerHighest = Color(0xFF25293A),
)

/** "Parchment" day: warm vellum, bronze ink and indigo accents with WCAG-AA contrast. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5A12),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDFA0),
    onPrimaryContainer = Color(0xFF271900),
    inversePrimary = Color(0xFFE8C170),
    secondary = Color(0xFF5546B8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5DEFF),
    onSecondaryContainer = Color(0xFF1A0F5E),
    tertiary = Color(0xFF00687A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA9EDFF),
    onTertiaryContainer = Color(0xFF001F26),
    background = Color(0xFFF6F1E7),
    onBackground = Color(0xFF1C1B22),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFFEDE6D7),
    onSurfaceVariant = Color(0xFF4C4756),
    surfaceTint = Color(0xFF7A5A12),
    inverseSurface = Color(0xFF302F37),
    inverseOnSurface = Color(0xFFF5EFE4),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF7D7787),
    outlineVariant = Color(0xFFD4CCBC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFCF6),
    surfaceDim = Color(0xFFE3DCCD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF7EF),
    surfaceContainer = Color(0xFFF5EFE3),
    surfaceContainerHigh = Color(0xFFEFE8DA),
    surfaceContainerHighest = Color(0xFFE9E1D1),
)

private val DiceShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private fun diceTypography(): Typography {
    val base = Typography()
    fun TextStyle.display(weight: FontWeight = FontWeight.Bold, tracking: Float = 0f) =
        copy(fontFamily = Cinzel, fontWeight = weight, letterSpacing = tracking.sp)
    return base.copy(
        displayLarge = base.displayLarge.display(FontWeight.Black),
        displayMedium = base.displayMedium.display(FontWeight.Black),
        displaySmall = base.displaySmall.display(FontWeight.Bold),
        headlineLarge = base.headlineLarge.display(tracking = 0.2f),
        headlineMedium = base.headlineMedium.display(tracking = 0.2f),
        headlineSmall = base.headlineSmall.display(tracking = 0.3f),
        titleLarge = base.titleLarge.display(tracking = 0f).copy(fontSize = 20.sp, lineHeight = 27.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        bodyLarge = base.bodyLarge.copy(lineHeight = 25.sp, letterSpacing = 0.15.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp, letterSpacing = 0.15.sp),
        bodySmall = base.bodySmall.copy(lineHeight = 18.sp, letterSpacing = 0.2.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.7.sp),
    )
}

/** Brand accents that are not Material roles (gilding, effect bonus/malus, glass). */
@Immutable
data class DiceAccents(
    val gold: Color,
    val goldBright: Color,
    val goldDeep: Color,
    val arcane: Color,
    val aether: Color,
    val bonus: Color,
    val malus: Color,
    val glass: Color,
    val glassBorder: Color,
    val isDark: Boolean,
)

private val DarkAccents = DiceAccents(
    gold = Color(0xFFE8C170),
    goldBright = Color(0xFFFFE6A6),
    goldDeep = Color(0xFF9C7425),
    arcane = Color(0xFFB7A8FF),
    aether = Color(0xFF7FD8E8),
    bonus = Color(0xFF6EE7B7),
    malus = Color(0xFFFF8A8A),
    glass = Color(0xE60B0C16),
    glassBorder = Color(0x66E8C170),
    isDark = true,
)

private val LightAccents = DiceAccents(
    gold = Color(0xFF8A6418),
    goldBright = Color(0xFFC79A3B),
    goldDeep = Color(0xFF5E430A),
    arcane = Color(0xFF5546B8),
    aether = Color(0xFF00687A),
    bonus = Color(0xFF047857),
    malus = Color(0xFFB42318),
    glass = Color(0xE60B0C16),
    glassBorder = Color(0x80C79A3B),
    isDark = false,
)

val LocalDiceAccents = staticCompositionLocalOf { DarkAccents }

object DiceTheme {
    val accents: DiceAccents
        @Composable get() = LocalDiceAccents.current
}

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
    val view = LocalView.current
    if (!view.isInEditMode) {
        // Status/navigation icons follow the in-app theme, not only the system night mode.
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    CompositionLocalProvider(LocalDiceAccents provides if (dark) DarkAccents else LightAccents) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = diceTypography(),
            shapes = DiceShapes,
            content = content,
        )
    }
}

/** Full-bleed dark scenes (the dice table) always need light system-bar icons. */
@Composable
fun LightSystemBarIcons() {
    val view = LocalView.current
    val dark = DiceTheme.accents.isDark
    DisposableEffect(view, dark) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = !dark
            controller?.isAppearanceLightNavigationBars = !dark
        }
    }
}
