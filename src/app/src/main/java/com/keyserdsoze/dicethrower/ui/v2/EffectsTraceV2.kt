package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectComparison
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.RollLogEffectStep

/** Same localized trace component for live statistics and restored history. */
@Composable
internal fun EffectsTraceV2(
    steps: List<RollLogEffectStep>,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.effects_heading), style = MaterialTheme.typography.titleMedium)
        steps.forEach { step ->
            val active = step.activated
            val accent = if (step.type == EffectType.BONUS)
                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (active) 1f else 0.7f },
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (step.type == EffectType.BONUS) "+" else "−",
                            color = accent,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(step.name, modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(if (active) R.string.effects_trace_triggered
                                else R.string.effects_trace_skipped),
                            color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    step.conditions.forEach { condition ->
                        val symbol = when (condition.comparison) {
                            EffectComparison.GREATER_OR_EQUAL -> "≥"
                            EffectComparison.LESS_OR_EQUAL -> "≤"
                            EffectComparison.GREATER -> ">"
                            EffectComparison.LESS -> "<"
                            EffectComparison.EQUAL -> "="
                            EffectComparison.NOT_EQUAL -> "≠"
                        }
                        Text(
                            "${condition.actual ?: "?"} $symbol ${condition.threshold ?: "?"} " +
                                if (condition.passed) "✓" else "×",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        condition.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    step.actions.forEach { action ->
                        val actionLabel = stringResource(when (action.kind) {
                            EffectActionType.ADD -> R.string.effects_action_add
                            EffectActionType.SUBTRACT -> R.string.effects_action_subtract
                            EffectActionType.MULTIPLY -> R.string.effects_action_multiply
                            EffectActionType.REPLACE -> R.string.effects_action_replace
                            EffectActionType.REROLL -> R.string.effects_action_reroll
                            EffectActionType.ROLL_AFTER -> R.string.effects_action_roll_after
                        })
                        Text(
                            "$actionLabel · ${action.before ?: "?"} → ${action.after ?: "?"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (action.applied) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.error,
                        )
                        action.generatedDiceDetail?.let { detail ->
                            Text(detail, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        action.error?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
