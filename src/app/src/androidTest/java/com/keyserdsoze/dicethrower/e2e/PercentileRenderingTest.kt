package com.keyserdsoze.dicethrower.e2e

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keyserdsoze.dicethrower.dice.DiceComponent
import com.keyserdsoze.dicethrower.dice.DiceRollResult
import com.keyserdsoze.dicethrower.dice.DiceRollVisualEvent
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.ThemeMode
import com.keyserdsoze.dicethrower.ui.dice3d.Dice3DScene
import com.keyserdsoze.dicethrower.ui.theme.DiceThrowerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong

/** Fixed renderer inputs exercise both d10 mesh variants in the same actual OpenGL context. */
@RunWith(AndroidJUnit4::class)
class PercentileRenderingTest {
    private val compose = createComposeRule()
    private val diagnostics = NativeAppE2ERule(compose)
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compose).around(diagnostics)

    @Test
    fun zeroUnitsAndStandaloneTenRenderTogetherBeforeAndAfterAnEffect() {
        val baseline = DiceRollResult(110, listOf(
            DiceComponent(1, 100, 1, listOf(100)), DiceComponent(1, 10, 1, listOf(10)),
        ), 0)
        val event = mutableStateOf(DiceRollVisualEvent(1L, baseline, emptyList()))
        val animate = mutableStateOf(false)
        val rendered = AtomicLong()
        val settled = AtomicLong()
        compose.setContent {
            DiceThrowerTheme(ThemeMode.DARK) {
                Dice3DScene(
                    event = event.value,
                    modifier = Modifier.fillMaxSize(),
                    fullBleed = true,
                    animateRoll = animate.value,
                    onFrameRendered = { id, _ -> rendered.set(id) },
                    onSettled = settled::set,
                )
            }
        }
        diagnostics.step("Render d100=100 (00 + 0) alongside standalone d10=10")
        compose.waitUntil(10_000) { rendered.get() == 1L && settled.get() == 1L }
        diagnostics.checkpoint("percentile-zero-and-standalone-ten")

        diagnostics.step("Append one effect d4, preserving two logical samples / three bodies")
        val extra = DiceComponent(1, 4, 1, listOf(3))
        compose.runOnIdle {
            event.value = DiceRollVisualEvent(
                id = 2L,
                result = baseline.copy(total = 113, components = baseline.components + extra),
                appearances = emptyList(),
                effectAccentComponents = mapOf(2 to EffectType.BONUS),
                persistentDiceCount = 2,
            )
            animate.value = true
        }
        compose.waitUntil(10_000) { rendered.get() == 2L && settled.get() == 2L }
        diagnostics.checkpoint("percentile-pair-after-effect")
        assertEquals(110, baseline.total)
        assertEquals(listOf(100, 10), baseline.components.map { it.rolls.single() })
    }
}
