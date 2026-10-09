package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class RollTableGestureV2Test {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun doubleTapTogglesStatsWithoutDispatchingReroll() {
        var throws = 0
        var toggles = 0
        composeRule.setContent {
            RollTableGestureLayerV2(
                tapToRollEnabled = true,
                swipeToRollEnabled = false,
                canRoll = true,
                statsDoubleTapEnabled = true,
                canToggleStats = true,
                onRollRequest = { throws++ },
                onStatsToggle = { toggles++ },
                modifier = Modifier.size(300.dp),
            )
        }

        composeRule.onNodeWithTag("roll-table-gesture-layer")
            .performTouchInput { doubleClick() }
        composeRule.runOnIdle {
            assertEquals(1, toggles)
            assertEquals("Double tap must not request a throw", 0, throws)
        }
    }

    @Test
    fun disabledPreferencePreservesSingleTapAndNeverOpensStats() {
        var throws = 0
        var toggles = 0
        composeRule.setContent {
            RollTableGestureLayerV2(
                tapToRollEnabled = true,
                swipeToRollEnabled = false,
                canRoll = true,
                statsDoubleTapEnabled = false,
                canToggleStats = true,
                onRollRequest = { throws++ },
                onStatsToggle = { toggles++ },
                modifier = Modifier.size(300.dp),
            )
        }
        composeRule.onNodeWithTag("roll-table-gesture-layer")
            .performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(1, throws)
            assertEquals(0, toggles)
        }
    }

    @Test
    fun statsGestureRequiresCompletedRollAndKeepsSwipe() {
        var throws = 0
        var toggles = 0
        composeRule.setContent {
            RollTableGestureLayerV2(
                tapToRollEnabled = false,
                swipeToRollEnabled = true,
                canRoll = true,
                statsDoubleTapEnabled = true,
                canToggleStats = false,
                onRollRequest = { throws++ },
                onStatsToggle = { toggles++ },
                modifier = Modifier.size(300.dp),
            )
        }
        val node = composeRule.onNodeWithTag("roll-table-gesture-layer")
        node.performTouchInput { doubleClick() }
        composeRule.runOnIdle {
            assertEquals(0, throws)
            assertEquals(0, toggles)
        }
        node.performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertEquals(1, throws)
            assertEquals(0, toggles)
        }
    }

    @Test
    fun directionSwipesDispatchExactRequestedMode() {
        var normal = 0
        var best = 0
        var worst = 0
        composeRule.setContent {
            RollTableGestureLayerV2(
                tapToRollEnabled = false,
                swipeToRollEnabled = true,
                canRoll = true,
                statsDoubleTapEnabled = false,
                canToggleStats = false,
                onRollRequest = { normal++ },
                onStatsToggle = {},
                modifier = Modifier.size(300.dp),
                directionalDoubleRollEnabled = true,
                onBestRollRequest = { best++ },
                onWorstRollRequest = { worst++ },
            )
        }
        val table = composeRule.onNodeWithTag("roll-table-gesture-layer")
        table.performTouchInput { swipeLeft() }
        table.performTouchInput { swipeRight() }
        table.performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertEquals(1, worst)
            assertEquals(1, best)
            assertEquals(1, normal)
        }
    }

    @Test
    fun directionalPreferenceOffRestoresOriginalUpSwipeOnly() {
        var normal = 0
        var best = 0
        var worst = 0
        composeRule.setContent {
            RollTableGestureLayerV2(
                tapToRollEnabled = false,
                swipeToRollEnabled = true,
                canRoll = true,
                statsDoubleTapEnabled = false,
                canToggleStats = false,
                onRollRequest = { normal++ },
                onStatsToggle = {},
                modifier = Modifier.size(300.dp),
                directionalDoubleRollEnabled = false,
                onBestRollRequest = { best++ },
                onWorstRollRequest = { worst++ },
            )
        }
        val table = composeRule.onNodeWithTag("roll-table-gesture-layer")
        table.performTouchInput { swipeLeft() }
        table.performTouchInput { swipeRight() }
        table.performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertEquals(0, worst)
            assertEquals(0, best)
            assertEquals(1, normal)
        }
    }

    @Test
    fun secondDoubleTapClosesStatsSheetWhenEnabled() {
        var visible by mutableStateOf(true)
        composeRule.setContent {
            Box(
                Modifier.size(300.dp)
                    .dismissStatsOnDoubleTap(true) { visible = false }
                    .testTag("stats-sheet"),
            ) {
                Text(if (visible) "Statistics open" else "Statistics closed")
            }
        }
        composeRule.onNodeWithTag("stats-sheet")
            .performTouchInput { doubleClick() }
        composeRule.runOnIdle { assertFalse(visible) }
    }
}
