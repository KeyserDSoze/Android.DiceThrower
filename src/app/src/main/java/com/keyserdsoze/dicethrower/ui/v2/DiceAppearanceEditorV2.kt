package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.dice.DiceAppearanceResolver
import com.keyserdsoze.dicethrower.dice.DiceExpression
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DiceAppearanceMode
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.RollDiceAppearance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiceAppearanceEditorV2(
    character: CharacterProfile,
    diceStyles: List<DiceStyle>,
    resolvedExpression: String?,
    appearance: RollDiceAppearance,
    onAppearanceChanged: (RollDiceAppearance) -> Unit,
) {
    val styles = diceStyles.filter { it.characterId == character.id }.sortedBy { it.order }
    val slots = remember(resolvedExpression) {
        resolvedExpression?.let { expression ->
            runCatching { DiceAppearanceResolver.slotsFor(DiceExpression.parse(expression)) }.getOrDefault(emptyList())
        }.orEmpty()
    }
    var modeMenuExpanded by remember { mutableStateOf(false) }

    val modeLabel = when (appearance.mode) {
        DiceAppearanceMode.CHARACTER_DEFAULT -> stringResource(R.string.appearance_character_default)
        DiceAppearanceMode.UNIFORM -> stringResource(R.string.appearance_uniform)
        DiceAppearanceMode.PER_DIE -> stringResource(R.string.appearance_per_die)
        DiceAppearanceMode.RANDOM_UNIFORM -> stringResource(R.string.appearance_random_uniform)
        DiceAppearanceMode.RANDOM_PER_DIE -> stringResource(R.string.appearance_random_per_die)
    }
    val modeHelp = when (appearance.mode) {
        DiceAppearanceMode.CHARACTER_DEFAULT -> stringResource(R.string.appearance_character_default_help)
        DiceAppearanceMode.UNIFORM -> stringResource(R.string.appearance_uniform_help)
        DiceAppearanceMode.PER_DIE -> stringResource(R.string.appearance_per_die_help)
        DiceAppearanceMode.RANDOM_UNIFORM -> stringResource(R.string.appearance_random_uniform_help)
        DiceAppearanceMode.RANDOM_PER_DIE -> stringResource(R.string.appearance_random_per_die_help)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column {
                Text(stringResource(R.string.dice_appearance), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.dice_appearance_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
                OutlinedButton(onClick = { modeMenuExpanded = true }) {
                    Text(modeLabel)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = modeMenuExpanded,
                    onDismissRequest = { modeMenuExpanded = false },
                ) {
                    DiceAppearanceMode.entries.forEach { mode ->
                        val needsStyles = mode != DiceAppearanceMode.CHARACTER_DEFAULT
                        DropdownMenuItem(
                            enabled = !needsStyles || styles.isNotEmpty(),
                            text = { Text(appearanceModeLabel(mode)) },
                            onClick = {
                                modeMenuExpanded = false
                                val selectedStyleId = appearance.styleId
                                    ?.takeIf { id -> styles.any { it.id == id } }
                                    ?: character.defaultDiceStyleId?.takeIf { id -> styles.any { it.id == id } }
                                    ?: styles.firstOrNull()?.id
                                onAppearanceChanged(
                                    appearance.copy(
                                        mode = mode,
                                        styleId = if (mode == DiceAppearanceMode.UNIFORM) selectedStyleId else appearance.styleId,
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            Text(
                modeHelp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (styles.isEmpty()) {
                Text(
                    stringResource(R.string.no_dice_styles),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                when (appearance.mode) {
                    DiceAppearanceMode.CHARACTER_DEFAULT -> Unit
                    DiceAppearanceMode.UNIFORM -> AppearanceStylePicker(
                        styles = styles,
                        character = character,
                        selectedStyleId = appearance.styleId,
                        allowCharacterDefault = false,
                        onSelected = { styleId ->
                            styleId?.let { onAppearanceChanged(appearance.copy(styleId = it)) }
                        },
                    )
                    DiceAppearanceMode.PER_DIE -> {
                        HorizontalDivider()
                        slots.forEach { slot ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(
                                        R.string.dice_slot_label,
                                        slot.componentIndex + 1,
                                        slot.sides,
                                        slot.dieIndex + 1,
                                    ),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                AppearanceStylePicker(
                                    styles = styles,
                                    character = character,
                                    selectedStyleId = appearance.perDieStyleIds[slot.key],
                                    allowCharacterDefault = true,
                                    onSelected = { styleId ->
                                        val updated = appearance.perDieStyleIds.toMutableMap()
                                        if (styleId == null) updated.remove(slot.key) else updated[slot.key] = styleId
                                        onAppearanceChanged(appearance.copy(perDieStyleIds = updated))
                                    },
                                )
                            }
                        }
                    }
                    DiceAppearanceMode.RANDOM_UNIFORM,
                    DiceAppearanceMode.RANDOM_PER_DIE,
                    -> RandomStylePool(
                        styles = styles,
                        selectedStyleIds = appearance.randomStyleIds,
                        onSelectedStyleIdsChanged = { onAppearanceChanged(appearance.copy(randomStyleIds = it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun appearanceModeLabel(mode: DiceAppearanceMode): String = when (mode) {
    DiceAppearanceMode.CHARACTER_DEFAULT -> stringResource(R.string.appearance_character_default)
    DiceAppearanceMode.UNIFORM -> stringResource(R.string.appearance_uniform)
    DiceAppearanceMode.PER_DIE -> stringResource(R.string.appearance_per_die)
    DiceAppearanceMode.RANDOM_UNIFORM -> stringResource(R.string.appearance_random_uniform)
    DiceAppearanceMode.RANDOM_PER_DIE -> stringResource(R.string.appearance_random_per_die)
}

@Composable
private fun AppearanceStylePicker(
    styles: List<DiceStyle>,
    character: CharacterProfile,
    selectedStyleId: String?,
    allowCharacterDefault: Boolean,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = styles.firstOrNull { it.id == selectedStyleId }?.name
        ?: if (allowCharacterDefault) stringResource(R.string.appearance_character_default) else styles.first().name

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedName)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowCharacterDefault) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.appearance_character_default)) },
                    onClick = {
                        expanded = false
                        onSelected(null)
                    },
                )
            }
            styles.forEach { style ->
                val suffix = if (style.id == character.defaultDiceStyleId) {
                    " · ${stringResource(R.string.default_style)}"
                } else ""
                DropdownMenuItem(
                    text = { Text(style.name + suffix) },
                    onClick = {
                        expanded = false
                        onSelected(style.id)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RandomStylePool(
    styles: List<DiceStyle>,
    selectedStyleIds: List<String>,
    onSelectedStyleIdsChanged: (List<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.random_style_pool), fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.random_style_pool_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilterChip(
            selected = selectedStyleIds.isEmpty(),
            onClick = { onSelectedStyleIdsChanged(emptyList()) },
            label = { Text(stringResource(R.string.all_character_styles)) },
        )
        styles.forEach { style ->
            FilterChip(
                selected = style.id in selectedStyleIds,
                onClick = {
                    val updated = if (selectedStyleIds.isEmpty()) {
                        listOf(style.id)
                    } else if (style.id in selectedStyleIds) {
                        selectedStyleIds - style.id
                    } else {
                        selectedStyleIds + style.id
                    }
                    onSelectedStyleIdsChanged(updated)
                },
                label = { Text(style.name) },
            )
        }
    }
}
