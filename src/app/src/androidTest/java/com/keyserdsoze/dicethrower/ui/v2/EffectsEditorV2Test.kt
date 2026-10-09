package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.RollEffect
import com.keyserdsoze.dicethrower.model.RollSubgroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EffectsEditorV2Test {
    @get:Rule val composeRule = createComposeRule()

    private val parts = listOf(
        RollSubgroup("attack", "Attack", "1d20"),
        RollSubgroup("damage", "Damage", "2d6+3"),
    )

    @Test
    fun buttonsAddDifferentTypesAndDefaultOrdering() {
        var effects by mutableStateOf(emptyList<RollEffect>())
        composeRule.setContent {
            MaterialTheme {
                EffectsEditorSectionV2(
                    effects = effects,
                    parts = parts,
                    variableNames = listOf("level", "Strength"),
                    onChange = { effects = it },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
        composeRule.onNodeWithText("+ Add bonus").performClick()
        composeRule.runOnIdle {
            assertEquals(1, effects.size)
            assertEquals(EffectType.BONUS, effects.single().type)
        }
        composeRule.onNodeWithText("+ Add malus").performClick()
        composeRule.runOnIdle {
            assertEquals(2, effects.size)
            assertEquals(listOf(0, 1), effects.map { it.order })
            assertEquals(EffectType.MALUS, effects[1].type)
            assertTrue(effects.all { it.activationGroups.size == 1 && it.actions.size == 1 })
        }
    }
}
