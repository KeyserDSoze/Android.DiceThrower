package com.keyserdsoze.dicethrower.ui.v2

import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DiceTableTheme
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.ThemeMode
import com.keyserdsoze.dicethrower.ui.theme.DiceThrowerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RollDialogV2Test {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun diceTablePickerShowsPremiumPresetPreviews() {
        val character = CharacterProfile(id = "character", name = "Test")
        composeRule.setContent {
            DiceThrowerTheme(themeMode = ThemeMode.DARK) {
                DiceTablePickerV2(
                    character = character,
                    onThemeChanged = {},
                    onImageChanged = {},
                )
            }
        }

        composeRule.onNodeWithText("Arcane Night").assertIsDisplayed()
        // Presets are hidden until the compact, selected-table accordion is opened.
        assertTrue(composeRule.onAllNodesWithContentDescription("Tavern Wood").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Dice table").performClick()
        listOf(
            "Arcane Night",
            "Fantasy Felt",
            "Ancient Map",
            "Sci-Fi Neon",
            "Tavern Wood",
            "Dungeon Stone",
            "Elven Grove",
            "Frozen Realm",
            "Desert Ruins",
            "Astral Void",
        ).forEach { label ->
            assertTrue(
                "$label preview node is missing",
                composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Test
    fun premiumTableBitmapsContainVisibleArtwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        DiceTableTheme.entries.forEach { theme ->
            val bitmap = theme.presetBitmap(context)
            val stepX = (bitmap.width / 12).coerceAtLeast(1)
            val stepY = (bitmap.height / 20).coerceAtLeast(1)
            var samples = 0
            var visibleSamples = 0

            for (y in 0 until bitmap.height step stepY) {
                for (x in 0 until bitmap.width step stepX) {
                    val pixel = bitmap.getPixel(x, y)
                    val brightness = Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)
                    samples += 1
                    if (brightness > 30) visibleSamples += 1
                }
            }

            assertTrue(
                "${theme.name} preview is effectively black",
                visibleSamples >= (samples * 0.12f).toInt().coerceAtLeast(1),
            )
        }
    }

    @Test
    fun generatedFantasyTablesHaveNativeFullResolutionAndSmallPreviews() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val generated = listOf(
            DiceTableTheme.TAVERN_WOOD,
            DiceTableTheme.DUNGEON_STONE,
            DiceTableTheme.ELVEN_GROVE,
            DiceTableTheme.FROZEN_REALM,
            DiceTableTheme.DESERT_RUINS,
            DiceTableTheme.ASTRAL_VOID,
        )
        generated.forEach { theme ->
            val preview = theme.presetBitmap(context, preview = true)
            assertTrue("${theme.name} preview width", preview.width == 360)
            assertTrue("${theme.name} preview height", preview.height == 640)
            preview.recycle()
            val full = theme.presetBitmap(context)
            assertTrue("${theme.name} full width", full.width == 1080)
            assertTrue("${theme.name} full height", full.height == 1920)
            full.recycle()
        }
    }

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

        composeRule.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasContentDescription("New roll"))
        composeRule.onNodeWithContentDescription("New roll").performClick()
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

        composeRule.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("Strength"))
        composeRule.onNodeWithText("Strength").assertIsDisplayed()
        composeRule.onNodeWithText("Used by one or more rolls").assertIsDisplayed()
    }

    @Test
    fun characterLevelSupportsManualEditAndRejectsZero() {
        val character = CharacterProfile(id = "character", name = "Test", level = 2)
        var latestData = AppData(characters = listOf(character))
        composeRule.setContent {
            DiceThrowerTheme(themeMode = ThemeMode.DARK) {
                CharacterEditContentV2(
                    character = character,
                    data = latestData,
                    onOpenGroup = {},
                    onDataChanged = { latestData = it },
                )
            }
        }

        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("5")
        composeRule.runOnIdle { assertEquals(5, latestData.characters.single().level) }

        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("0")
        composeRule.runOnIdle { assertEquals(5, latestData.characters.single().level) }
    }

    @Test
    fun characterLevelDownIsDisabledAtOne() {
        val character = CharacterProfile(id = "character", name = "Test", level = 1)
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

        composeRule.onNodeWithText("−").assertIsNotEnabled()
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

    @Test
    fun guidedBuilderPreservesValidAdvancedEditAndSupportsSubgroupReordering() {
        val character = CharacterProfile(id = "character", name = "Test", level = 3)
        val existing = RollDefinition(
            id = "combo",
            characterId = character.id,
            name = "Combo",
            expression = "(1d20)+(2d6)",
            subgroups = listOf(
                RollSubgroup("attack", "Attack", "1d20"),
                RollSubgroup("damage", "Damage", "2d6"),
            ),
        )
        composeRule.setContent {
            DiceThrowerTheme(themeMode = ThemeMode.DARK) {
                RollBuilderScreenV2(
                    title = "Edit roll",
                    character = character,
                    modifiers = emptyList(),
                    groups = emptyList(),
                    existing = existing,
                    onDismiss = {},
                    onSave = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Move down")[0].performClick()
        composeRule.onNodeWithText("Advanced formula").performClick()
        composeRule.onAllNodes(hasSetTextAction())[1].performTextReplacement("2*(1d20+1)")
        composeRule.onNodeWithText("Guided builder").performClick()
        composeRule.onNodeWithText("(2*(1d20+1))").assertIsDisplayed()
    }
}
