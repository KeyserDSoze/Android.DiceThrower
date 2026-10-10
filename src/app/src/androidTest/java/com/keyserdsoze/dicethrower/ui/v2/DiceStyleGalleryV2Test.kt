package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DiceStylePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class DiceStyleGalleryV2Test {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun choosePreviewThenCopyPresetWithoutAffectingOtherCharacters() {
        val one = CharacterProfile(id = "hero", name = "Hero")
        val two = CharacterProfile(id = "rival", name = "Rival")
        var data by mutableStateOf(AppData(characters = listOf(one, two)))
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DiceStyleLibraryV2(
                        character = one,
                        data = data,
                        onDataChanged = { data = it },
                    )
                }
            }
        }
        composeRule.onNodeWithText("System dice styles").assertExists()
        composeRule.onNodeWithTag("dice-preset-emerald-aether").performScrollTo().performClick()
        composeRule.onNodeWithTag("dice-add-selected-preset").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(1, data.diceStyles.size)
            assertEquals("hero", data.diceStyles.single().characterId)
            assertEquals(DiceStylePresets.find("emerald-aether")!!.primaryColorArgb,
                data.diceStyles.single().primaryColorArgb)
            assertEquals(null, data.characters.last().defaultDiceStyleId)
            assertNotEquals("preview-preset", data.diceStyles.single().id)
        }
    }

    @Test
    fun characterStyleSectionStartsCollapsedAndShowsCatalogOnlyWhenAdding() {
        val character = CharacterProfile(id = "hero", name = "Hero")
        var data by mutableStateOf(AppData(characters = listOf(character)))
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CharacterDiceStyleSectionV2(
                        character = character,
                        data = data,
                        onDataChanged = { data = it },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("dice-character-add-style").assertDoesNotExist()
        composeRule.onNodeWithTag("dice-character-style-toggle").performClick()
        composeRule.onNodeWithTag("dice-character-add-style").assertExists().performClick()
        composeRule.onNodeWithText("System dice styles").assertExists()
    }
}
