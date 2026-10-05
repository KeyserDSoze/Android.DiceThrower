package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.ui.theme.DiceThrowerTheme
import com.keyserdsoze.dicethrower.model.ThemeMode
import org.junit.Rule
import org.junit.Test

class RollDialogV2Test {
    @get:Rule
    val composeRule = createComposeRule()

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
}
