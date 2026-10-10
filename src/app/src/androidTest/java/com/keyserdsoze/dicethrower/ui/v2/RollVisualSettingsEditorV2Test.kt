package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.keyserdsoze.dicethrower.model.RollVisualEffectsSettings
import com.keyserdsoze.dicethrower.model.RollVisualProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RollVisualSettingsEditorV2Test {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun allPresetsAreEditableWithoutChangingRollOutcome() {
        var current by mutableStateOf(RollVisualEffectsSettings())
        composeRule.setContent {
            MaterialTheme {
                RollVisualSettingsEditorV2(settings = current, onChange = { current = it })
            }
        }
        composeRule.onNodeWithText("Balanced").assertExists()
        composeRule.onNodeWithText("Subtle").performClick()
        composeRule.runOnIdle {
            assertEquals(RollVisualProfile.SUBTLE, current.profile)
            assertTrue(current.badge && current.groupLanes && current.winnerSpotlight)
            assertFalse(current.particles || current.tableAura || current.resultTransitions)
        }
        composeRule.onNodeWithText("Off").performClick()
        composeRule.runOnIdle {
            assertEquals(RollVisualProfile.OFF, current.profile)
            assertFalse(current.badge || current.groupLanes || current.winnerSpotlight)
            assertFalse(current.actionCues)
        }
        composeRule.onNodeWithText("Balanced").performClick()
        composeRule.runOnIdle {
            assertEquals(RollVisualProfile.BALANCED, current.profile)
            assertTrue(current.particles && current.actionCues && current.resultTransitions)
        }
    }
}
