package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class EditorStateRestorationTest {
    @get:Rule val composeRule = createComposeRule()
    private val hero = CharacterProfile("hero", "Hero")

    private fun scrollToText(text: String) {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }

    private fun scrollToTag(tag: String) {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun incompleteRollAndCursorSurviveRestorationWithoutEnablingSave() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            MaterialTheme {
                RollBuilderScreenV2(
                    title = "New roll", character = hero, modifiers = emptyList(), groups = emptyList(),
                    existing = null, onDismiss = {}, onSave = {},
                )
            }
        }
        composeRule.onNodeWithText("Roll name").performTextReplacement("Unfinished spell")
        composeRule.onNodeWithTag("roll-minimum-level").performTextReplacement("")
        scrollToTag("composer-expression")
        composeRule.onNodeWithTag("composer-expression").performTextReplacement("2d6+{")
        composeRule.onNodeWithTag("composer-expression").performTextInputSelection(TextRange(2))

        restoration.emulateSavedInstanceStateRestore()

        scrollToTag("composer-expression")
        composeRule.onNodeWithTag("composer-expression").assertTextContains("2d6+{")
        assertEquals(TextRange(2), composeRule.onNodeWithTag("composer-expression")
            .fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        scrollToText("Roll name")
        composeRule.onNodeWithText("Roll name").assertTextContains("Unfinished spell")
        assertEquals("", composeRule.onNodeWithTag("roll-minimum-level")
            .fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        scrollToText("Save")
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun restorationDoesNotRegenerateTheNewRollPartId() {
        var saved: RollDefinition? = null
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            MaterialTheme {
                RollBuilderScreenV2(
                    title = "New roll", character = hero, modifiers = emptyList(), groups = emptyList(),
                    existing = null, onDismiss = {}, onSave = { saved = it },
                )
            }
        }
        composeRule.onNodeWithText("Roll name").performTextReplacement("Prepared spell")
        scrollToText("Save")
        composeRule.onNodeWithText("Save").performClick()
        val originalPartId = saved?.subgroups?.single()?.id
        assertNotNull(originalPartId)

        restoration.emulateSavedInstanceStateRestore()
        scrollToText("Save")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.runOnIdle {
            assertEquals(originalPartId, saved?.subgroups?.single()?.id)
            assertEquals("Prepared spell", saved?.name)
        }
    }

    @Test
    fun nestedGroupEditorReopensWithItsDraftButCancelDiscardsIt() {
        val group = RollGroup("group", hero.id, "Spells")
        val data = AppData(characters = listOf(hero), groups = listOf(group))
        var saved: AppData? = null
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            MaterialTheme {
                GroupEditContentV2(hero, group, data, onDataChanged = { saved = it })
            }
        }
        composeRule.onNodeWithText("New roll").performClick()
        composeRule.onNodeWithText("Roll name").performTextReplacement("Group draft")

        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("roll-editor-screen").assertExists()
        scrollToText("Roll name")
        composeRule.onNodeWithText("Roll name").assertTextContains("Group draft")
        scrollToText("Cancel")
        composeRule.onNodeWithText("Cancel").performClick()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("roll-editor-screen").assertDoesNotExist()
        assertEquals(null, saved)
        composeRule.onNodeWithText("New roll").performClick()
        assertEquals("", composeRule.onNodeWithText("Roll name")
            .fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }

    @Test
    fun newGroupDialogNameSurvivesRestoration() {
        var updated: AppData? = null
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            MaterialTheme {
                CharacterEditContentV2(hero, AppData(characters = listOf(hero)), {}, { updated = it })
            }
        }
        scrollToText("New group")
        composeRule.onNodeWithText("New group").performClick()
        composeRule.onNodeWithText("Group name").performTextReplacement("Rotating group")

        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Group name").assertTextContains("Rotating group")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.runOnIdle { assertEquals("Rotating group", updated?.groups?.single()?.name) }
    }
}
