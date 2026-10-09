package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.ThemeMode
import com.keyserdsoze.dicethrower.ui.theme.DiceThrowerTheme
import org.junit.Rule
import org.junit.Test

class EffectsVisualCueV2Test {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun bonusHasReadableLabelWhenDarkAndMotionIsOff() {
        composeRule.setContent {
            DiceThrowerTheme(themeMode = ThemeMode.DARK) {
                EffectsVisualCueV2("Power hit", EffectType.BONUS, rollAfter = true,
                    animate = false)
            }
        }
        composeRule.onNodeWithTag("effects-visual-cue").assertExists()
        composeRule.onNodeWithText("Bonus · Power hit · Roll after").assertExists()
    }

    @Test
    fun malusHasReadableLabelInLightTheme() {
        composeRule.setContent {
            DiceThrowerTheme(themeMode = ThemeMode.LIGHT) {
                EffectsVisualCueV2("Weakened", EffectType.MALUS, rollAfter = false,
                    animate = false)
            }
        }
        composeRule.onNodeWithTag("effects-visual-cue").assertExists()
        composeRule.onNodeWithText("Malus · Weakened").assertExists()
    }
}
