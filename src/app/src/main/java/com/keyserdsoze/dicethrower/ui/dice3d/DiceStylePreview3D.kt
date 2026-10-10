package com.keyserdsoze.dicethrower.ui.dice3d

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.keyserdsoze.dicethrower.dice.DiceComponent
import com.keyserdsoze.dicethrower.dice.DiceRollResult
import com.keyserdsoze.dicethrower.dice.DiceRollVisualEvent
import com.keyserdsoze.dicethrower.dice.ResolvedDiceAppearance
import com.keyserdsoze.dicethrower.model.DiceStyle
import java.util.concurrent.atomic.AtomicLong

@Composable
fun DiceStylePreview3D(
    style: DiceStyle,
    modifier: Modifier = Modifier,
) {
    val event = remember(style.material, style.primaryColorArgb, style.secondaryColorArgb) {
        DiceRollVisualEvent(
            id = previewEventIds.incrementAndGet(),
            result = previewResult,
            appearances = listOf(
                ResolvedDiceAppearance(
                    slotKey = "0:0",
                    componentIndex = 0,
                    dieIndex = 0,
                    sourceStyleId = style.id,
                    material = style.material,
                    primaryColorArgb = style.primaryColorArgb,
                    secondaryColorArgb = style.secondaryColorArgb,
                ),
            ),
        )
    }
    Dice3DScene(event = event, modifier = modifier, animateRoll = false, previewMode = true)
}

private val previewEventIds = AtomicLong(10_000L)
private val previewResult = DiceRollResult(
    total = 20,
    components = listOf(DiceComponent(count = 1, sides = 20, sign = 1, rolls = listOf(20))),
    constantTotal = 0,
)
