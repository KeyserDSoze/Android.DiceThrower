package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.RollVisualEffectsSettings
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Time-bounded visual treatment driven by the *already evaluated* effect.
 * Uses no RNG, no new logical dice and no additional saved history entries.
 */
@Composable
internal fun CinematicEffectOverlayV2(
    key: String,
    type: EffectType,
    action: EffectActionType?,
    particles: Boolean,
    aura: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!particles && !aura) return
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 1150))
    }
    val t = progress.value
    if (t >= 1f) return
    val tint = if (type == EffectType.BONUS) Color(0xFF5EFFD0) else Color(0xFFFF5774)
    Canvas(modifier) {
        val center = Offset(size.width * 0.50f, size.height * 0.44f)
        val maxRadius = size.minDimension * 0.40f
        val r = maxRadius * (0.22f + t * 0.74f)
        val strength = (1f - t) * (1f - t)
        if (aura) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(tint.copy(alpha = 0.20f * strength), Color.Transparent),
                    center = center, radius = r.coerceAtLeast(1f),
                ),
                radius = r.coerceAtLeast(1f), center = center,
            )
            val rings = when (action) {
                EffectActionType.MULTIPLY -> 2
                EffectActionType.ROLL_AFTER, EffectActionType.REROLL -> 3
                else -> 1
            }
            repeat(rings) { index ->
                val radius = r * (1f - index * 0.16f)
                drawCircle(tint.copy(alpha = 0.47f * strength),
                    radius = radius.coerceAtLeast(1f), center = center,
                    style = Stroke(width = (2.2f - index * 0.4f).dp.toPx()))
            }
            repeat(12) { i ->
                val a = i * PI.toFloat() / 6f
                val outer = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
                val inner = Offset(center.x + cos(a) * r * 0.91f, center.y + sin(a) * r * 0.91f)
                drawLine(tint.copy(alpha = 0.58f * strength), inner, outer, 1.3.dp.toPx())
            }
            // A malus briefly fractures the illuminated rune instead of
            // using the bonus' clean luminous halo.
            if (type == EffectType.MALUS) repeat(6) { i ->
                val a = (i + 0.18f) * PI.toFloat() / 3f
                val mid = Offset(center.x + cos(a) * r * 0.64f,
                    center.y + sin(a) * r * 0.64f)
                val kink = Offset(center.x + cos(a + 0.18f) * r * 0.81f,
                    center.y + sin(a + 0.18f) * r * 0.81f)
                val end = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
                drawLine(tint.copy(alpha = 0.55f * strength), mid, kink, 1.9.dp.toPx())
                drawLine(tint.copy(alpha = 0.55f * strength), kink, end, 1.3.dp.toPx())
            }
        }
        if (particles) {
            val count = if (action == EffectActionType.REROLL || action == EffectActionType.ROLL_AFTER) 22 else 14
            repeat(count) { i ->
                val angle = i * (PI.toFloat() * 2 / count) + when (action) {
                    EffectActionType.REROLL -> t * PI.toFloat() * 3f
                    else -> 0f
                }
                val distance = maxRadius * (0.10f + (i % 5) * 0.08f + t * 0.75f)
                val fall = if (type == EffectType.MALUS) t * maxRadius * 0.38f else -t * maxRadius * 0.17f
                val point = Offset(center.x + cos(angle) * distance,
                    center.y + sin(angle) * distance + fall)
                if (type == EffectType.BONUS) {
                    val tail = Offset(center.x + cos(angle) * (distance - r * 0.13f),
                        center.y + sin(angle) * (distance - r * 0.13f) + fall + r * 0.05f)
                    drawLine(tint.copy(alpha = 0.37f * strength),
                        tail, point, 1.4.dp.toPx())
                }
                drawCircle(
                    color = tint.copy(alpha = (0.40f + (i % 3) * 0.16f) * strength),
                    radius = (1.2f + i % 4).dp.toPx(),
                    center = point,
                )
            }
        }
    }
}

@Composable
internal fun CinematicActionCueV2(
    action: EffectActionType,
    type: EffectType,
    before: Int?,
    after: Int?,
    modifier: Modifier = Modifier,
) {
    val actionName = stringResource(when (action) {
        EffectActionType.ADD -> R.string.effects_action_add
        EffectActionType.SUBTRACT -> R.string.effects_action_subtract
        EffectActionType.MULTIPLY -> R.string.effects_action_multiply
        EffectActionType.REPLACE -> R.string.effects_action_replace
        EffectActionType.REROLL -> R.string.effects_action_reroll
        EffectActionType.ROLL_AFTER -> R.string.effects_action_roll_after
    })
    val color = if (type == EffectType.BONUS) Color(0xFFB0FFDA) else Color(0xFFFFB5C0)
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = Color(0xF01A1B29), contentColor = Color.White) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(actionName, color = color, style = MaterialTheme.typography.labelLarge)
            if (before != null && after != null && before != after) {
                Text("  $before → $after", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A visual cue replays the *trace*, never the actual logical action. */
internal data class CinematicActionMoment(
    val effectId: String,
    val type: EffectType,
    val action: EffectActionType,
    val before: Int?,
    val after: Int?,
)

@Composable
internal fun CinematicActionSequenceV2(
    rollSeed: Long,
    moments: List<CinematicActionMoment>,
    options: RollVisualEffectsSettings,
    modifier: Modifier = Modifier,
) {
    if (moments.isEmpty() || !(options.actionCues || options.tableAura || options.particles)) return
    val limited = remember(moments) { moments.take(16) }
    var index by remember(rollSeed, limited) { mutableIntStateOf(0) }
    LaunchedEffect(rollSeed, limited) {
        index = 0
        for (n in limited.indices) {
            index = n
            delay(980L)
        }
        index = limited.size
    }
    val moment = limited.getOrNull(index) ?: return
    Box(modifier) {
        CinematicEffectOverlayV2(
            key = "$rollSeed:$index:${moment.effectId}",
            type = moment.type,
            action = moment.action,
            particles = options.particles,
            aura = options.tableAura,
            modifier = Modifier.fillMaxSize(),
        )
        if (options.actionCues) {
            CinematicActionCueV2(
                action = moment.action,
                type = moment.type,
                before = moment.before,
                after = moment.after,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
