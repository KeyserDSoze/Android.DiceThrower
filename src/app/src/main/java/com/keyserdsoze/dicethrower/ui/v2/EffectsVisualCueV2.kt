package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.model.EffectType

/**
 * Theme-safe overlay feedback: semantic labels do not rely on color alone.
 * Reduced-motion users retain clear static feedback and no flashing.
 */
@Composable
internal fun EffectsVisualCueV2(
    effectName: String,
    type: EffectType,
    rollAfter: Boolean,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "effects-cue")
    val brightness = if (animate) transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 670),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "effects-cue-pulse",
    ).value else 1f
    val tint = if (type == EffectType.BONUS) Color(0xFF9BF2C3) else Color(0xFFFFB0B7)
    Surface(
        modifier = modifier.graphicsLayer { alpha = brightness }
            .testTag("effects-visual-cue"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xE91A1D2B),
        contentColor = Color.White,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (type == EffectType.BONUS) "✦ +" else "◆ −", color = tint,
                style = MaterialTheme.typography.titleMedium)
            Text(
                "${stringResource(if (type == EffectType.BONUS) R.string.effects_bonus
                    else R.string.effects_malus)} · $effectName" +
                    if (rollAfter) " · ${stringResource(R.string.effects_action_roll_after)}" else "",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
