package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DiceStyle

/**
 * Assign appearances on the character; editing the actual look requires an explicit Add action.
 * The catalog itself must not decide where a die is used.
 */
@Composable
internal fun CharacterDiceStyleSectionV2(
    character: CharacterProfile,
    data: AppData,
    onDataChanged: (AppData) -> Unit,
) {
    val styles = data.diceStyles.filter { it.characterId == character.id }.sortedBy { it.order }
    var expanded by remember(character.id) { mutableStateOf(false) }
    var adding by remember(character.id) { mutableStateOf(false) }
    val fallbackName = stringResource(R.string.dice_character_style_system_default)
    val defaultName = styles.firstOrNull { it.id == character.defaultDiceStyleId }?.name ?: fallbackName

    PremiumCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .testTag("dice-character-style-toggle")
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.dice_styles),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.dice_character_styles_summary, defaultName, styles.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.dice_styles),
                )
            }
            if (expanded) {
                DiceStyleAssignmentChoiceV2(
                    label = stringResource(R.string.default_style),
                    selection = defaultName,
                    fallback = fallbackName,
                    styles = styles,
                    onSelect = { id ->
                        onDataChanged(data.copy(characters = data.characters.map {
                            if (it.id == character.id) it.copy(defaultDiceStyleId = id) else it
                        }))
                    },
                )
                DiceStyleAssignmentChoiceV2(
                    label = stringResource(R.string.cinematic_candidate_second),
                    selection = styles.firstOrNull { it.id == character.secondaryDiceStyleId }?.name
                        ?: stringResource(R.string.secondary_dice_style_auto),
                    fallback = stringResource(R.string.secondary_dice_style_auto),
                    styles = styles,
                    onSelect = { id ->
                        onDataChanged(data.copy(characters = data.characters.map {
                            if (it.id == character.id) it.copy(secondaryDiceStyleId = id) else it
                        }))
                    },
                )
                styles.forEach { style ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("🎲", style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.weight(1f)) {
                            Text(style.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.dice_catalog_custom_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { adding = !adding },
                    modifier = Modifier.fillMaxWidth().testTag("dice-character-add-style"),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.dice_character_styles_add))
                }
                if (adding) {
                    DiceStyleLibraryV2(character = character, data = data, onDataChanged = onDataChanged)
                }
            }
        }
    }
}

@Composable
private fun DiceStyleAssignmentChoiceV2(
    label: String,
    selection: String,
    fallback: String,
    styles: List<DiceStyle>,
    onSelect: (String?) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(onClick = { showMenu = true }) {
                Text(selection)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text(fallback) }, onClick = {
                    showMenu = false
                    onSelect(null)
                })
                styles.forEach { style ->
                    DropdownMenuItem(text = { Text(style.name) }, onClick = {
                        showMenu = false
                        onSelect(style.id)
                    })
                }
            }
        }
    }
}
