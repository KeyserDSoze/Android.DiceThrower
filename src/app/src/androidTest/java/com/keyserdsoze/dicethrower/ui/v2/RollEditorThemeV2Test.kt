package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.ThemeMode
import com.keyserdsoze.dicethrower.ui.theme.DiceThrowerTheme
import org.junit.Rule
import org.junit.Test

/**
 * The Roll builder is a real full-screen destination in both app themes,
 * independent of the navigation path or what surface is behind it.
 */
class RollEditorThemeV2Test {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun darkEditorHasDedicatedScreenAndHeaderOnTopOfPreviousBackground() {
        displayRollEditor(ThemeMode.DARK)
        composeRule.onNodeWithTag("roll-editor-screen").assertIsDisplayed()
        composeRule.onNodeWithText("Edit roll").assertIsDisplayed()
        composeRule.onNodeWithText("Roll name").assertIsDisplayed()
    }

    @Test
    fun lightEditorHasDedicatedScreenAndHeaderOnTopOfPreviousBackground() {
        displayRollEditor(ThemeMode.LIGHT)
        composeRule.onNodeWithTag("roll-editor-screen").assertIsDisplayed()
        composeRule.onNodeWithText("Edit roll").assertIsDisplayed()
        composeRule.onNodeWithText("Roll name").assertIsDisplayed()
    }

    private fun displayRollEditor(mode: ThemeMode) {
        val hero = CharacterProfile(id = "hero", name = "Hero")
        val roll = RollDefinition(id = "roll", characterId = hero.id,
            name = "Test", expression = "(1d20)")
        composeRule.setContent {
            DiceThrowerTheme(mode) {
                // A deliberately contrasting host stands in for a previously
                // displayed dice table. The editor must paint its own background.
                Box(Modifier.fillMaxSize().background(Color(0xFFCB7D3C))) {
                    RollBuilderScreenV2(
                        title = "Edit roll",
                        character = hero,
                        modifiers = emptyList(),
                        groups = emptyList(),
                        existing = roll,
                        onDismiss = {},
                        onSave = {},
                    )
                }
            }
        }
    }
}
