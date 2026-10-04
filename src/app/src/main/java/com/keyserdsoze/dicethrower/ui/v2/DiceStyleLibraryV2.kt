package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.data.DiceStyleDataOperations
import com.keyserdsoze.dicethrower.dice.DiceAppearanceResolver
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DiceMaterial
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.ui.dice3d.DiceStylePreview3D
import java.util.Locale
import java.util.UUID

@Composable
internal fun DiceStyleLibraryV2(
    character: CharacterProfile,
    data: AppData,
    onDataChanged: (AppData) -> Unit,
) {
    val styles = data.diceStyles.filter { it.characterId == character.id }.sortedBy { it.order }
    var creating by remember(character.id) { mutableStateOf(false) }
    var editingStyleId by remember(character.id) { mutableStateOf<String?>(null) }
    var deletingStyleId by remember(character.id) { mutableStateOf<String?>(null) }
    val copySuffix = stringResource(R.string.copy_suffix)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dice_styles),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(R.string.dice_styles_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { creating = true }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.new_dice_style))
            }
        }

        if (styles.isEmpty()) {
            PremiumCard(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.no_dice_styles),
                    modifier = Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        styles.forEachIndexed { index, style ->
            val isDefault = character.defaultDiceStyleId == style.id
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DiceStyleSwatch(style)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(style.name, fontWeight = FontWeight.Bold)
                                if (isDefault) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.default_style),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                            Text(
                                text = materialLabel(style.material),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            enabled = index > 0,
                            onClick = {
                                onDataChanged(DiceStyleDataOperations.moveStyle(data, character.id, style.id, -1))
                            },
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
                        }
                        IconButton(
                            enabled = index < styles.lastIndex,
                            onClick = {
                                onDataChanged(DiceStyleDataOperations.moveStyle(data, character.id, style.id, 1))
                            },
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            enabled = !isDefault,
                            onClick = {
                                onDataChanged(DiceStyleDataOperations.setDefaultStyle(data, character.id, style.id))
                            },
                        ) {
                            Icon(
                                imageVector = if (isDefault) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = stringResource(R.string.set_default_style),
                            )
                        }
                        IconButton(
                            onClick = {
                                val copyName = nextCopyName(styles.map { it.name }, style.name, copySuffix)
                                onDataChanged(
                                    DiceStyleDataOperations.duplicateStyle(
                                        data = data,
                                        styleId = style.id,
                                        newId = UUID.randomUUID().toString(),
                                        newName = copyName,
                                    ),
                                )
                            },
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.duplicate))
                        }
                        IconButton(onClick = { editingStyleId = style.id }) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit))
                        }
                        IconButton(onClick = { deletingStyleId = style.id }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        DiceStyleEditorDialog(
            existing = null,
            characterId = character.id,
            existingNames = styles.map { it.name },
            onDismiss = { creating = false },
            onSave = { style ->
                onDataChanged(DiceStyleDataOperations.createStyle(data, style))
                creating = false
            },
        )
    }

    editingStyleId?.let { id ->
        styles.firstOrNull { it.id == id }?.let { existing ->
            DiceStyleEditorDialog(
                existing = existing,
                characterId = character.id,
                existingNames = styles.filterNot { it.id == id }.map { it.name },
                onDismiss = { editingStyleId = null },
                onSave = { style ->
                    onDataChanged(DiceStyleDataOperations.updateStyle(data, style))
                    editingStyleId = null
                },
            )
        }
    }

    deletingStyleId?.let { id ->
        styles.firstOrNull { it.id == id }?.let { style ->
            val usage = DiceStyleDataOperations.usage(data, id)
            AlertDialog(
                onDismissRequest = { deletingStyleId = null },
                title = { Text(stringResource(R.string.delete_dice_style_title, style.name)) },
                text = {
                    Text(
                        stringResource(
                            if (usage.isInUse) R.string.delete_dice_style_in_use else R.string.delete_dice_style_unused,
                        ),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDataChanged(DiceStyleDataOperations.deleteStyle(data, id))
                            deletingStyleId = null
                        },
                    ) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { deletingStyleId = null }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }
}

@Composable
private fun DiceStyleSwatch(style: DiceStyle) {
    Box(Modifier.size(44.dp)) {
        Surface(
            modifier = Modifier.size(32.dp).align(Alignment.TopStart),
            shape = CircleShape,
            color = Color(style.primaryColorArgb),
            shadowElevation = 2.dp,
        ) {}
        Surface(
            modifier = Modifier.size(28.dp).align(Alignment.BottomEnd),
            shape = CircleShape,
            color = Color(style.secondaryColorArgb),
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
            shadowElevation = 2.dp,
        ) {}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiceStyleEditorDialog(
    existing: DiceStyle?,
    characterId: String,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (DiceStyle) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var material by remember(existing?.id) { mutableStateOf(existing?.material ?: DiceMaterial.GLOSSY_RESIN) }
    var primaryHex by remember(existing?.id) {
        mutableStateOf(formatDiceColor(existing?.primaryColorArgb ?: DiceAppearanceResolver.defaultPrimaryColorArgb))
    }
    var secondaryHex by remember(existing?.id) {
        mutableStateOf(formatDiceColor(existing?.secondaryColorArgb ?: DiceAppearanceResolver.defaultSecondaryColorArgb))
    }
    val primary = parseDiceColor(primaryHex)
    val secondary = parseDiceColor(secondaryHex)
    val normalizedName = name.trim()
    val duplicateName = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val valid = normalizedName.isNotBlank() && !duplicateName && primary != null && secondary != null
    val previewStyle = DiceStyle(
        id = existing?.id ?: "preview",
        characterId = characterId,
        name = normalizedName,
        material = material,
        primaryColorArgb = primary ?: DiceAppearanceResolver.defaultPrimaryColorArgb,
        secondaryColorArgb = secondary ?: DiceAppearanceResolver.defaultSecondaryColorArgb,
        order = existing?.order ?: 0,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.new_dice_style else R.string.edit_dice_style)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DiceStylePreview3D(
                    style = previewStyle,
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                )
                Text(
                    text = stringResource(R.string.live_preview),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.style_name)) },
                    supportingText = {
                        if (duplicateName) Text(stringResource(R.string.duplicate_style_name))
                    },
                    isError = duplicateName,
                    singleLine = true,
                )
                Text(stringResource(R.string.dice_material), fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DiceMaterial.entries.forEach { candidate ->
                        FilterChip(
                            selected = material == candidate,
                            onClick = { material = candidate },
                            label = { Text(materialLabel(candidate)) },
                        )
                    }
                }
                DiceColorEditor(
                    label = stringResource(R.string.primary_color),
                    value = primaryHex,
                    onValueChange = { primaryHex = it },
                )
                DiceColorEditor(
                    label = stringResource(R.string.secondary_color),
                    value = secondaryHex,
                    onValueChange = { secondaryHex = it },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        previewStyle.copy(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = normalizedName,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiceColorEditor(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val parsed = parseDiceColor(value)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { input -> onValueChange(input.take(7).uppercase(Locale.ROOT)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.dice_color_example)) },
            supportingText = { if (parsed == null) Text(stringResource(R.string.invalid_dice_color)) },
            isError = parsed == null,
            singleLine = true,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            diceColorPresets.forEach { colorArgb ->
                val selected = parsed == colorArgb
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onValueChange(formatDiceColor(colorArgb)) },
                ) {
                    Surface(
                        modifier = Modifier.size(22.dp).align(Alignment.Center),
                        shape = CircleShape,
                        color = Color(colorArgb),
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun materialLabel(material: DiceMaterial): String = stringResource(
    when (material) {
        DiceMaterial.GLOSSY_RESIN -> R.string.material_glossy_resin
        DiceMaterial.MATTE_RESIN -> R.string.material_matte_resin
        DiceMaterial.METAL -> R.string.material_metal
        DiceMaterial.GEMSTONE -> R.string.material_gemstone
    },
)

internal fun parseDiceColor(raw: String): Int? {
    val hex = raw.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { it.digitToIntOrNull(16) == null }) return null
    return (0xFF000000L or hex.toLong(16)).toInt()
}

internal fun formatDiceColor(argb: Int): String = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)

internal fun nextCopyName(existingNames: List<String>, sourceName: String, suffix: String): String {
    val base = "$sourceName $suffix"
    if (existingNames.none { it.equals(base, ignoreCase = true) }) return base
    var index = 2
    while (existingNames.any { it.equals("$base $index", ignoreCase = true) }) index += 1
    return "$base $index"
}

private val diceColorPresets = listOf(
    0xFF2563EB.toInt(),
    0xFF7C3AED.toInt(),
    0xFFDB2777.toInt(),
    0xFFDC2626.toInt(),
    0xFFEA580C.toInt(),
    0xFFF6C453.toInt(),
    0xFF16A34A.toInt(),
    0xFF0891B2.toInt(),
    0xFF64748B.toInt(),
    0xFFE2E8F0.toInt(),
)
