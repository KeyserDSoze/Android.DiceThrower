package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.test.junit4.createComposeRule
import com.keyserdsoze.dicethrower.model.ThemeMode
import com.keyserdsoze.dicethrower.ui.theme.DiceThrowerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PremiumCardThemeTest {
    @get:Rule val composeRule = createComposeRule()

    private fun assertPremiumContrast(themeMode: ThemeMode) {
        var foreground: Color? = null
        var background: Color? = null
        var expected: Color? = null
        composeRule.setContent {
            DiceThrowerTheme(themeMode = themeMode) {
                expected = MaterialTheme.colorScheme.onSurface
                background = MaterialTheme.colorScheme.surface
                PremiumCard {
                    foreground = LocalContentColor.current
                    Text("Formula composer")
                }
            }
        }
        composeRule.runOnIdle {
            assertEquals(expected, foreground)
            val fg = requireNotNull(foreground).luminance()
            val bg = requireNotNull(background).luminance()
            val contrast = (maxOf(fg, bg) + 0.05f) / (minOf(fg, bg) + 0.05f)
            assertTrue("Insufficient $themeMode card text/icon contrast: $contrast", contrast >= 4.5f)
        }
    }

    @Test
    fun formulaComposerCardIsReadableInDarkTheme() {
        assertPremiumContrast(ThemeMode.DARK)
    }

    @Test
    fun formulaComposerCardIsReadableInLightTheme() {
        assertPremiumContrast(ThemeMode.LIGHT)
    }
}
