package com.keyserdsoze.dicethrower

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.model.AppData

/** Unified, opt-in preview for shared URLs and SAF JSON files. */
@Composable
internal fun CharacterImportPreviewDialog(
    incoming: AppData,
    onDismiss: () -> Unit,
    onImport: (name: String, level: Int) -> Unit,
) {
    val character = incoming.characters.single()
    var name by remember(incoming) { mutableStateOf(character.name) }
    var levelText by remember(incoming) { mutableStateOf(character.level.toString()) }
    val parsedLevel = levelText.toIntOrNull()
    val isValid = name.trim().isNotEmpty() && name.trim().length <= 160 &&
        parsedLevel != null && parsedLevel in 1..9999
    val levelLabel = stringResource(R.string.level)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_character_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(52.dp).background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(14.dp),
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    character.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Column {
                                Text(character.name, fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium)
                                Text("$levelLabel ${character.level}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (character.tag.isNotBlank()) {
                            Text(character.tag, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "${incoming.groups.size} ${stringResource(R.string.groups)} · " +
                                "${incoming.rolls.size} ${stringResource(R.string.rolls)}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    isError = name.trim().isEmpty() || name.trim().length > 160,
                    label = { Text(stringResource(R.string.character_name)) },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = levelText,
                    onValueChange = { levelText = it },
                    singleLine = true,
                    isError = parsedLevel == null || parsedLevel !in 1..9999,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(levelLabel) },
                )
                Text(
                    stringResource(
                        R.string.import_character_message,
                        character.name,
                        character.level,
                        incoming.rolls.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (incoming.rolls.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(stringResource(R.string.rolls),
                                style = MaterialTheme.typography.titleSmall)
                            incoming.rolls.sortedBy { it.order }.take(12).forEach { roll ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(roll.name, modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium)
                                    if (roll.minimumLevel > 1) {
                                        Text("$levelLabel ${roll.minimumLevel}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                val parts = roll.subgroups.map { it.name }.filter { it.isNotBlank() }
                                if (parts.isNotEmpty()) {
                                    Text(parts.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = isValid, onClick = {
                parsedLevel?.let { onImport(name.trim(), it) }
            }) { Text(stringResource(R.string.import_character_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
