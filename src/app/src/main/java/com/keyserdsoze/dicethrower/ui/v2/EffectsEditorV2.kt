package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.dice.EffectFormulaInterpreter
import com.keyserdsoze.dicethrower.dice.RollFormulaResolver
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.EffectAction
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectActivationGroup
import com.keyserdsoze.dicethrower.model.EffectComparison
import com.keyserdsoze.dicethrower.model.EffectCondition
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.EffectValueScope
import com.keyserdsoze.dicethrower.model.EffectValueSource
import com.keyserdsoze.dicethrower.model.PartReferenceAliases
import com.keyserdsoze.dicethrower.model.RollEffect
import com.keyserdsoze.dicethrower.model.RollSubgroup
import java.util.UUID

/**
 * Only the editor's in-progress strings may hold readable {parts:Name} aliases.
 * Saved Effects contain immutable {partId:...} references, so Part renames do
 * not change their targets or any stored expression.
 */
internal object EffectEditorDraft {
    fun initial(kind: EffectType, parts: List<RollSubgroup>, order: Int, defaultName: String): RollEffect {
        require(parts.isNotEmpty())
        val target = parts.first().id
        return RollEffect(
            id = UUID.randomUUID().toString(),
            name = defaultName,
            type = kind,
            order = order,
            activationGroups = listOf(
                EffectActivationGroup(UUID.randomUUID().toString(), listOf(
                    EffectCondition(UUID.randomUUID().toString(), partId = target, threshold = "20"),
                )),
            ),
            actions = listOf(
                EffectAction(UUID.randomUUID().toString(),
                    if (kind == EffectType.BONUS) EffectActionType.ADD else EffectActionType.SUBTRACT,
                    target, EffectValueScope.TOTAL, "1"),
            ),
        )
    }

    fun canonicalize(
        effects: List<RollEffect>,
        parts: List<RollSubgroup>,
        level: Int,
        modifiers: List<CharacterModifier>,
    ): List<RollEffect> {
        val partIds = parts.map { it.id }.toSet()
        val variables = mutableMapOf<String, Double>("level" to level.toDouble())
        modifiers.forEach { variables[it.name] = it.value.toDouble() }
        val partValues = parts.associate { it.id to 1.0 }
        return effects.mapIndexed { index, effect ->
            require(effect.name.isNotBlank() && effect.actions.isNotEmpty() &&
                effect.activationGroups.isNotEmpty()) { "Effect requires name, activation and action" }
            effect.copy(
                name = effect.name.trim(),
                order = index,
                activationGroups = effect.activationGroups.map { group ->
                    require(group.conditions.isNotEmpty()) { "Empty AND condition group" }
                    group.copy(conditions = group.conditions.map { condition ->
                        require(condition.source != EffectValueSource.PART || condition.partId in partIds) {
                            "Unknown trigger Part"
                        }
                        require(condition.source != EffectValueSource.VARIABLE ||
                            (condition.variableName != null &&
                                variables.keys.any { it.equals(condition.variableName, true) })) {
                            "Unknown trigger variable"
                        }
                        val normalized = PartReferenceAliases.store(condition.threshold, parts)
                        EffectFormulaInterpreter.evaluate(normalized, variables, partValues)
                        condition.copy(threshold = normalized)
                    })
                },
                actions = effect.actions.map { action ->
                    require(action.targetPartId in partIds) { "Unknown target Part" }
                    val normalized = PartReferenceAliases.store(action.expression, parts)
                    if (action.kind == EffectActionType.REROLL ||
                        action.kind == EffectActionType.ROLL_AFTER) {
                        require(normalized.isBlank() || RollFormulaResolver.validateTemplate(
                            normalized, level, modifiers,
                        )) { "Invalid follow-up dice formula" }
                    } else {
                        EffectFormulaInterpreter.evaluate(normalized, variables, partValues)
                    }
                    action.copy(expression = normalized)
                },
            )
        }
    }
}

@Composable
internal fun EffectsEditorSectionV2(
    effects: List<RollEffect>,
    parts: List<RollSubgroup>,
    variableNames: List<String>,
    onChange: (List<RollEffect>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultBonusName = stringResource(R.string.effects_bonus)
    val defaultMalusName = stringResource(R.string.effects_malus)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.effects_heading), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.effects_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        effects.forEachIndexed { index, effect ->
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(if (effect.type == EffectType.BONUS)
                                R.string.effects_bonus else R.string.effects_malus),
                            color = if (effect.type == EffectType.BONUS)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(enabled = index > 0, onClick = {
                            onChange(moveEffect(effects, index, -1))
                        }) {
                            Icon(Icons.Rounded.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.move_up))
                        }
                        IconButton(enabled = index < effects.lastIndex, onClick = {
                            onChange(moveEffect(effects, index, 1))
                        }) {
                            Icon(Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.move_down))
                        }
                        IconButton(onClick = {
                            onChange(effects.filterNot { it.id == effect.id }.mapIndexed { i, e ->
                                e.copy(order = i)
                            })
                        }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    fun replace(updated: RollEffect) = onChange(effects.map {
                        if (it.id == effect.id) updated else it
                    })
                    OutlinedTextField(
                        value = effect.name,
                        onValueChange = { replace(effect.copy(name = it)) },
                        label = { Text(stringResource(R.string.effects_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    ToggleEffectOption(
                        text = stringResource(R.string.effects_enabled),
                        checked = effect.enabled,
                        onChecked = { replace(effect.copy(enabled = it)) },
                    )
                    ToggleEffectOption(
                        text = stringResource(R.string.effects_stop_following),
                        checked = effect.stopFollowingEffects,
                        onChecked = { replace(effect.copy(stopFollowingEffects = it)) },
                    )
                    Text(stringResource(R.string.effects_or_groups),
                        style = MaterialTheme.typography.titleSmall)
                    effect.activationGroups.forEachIndexed { groupIndex, group ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.effects_or_group_number, groupIndex + 1),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                IconButton(onClick = {
                                    replace(effect.copy(activationGroups =
                                        effect.activationGroups.filterNot { it.id == group.id }))
                                }) {
                                    Icon(Icons.Rounded.Delete,
                                        contentDescription = stringResource(R.string.delete))
                                }
                            }
                            group.conditions.forEachIndexed { conditionIndex, condition ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.effects_and_condition, conditionIndex + 1),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    IconButton(onClick = {
                                        replace(effect.copy(activationGroups =
                                            effect.activationGroups.map {
                                                if (it.id == group.id) it.copy(
                                                    conditions = it.conditions.filterNot { c ->
                                                        c.id == condition.id
                                                    },
                                                ) else it
                                            }))
                                    }) {
                                        Icon(Icons.Rounded.Delete,
                                            contentDescription = stringResource(R.string.delete))
                                    }
                                }
                                EffectConditionEditorV2(condition, parts, variableNames) { changed ->
                                    replace(effect.copy(activationGroups = effect.activationGroups.map {
                                        if (it.id == group.id) it.copy(
                                            conditions = it.conditions.map { c ->
                                                if (c.id == condition.id) changed else c
                                            },
                                        ) else it
                                    }))
                                }
                            }
                            TextButton(onClick = {
                                replace(effect.copy(activationGroups = effect.activationGroups.map {
                                    if (it.id == group.id) it.copy(conditions =
                                        it.conditions + EffectCondition(UUID.randomUUID().toString(),
                                            partId = parts.first().id)) else it
                                }))
                            }) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Text(stringResource(R.string.effects_add_and))
                            }
                        }
                    }
                    OutlinedButton(onClick = {
                        replace(effect.copy(activationGroups = effect.activationGroups +
                            EffectActivationGroup(UUID.randomUUID().toString(), listOf(
                                EffectCondition(UUID.randomUUID().toString(), partId = parts.first().id),
                            ))))
                    }) {
                        Text(stringResource(R.string.effects_add_or))
                    }
                    Text(stringResource(R.string.effects_actions),
                        style = MaterialTheme.typography.titleSmall)
                    effect.actions.forEach { action ->
                        EffectActionEditorV2(action, parts, onRemove = {
                            replace(effect.copy(actions = effect.actions.filterNot { it.id == action.id }))
                        }, onChange = { updated ->
                            replace(effect.copy(actions = effect.actions.map {
                                if (it.id == action.id) updated else it
                            }))
                        })
                    }
                    OutlinedButton(onClick = {
                        replace(effect.copy(actions = effect.actions + EffectAction(
                            UUID.randomUUID().toString(), EffectActionType.ADD, parts.first().id,
                            EffectValueScope.TOTAL, "1",
                        )))
                    }) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text(stringResource(R.string.effects_add_action))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                onChange(effects + EffectEditorDraft.initial(
                    EffectType.BONUS, parts, effects.size, defaultBonusName))
            }) {
                Text(stringResource(R.string.effects_add_bonus))
            }
            OutlinedButton(onClick = {
                onChange(effects + EffectEditorDraft.initial(
                    EffectType.MALUS, parts, effects.size, defaultMalusName))
            }) {
                Text(stringResource(R.string.effects_add_malus))
            }
        }
        Text(stringResource(R.string.effects_formula_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun moveEffect(effects: List<RollEffect>, index: Int, offset: Int): List<RollEffect> =
    effects.toMutableList().apply {
        add(index + offset, removeAt(index))
    }.mapIndexed { order, effect -> effect.copy(order = order) }

@Composable
private fun ToggleEffectOption(text: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun <T> EffectChoice(
    selected: T,
    choices: List<T>,
    title: String,
    label: @Composable (T) -> String,
    onChange: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(title, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { open = true }) {
            Text(label(selected))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(label(choice)) }, onClick = {
                    onChange(choice)
                    open = false
                })
            }
        }
    }
}

@Composable
private fun scopeLabel(scope: EffectValueScope) = stringResource(when (scope) {
    EffectValueScope.DICE_ONLY -> R.string.effects_scope_dice
    EffectValueScope.MODIFIERS_ONLY -> R.string.effects_scope_modifiers
    EffectValueScope.TOTAL -> R.string.effects_scope_total
})

@Composable
private fun sourceLabel(source: EffectValueSource) = stringResource(when (source) {
    EffectValueSource.PART -> R.string.effects_source_part
    EffectValueSource.ROLL -> R.string.effects_source_roll
    EffectValueSource.VARIABLE -> R.string.effects_source_variable
})

@Composable
private fun actionLabel(action: EffectActionType) = stringResource(when (action) {
    EffectActionType.ADD -> R.string.effects_action_add
    EffectActionType.SUBTRACT -> R.string.effects_action_subtract
    EffectActionType.MULTIPLY -> R.string.effects_action_multiply
    EffectActionType.REPLACE -> R.string.effects_action_replace
    EffectActionType.REROLL -> R.string.effects_action_reroll
    EffectActionType.ROLL_AFTER -> R.string.effects_action_roll_after
})

@Composable
private fun EffectConditionEditorV2(
    condition: EffectCondition,
    parts: List<RollSubgroup>,
    variableNames: List<String>,
    onChange: (EffectCondition) -> Unit,
) {
    val availableVariables = variableNames
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EffectChoice(condition.source, EffectValueSource.entries,
                stringResource(R.string.effects_source), { sourceLabel(it) }) { chosen ->
                onChange(condition.copy(
                    source = chosen,
                    partId = if (chosen == EffectValueSource.PART)
                        (condition.partId ?: parts.firstOrNull()?.id) else null,
                    variableName = if (chosen == EffectValueSource.VARIABLE)
                        (condition.variableName ?: "level") else null,
                    scope = if (chosen == EffectValueSource.VARIABLE) EffectValueScope.TOTAL else condition.scope,
                ))
            }
            if (condition.source != EffectValueSource.VARIABLE) {
                EffectChoice(condition.scope, EffectValueScope.entries,
                    stringResource(R.string.effects_scope), { scopeLabel(it) }) {
                    onChange(condition.copy(scope = it))
                }
            }
        }
        if (condition.source == EffectValueSource.PART && parts.isNotEmpty()) {
            EffectChoice(condition.partId ?: parts.first().id, parts.map { it.id },
                stringResource(R.string.effects_target), { id ->
                    parts.firstOrNull { it.id == id }?.name?.ifBlank {
                        stringResource(R.string.roll_subgroups)
                    } ?: stringResource(R.string.effects_missing_part)
                }) {
                onChange(condition.copy(partId = it))
            }
        } else if (condition.source == EffectValueSource.VARIABLE) {
            EffectChoice(condition.variableName ?: "level", availableVariables,
                stringResource(R.string.effects_source_variable), { it }) {
                onChange(condition.copy(variableName = it, scope = EffectValueScope.TOTAL))
            }
        }
        EffectChoice(condition.comparison, EffectComparison.entries,
            stringResource(R.string.effects_comparison), { comparisonSymbol(it) }) {
            onChange(condition.copy(comparison = it))
        }
        OutlinedTextField(
            value = PartReferenceAliases.display(condition.threshold, parts),
            onValueChange = { onChange(condition.copy(threshold = it)) },
            label = { Text(stringResource(R.string.effects_threshold)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun comparisonSymbol(comparison: EffectComparison): String = when (comparison) {
    EffectComparison.GREATER_OR_EQUAL -> "≥"
    EffectComparison.LESS_OR_EQUAL -> "≤"
    EffectComparison.GREATER -> ">"
    EffectComparison.LESS -> "<"
    EffectComparison.EQUAL -> "="
    EffectComparison.NOT_EQUAL -> "≠"
}

@Composable
private fun EffectActionEditorV2(
    action: EffectAction,
    parts: List<RollSubgroup>,
    onRemove: () -> Unit,
    onChange: (EffectAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.effects_action), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
        EffectChoice(action.kind, EffectActionType.entries,
            stringResource(R.string.effects_action), { actionLabel(it) }) { kind ->
            onChange(action.copy(
                kind = kind,
                expression = when {
                    kind == EffectActionType.REROLL -> ""
                    kind == EffectActionType.ROLL_AFTER -> "1d6"
                    action.expression.isBlank() -> "1"
                    else -> action.expression
                },
            ))
        }
        if (parts.isNotEmpty()) {
            EffectChoice(action.targetPartId ?: parts.first().id, parts.map { it.id },
                stringResource(R.string.effects_target), { id ->
                    parts.firstOrNull { it.id == id }?.name?.ifBlank {
                        stringResource(R.string.roll_subgroups)
                    } ?: stringResource(R.string.effects_missing_part)
                }) { onChange(action.copy(targetPartId = it)) }
        }
        EffectChoice(action.scope, EffectValueScope.entries,
            stringResource(R.string.effects_scope), { scopeLabel(it) }) {
            onChange(action.copy(scope = it))
        }
        OutlinedTextField(
            value = PartReferenceAliases.display(action.expression, parts),
            onValueChange = { onChange(action.copy(expression = it)) },
            label = { Text(stringResource(if (action.kind == EffectActionType.REROLL ||
                action.kind == EffectActionType.ROLL_AFTER)
                R.string.effects_dice_expression else R.string.effects_formula)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}
