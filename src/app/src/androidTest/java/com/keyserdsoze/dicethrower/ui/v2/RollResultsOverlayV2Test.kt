package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.dice.DiceRollResult
import com.keyserdsoze.dicethrower.dice.ResolvedRollSubgroup
import com.keyserdsoze.dicethrower.dice.ResolvedRollSubgroupResult
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import com.keyserdsoze.dicethrower.model.ThemeMode
import com.keyserdsoze.dicethrower.ui.theme.DiceThrowerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RollResultsOverlayV2Test {
    @get:Rule val composeRule = createComposeRule()

    private fun parts(count: Int) = (1..count).map { index ->
        ResolvedRollSubgroupResult(
            subgroup = ResolvedRollSubgroup(
                id = "part-${index}",
                name = "Part ${index}",
                operator = RollSubgroupOperator.ADD,
                expression = "1d20",
            ),
            result = DiceRollResult(
                total = index * 3,
                components = emptyList(),
                constantTotal = index * 3,
            ),
        )
    }

    @Test
    fun partsUseIndividualTotalsAndStayAboveFooter() {
        composeRule.setContent {
            DiceThrowerTheme(themeMode = ThemeMode.DARK) {
                Box(Modifier.size(width = 350.dp, height = 460.dp)) {
                    RollResultsOverlayV2(
                        parts = parts(2),
                        total = 999,
                        aboveAverage = false,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 88.dp)
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                    )
                    Box(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(72.dp)
                            .testTag("footer"),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Part 1").assertIsDisplayed()
        composeRule.onNodeWithText("Part 2").assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithText("6").assertIsDisplayed()
        composeRule.onNodeWithText("999").assertDoesNotExist()
        val overlay = composeRule.onNodeWithTag("roll-results-overlay").fetchSemanticsNode().boundsInRoot
        val footer = composeRule.onNodeWithTag("footer").fetchSemanticsNode().boundsInRoot
        assertTrue("Results overlap footer", overlay.bottom < footer.top)
    }

    @Test
    fun manyPartsScrollInsideSmallBottomViewport() {
        composeRule.setContent {
            DiceThrowerTheme(themeMode = ThemeMode.DARK) {
                Box(Modifier.size(width = 300.dp, height = 360.dp)) {
                    RollResultsOverlayV2(
                        parts = parts(10),
                        total = 999,
                        aboveAverage = false,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 82.dp)
                            .fillMaxWidth()
                            .heightIn(max = 112.dp),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("roll-parts-list").assert(hasScrollAction())
        composeRule.onNodeWithTag("roll-part-9").performScrollTo()
        composeRule.onNodeWithText("Part 10").assertIsDisplayed()
        composeRule.onNodeWithText("999").assertDoesNotExist()
    }

    @Test
    fun singleRollHasCompactTotalWithoutPartCards() {
        composeRule.setContent {
            DiceThrowerTheme(themeMode = ThemeMode.LIGHT) {
                RollResultsOverlayV2(
                    parts = emptyList(),
                    total = 27,
                    aboveAverage = true,
                )
            }
        }

        composeRule.onNodeWithTag("roll-single-result").assertIsDisplayed()
        composeRule.onNodeWithText("27").assertIsDisplayed()
        composeRule.onNodeWithTag("roll-parts-list").assertDoesNotExist()
    }
}
