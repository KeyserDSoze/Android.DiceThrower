package com.keyserdsoze.dicethrower.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.AppLanguageOption
import com.keyserdsoze.dicethrower.AppLocaleManager
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.data.CharacterImageAssetStore
import com.keyserdsoze.dicethrower.data.LocalStore
import com.keyserdsoze.dicethrower.dice.DiceAppearanceResolver
import com.keyserdsoze.dicethrower.dice.DiceExpression
import com.keyserdsoze.dicethrower.dice.DiceRollResult
import com.keyserdsoze.dicethrower.dice.DiceRollVisualBus
import com.keyserdsoze.dicethrower.dice.RollFormulaResolver
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterImageRef
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollButtonPosition
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollGroup
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.RollLog
import com.keyserdsoze.dicethrower.model.ThemeMode
import com.keyserdsoze.dicethrower.sensor.ShakeDetector
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlin.random.Random

private enum class Route { CHARACTERS, CHARACTER, ROLL, SETTINGS, LOGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiceThrowerApp(
    store: LocalStore,
    settings: AppSettings,
    selectedLanguage: String,
    languageOptions: List<AppLanguageOption>,
    onSettingsChanged: (AppSettings) -> Unit,
    onLanguageChanged: (String) -> Unit,
) {
    var data by remember { mutableStateOf(store.loadData()) }
    var route by remember { mutableStateOf(Route.CHARACTERS) }
    var selectedCharacterId by remember { mutableStateOf<String?>(null) }
    var selectedRollId by remember { mutableStateOf<String?>(null) }
    var editMode by remember { mutableStateOf(false) }

    fun persist(updated: AppData) {
        data = store.saveData(updated)
    }

    when (route) {
        Route.CHARACTERS -> CharactersScreen(
            data = data,
            onOpenCharacter = { id ->
                selectedCharacterId = id
                editMode = false
                route = Route.CHARACTER
            },
            onAddCharacter = { character ->
                persist(data.copy(characters = data.characters + character))
            },
            onSettings = { route = Route.SETTINGS },
        )

        Route.CHARACTER -> {
            val character = data.characters.firstOrNull { it.id == selectedCharacterId }
            if (character == null) {
                route = Route.CHARACTERS
            } else {
                CharacterScreen(
                    character = character,
                    data = data,
                    editMode = editMode,
                    onBack = { route = Route.CHARACTERS },
                    onToggleMode = { editMode = !editMode },
                    onOpenLogs = { route = Route.LOGS },
                    onOpenRoll = { rollId ->
                        selectedRollId = rollId
                        route = Route.ROLL
                    },
                    onDataChanged = ::persist,
                )
            }
        }

        Route.ROLL -> {
            val character = data.characters.firstOrNull { it.id == selectedCharacterId }
            val roll = data.rolls.firstOrNull { it.id == selectedRollId }
            if (character == null || roll == null) {
                route = Route.CHARACTER
            } else {
                RollScreen(
                    character = character,
                    modifiers = data.modifiers.filter { it.characterId == character.id },
                    diceStyles = data.diceStyles,
                    roll = roll,
                    settings = settings,
                    onBack = { route = Route.CHARACTER },
                    onLogged = { log ->
                        val newLogs = (data.logs + log).let { logs ->
                            if (settings.logRetention == 0) logs
                            else logs.sortedByDescending { it.timestamp }.take(settings.logRetention)
                        }
                        persist(data.copy(logs = newLogs))
                    },
                )
            }
        }

        Route.SETTINGS -> SettingsScreen(
            settings = settings,
            selectedLanguage = selectedLanguage,
            languageOptions = languageOptions,
            onSettingsChanged = onSettingsChanged,
            onLanguageChanged = onLanguageChanged,
            onBack = { route = Route.CHARACTERS },
        )

        Route.LOGS -> {
            val character = data.characters.firstOrNull { it.id == selectedCharacterId }
            if (character == null) {
                route = Route.CHARACTERS
            } else {
                LogsScreen(
                    character = character,
                    logs = data.logs.filter { it.characterId == character.id }
                        .sortedByDescending { it.timestamp },
                    onBack = { route = Route.CHARACTER },
                    onClear = {
                        persist(data.copy(logs = data.logs.filterNot { it.characterId == character.id }))
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharactersScreen(
    data: AppData,
    onOpenCharacter: (String) -> Unit,
    onAddCharacter: (CharacterProfile) -> Unit,
    onSettings: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.characters)) },
                actions = {
                    TextButton(onClick = onSettings) {
                        Text(stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        val characters = data.characters.sortedBy { it.order }
        if (characters.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.no_characters))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(characters, key = { it.id }) { character ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onOpenCharacter(character.id)
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CharacterAvatar(character)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    character.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (character.tag.isNotBlank()) {
                                    Text(
                                        character.tag,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        CharacterDialog(
            nextOrder = data.characters.size,
            onDismiss = { showAdd = false },
            onCreate = {
                onAddCharacter(it)
                showAdd = false
            },
        )
    }
}

@Composable
private fun CharacterAvatar(character: CharacterProfile) {
    val context = LocalContext.current
    val bitmap = remember(character.image, character.imageUri) {
        val portable = character.image?.let { ref ->
            CharacterImageAssetStore(context).loadVerified(ref)?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
        portable ?: character.imageUri?.let { raw ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(raw)).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = character.name,
            modifier = Modifier.size(64.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                character.name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
private fun CharacterDialog(
    nextOrder: Int,
    onDismiss: () -> Unit,
    onCreate: (CharacterProfile) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var levelText by remember { mutableStateOf("1") }
    var image by remember { mutableStateOf<CharacterImageRef?>(null) }
    var imageImportFailed by remember { mutableStateOf(false) }
    val imageAssetStore = remember(context) { CharacterImageAssetStore(context) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { imageAssetStore.importFromUri(uri) }
                .onSuccess {
                    image = it
                    imageImportFailed = false
                }
                .onFailure { imageImportFailed = true }
        }
    }

    val level = levelText.toIntOrNull()
    val valid = name.isNotBlank() && level != null && level >= 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_character)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.character_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text(stringResource(R.string.character_tag)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = levelText,
                    onValueChange = { levelText = it.filter { char -> char.isDigit() } },
                    label = { Text(stringResource(R.string.level)) },
                    singleLine = true,
                )
                OutlinedButton(onClick = { imageLauncher.launch(arrayOf("image/*")) }) {
                    Text(
                        if (image == null) stringResource(R.string.choose_image)
                        else stringResource(R.string.image_selected),
                    )
                }
                if (imageImportFailed) {
                    Text(
                        stringResource(R.string.image_import_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onCreate(
                        CharacterProfile(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            image = image,
                            tag = tag.trim(),
                            level = level ?: 1,
                            order = nextOrder,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterScreen(
    character: CharacterProfile,
    data: AppData,
    editMode: Boolean,
    onBack: () -> Unit,
    onToggleMode: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenRoll: (String) -> Unit,
    onDataChanged: (AppData) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val levelLabel = stringResource(R.string.level)
                        Text(character.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                if (character.tag.isNotBlank()) {
                                    append(character.tag)
                                    append(" · ")
                                }
                                append(levelLabel)
                                append(" ")
                                append(character.level)
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(onClick = onOpenLogs) { Text(stringResource(R.string.logs)) }
                    TextButton(onClick = onToggleMode) {
                        Text(stringResource(if (editMode) R.string.use else R.string.edit))
                    }
                },
            )
        },
    ) { padding ->
        if (editMode) {
            EditCharacterContent(
                character = character,
                data = data,
                onDataChanged = onDataChanged,
                modifier = Modifier.padding(padding),
            )
        } else {
            DashboardContent(
                character = character,
                data = data,
                onOpenRoll = onOpenRoll,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

private sealed interface DashboardOrderItem {
    val key: String
    val order: Int

    data class GroupItem(val group: RollGroup) : DashboardOrderItem {
        override val key: String = "group-${group.id}"
        override val order: Int = group.order
    }

    data class RollItem(val roll: RollDefinition) : DashboardOrderItem {
        override val key: String = "roll-${roll.id}"
        override val order: Int = roll.order
    }
}

private fun dashboardItems(
    groups: List<RollGroup>,
    ungroupedRolls: List<RollDefinition>,
): List<DashboardOrderItem> =
    (groups.map { DashboardOrderItem.GroupItem(it) } +
        ungroupedRolls.map { DashboardOrderItem.RollItem(it) })
        .sortedWith(compareBy<DashboardOrderItem> { it.order }.thenBy { it.key })

@Composable
private fun DashboardContent(
    character: CharacterProfile,
    data: AppData,
    onOpenRoll: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val groups = data.groups.filter { it.characterId == character.id }.sortedBy { it.order }
    val activeRolls = data.rolls.filter { it.characterId == character.id && it.enabled }
    val ungrouped = activeRolls.filter { it.groupId == null }
    val visibleGroups = groups.filter { group -> activeRolls.any { it.groupId == group.id } }
    val topLevel = dashboardItems(visibleGroups, ungrouped)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(topLevel, key = { it.key }) { item ->
            when (item) {
                is DashboardOrderItem.GroupItem -> {
                    val group = item.group
                    val groupRolls = activeRolls
                        .filter { it.groupId == group.id }
                        .sortedBy { it.order }

                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            expanded[group.id] = !(expanded[group.id] ?: false)
                        },
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(group.name, style = MaterialTheme.typography.titleMedium)
                            if (expanded[group.id] == true) {
                                Spacer(Modifier.height(10.dp))
                                groupRolls.forEach { roll ->
                                    RollLaunchRow(roll, onOpenRoll)
                                }
                            } else {
                                Text(
                                    stringResource(R.string.tap_to_expand),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                is DashboardOrderItem.RollItem -> {
                    val roll = item.roll
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenRoll(roll.id) },
                    ) {
                        RollLaunchRow(roll, onOpenRoll)
                    }
                }
            }
        }

        if (topLevel.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_rolls),
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun RollLaunchRow(
    roll: RollDefinition,
    onOpenRoll: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpenRoll(roll.id) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(roll.name, fontWeight = FontWeight.SemiBold)
            Text(
                roll.expression,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun EditCharacterContent(
    character: CharacterProfile,
    data: AppData,
    onDataChanged: (AppData) -> Unit,
    modifier: Modifier = Modifier,
) {
    var addGroup by remember { mutableStateOf(false) }
    var addRoll by remember { mutableStateOf(false) }
    var addModifier by remember { mutableStateOf(false) }
    var editingModifierId by remember { mutableStateOf<String?>(null) }
    var editingRollId by remember { mutableStateOf<String?>(null) }
    var addRuleForRollId by remember { mutableStateOf<String?>(null) }

    val groups = data.groups.filter { it.characterId == character.id }.sortedBy { it.order }
    val rolls = data.rolls.filter { it.characterId == character.id }
    val modifiers = data.modifiers.filter { it.characterId == character.id }.sortedBy { it.order }
    val ungroupedRolls = rolls.filter { it.groupId == null }
    val topLevel = dashboardItems(groups, ungroupedRolls)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.character_progression),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(stringResource(R.string.level), fontWeight = FontWeight.SemiBold)
                        Text(
                            character.level.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = character.level > 1,
                            onClick = {
                                onDataChanged(
                                    data.copy(
                                        characters = data.characters.map {
                                            if (it.id == character.id) {
                                                it.copy(level = (it.level - 1).coerceAtLeast(1))
                                            } else {
                                                it
                                            }
                                        },
                                    ),
                                )
                            },
                        ) {
                            Text("−")
                        }
                        Button(
                            onClick = {
                                onDataChanged(
                                    data.copy(
                                        characters = data.characters.map {
                                            if (it.id == character.id) {
                                                it.copy(level = it.level + 1)
                                            } else {
                                                it
                                            }
                                        },
                                    ),
                                )
                            },
                        ) {
                            Text("+")
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.modifiers), style = MaterialTheme.typography.titleLarge)
                Button(onClick = { addModifier = true }) {
                    Text(stringResource(R.string.add))
                }
            }
            Text(
                stringResource(R.string.modifier_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(modifiers, key = { "modifier-${it.id}" }) { characterModifier ->
            val used = modifierIsReferenced(characterModifier.name, rolls)
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(characterModifier.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            characterModifier.value.toString(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    TextButton(onClick = { editingModifierId = characterModifier.id }) {
                        Text(stringResource(R.string.edit))
                    }
                    TextButton(
                        enabled = !used,
                        onClick = {
                            onDataChanged(
                                data.copy(
                                    modifiers = data.modifiers.filterNot {
                                        it.id == characterModifier.id
                                    },
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.dashboard), style = MaterialTheme.typography.titleLarge)
        }

        items(topLevel, key = { "dashboard-${it.key}" }) { item ->
            val title = when (item) {
                is DashboardOrderItem.GroupItem -> item.group.name
                is DashboardOrderItem.RollItem -> item.roll.name
            }
            val type = when (item) {
                is DashboardOrderItem.GroupItem -> stringResource(R.string.group)
                is DashboardOrderItem.RollItem -> stringResource(R.string.rolls)
            }

            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Text(
                            type,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SmallOrderButtons(
                        onUp = {
                            onDataChanged(reorderTopLevel(data, character.id, item.key, -1))
                        },
                        onDown = {
                            onDataChanged(reorderTopLevel(data, character.id, item.key, 1))
                        },
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.groups), style = MaterialTheme.typography.titleLarge)
                Button(onClick = { addGroup = true }) { Text(stringResource(R.string.add)) }
            }
        }

        items(groups, key = { "edit-group-${it.id}" }) { group ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(group.name, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            onDataChanged(deleteGroup(data, character.id, group.id))
                        },
                    ) { Text(stringResource(R.string.delete)) }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.rolls), style = MaterialTheme.typography.titleLarge)
                Button(onClick = { addRoll = true }) { Text(stringResource(R.string.add)) }
            }
        }

        items(
            rolls.sortedWith(compareBy<RollDefinition> { it.groupId ?: "" }.thenBy { it.order }),
            key = { "edit-roll-${it.id}" },
        ) { roll ->
            var groupMenu by remember(roll.id) { mutableStateOf(false) }
            val groupName = groups.firstOrNull { it.id == roll.groupId }?.name
                ?: stringResource(R.string.ungrouped)
            val resolved = runCatching {
                RollFormulaResolver.resolve(character, modifiers, roll).expression
            }.getOrNull()

            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(roll.name, fontWeight = FontWeight.SemiBold)
                            Text(roll.expression)
                            if (resolved != null && resolved != roll.expression) {
                                Text(
                                    "${stringResource(R.string.resolved_expression)}: $resolved",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Switch(
                            checked = roll.enabled,
                            onCheckedChange = { enabled ->
                                onDataChanged(
                                    data.copy(
                                        rolls = data.rolls.map {
                                            if (it.id == roll.id) it.copy(enabled = enabled) else it
                                        },
                                    ),
                                )
                            },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { groupMenu = true }) {
                                Text(groupName)
                            }
                            DropdownMenu(
                                expanded = groupMenu,
                                onDismissRequest = { groupMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ungrouped)) },
                                    onClick = {
                                        groupMenu = false
                                        onDataChanged(changeRollGroup(data, character.id, roll.id, null))
                                    },
                                )
                                groups.forEach { group ->
                                    DropdownMenuItem(
                                        text = { Text(group.name) },
                                        onClick = {
                                            groupMenu = false
                                            onDataChanged(changeRollGroup(data, character.id, roll.id, group.id))
                                        },
                                    )
                                }
                            }
                        }

                        if (roll.groupId != null) {
                            SmallOrderButtons(
                                onUp = {
                                    onDataChanged(
                                        data.copy(
                                            rolls = reorderRollsInGroup(
                                                data.rolls,
                                                character.id,
                                                roll.groupId,
                                                roll.id,
                                                -1,
                                            ),
                                        ),
                                    )
                                },
                                onDown = {
                                    onDataChanged(
                                        data.copy(
                                            rolls = reorderRollsInGroup(
                                                data.rolls,
                                                character.id,
                                                roll.groupId,
                                                roll.id,
                                                1,
                                            ),
                                        ),
                                    )
                                },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = { editingRollId = roll.id }) {
                            Text(stringResource(R.string.edit_roll))
                        }
                        TextButton(onClick = { addRuleForRollId = roll.id }) {
                            Text(stringResource(R.string.add_level_rule))
                        }
                        TextButton(
                            onClick = {
                                onDataChanged(
                                    data.copy(
                                        rolls = data.rolls.filterNot { it.id == roll.id },
                                    ),
                                )
                            },
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    }

                    Text(
                        stringResource(R.string.level_scaling),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (roll.levelRules.isEmpty()) {
                        Text(
                            stringResource(R.string.no_level_rules),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        roll.levelRules.forEach { rule ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = when (rule.kind) {
                                        LevelRuleKind.FROM_LEVEL ->
                                            "${stringResource(R.string.from_level)} ${rule.trigger}: +${rule.expression}"
                                        LevelRuleKind.EVERY_LEVELS ->
                                            "${stringResource(R.string.every_levels)} ${rule.trigger}: +${rule.expression}"
                                    },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(
                                    onClick = {
                                        onDataChanged(
                                            data.copy(
                                                rolls = data.rolls.map {
                                                    if (it.id == roll.id) {
                                                        it.copy(
                                                            levelRules = it.levelRules.filterNot {
                                                                existingRule -> existingRule.id == rule.id
                                                            },
                                                        )
                                                    } else {
                                                        it
                                                    }
                                                },
                                            ),
                                        )
                                    },
                                ) {
                                    Text("×")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (addModifier) {
        ModifierDialog(
            existing = null,
            characterId = character.id,
            nextOrder = modifiers.size,
            existingNames = modifiers.map { it.name },
            onDismiss = { addModifier = false },
            onSave = { saved ->
                onDataChanged(data.copy(modifiers = data.modifiers + saved))
                addModifier = false
            },
        )
    }

    editingModifierId?.let { modifierId ->
        modifiers.firstOrNull { it.id == modifierId }?.let { existing ->
            ModifierDialog(
                existing = existing,
                characterId = character.id,
                nextOrder = existing.order,
                existingNames = modifiers.filterNot { it.id == existing.id }.map { it.name },
                onDismiss = { editingModifierId = null },
                onSave = { saved ->
                    onDataChanged(
                        data.copy(
                            modifiers = data.modifiers.map {
                                if (it.id == saved.id) saved else it
                            },
                        ),
                    )
                    editingModifierId = null
                },
            )
        }
    }

    if (addGroup) {
        AddGroupDialog(
            nextOrder = topLevel.size,
            characterId = character.id,
            onDismiss = { addGroup = false },
            onAdd = { group ->
                onDataChanged(data.copy(groups = data.groups + group))
                addGroup = false
            },
        )
    }

    if (addRoll) {
        AddRollDialog(
            character = character,
            modifiers = modifiers,
            characterId = character.id,
            groups = groups,
            nextOrder = rolls.size,
            onDismiss = { addRoll = false },
            onAdd = { roll ->
                val order = if (roll.groupId == null) {
                    topLevel.size
                } else {
                    rolls.count { it.groupId == roll.groupId }
                }
                onDataChanged(data.copy(rolls = data.rolls + roll.copy(order = order)))
                addRoll = false
            },
        )
    }

    editingRollId?.let { rollId ->
        rolls.firstOrNull { it.id == rollId }?.let { existing ->
            EditRollDialog(
                character = character,
                modifiers = modifiers,
                roll = existing,
                onDismiss = { editingRollId = null },
                onSave = { updated ->
                    onDataChanged(
                        data.copy(
                            rolls = data.rolls.map {
                                if (it.id == updated.id) updated else it
                            },
                        ),
                    )
                    editingRollId = null
                },
            )
        }
    }

    addRuleForRollId?.let { rollId ->
        rolls.firstOrNull { it.id == rollId }?.let { existing ->
            AddLevelRuleDialog(
                character = character,
                modifiers = modifiers,
                onDismiss = { addRuleForRollId = null },
                onAdd = { rule ->
                    onDataChanged(
                        data.copy(
                            rolls = data.rolls.map {
                                if (it.id == existing.id) {
                                    it.copy(levelRules = it.levelRules + rule)
                                } else {
                                    it
                                }
                            },
                        ),
                    )
                    addRuleForRollId = null
                },
            )
        }
    }
}

@Composable
private fun SmallOrderButtons(onUp: () -> Unit, onDown: () -> Unit) {
    TextButton(onClick = onUp) { Text("↑") }
    TextButton(onClick = onDown) { Text("↓") }
}

private fun modifierIsReferenced(
    modifierName: String,
    rolls: List<RollDefinition>,
): Boolean {
    val variableRegex = Regex("""\{([^{}]+)\}""")
    return rolls.any { roll ->
        val expressions = listOf(roll.expression) + roll.levelRules.map { it.expression }
        expressions.any { expression ->
            variableRegex.findAll(expression).any { match ->
                match.groupValues[1].trim().equals(modifierName.trim(), ignoreCase = true)
            }
        }
    }
}

private fun reorderTopLevel(
    data: AppData,
    characterId: String,
    itemKey: String,
    direction: Int,
): AppData {
    val groups = data.groups.filter { it.characterId == characterId }
    val ungrouped = data.rolls.filter { it.characterId == characterId && it.groupId == null }
    val items = dashboardItems(groups, ungrouped).toMutableList()
    val index = items.indexOfFirst { it.key == itemKey }
    val target = index + direction
    if (index < 0 || target !in items.indices) return data

    val temp = items[index]
    items[index] = items[target]
    items[target] = temp
    val orderByKey = items.mapIndexed { order, item -> item.key to order }.toMap()

    return data.copy(
        groups = data.groups.map { group ->
            orderByKey["group-${group.id}"]?.let { group.copy(order = it) } ?: group
        },
        rolls = data.rolls.map { roll ->
            if (roll.groupId == null) {
                orderByKey["roll-${roll.id}"]?.let { roll.copy(order = it) } ?: roll
            } else {
                roll
            }
        },
    )
}

private fun reorderRollsInGroup(
    all: List<RollDefinition>,
    characterId: String,
    groupId: String,
    itemId: String,
    direction: Int,
): List<RollDefinition> {
    val local = all
        .filter { it.characterId == characterId && it.groupId == groupId }
        .sortedBy { it.order }
        .toMutableList()
    val index = local.indexOfFirst { it.id == itemId }
    val target = index + direction
    if (index < 0 || target !in local.indices) return all

    val temp = local[index]
    local[index] = local[target]
    local[target] = temp
    val orderById = local.mapIndexed { order, roll -> roll.id to order }.toMap()

    return all.map { roll ->
        orderById[roll.id]?.let { roll.copy(order = it) } ?: roll
    }
}

private fun changeRollGroup(
    data: AppData,
    characterId: String,
    rollId: String,
    newGroupId: String?,
): AppData {
    val roll = data.rolls.firstOrNull { it.id == rollId && it.characterId == characterId }
        ?: return data
    if (roll.groupId == newGroupId) return data

    val otherRolls = data.rolls.filterNot { it.id == rollId }
    val newOrder = if (newGroupId == null) {
        dashboardItems(
            data.groups.filter { it.characterId == characterId },
            otherRolls.filter { it.characterId == characterId && it.groupId == null },
        ).size
    } else {
        otherRolls.count { it.characterId == characterId && it.groupId == newGroupId }
    }

    return data.copy(
        rolls = otherRolls + roll.copy(groupId = newGroupId, order = newOrder),
    )
}

private fun deleteGroup(
    data: AppData,
    characterId: String,
    groupId: String,
): AppData {
    val remainingGroups = data.groups.filterNot { it.id == groupId }
    val existingUngrouped = data.rolls.filter {
        it.characterId == characterId && it.groupId == null
    }
    var nextOrder = dashboardItems(
        remainingGroups.filter { it.characterId == characterId },
        existingUngrouped,
    ).size

    val updatedRolls = data.rolls.map { roll ->
        if (roll.characterId == characterId && roll.groupId == groupId) {
            roll.copy(groupId = null, order = nextOrder++)
        } else {
            roll
        }
    }

    return data.copy(groups = remainingGroups, rolls = updatedRolls)
}

@Composable
private fun AddGroupDialog(
    characterId: String,
    nextOrder: Int,
    onDismiss: () -> Unit,
    onAdd: (RollGroup) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_group)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.group_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onAdd(
                        RollGroup(
                            id = UUID.randomUUID().toString(),
                            characterId = characterId,
                            name = name.trim(),
                            order = nextOrder,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ModifierDialog(
    existing: CharacterModifier?,
    characterId: String,
    nextOrder: Int,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (CharacterModifier) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var valueText by remember(existing?.id) {
        mutableStateOf(existing?.value?.toString() ?: "0")
    }

    val value = valueText.toIntOrNull()
    val normalizedName = name.trim()
    val duplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    val reserved = normalizedName.equals(RollFormulaResolver.LEVEL_VARIABLE, ignoreCase = true)
    val valid = normalizedName.isNotBlank() && value != null && !duplicate && !reserved

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.new_modifier else R.string.edit_modifier,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (existing == null) name = it },
                    enabled = existing == null,
                    label = { Text(stringResource(R.string.modifier_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { input ->
                        valueText = input.filterIndexed { index, char ->
                            char.isDigit() || (char == '-' && index == 0)
                        }
                    },
                    label = { Text(stringResource(R.string.modifier_value)) },
                    singleLine = true,
                    supportingText = {
                        if (duplicate || reserved) {
                            Text(stringResource(R.string.duplicate_modifier))
                        }
                    },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        CharacterModifier(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            characterId = characterId,
                            name = normalizedName,
                            value = value ?: 0,
                            order = existing?.order ?: nextOrder,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun EditRollDialog(
    character: CharacterProfile,
    modifiers: List<CharacterModifier>,
    roll: RollDefinition,
    onDismiss: () -> Unit,
    onSave: (RollDefinition) -> Unit,
) {
    var name by remember(roll.id) { mutableStateOf(roll.name) }
    var expression by remember(roll.id) { mutableStateOf(roll.expression) }
    val validExpression = RollFormulaResolver.validateTemplate(
        expression = expression,
        level = character.level,
        modifiers = modifiers,
    )
    val valid = name.isNotBlank() && validExpression

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_roll)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.roll_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = expression,
                    onValueChange = { expression = it },
                    label = { Text(stringResource(R.string.expression)) },
                    singleLine = true,
                    supportingText = {
                        Text(
                            if (expression.isBlank() || validExpression) {
                                stringResource(R.string.expression_hint)
                            } else {
                                stringResource(R.string.invalid_expression)
                            },
                        )
                    },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        roll.copy(
                            name = name.trim(),
                            expression = expression.trim(),
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun AddLevelRuleDialog(
    character: CharacterProfile,
    modifiers: List<CharacterModifier>,
    onDismiss: () -> Unit,
    onAdd: (RollLevelRule) -> Unit,
) {
    var kind by remember { mutableStateOf(LevelRuleKind.FROM_LEVEL) }
    var triggerText by remember { mutableStateOf("2") }
    var expression by remember { mutableStateOf("") }

    val trigger = triggerText.toIntOrNull()
    val expressionValid = RollFormulaResolver.validateTemplate(
        expression = expression,
        level = character.level,
        modifiers = modifiers,
    )
    val valid = trigger != null && trigger >= 1 && expressionValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_level_rule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == LevelRuleKind.FROM_LEVEL,
                        onClick = { kind = LevelRuleKind.FROM_LEVEL },
                        label = { Text(stringResource(R.string.from_level)) },
                    )
                    FilterChip(
                        selected = kind == LevelRuleKind.EVERY_LEVELS,
                        onClick = { kind = LevelRuleKind.EVERY_LEVELS },
                        label = { Text(stringResource(R.string.every_levels)) },
                    )
                }
                OutlinedTextField(
                    value = triggerText,
                    onValueChange = { triggerText = it.filter { char -> char.isDigit() } },
                    label = {
                        Text(
                            stringResource(
                                if (kind == LevelRuleKind.FROM_LEVEL) {
                                    R.string.trigger_level
                                } else {
                                    R.string.interval_levels
                                },
                            ),
                        )
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = expression,
                    onValueChange = { expression = it },
                    label = { Text(stringResource(R.string.rule_expression)) },
                    singleLine = true,
                    supportingText = {
                        Text(
                            if (expression.isBlank() || expressionValid) {
                                stringResource(R.string.expression_hint)
                            } else {
                                stringResource(R.string.invalid_expression)
                            },
                        )
                    },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onAdd(
                        RollLevelRule(
                            id = UUID.randomUUID().toString(),
                            kind = kind,
                            trigger = trigger ?: 1,
                            expression = expression.trim(),
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun AddRollDialog(
    character: CharacterProfile,
    modifiers: List<CharacterModifier>,
    characterId: String,
    groups: List<RollGroup>,
    nextOrder: Int,
    onDismiss: () -> Unit,
    onAdd: (RollDefinition) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var expression by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf<String?>(null) }
    var groupMenu by remember { mutableStateOf(false) }

    val validExpression = RollFormulaResolver.validateTemplate(
        expression = expression,
        level = character.level,
        modifiers = modifiers,
    )
    val valid = name.isNotBlank() && validExpression

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_roll)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.roll_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = expression,
                    onValueChange = { expression = it },
                    label = { Text(stringResource(R.string.expression)) },
                    supportingText = {
                        Text(
                            if (expression.isBlank() || validExpression) {
                                stringResource(R.string.expression_hint)
                            } else {
                                stringResource(R.string.invalid_expression)
                            },
                        )
                    },
                    singleLine = true,
                )
                Box {
                    OutlinedButton(onClick = { groupMenu = true }) {
                        val selectedName = groups.firstOrNull { it.id == groupId }?.name
                        Text(selectedName ?: stringResource(R.string.ungrouped))
                    }
                    DropdownMenu(
                        expanded = groupMenu,
                        onDismissRequest = { groupMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ungrouped)) },
                            onClick = {
                                groupId = null
                                groupMenu = false
                            },
                        )
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = {
                                    groupId = group.id
                                    groupMenu = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onAdd(
                        RollDefinition(
                            id = UUID.randomUUID().toString(),
                            characterId = characterId,
                            name = name.trim(),
                            expression = expression.trim(),
                            groupId = groupId,
                            order = nextOrder,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RollScreen(
    character: CharacterProfile,
    modifiers: List<CharacterModifier>,
    diceStyles: List<DiceStyle>,
    roll: RollDefinition,
    settings: AppSettings,
    onBack: () -> Unit,
    onLogged: (RollLog) -> Unit,
) {
    val context = LocalContext.current
    val formula = remember(character.level, modifiers, roll) {
        RollFormulaResolver.resolve(character, modifiers, roll)
    }
    val appearanceRandom = remember(roll.id) { Random(System.nanoTime()) }
    var outcome by remember(roll.id) { mutableStateOf<DiceRollResult?>(null) }

    fun throwDice() {
        val result = DiceExpression.parse(formula.expression).evaluate()
        val appearances = DiceAppearanceResolver.resolve(
            character = character,
            styles = diceStyles,
            appearance = roll.diceAppearance,
            result = result,
            random = appearanceRandom,
        )
        DiceRollVisualBus.publish(result, appearances)
        outcome = result
        onLogged(
            RollLog(
                id = UUID.randomUUID().toString(),
                characterId = character.id,
                rollDefinitionId = roll.id,
                rollName = roll.name,
                expression = formula.expression,
                total = result.total,
                detail = result.detail(),
                timestamp = System.currentTimeMillis(),
            ),
        )
    }

    DisposableEffect(settings.shakeEnabled, roll.id, formula.expression) {
        val detector = if (settings.shakeEnabled) {
            ShakeDetector(context, ::throwDice).also { it.start() }
        } else {
            null
        }
        onDispose { detector?.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(roll.name) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "${stringResource(R.string.level)} ${character.level}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    roll.expression,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (formula.expression != roll.expression) {
                    Text(
                        "${stringResource(R.string.resolved_expression)}: ${formula.expression}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (settings.shakeEnabled && settings.showRollButton) {
                        stringResource(R.string.shake_to_throw)
                    } else if (settings.shakeEnabled) {
                        stringResource(R.string.shake_only)
                    } else {
                        stringResource(R.string.waiting_for_throw)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                if (settings.animationsEnabled) {
                    AnimatedContent(
                        targetState = outcome,
                        label = "dice-result",
                    ) { value ->
                        ResultContent(value)
                    }
                } else {
                    ResultContent(outcome)
                }
            }

            if (settings.showRollButton) {
                FloatingActionButton(
                    onClick = ::throwDice,
                    modifier = Modifier
                        .align(settings.rollButtonPosition.toAlignment())
                        .padding(20.dp),
                ) {
                    Text(stringResource(R.string.throw_dice))
                }
            }
        }
    }
}

@Composable
private fun ResultContent(outcome: DiceRollResult?) {
    if (outcome == null) {
        Text(
            stringResource(R.string.waiting_for_throw),
            style = MaterialTheme.typography.titleMedium,
        )
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.result), style = MaterialTheme.typography.titleMedium)
        Text(
            outcome.total.toString(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
        )
        outcome.components.forEach { component ->
            Card {
                Text(
                    text = "${when (component.sign) {
                        1 -> ""
                        -1 -> "-"
                        else -> "${component.sign}x"
                    }}${component.count}d${component.sides}: ${component.rolls.joinToString(" · ")}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        if (outcome.constantTotal != 0) {
            Text(
                text = "${if (outcome.constantTotal > 0) "+" else ""}${outcome.constantTotal}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun RollButtonPosition.toAlignment(): Alignment = when (this) {
    RollButtonPosition.TOP_LEFT -> Alignment.TopStart
    RollButtonPosition.TOP_CENTER -> Alignment.TopCenter
    RollButtonPosition.TOP_RIGHT -> Alignment.TopEnd
    RollButtonPosition.BOTTOM_LEFT -> Alignment.BottomStart
    RollButtonPosition.BOTTOM_CENTER -> Alignment.BottomCenter
    RollButtonPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    settings: AppSettings,
    selectedLanguage: String,
    languageOptions: List<AppLanguageOption>,
    onSettingsChanged: (AppSettings) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    var languageMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                SettingSection(stringResource(R.string.theme)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onSettingsChanged(settings.copy(themeMode = mode)) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> R.string.theme_system
                                                ThemeMode.LIGHT -> R.string.theme_light
                                                ThemeMode.DARK -> R.string.theme_dark
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            item {
                ToggleSetting(
                    label = stringResource(R.string.shake_enabled),
                    checked = settings.shakeEnabled,
                    onChecked = { onSettingsChanged(settings.copy(shakeEnabled = it)) },
                )
            }

            item {
                ToggleSetting(
                    label = stringResource(R.string.animations_enabled),
                    checked = settings.animationsEnabled,
                    onChecked = { onSettingsChanged(settings.copy(animationsEnabled = it)) },
                )
            }

            item {
                ToggleSetting(
                    label = stringResource(R.string.show_roll_button),
                    checked = settings.showRollButton,
                    onChecked = { onSettingsChanged(settings.copy(showRollButton = it)) },
                )
            }

            if (settings.showRollButton) {
                item {
                    SettingSection(stringResource(R.string.button_position)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PositionRow(
                                settings,
                                listOf(
                                    RollButtonPosition.TOP_LEFT to R.string.top_left,
                                    RollButtonPosition.TOP_CENTER to R.string.top_center,
                                    RollButtonPosition.TOP_RIGHT to R.string.top_right,
                                ),
                                onSettingsChanged,
                            )
                            PositionRow(
                                settings,
                                listOf(
                                    RollButtonPosition.BOTTOM_LEFT to R.string.bottom_left,
                                    RollButtonPosition.BOTTOM_CENTER to R.string.bottom_center,
                                    RollButtonPosition.BOTTOM_RIGHT to R.string.bottom_right,
                                ),
                                onSettingsChanged,
                            )
                        }
                    }
                }
            }

            item {
                SettingSection(stringResource(R.string.log_retention)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RetentionRow(settings, 20, R.string.keep_20, onSettingsChanged)
                        RetentionRow(settings, 50, R.string.keep_50, onSettingsChanged)
                        RetentionRow(settings, 100, R.string.keep_100, onSettingsChanged)
                        RetentionRow(settings, 0, R.string.keep_all, onSettingsChanged)
                    }
                }
            }

            item {
                SettingSection(stringResource(R.string.language)) {
                    Box {
                        OutlinedButton(onClick = { languageMenu = true }) {
                            val name = if (selectedLanguage == AppLocaleManager.SYSTEM) {
                                stringResource(R.string.system_language)
                            } else {
                                languageOptions.firstOrNull { it.code == selectedLanguage }?.nativeName
                                    ?: selectedLanguage
                            }
                            Text(name)
                        }
                        DropdownMenu(
                            expanded = languageMenu,
                            onDismissRequest = { languageMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.system_language)) },
                                onClick = {
                                    languageMenu = false
                                    onLanguageChanged(AppLocaleManager.SYSTEM)
                                },
                            )
                            languageOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.nativeName) },
                                    onClick = {
                                        languageMenu = false
                                        onLanguageChanged(option.code)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Text(
                    stringResource(R.string.all_local),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun ToggleSetting(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun PositionRow(
    settings: AppSettings,
    choices: List<Pair<RollButtonPosition, Int>>,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { (position, label) ->
            FilterChip(
                selected = settings.rollButtonPosition == position,
                onClick = { onSettingsChanged(settings.copy(rollButtonPosition = position)) },
                label = { Text(stringResource(label), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun RetentionRow(
    settings: AppSettings,
    value: Int,
    label: Int,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    FilterChip(
        selected = settings.logRetention == value,
        onClick = { onSettingsChanged(settings.copy(logRetention = value)) },
        label = { Text(stringResource(label)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsScreen(
    character: CharacterProfile,
    logs: List<RollLog>,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${stringResource(R.string.logs)} · ${character.name}") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
                actions = {
                    if (logs.isNotEmpty()) {
                        TextButton(onClick = onClear) { Text(stringResource(R.string.clear_logs)) }
                    }
                },
            )
        },
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.no_logs))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(logs, key = { it.id }) { log ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(log.rollName, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(log.total.toString(), style = MaterialTheme.typography.titleLarge)
                            }
                            Text(log.expression, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (log.detail.isNotBlank()) {
                                Text(log.detail, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(log.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
