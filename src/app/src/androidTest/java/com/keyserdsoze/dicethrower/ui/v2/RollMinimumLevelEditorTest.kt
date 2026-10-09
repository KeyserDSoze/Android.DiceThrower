package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.RollDefinition
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RollMinimumLevelEditorTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun newRollCanBePreconfiguredForFutureLevel() {
        var saved: RollDefinition? = null
        composeRule.setContent {
            MaterialTheme {
                RollBuilderScreenV2(
                    title = "New roll",
                    character = CharacterProfile(id = "hero", name = "Hero", level = 2),
                    modifiers = emptyList(),
                    groups = emptyList(),
                    existing = null,
                    onDismiss = {},
                    onSave = { saved = it },
                )
            }
        }
        composeRule.onNodeWithText("Roll name").performTextReplacement("Prepared spell")
        composeRule.onNodeWithTag("roll-minimum-level").performTextReplacement("6")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(6, saved?.minimumLevel)
            assertEquals("Prepared spell", saved?.name)
        }
    }

    @Test
    fun editingOldRollKeepsDefaultLevelOne() {
        val old = RollDefinition(
            id = "old", characterId = "hero", name = "Legacy", expression = "(1d20)",
        )
        composeRule.setContent {
            MaterialTheme {
                RollBuilderScreenV2(
                    title = "Edit roll",
                    character = CharacterProfile(id = "hero", name = "Hero", level = 2),
                    modifiers = emptyList(),
                    groups = emptyList(),
                    existing = old,
                    onDismiss = {},
                    onSave = {},
                )
            }
        }
        composeRule.onNodeWithTag("roll-minimum-level").assertExists()
    }
}
