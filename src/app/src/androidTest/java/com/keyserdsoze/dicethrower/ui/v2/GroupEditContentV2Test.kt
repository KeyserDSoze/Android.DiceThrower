package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollGroup
import com.keyserdsoze.dicethrower.model.ThemeMode
import com.keyserdsoze.dicethrower.ui.theme.DiceThrowerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GroupEditContentV2Test {
    @get:Rule val composeRule = createComposeRule()

    private val character = CharacterProfile(id = "hero", name = "Hero")
    private val group = RollGroup(id = "group", characterId = "hero", name = "Spells", order = 0)

    private fun fixture() = AppData(
        characters = listOf(character),
        groups = listOf(group, RollGroup("defense", "hero", "Defense", 1)),
        rolls = listOf(
            RollDefinition("fire", "hero", "Fire", "1d6", "group", order = 0),
            RollDefinition("ice", "hero", "Ice", "1d8", "group", order = 1),
        ),
    )

    @Test
    fun compactGroupRollOpensFullDetailsInsteadOfAccordion() {
        val data = mutableStateOf(fixture())
        composeRule.setContent {
            DiceThrowerTheme(themeMode = ThemeMode.DARK) {
                GroupEditContentV2(
                    character = character,
                    group = group,
                    data = data.value,
                    onDataChanged = { data.value = it },
                )
            }
        }

        composeRule.onNodeWithText("Fire").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("1d6").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Fire").performClick()
        composeRule.onNodeWithText("1d6").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(composeRule.onAllNodesWithText("1d6").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Fire").assertIsDisplayed()
    }

    @Test
    fun explicitGroupArrowsReorderPersistedRolls() {
        val data = mutableStateOf(fixture())
        composeRule.setContent {
            DiceThrowerTheme(themeMode = ThemeMode.DARK) {
                GroupEditContentV2(
                    character = character,
                    group = group,
                    data = data.value,
                    onDataChanged = { data.value = it },
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Move up")[0].assertIsNotEnabled()
        composeRule.onAllNodesWithContentDescription("Move down")[0].performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf("ice", "fire"),
                data.value.rolls.filter { it.groupId == "group" }.sortedBy { it.order }.map { it.id },
            )
        }
        composeRule.onNodeWithText("Ice").assertIsDisplayed()
    }

    @Test
    fun groupSelectorMovesRollWithoutExpandingRow() {
        val data = mutableStateOf(fixture())
        composeRule.setContent {
            DiceThrowerTheme(themeMode = ThemeMode.DARK) {
                GroupEditContentV2(
                    character = character,
                    group = group,
                    data = data.value,
                    onDataChanged = { data.value = it },
                )
            }
        }
        composeRule.onAllNodesWithContentDescription("Group")[0].performClick()
        composeRule.onNodeWithText("Defense").performClick()
        composeRule.runOnIdle {
            assertEquals("defense", data.value.rolls.single { it.id == "fire" }.groupId)
            assertEquals("group", data.value.rolls.single { it.id == "ice" }.groupId)
        }
    }
}
