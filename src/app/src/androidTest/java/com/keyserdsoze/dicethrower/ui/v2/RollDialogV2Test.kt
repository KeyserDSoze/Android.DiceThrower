package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.ThemeMode
import com.keyserdsoze.dicethrower.ui.theme.DiceThrowerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RollDialogV2Test {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun characterEditorAddRollOpensDialogWithoutCrashing() {
        val character = CharacterProfile(id = "character", name = "Test")
        composeRule.setContent {
            DiceThrowerTheme(themeMode = ThemeMode.DARK) {
                CharacterEditContentV2(
                    character = character,
                    data = AppData(characters = listOf(character)),
                    onOpenGroup = {},
                    onDataChanged = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("New roll").performClick()
        composeRule.onNodeWithText("New roll").assertIsDisplayed()
        composeRule.onNodeWithText("1d20").assertIsDisplayed()
    }

    @Test
    fun characterEditorRendersReferencedModifierWithoutRegexCrash() {
        val character = CharacterProfile(id = "character", name = "Test")
        val modifier = CharacterModifier(
            id = "strength",
            characterId = character.id,
            name = "Strength",
            value = 3,
        )
        val roll = RollDefinition(
            id = "attack",
            characterId = character.id,
            name = "Attack",
            expression = "1d20+{Strength}",
        )
        composeRule.setContent {
            DiceThrowerTheme(themeMode = ThemeMode.DARK) {
                CharacterEditContentV2(
                    character = character,
                    data = AppData(
                        characters = listOf(character),
                        modifiers = listOf(modifier),
                        rolls = listOf(roll),
                    ),
                    onOpenGroup = {},
                    onDataChanged = {},
                )
            }
        }

        composeRule.onNodeWithText("Strength").assertIsDisplayed()
        composeRule.onNodeWithText("Used by one or more rolls").assertIsDisplayed()
    }

    @Test
    fun newRollDialogRendersWithEmptyOptionalCollections() {
        composeRule.setContent {
            DiceThrowerTheme(themeMode = ThemeMode.DARK) {
                RollDialogV2(
                    title = "New roll",
                    character = CharacterProfile(id = "character", name = "Test"),
                    modifiers = emptyList(),
                    groups = emptyList(),
                    existing = null,
                    onDismiss = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText("New roll").assertIsDisplayed()
        composeRule.onNodeWithText("1d20").assertIsDisplayed()
    }

    @Test
    fun newRollDialogSavesParameterizedModifierFormula() {
        val character = CharacterProfile(id = "character", name = "Test")
        val modifier = CharacterModifier(
            id = "strength",
            characterId = character.id,
            name = "Strength",
            value = 3,
        )
        var saved: RollDefinition? = null
        composeRule.setContent {
            DiceThrowerTheme(themeMode = ThemeMode.DARK) {
                RollDialogV2(
                    title = "New roll",
                    character = character,
                    modifiers = listOf(modifier),
                    groups = emptyList(),
                    existing = null,
                    onDismiss = {},
                    onSave = { saved = it },
                )
            }
        }

        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("Attack")
        composeRule.onAllNodes(hasSetTextAction())[1].performTextReplacement("1d20+{Strength}")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.runOnIdle {
            assertEquals("Attack", saved?.name)
            assertEquals("1d20+{Strength}", saved?.expression)
        }
    }
}
