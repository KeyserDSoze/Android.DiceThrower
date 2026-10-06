package com.keyserdsoze.dicethrower.ui.v2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.AppLanguageOption
import com.keyserdsoze.dicethrower.AppLocaleManager
import com.keyserdsoze.dicethrower.GoogleConnectionFailure
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.data.CharacterImageAssetStore
import com.keyserdsoze.dicethrower.data.CloudAccountState
import com.keyserdsoze.dicethrower.data.DashboardDataOperations
import com.keyserdsoze.dicethrower.data.LocalStore
import com.keyserdsoze.dicethrower.data.sync.ConflictArea
import com.keyserdsoze.dicethrower.data.sync.SyncConflict
import com.keyserdsoze.dicethrower.data.sync.SyncConflictResolution
import com.keyserdsoze.dicethrower.data.sync.SyncErrorKind
import com.keyserdsoze.dicethrower.data.sync.SyncStatus
import com.keyserdsoze.dicethrower.data.sync.SyncStatusKind
import com.keyserdsoze.dicethrower.dice.DiceAppearanceResolver
import com.keyserdsoze.dicethrower.dice.DiceComponent
import com.keyserdsoze.dicethrower.dice.DiceExpression
import com.keyserdsoze.dicethrower.dice.DiceRollResult
import com.keyserdsoze.dicethrower.dice.DiceRollVisualEvent
import com.keyserdsoze.dicethrower.dice.RollFormulaResolver
import com.keyserdsoze.dicethrower.dice.subgroupResults
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterImageRef
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.ConflictPolicy
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.DiceTableTheme
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollButtonPosition
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollGroup
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.RollLog
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import com.keyserdsoze.dicethrower.model.ThemeMode
import com.keyserdsoze.dicethrower.sensor.ShakeDetector
import com.keyserdsoze.dicethrower.ui.dice3d.Dice3DScene
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class RouteV2 { CHARACTERS, CHARACTER, GROUP, ROLL, SETTINGS, LOGS }

internal fun moveRollSubgroup(
    subgroups: List<RollSubgroup>,
    index: Int,
    offset: Int,
): List<RollSubgroup> {
    if (index !in subgroups.indices) return subgroups
    val target = index + offset
    if (target !in subgroups.indices) return subgroups
    return subgroups.toMutableList().apply {
        val moving = removeAt(index)
        add(target, moving)
    }
}

internal fun guidedSubgroupsFromAdvancedExpression(
    expression: String,
    current: List<RollSubgroup>,
    level: Int,
    modifiers: List<CharacterModifier>,
    idFactory: () -> String = { UUID.randomUUID().toString() },
): List<RollSubgroup>? {
    val candidate = expression.trim()
    if (!RollFormulaResolver.validateTemplate(candidate, level, modifiers)) return null

    val currentCanonical = runCatching {
        RollFormulaResolver.canonicalExpression(current)
    }.getOrNull()
    if (candidate == currentCanonical) return current

    val previousSingle = current.singleOrNull()
    return listOf(
        RollSubgroup(
            id = previousSingle?.id ?: idFactory(),
            name = previousSingle?.name.orEmpty(),
            expression = candidate,
            operator = RollSubgroupOperator.ADD,
        ),
    )
}

private sealed interface DashboardEntry {
    val key: String
    val order: Int

    data class GroupEntry(val group: RollGroup) : DashboardEntry {
        override val key: String = "group-${group.id}"
        override val order: Int = group.order
    }

    data class RollEntry(val roll: RollDefinition) : DashboardEntry {
        override val key: String = "roll-${roll.id}"
        override val order: Int = roll.order
    }
}

@Composable
fun DiceThrowerAppV2(
    store: LocalStore,
    settings: AppSettings,
    selectedLanguage: String,
    languageOptions: List<AppLanguageOption>,
    cloudAccountState: CloudAccountState,
    accountFlowBusy: Boolean,
    accountFailure: GoogleConnectionFailure?,
    syncStatus: SyncStatus,
    cloudDeleteBusy: Boolean,
    cloudDeleteFailed: Boolean,
    dataRefreshVersion: Int,
    onSettingsChanged: (AppSettings) -> Unit,
    onLocalDataChanged: () -> Unit,
    onSyncNow: () -> Unit,
    onResolveConflict: (SyncConflict, SyncConflictResolution) -> Unit,
    onUseStandalone: () -> Unit,
    onConnectGoogle: () -> Unit,
    onDisconnectGoogle: () -> Unit,
    onDeleteCloudData: () -> Unit,
    onLanguageChanged: (String) -> Unit,
) {
    if (cloudAccountState.onboardingRequired) {
        AccountOnboardingV2(
            busy = accountFlowBusy,
            failure = accountFailure,
            onUseStandalone = onUseStandalone,
            onConnectGoogle = onConnectGoogle,
        )
        return
    }

    var data by remember { mutableStateOf(store.loadData()) }
    var route by remember { mutableStateOf(RouteV2.CHARACTERS) }
    var selectedCharacterId by remember { mutableStateOf<String?>(null) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var selectedRollId by remember { mutableStateOf<String?>(null) }
    var rollReturnGroupId by remember { mutableStateOf<String?>(null) }
    var editMode by remember { mutableStateOf(false) }

    fun persist(updated: AppData) {
        data = store.saveData(updated)
        onLocalDataChanged()
    }

    LaunchedEffect(dataRefreshVersion) {
        if (dataRefreshVersion > 0) data = store.loadData()
    }

    syncStatus.conflicts.firstOrNull()?.let { conflict ->
        ConflictDialogV2(
            conflict = conflict,
            onKeepLocal = { onResolveConflict(conflict, SyncConflictResolution.KEEP_LOCAL) },
            onUseRemote = { onResolveConflict(conflict, SyncConflictResolution.USE_REMOTE) },
        )
    }

    when (route) {
        RouteV2.CHARACTERS -> CharactersScreenV2(
            data = data,
            onOpenCharacter = { id ->
                selectedCharacterId = id
                selectedGroupId = null
                rollReturnGroupId = null
                editMode = false
                route = RouteV2.CHARACTER
            },
            onAddCharacter = { character ->
                persist(data.copy(characters = data.characters + character))
            },
            onSettings = { route = RouteV2.SETTINGS },
        )

        RouteV2.CHARACTER -> {
            val character = data.characters.firstOrNull { it.id == selectedCharacterId }
            if (character == null) {
                route = RouteV2.CHARACTERS
            } else {
                CharacterScreenV2(
                    character = character,
                    data = data,
                    editMode = editMode,
                    onBack = { route = RouteV2.CHARACTERS },
                    onToggleMode = { editMode = !editMode },
                    onOpenLogs = { route = RouteV2.LOGS },
                    onOpenRoll = { rollId ->
                        selectedRollId = rollId
                        rollReturnGroupId = null
                        route = RouteV2.ROLL
                    },
                    onOpenGroup = { groupId ->
                        selectedGroupId = groupId
                        route = RouteV2.GROUP
                    },
                    onDataChanged = ::persist,
                )
            }
        }

        RouteV2.GROUP -> {
            val character = data.characters.firstOrNull { it.id == selectedCharacterId }
            val group = data.groups.firstOrNull {
                it.id == selectedGroupId && it.characterId == selectedCharacterId
            }
            if (character == null || group == null) {
                route = RouteV2.CHARACTER
            } else {
                GroupScreenV2(
                    character = character,
                    group = group,
                    data = data,
                    editMode = editMode,
                    onBack = { route = RouteV2.CHARACTER },
                    onOpenRoll = { rollId ->
                        selectedRollId = rollId
                        rollReturnGroupId = group.id
                        route = RouteV2.ROLL
                    },
                    onDeleteGroup = {
                        persist(DashboardDataOperations.deleteGroup(data, character.id, group.id))
                        route = RouteV2.CHARACTER
                    },
                    onDataChanged = ::persist,
                )
            }
        }

        RouteV2.ROLL -> {
            val character = data.characters.firstOrNull { it.id == selectedCharacterId }
            val roll = data.rolls.firstOrNull { it.id == selectedRollId }
            if (character == null || roll == null) {
                route = RouteV2.CHARACTER
            } else {
                RollScreenV2(
                    character = character,
                    modifiers = data.modifiers.filter { it.characterId == character.id },
                    diceStyles = data.diceStyles,
                    roll = roll,
                    settings = settings,
                    onBack = {
                        route = if (
                            rollReturnGroupId != null &&
                            data.groups.any { it.id == rollReturnGroupId && it.characterId == character.id }
                        ) {
                            selectedGroupId = rollReturnGroupId
                            RouteV2.GROUP
                        } else {
                            RouteV2.CHARACTER
                        }
                    },
                    onLogged = { log ->
                        val logs = (data.logs + log).let { all ->
                            if (settings.logRetention == 0) all
                            else all.sortedByDescending { it.timestamp }.take(settings.logRetention)
                        }
                        persist(data.copy(logs = logs))
                    },
                )
            }
        }

        RouteV2.SETTINGS -> SettingsScreenV2(
            settings = settings,
            selectedLanguage = selectedLanguage,
            languageOptions = languageOptions,
            cloudAccountState = cloudAccountState,
            accountFlowBusy = accountFlowBusy,
            accountFailure = accountFailure,
            syncStatus = syncStatus,
            cloudDeleteBusy = cloudDeleteBusy,
            cloudDeleteFailed = cloudDeleteFailed,
            onSettingsChanged = onSettingsChanged,
            onSyncNow = onSyncNow,
            onConnectGoogle = onConnectGoogle,
            onDisconnectGoogle = onDisconnectGoogle,
            onDeleteCloudData = onDeleteCloudData,
            onLanguageChanged = onLanguageChanged,
            onBack = { route = RouteV2.CHARACTERS },
        )

        RouteV2.LOGS -> {
            val character = data.characters.firstOrNull { it.id == selectedCharacterId }
            if (character == null) {
                route = RouteV2.CHARACTERS
            } else {
                LogsScreenV2(
                    character = character,
                    logs = data.logs
                        .filter { it.characterId == character.id }
                        .sortedByDescending { it.timestamp },
                    onBack = { route = RouteV2.CHARACTER },
                    onClear = {
                        persist(data.copy(logs = data.logs.filterNot { it.characterId == character.id }))
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountOnboardingV2(
    busy: Boolean,
    failure: GoogleConnectionFailure?,
    onUseStandalone: () -> Unit,
    onConnectGoogle: () -> Unit,
) {
    ArcaneBackground {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Casino, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.welcome_to_dice_thrower), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    }
                    Text(
                        stringResource(R.string.onboarding_account_intro),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onUseStandalone,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CloudOff, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.use_standalone))
                    }
                    OutlinedButton(
                        onClick = onConnectGoogle,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (busy) R.string.connecting_google else R.string.continue_with_google))
                    }
                    Text(
                        stringResource(R.string.onboarding_standalone_reassurance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    failure?.let {
                        Text(
                            accountFailureText(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharactersScreenV2(
    data: AppData,
    onOpenCharacter: (String) -> Unit,
    onAddCharacter: (CharacterProfile) -> Unit,
    onSettings: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }

    ArcaneBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = transparentTopBarColors(),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrandIcon(size = 42)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    fontWeight = FontWeight.Black,
                                )
                                Text(
                                    text = stringResource(R.string.app_tagline),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_character)) },
                )
            },
        ) { padding ->
            val characters = data.characters.sortedBy { it.order }
            if (characters.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PremiumCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            BrandIcon(size = 104)
                            Text(
                                stringResource(R.string.no_characters),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.app_intro_short),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(characters, key = { it.id }) { character ->
                        PremiumCard(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenCharacter(character.id) },
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
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (character.tag.isNotBlank()) {
                                        Text(
                                            character.tag,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                LevelBadge(character.level)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        CharacterDialogV2(
            nextOrder = data.characters.size,
            onDismiss = { showAdd = false },
            onCreate = {
                onAddCharacter(it)
                showAdd = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterScreenV2(
    character: CharacterProfile,
    data: AppData,
    editMode: Boolean,
    onBack: () -> Unit,
    onToggleMode: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenRoll: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onDataChanged: (AppData) -> Unit,
) {
    ArcaneBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = transparentTopBarColors(),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CharacterAvatar(character = character, size = 42)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    character.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (character.tag.isNotBlank()) {
                                        Text(
                                            character.tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        "Lv ${character.level}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenLogs) {
                            Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.logs))
                        }
                        IconButton(onClick = onToggleMode) {
                            Icon(
                                imageVector = if (editMode) Icons.Rounded.Casino else Icons.Rounded.Edit,
                                contentDescription = stringResource(if (editMode) R.string.use else R.string.edit),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            if (editMode) {
                CharacterEditContentV2(
                    character = character,
                    data = data,
                    onOpenGroup = onOpenGroup,
                    onDataChanged = onDataChanged,
                    modifier = Modifier.padding(padding),
                )
            } else {
                DashboardContentV2(
                    character = character,
                    data = data,
                    onOpenRoll = onOpenRoll,
                    onOpenGroup = onOpenGroup,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun DashboardContentV2(
    character: CharacterProfile,
    data: AppData,
    onOpenRoll: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modifiers = data.modifiers.filter { it.characterId == character.id }
    val activeRolls = data.rolls.filter { it.characterId == character.id && it.enabled }
    val visibleGroups = data.groups
        .filter { it.characterId == character.id }
        .filter { group -> activeRolls.any { it.groupId == group.id } }
    val ungrouped = activeRolls.filter { it.groupId == null }
    val entries = dashboardEntries(visibleGroups, ungrouped)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (entries.isEmpty()) {
            item {
                PremiumCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Rounded.Casino,
                            contentDescription = null,
                            modifier = Modifier.size(46.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.no_rolls), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is DashboardEntry.GroupEntry -> {
                    val group = entry.group
                    val groupRolls = activeRolls.filter { it.groupId == group.id }.sortedBy { it.order }
                    PremiumCard(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenGroup(group.id) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.padding(10.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${groupRolls.size} ${stringResource(R.string.rolls).lowercase()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        }
                    }
                }

                is DashboardEntry.RollEntry -> {
                    val roll = entry.roll
                    PremiumCard(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenRoll(roll.id) },
                    ) {
                        RollLaunchRowV2(character, modifiers, roll, onOpenRoll)
                    }
                }
            }
        }
    }
}

@Composable
private fun RollLaunchRowV2(
    character: CharacterProfile,
    modifiers: List<CharacterModifier>,
    roll: RollDefinition,
    onOpenRoll: (String) -> Unit,
) {
    val resolved = runCatching { RollFormulaResolver.resolve(character, modifiers, roll).expression }.getOrNull()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenRoll(roll.id) }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(
                Icons.Rounded.Casino,
                contentDescription = null,
                modifier = Modifier.padding(10.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(roll.name, fontWeight = FontWeight.Bold)
            Text(
                resolved ?: roll.expression,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
internal fun CharacterEditContentV2(
    character: CharacterProfile,
    data: AppData,
    onOpenGroup: (String) -> Unit,
    onDataChanged: (AppData) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddModifier by remember { mutableStateOf(false) }
    var editingModifierId by remember { mutableStateOf<String?>(null) }
    var showAddGroup by remember { mutableStateOf(false) }
    var showAddRoll by remember { mutableStateOf(false) }
    var editingRollId by remember { mutableStateOf<String?>(null) }
    var addRuleRollId by remember { mutableStateOf<String?>(null) }
    var levelText by remember(character.id) { mutableStateOf(character.level.toString()) }

    LaunchedEffect(character.level) {
        if (levelText.toIntOrNull() != character.level) levelText = character.level.toString()
    }

    val groups = data.groups.filter { it.characterId == character.id }.sortedBy { it.order }
    val rolls = data.rolls.filter { it.characterId == character.id }
    val modifiers = data.modifiers.filter { it.characterId == character.id }.sortedBy { it.order }
    val diceStyles = data.diceStyles.filter { it.characterId == character.id }.sortedBy { it.order }
    val entries = dashboardEntries(groups, rolls.filter { it.groupId == null })

    val builderRoll = editingRollId?.let { id -> rolls.firstOrNull { it.id == id } }
    if (showAddRoll || builderRoll != null) {
        RollBuilderScreenV2(
            title = stringResource(if (builderRoll == null) R.string.new_roll else R.string.edit_roll),
            character = character,
            modifiers = modifiers,
            groups = groups,
            existing = builderRoll,
            onDismiss = {
                showAddRoll = false
                editingRollId = null
            },
            onSave = { draft ->
                if (builderRoll == null) {
                    val order = if (draft.groupId == null) {
                        DashboardDataOperations.nextTopLevelOrder(data, character.id)
                    } else {
                        DashboardDataOperations.nextGroupRollOrder(data, character.id, draft.groupId)
                    }
                    onDataChanged(
                        data.copy(
                            rolls = data.rolls + draft.copy(
                                id = UUID.randomUUID().toString(),
                                characterId = character.id,
                                order = order,
                            ),
                        ),
                    )
                } else {
                    onDataChanged(updateRoll(data, character.id, builderRoll, draft))
                }
                showAddRoll = false
                editingRollId = null
            },
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionTitleV2(
                title = stringResource(R.string.character_progression),
                subtitle = stringResource(R.string.level_auto_update_help),
            )
        }

        item {
            PremiumCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = levelText,
                        onValueChange = { raw ->
                            val candidate = raw.filter(Char::isDigit).take(4)
                            if (candidate.isBlank() || (candidate.toIntOrNull() ?: 0) >= 1) {
                                levelText = candidate
                                candidate.toIntOrNull()?.let { newLevel ->
                                    if (newLevel != character.level) {
                                        onDataChanged(
                                            data.copy(
                                                characters = data.characters.map {
                                                    if (it.id == character.id) it.copy(level = newLevel) else it
                                                },
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.level)) },
                        singleLine = true,
                        modifier = Modifier.width(96.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        enabled = character.level > 1,
                        onClick = {
                            levelText = (character.level - 1).coerceAtLeast(1).toString()
                            onDataChanged(
                                data.copy(
                                    characters = data.characters.map {
                                        if (it.id == character.id) it.copy(level = (it.level - 1).coerceAtLeast(1)) else it
                                    },
                                ),
                            )
                        },
                    ) { Text("−") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            levelText = (character.level + 1).toString()
                            onDataChanged(
                                data.copy(
                                    characters = data.characters.map {
                                        if (it.id == character.id) it.copy(level = it.level + 1) else it
                                    },
                                ),
                            )
                        },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.level_up))
                    }
                }
            }
        }

        item {
            DiceTablePickerV2(
                character = character,
                onThemeChanged = { tableTheme ->
                    onDataChanged(
                        data.copy(
                            characters = data.characters.map {
                                if (it.id == character.id) it.copy(diceTableTheme = tableTheme) else it
                            },
                        ),
                    )
                },
                onImageChanged = { tableImage ->
                    onDataChanged(
                        data.copy(
                            characters = data.characters.map {
                                if (it.id == character.id) it.copy(diceTableImage = tableImage) else it
                            },
                        ),
                    )
                },
            )
        }

        item {
            DiceStyleLibraryV2(
                character = character,
                data = data,
                onDataChanged = onDataChanged,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitleV2(
                    title = stringResource(R.string.modifiers),
                    subtitle = stringResource(R.string.modifier_help),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showAddModifier = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.new_modifier), tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        items(modifiers, key = { it.id }) { characterModifier ->
            val referenced = modifierIsReferenced(characterModifier.name, rolls)
            PremiumCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = if (characterModifier.value >= 0) "+${characterModifier.value}" else characterModifier.value.toString(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(characterModifier.name, fontWeight = FontWeight.Bold)
                        if (referenced) {
                            Text(
                                stringResource(R.string.modifier_in_use),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    IconButton(onClick = { editingModifierId = characterModifier.id }) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(
                        enabled = !referenced,
                        onClick = {
                            onDataChanged(
                                data.copy(
                                    modifiers = data.modifiers.filterNot { it.id == characterModifier.id },
                                ),
                            )
                        },
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }

        item {
            SectionTitleV2(
                title = stringResource(R.string.dashboard),
                subtitle = stringResource(R.string.drag_to_reorder),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { showAddGroup = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.new_group))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.new_group))
                }
                Button(
                    onClick = { showAddRoll = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.new_roll))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.new_roll))
                }
            }
        }

        itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
            DragReorderCard(
                key = entry.key,
                canMoveUp = index > 0,
                canMoveDown = index < entries.lastIndex,
                onMoveUp = {
                    onDataChanged(DashboardDataOperations.moveTopLevel(data, character.id, entry.key, -1))
                },
                onMoveDown = {
                    onDataChanged(DashboardDataOperations.moveTopLevel(data, character.id, entry.key, 1))
                },
            ) {
                val title = when (entry) {
                    is DashboardEntry.GroupEntry -> entry.group.name
                    is DashboardEntry.RollEntry -> entry.roll.name
                }
                val subtitle = when (entry) {
                    is DashboardEntry.GroupEntry -> {
                        val rollCount = rolls.count { it.groupId == entry.group.id }
                        "$rollCount ${stringResource(R.string.rolls).lowercase()}"
                    }
                    is DashboardEntry.RollEntry -> stringResource(R.string.rolls)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when (entry) {
                                is DashboardEntry.GroupEntry -> onOpenGroup(entry.group.id)
                                is DashboardEntry.RollEntry -> editingRollId = entry.roll.id
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (entry is DashboardEntry.GroupEntry) Icons.Rounded.AutoAwesome else Icons.Rounded.Casino,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        enabled = index > 0,
                        onClick = {
                            onDataChanged(DashboardDataOperations.moveTopLevel(data, character.id, entry.key, -1))
                        },
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
                    }
                    IconButton(
                        enabled = index < entries.lastIndex,
                        onClick = {
                            onDataChanged(DashboardDataOperations.moveTopLevel(data, character.id, entry.key, 1))
                        },
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }

    if (showAddModifier) {
        ModifierDialogV2(
            existing = null,
            characterId = character.id,
            nextOrder = modifiers.size,
            existingNames = modifiers.map { it.name },
            onDismiss = { showAddModifier = false },
            onSave = {
                onDataChanged(data.copy(modifiers = data.modifiers + it))
                showAddModifier = false
            },
        )
    }

    editingModifierId?.let { id ->
        modifiers.firstOrNull { it.id == id }?.let { existing ->
            ModifierDialogV2(
                existing = existing,
                characterId = character.id,
                nextOrder = existing.order,
                existingNames = modifiers.filterNot { it.id == existing.id }.map { it.name },
                onDismiss = { editingModifierId = null },
                onSave = { saved ->
                    onDataChanged(
                        data.copy(
                            modifiers = data.modifiers.map { if (it.id == saved.id) saved else it },
                        ),
                    )
                    editingModifierId = null
                },
            )
        }
    }

    if (showAddGroup) {
        GroupDialogV2(
            onDismiss = { showAddGroup = false },
            onSave = { name ->
                val group = RollGroup(
                    id = UUID.randomUUID().toString(),
                    characterId = character.id,
                    name = name,
                    order = DashboardDataOperations.nextTopLevelOrder(data, character.id),
                )
                onDataChanged(
                    data.copy(
                        groups = data.groups + group,
                    ),
                )
                showAddGroup = false
                onOpenGroup(group.id)
            },
        )
    }

    addRuleRollId?.let { rollId ->
        rolls.firstOrNull { it.id == rollId }?.let { roll ->
            LevelRuleDialogV2(
                character = character,
                modifiers = modifiers,
                onDismiss = { addRuleRollId = null },
                onSave = { rule ->
                    onDataChanged(
                        data.copy(
                            rolls = data.rolls.map {
                                if (it.id == roll.id) it.copy(levelRules = it.levelRules + rule) else it
                            },
                        ),
                    )
                    addRuleRollId = null
                },
            )
        }
    }
}

@Composable
private fun DiceTablePickerV2(
    character: CharacterProfile,
    onThemeChanged: (DiceTableTheme) -> Unit,
    onImageChanged: (CharacterImageRef?) -> Unit,
) {
    val context = LocalContext.current
    val imageAssetStore = remember(context) { CharacterImageAssetStore(context) }
    var imageImportFailed by remember { mutableStateOf(false) }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { imageAssetStore.importFromUri(uri) }
                .onSuccess {
                    imageImportFailed = false
                    onImageChanged(it)
                }
                .onFailure { imageImportFailed = true }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitleV2(
            title = stringResource(R.string.dice_table),
            subtitle = stringResource(R.string.dice_table_help),
        )
        DiceTableTheme.values().toList().chunked(2).forEach { themes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                themes.forEach { theme ->
                    FilterChip(
                        selected = character.diceTableTheme == theme,
                        onClick = { onThemeChanged(theme) },
                        label = {
                            Text(
                                stringResource(
                                    when (theme) {
                                        DiceTableTheme.ARCANE -> R.string.table_arcane
                                        DiceTableTheme.OAK -> R.string.table_oak
                                        DiceTableTheme.EMERALD -> R.string.table_emerald
                                        DiceTableTheme.OBSIDIAN -> R.string.table_obsidian
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { imageLauncher.launch(arrayOf("image/*")) },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (character.diceTableImage == null) {
                        stringResource(R.string.choose_image)
                    } else {
                        stringResource(R.string.image_selected)
                    },
                )
            }
            if (character.diceTableImage != null) {
                IconButton(onClick = { onImageChanged(null) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
        if (imageImportFailed) {
            Text(
                stringResource(R.string.image_import_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupScreenV2(
    character: CharacterProfile,
    group: RollGroup,
    data: AppData,
    editMode: Boolean,
    onBack: () -> Unit,
    onOpenRoll: (String) -> Unit,
    onDeleteGroup: () -> Unit,
    onDataChanged: (AppData) -> Unit,
) {
    ArcaneBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = transparentTopBarColors(),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    title = {
                        Column {
                            Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.group),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        if (editMode) {
                            IconButton(onClick = onDeleteGroup) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                    },
                )
            },
        ) { padding ->
            if (editMode) {
                GroupEditContentV2(
                    character = character,
                    group = group,
                    data = data,
                    onDataChanged = onDataChanged,
                    modifier = Modifier.padding(padding),
                )
            } else {
                GroupLaunchContentV2(
                    character = character,
                    group = group,
                    data = data,
                    onOpenRoll = onOpenRoll,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun GroupLaunchContentV2(
    character: CharacterProfile,
    group: RollGroup,
    data: AppData,
    onOpenRoll: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modifiers = data.modifiers.filter { it.characterId == character.id }
    val rolls = data.rolls
        .filter { it.characterId == character.id && it.groupId == group.id && it.enabled }
        .sortedBy { it.order }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (rolls.isEmpty()) {
            item {
                PremiumCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.empty_group),
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(rolls, key = { it.id }) { roll ->
            PremiumCard(Modifier.fillMaxWidth().clickable { onOpenRoll(roll.id) }) {
                RollLaunchRowV2(character, modifiers, roll, onOpenRoll)
            }
        }
    }
}

@Composable
private fun GroupEditContentV2(
    character: CharacterProfile,
    group: RollGroup,
    data: AppData,
    onDataChanged: (AppData) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddRoll by remember { mutableStateOf(false) }
    var editingRollId by remember { mutableStateOf<String?>(null) }
    var addRuleRollId by remember { mutableStateOf<String?>(null) }

    val groups = data.groups.filter { it.characterId == character.id }.sortedBy { it.order }
    val allCharacterRolls = data.rolls.filter { it.characterId == character.id }
    val groupRolls = allCharacterRolls.filter { it.groupId == group.id }.sortedBy { it.order }
    val modifiers = data.modifiers.filter { it.characterId == character.id }.sortedBy { it.order }
    val diceStyles = data.diceStyles.filter { it.characterId == character.id }.sortedBy { it.order }

    val builderRoll = editingRollId?.let { id -> allCharacterRolls.firstOrNull { it.id == id } }
    if (showAddRoll || builderRoll != null) {
        RollBuilderScreenV2(
            title = stringResource(if (builderRoll == null) R.string.new_roll else R.string.edit_roll),
            character = character,
            modifiers = modifiers,
            groups = groups,
            existing = builderRoll,
            initialGroupId = group.id,
            onDismiss = {
                showAddRoll = false
                editingRollId = null
            },
            onSave = { draft ->
                if (builderRoll == null) {
                    val order = if (draft.groupId == null) {
                        DashboardDataOperations.nextTopLevelOrder(data, character.id)
                    } else {
                        DashboardDataOperations.nextGroupRollOrder(data, character.id, draft.groupId)
                    }
                    onDataChanged(
                        data.copy(
                            rolls = data.rolls + draft.copy(
                                id = UUID.randomUUID().toString(),
                                characterId = character.id,
                                order = order,
                            ),
                        ),
                    )
                } else {
                    onDataChanged(updateRoll(data, character.id, builderRoll, draft))
                }
                showAddRoll = false
                editingRollId = null
            },
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitleV2(
                    title = stringResource(R.string.rolls),
                    subtitle = stringResource(R.string.group_contents_help),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { showAddRoll = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.new_roll))
                }
            }
        }

        if (groupRolls.isEmpty()) {
            item {
                PremiumCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.empty_group),
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        itemsIndexed(groupRolls, key = { _, roll -> roll.id }) { index, roll ->
            DragReorderCard(
                key = "group-roll-${roll.id}",
                canMoveUp = index > 0,
                canMoveDown = index < groupRolls.lastIndex,
                onMoveUp = {
                    onDataChanged(
                        data.copy(
                            rolls = DashboardDataOperations.moveRollInsideGroup(
                                data.rolls,
                                character.id,
                                group.id,
                                roll.id,
                                -1,
                            ),
                        ),
                    )
                },
                onMoveDown = {
                    onDataChanged(
                        data.copy(
                            rolls = DashboardDataOperations.moveRollInsideGroup(
                                data.rolls,
                                character.id,
                                group.id,
                                roll.id,
                                1,
                            ),
                        ),
                    )
                },
            ) {
                RollEditorCardV2(
                    character = character,
                    modifiers = modifiers,
                    groups = groups,
                    diceStyles = diceStyles,
                    roll = roll,
                    onEnabledChanged = { enabled ->
                        onDataChanged(
                            data.copy(
                                rolls = data.rolls.map { if (it.id == roll.id) it.copy(enabled = enabled) else it },
                            ),
                        )
                    },
                    onGroupChanged = { groupId ->
                        onDataChanged(
                            DashboardDataOperations.changeRollGroup(data, character.id, roll.id, groupId),
                        )
                    },
                    onEdit = { editingRollId = roll.id },
                    onAppearanceChanged = { appearance ->
                        onDataChanged(
                            data.copy(
                                rolls = data.rolls.map {
                                    if (it.id == roll.id) it.copy(diceAppearance = appearance) else it
                                },
                            ),
                        )
                    },
                    onAddRule = { addRuleRollId = roll.id },
                    onDeleteRule = { ruleId ->
                        onDataChanged(
                            data.copy(
                                rolls = data.rolls.map {
                                    if (it.id == roll.id) {
                                        it.copy(levelRules = it.levelRules.filterNot { rule -> rule.id == ruleId })
                                    } else {
                                        it
                                    }
                                },
                            ),
                        )
                    },
                    onDelete = {
                        onDataChanged(data.copy(rolls = data.rolls.filterNot { it.id == roll.id }))
                    },
                )
            }
        }
    }

    addRuleRollId?.let { rollId ->
        allCharacterRolls.firstOrNull { it.id == rollId }?.let { roll ->
            LevelRuleDialogV2(
                character = character,
                modifiers = modifiers,
                onDismiss = { addRuleRollId = null },
                onSave = { rule ->
                    onDataChanged(
                        data.copy(
                            rolls = data.rolls.map {
                                if (it.id == roll.id) it.copy(levelRules = it.levelRules + rule) else it
                            },
                        ),
                    )
                    addRuleRollId = null
                },
            )
        }
    }
}

@Composable
private fun SectionTitleV2(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RollEditorCardV2(
    character: CharacterProfile,
    modifiers: List<CharacterModifier>,
    groups: List<RollGroup>,
    diceStyles: List<DiceStyle>,
    roll: RollDefinition,
    onEnabledChanged: (Boolean) -> Unit,
    onGroupChanged: (String?) -> Unit,
    onEdit: () -> Unit,
    onAppearanceChanged: (RollDiceAppearance) -> Unit,
    onAddRule: () -> Unit,
    onDeleteRule: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var groupMenu by remember(roll.id) { mutableStateOf(false) }
    val groupName = groups.firstOrNull { it.id == roll.groupId }?.name ?: stringResource(R.string.ungrouped)
    val resolved = runCatching { RollFormulaResolver.resolve(character, modifiers, roll).expression }.getOrNull()

    PremiumCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(
                        Icons.Rounded.Casino,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(roll.name, fontWeight = FontWeight.Bold)
                    Text(roll.expression, style = MaterialTheme.typography.bodyMedium)
                    if (resolved != null && resolved != roll.expression) {
                        Text(
                            "${stringResource(R.string.resolved_expression)}: $resolved",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Switch(checked = roll.enabled, onCheckedChange = onEnabledChanged)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(onClick = { groupMenu = true }) {
                        Text(groupName)
                    }
                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ungrouped)) },
                            onClick = {
                                groupMenu = false
                                onGroupChanged(null)
                            },
                        )
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = {
                                    groupMenu = false
                                    onGroupChanged(group.id)
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                }
            }

            DiceAppearanceEditorV2(
                character = character,
                diceStyles = diceStyles,
                resolvedExpression = resolved,
                appearance = roll.diceAppearance,
                onAppearanceChanged = onAppearanceChanged,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.level_scaling),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onAddRule) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.add_level_rule))
                }
            }

            if (roll.levelRules.isEmpty()) {
                Text(
                    stringResource(R.string.no_level_rules),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                roll.levelRules.forEach { rule ->
                    val signedRuleExpression = if (rule.expression.trimStart().startsWith("-")) {
                        rule.expression
                    } else {
                        "+${rule.expression}"
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when (rule.kind) {
                                    LevelRuleKind.FROM_LEVEL -> "${stringResource(R.string.from_level)} ${rule.trigger}  ·  $signedRuleExpression"
                                    LevelRuleKind.EVERY_LEVELS -> "${stringResource(R.string.every_levels)} ${rule.trigger}  ·  $signedRuleExpression"
                                },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            IconButton(onClick = { onDeleteRule(rule.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RollScreenV2(
    character: CharacterProfile,
    modifiers: List<CharacterModifier>,
    diceStyles: List<DiceStyle>,
    roll: RollDefinition,
    settings: AppSettings,
    onBack: () -> Unit,
    onLogged: (RollLog) -> Unit,
) {
    val context = LocalContext.current
    val tableImageStore = remember(context) { CharacterImageAssetStore(context) }
    val tableBitmap = remember(character.diceTableImage) {
        character.diceTableImage
            ?.let(tableImageStore::loadVerified)
            ?.let(::decodeTableBitmap)
    }
    val formula = remember(character.level, modifiers, roll) {
        RollFormulaResolver.resolve(character, modifiers, roll)
    }
    val parsedExpression = remember(formula.expression) { DiceExpression.parse(formula.expression) }
    val appearanceRandom = remember(roll.id) { Random(System.nanoTime()) }
    val previewResult = remember(formula.expression) { parsedExpression.previewResult() }
    val previewAppearances = remember(character, diceStyles, roll.diceAppearance, formula.expression) {
        DiceAppearanceResolver.resolve(
            character = character,
            styles = diceStyles,
            appearance = roll.diceAppearance,
            result = previewResult,
            random = Random(roll.id.hashCode()),
        )
    }
    val previewEvent = remember(roll.id, formula.expression, previewAppearances) {
        DiceRollVisualEvent(
            id = -kotlin.math.abs(roll.id.hashCode().toLong()).coerceAtLeast(1L),
            result = previewResult,
            appearances = previewAppearances,
        )
    }
    var visualEvent by remember(roll.id, formula.expression) { mutableStateOf(previewEvent) }
    var outcome by remember(roll.id, formula.expression) { mutableStateOf<DiceRollResult?>(null) }
    var hasRolled by remember(roll.id) { mutableStateOf(false) }
    var resultRevealed by remember(roll.id, formula.expression) { mutableStateOf(false) }
    var showStats by remember(roll.id) { mutableStateOf(false) }

    fun throwDice() {
        if (hasRolled && !resultRevealed) return
        val result = parsedExpression.evaluate()
        val appearances = DiceAppearanceResolver.resolve(
            character = character,
            styles = diceStyles,
            appearance = roll.diceAppearance,
            result = result,
            random = appearanceRandom,
        )
        visualEvent = DiceRollVisualEvent(
            id = System.nanoTime(),
            result = result,
            appearances = appearances,
        )
        outcome = result
        hasRolled = true
        resultRevealed = false
        showStats = false
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

    // Shake starts the first throw only. Every reroll is deliberately button-only.
    DisposableEffect(settings.shakeEnabled, hasRolled, roll.id, formula.expression) {
        val detector = if (settings.shakeEnabled && !hasRolled) {
            ShakeDetector(context, ::throwDice).also { it.start() }
        } else null
        onDispose { detector?.stop() }
    }

    ArcaneBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Dice3DScene(
                event = visualEvent,
                tableTheme = character.diceTableTheme,
                tableImage = tableBitmap,
                tableImageKey = character.diceTableImage?.assetId,
                fullBleed = true,
                animateRoll = hasRolled && settings.animationsEnabled,
                onSettled = { eventId ->
                    if (hasRolled && eventId == visualEvent.id) resultRevealed = true
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (resultRevealed) {
                outcome?.let { value ->
                    Box(Modifier.align(Alignment.Center)) {
                        RollTotalReveal(
                            total = value.total,
                            aboveAverage = value.total > value.expectedTotal(),
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.58f),
                contentColor = Color.White,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showStats = true }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            roll.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(7.dp))
                        Icon(
                            Icons.Rounded.BarChart,
                            contentDescription = stringResource(R.string.statistics),
                            modifier = Modifier.size(19.dp),
                            tint = Color.White.copy(alpha = 0.9f),
                        )
                    }
                    IconButton(
                        onClick = ::throwDice,
                        enabled = !hasRolled || resultRevealed,
                    ) {
                        Icon(
                            Icons.Rounded.Casino,
                            contentDescription = stringResource(if (hasRolled) R.string.roll_again else R.string.throw_dice),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }

            if (!hasRolled) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.58f),
                    contentColor = Color.White,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            when {
                                settings.shakeEnabled && settings.showRollButton -> stringResource(R.string.shake_to_throw)
                                settings.shakeEnabled -> stringResource(R.string.shake_only)
                                else -> stringResource(R.string.waiting_for_throw)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        if (settings.showRollButton || !settings.shakeEnabled) {
                            Button(onClick = ::throwDice) {
                                Icon(Icons.Rounded.Casino, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.throw_dice))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStats) {
        ModalBottomSheet(onDismissRequest = { showStats = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.statistics),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.expression), style = MaterialTheme.typography.labelMedium)
                        Text(roll.expression, fontWeight = FontWeight.Bold)
                        if (formula.expression != roll.expression) {
                            Text(
                                stringResource(R.string.resolved_expression),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(formula.expression, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${stringResource(R.string.level)} ${character.level}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                outcome?.let { rolled ->
                    formula.subgroupResults(rolled).forEachIndexed { index, grouped ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        grouped.subgroup.name.ifBlank { "${roll.name} ${index + 1}" },
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        grouped.subgroup.expression,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Text(
                                        grouped.result.detail(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                                Text(
                                    grouped.result.total.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                    }
                }
                ResultContentV2(outcome)
            }
        }
    }
}

private fun DiceExpression.previewResult(): DiceRollResult {
    val components = diceShape().map { shape ->
        DiceComponent(
            count = shape.count,
            sides = shape.sides,
            sign = shape.sign,
            rolls = List(shape.count) { ((shape.sides + 1) / 2).coerceAtLeast(1) },
        )
    }
    return DiceRollResult(
        total = components.sumOf { it.subtotal },
        components = components,
        constantTotal = 0,
    )
}

private fun decodeTableBitmap(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}

@Composable
private fun RollTotalReveal(total: Int, aboveAverage: Boolean) {
    val scale = remember(total) { Animatable(0.62f) }
    LaunchedEffect(total) { scale.animateTo(1f, animationSpec = tween(durationMillis = 520)) }
    Box(contentAlignment = Alignment.Center) {
        if (aboveAverage) CelebrationBurst()
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (aboveAverage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 14.dp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = scale.value
            },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.total), style = MaterialTheme.typography.labelLarge)
                Text(
                    total.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    color = if (aboveAverage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CelebrationBurst() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, animationSpec = tween(1250)) }
    val colors = listOf(Color(0xFFFFC857), Color(0xFF43D9AD), Color(0xFF7C6CFF), Color(0xFFFF5D8F))
    Canvas(Modifier.size(300.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        repeat(24) { index ->
            val angle = index * (Math.PI * 2.0 / 24.0)
            val startRadius = 48f + progress.value * 34f
            val endRadius = 62f + progress.value * 92f
            val start = Offset(
                center.x + cos(angle).toFloat() * startRadius,
                center.y + sin(angle).toFloat() * startRadius,
            )
            val end = Offset(
                center.x + cos(angle).toFloat() * endRadius,
                center.y + sin(angle).toFloat() * endRadius,
            )
            drawLine(
                color = colors[index % colors.size].copy(alpha = 1f - progress.value * 0.78f),
                start = start,
                end = end,
                strokeWidth = 7f,
            )
        }
    }
}

@Composable
private fun ResultContentV2(outcome: DiceRollResult?) {
    if (outcome == null) {
        PremiumCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.waiting_for_throw), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    PremiumCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.total), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                outcome.total.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
            )
            outcome.components.forEach { component ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${when (component.sign) {
                                1 -> ""
                                -1 -> "−"
                                else -> "${component.sign}×"
                            }}${component.count}d${component.sides}",
                            modifier = Modifier.width(72.dp),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(component.rolls.joinToString("  ·  "), modifier = Modifier.weight(1f))
                    }
                }
            }
            if (outcome.constantTotal != 0) {
                Text(
                    "${if (outcome.constantTotal > 0) "+" else ""}${outcome.constantTotal}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenV2(
    settings: AppSettings,
    selectedLanguage: String,
    languageOptions: List<AppLanguageOption>,
    cloudAccountState: CloudAccountState,
    accountFlowBusy: Boolean,
    accountFailure: GoogleConnectionFailure?,
    syncStatus: SyncStatus,
    cloudDeleteBusy: Boolean,
    cloudDeleteFailed: Boolean,
    onSettingsChanged: (AppSettings) -> Unit,
    onSyncNow: () -> Unit,
    onConnectGoogle: () -> Unit,
    onDisconnectGoogle: () -> Unit,
    onDeleteCloudData: () -> Unit,
    onLanguageChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    var languageMenu by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var confirmCloudDelete by remember { mutableStateOf(false) }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text(stringResource(R.string.disconnect_google_title)) },
            text = { Text(stringResource(R.string.disconnect_google_body)) },
            confirmButton = {
                Button(onClick = {
                    confirmDisconnect = false
                    onDisconnectGoogle()
                }) {
                    Text(stringResource(R.string.disconnect))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (confirmCloudDelete) {
        AlertDialog(
            onDismissRequest = { confirmCloudDelete = false },
            title = { Text(stringResource(R.string.delete_cloud_data_title)) },
            text = { Text(stringResource(R.string.delete_cloud_data_body)) },
            confirmButton = {
                Button(onClick = {
                    confirmCloudDelete = false
                    onDeleteCloudData()
                }) {
                    Text(stringResource(R.string.delete_cloud_data_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCloudDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    ArcaneBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = transparentTopBarColors(),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Tune, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold)
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    PremiumCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (cloudAccountState.googleConnected) Icons.Rounded.Cloud else Icons.Rounded.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.account_and_sync), fontWeight = FontWeight.Bold)
                            }
                            if (cloudAccountState.googleConnected) {
                                Text(
                                    cloudAccountState.account?.displayName?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.google_account),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                cloudAccountState.account?.email?.let {
                                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    stringResource(R.string.drive_appdata_authorized),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (cloudAccountState.initialReconciliationPending) {
                                    Text(
                                        stringResource(R.string.first_sync_reconcile_pending),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                Text(
                                    stringResource(syncStatusLabel(syncStatus.kind)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (syncStatus.kind) {
                                        SyncStatusKind.ERROR -> MaterialTheme.colorScheme.error
                                        SyncStatusKind.CONFLICT -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                syncStatus.lastSuccessfulSyncAt?.let { timestamp ->
                                    Text(
                                        stringResource(
                                            R.string.last_successful_sync,
                                            DateFormat.getDateTimeInstance().format(Date(timestamp)),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (syncStatus.pendingCount > 0) {
                                    Text(
                                        stringResource(R.string.sync_pending_count, syncStatus.pendingCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                syncStatus.error?.let { error ->
                                    Text(
                                        stringResource(syncErrorLabel(error)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                if (syncStatus.autoResolvedCount > 0) {
                                    Text(
                                        stringResource(R.string.sync_conflicts_auto_resolved, syncStatus.autoResolvedCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                Text(stringResource(R.string.conflict_policy), fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(R.string.conflict_policy_help),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ConflictPolicy.entries.forEach { policy ->
                                        FilterChip(
                                            selected = settings.conflictPolicy == policy,
                                            onClick = { onSettingsChanged(settings.copy(conflictPolicy = policy)) },
                                            label = {
                                                Text(
                                                    stringResource(
                                                        if (policy == ConflictPolicy.ASK) R.string.conflict_policy_ask
                                                        else R.string.conflict_policy_latest,
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = onSyncNow,
                                    enabled = syncStatus.kind != SyncStatusKind.SYNCING,
                                ) {
                                    Text(stringResource(R.string.sync_now))
                                }
                                OutlinedButton(
                                    onClick = { confirmDisconnect = true },
                                    enabled = !accountFlowBusy && !cloudDeleteBusy,
                                ) {
                                    Text(stringResource(if (accountFlowBusy) R.string.disconnecting else R.string.disconnect))
                                }
                                Text(
                                    stringResource(R.string.cloud_data_contents),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(
                                    onClick = { confirmCloudDelete = true },
                                    enabled = !accountFlowBusy && !cloudDeleteBusy,
                                ) {
                                    Text(
                                        stringResource(
                                            if (cloudDeleteBusy) R.string.deleting_cloud_data
                                            else R.string.delete_cloud_data,
                                        ),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                if (cloudDeleteFailed) {
                                    Text(
                                        stringResource(R.string.delete_cloud_data_failed),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            } else {
                                Text(stringResource(R.string.standalone_mode), fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(R.string.standalone_settings_help),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(onClick = onConnectGoogle, enabled = !accountFlowBusy) {
                                    Icon(Icons.Rounded.AccountCircle, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(if (accountFlowBusy) R.string.connecting_google else R.string.continue_with_google))
                                }
                            }
                            accountFailure?.let {
                                Text(
                                    accountFailureText(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                item {
                    PremiumCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.theme), fontWeight = FontWeight.Bold)
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
                }

                item { ToggleSettingCard(stringResource(R.string.shake_enabled), settings.shakeEnabled) { onSettingsChanged(settings.copy(shakeEnabled = it)) } }
                item { ToggleSettingCard(stringResource(R.string.animations_enabled), settings.animationsEnabled) { onSettingsChanged(settings.copy(animationsEnabled = it)) } }
                item { ToggleSettingCard(stringResource(R.string.show_roll_button), settings.showRollButton) { onSettingsChanged(settings.copy(showRollButton = it)) } }

                if (settings.showRollButton) {
                    item {
                        PremiumCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.button_position), fontWeight = FontWeight.Bold)
                                RollButtonPosition.entries.forEach { position ->
                                    FilterChip(
                                        selected = settings.rollButtonPosition == position,
                                        onClick = { onSettingsChanged(settings.copy(rollButtonPosition = position)) },
                                        label = { Text(positionLabel(position)) },
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    PremiumCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.log_retention), fontWeight = FontWeight.Bold)
                            listOf(
                                20 to R.string.keep_20,
                                50 to R.string.keep_50,
                                100 to R.string.keep_100,
                                0 to R.string.keep_all,
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = settings.logRetention == value,
                                    onClick = { onSettingsChanged(settings.copy(logRetention = value)) },
                                    label = { Text(stringResource(label)) },
                                )
                            }
                        }
                    }
                }

                item {
                    PremiumCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.language), fontWeight = FontWeight.Bold)
                            Box {
                                OutlinedButton(onClick = { languageMenu = true }) {
                                    Text(
                                        if (selectedLanguage == AppLocaleManager.SYSTEM) {
                                            stringResource(R.string.system_language)
                                        } else {
                                            languageOptions.firstOrNull { it.code == selectedLanguage }?.nativeName ?: selectedLanguage
                                        },
                                    )
                                }
                                DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
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
                            Text(
                                stringResource(R.string.all_local),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictDialogV2(
    conflict: SyncConflict,
    onKeepLocal: () -> Unit,
    onUseRemote: () -> Unit,
) {
    val localTime = conflict.localUpdatedAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
    val remoteTime = conflict.remoteUpdatedAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
    val changedAreaLabels = conflict.changedAreas.map { area ->
        stringResource(conflictAreaLabelResource(area))
    }
    val changedAreasText = changedAreaLabels.joinToString(", ")
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                conflict.characterName?.let { stringResource(R.string.sync_conflict_character_title, it) }
                    ?: stringResource(R.string.sync_conflict_settings_title),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sync_conflict_body))
                localTime?.let { Text(stringResource(R.string.sync_conflict_local_version, it)) }
                remoteTime?.let { Text(stringResource(R.string.sync_conflict_drive_version, it)) }
                if (conflict.changedAreas.isNotEmpty()) {
                    Text(
                        stringResource(R.string.sync_conflict_changes) + ": " +
                            changedAreasText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onKeepLocal) { Text(stringResource(R.string.keep_this_device)) }
        },
        dismissButton = {
            TextButton(onClick = onUseRemote) { Text(stringResource(R.string.use_drive_version)) }
        },
    )
}

private fun conflictAreaLabelResource(area: ConflictArea): Int = when (area) {
        ConflictArea.PROFILE -> R.string.conflict_area_profile
        ConflictArea.IMAGE -> R.string.conflict_area_image
        ConflictArea.MODIFIERS -> R.string.conflict_area_modifiers
        ConflictArea.ROLLS -> R.string.conflict_area_rolls
        ConflictArea.DICE_STYLES -> R.string.conflict_area_styles
        ConflictArea.HISTORY -> R.string.conflict_area_history
        ConflictArea.DELETION -> R.string.conflict_area_deletion
        ConflictArea.SETTINGS -> R.string.conflict_area_settings
    }

private fun syncStatusLabel(status: SyncStatusKind): Int = when (status) {
    SyncStatusKind.LOCAL_ONLY -> R.string.sync_status_local_only
    SyncStatusKind.SYNCED -> R.string.sync_status_synced
    SyncStatusKind.PENDING -> R.string.sync_status_pending
    SyncStatusKind.SYNCING -> R.string.sync_status_syncing
    SyncStatusKind.ERROR -> R.string.sync_status_error
    SyncStatusKind.CONFLICT -> R.string.sync_status_conflict
}

private fun syncErrorLabel(error: SyncErrorKind): Int = when (error) {
    SyncErrorKind.AUTHORIZATION -> R.string.sync_error_authorization
    SyncErrorKind.TRANSIENT -> R.string.sync_error_transient
    SyncErrorKind.SCHEMA -> R.string.sync_error_schema
    SyncErrorKind.REMOTE_PROTOCOL -> R.string.sync_error_remote
    SyncErrorKind.UNKNOWN -> R.string.sync_error_unknown
}

@Composable
private fun accountFailureText(failure: GoogleConnectionFailure): String = stringResource(
    when (failure) {
        GoogleConnectionFailure.CONFIGURATION -> R.string.google_configuration_missing
        GoogleConnectionFailure.SIGN_IN -> R.string.google_sign_in_failed
        GoogleConnectionFailure.DRIVE_AUTHORIZATION -> R.string.google_drive_authorization_failed
        GoogleConnectionFailure.DISCONNECT -> R.string.google_disconnect_failed
    },
)

@Composable
private fun ToggleSettingCard(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    PremiumCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsScreenV2(
    character: CharacterProfile,
    logs: List<RollLog>,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    ArcaneBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = transparentTopBarColors(),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    title = { Text("${stringResource(R.string.logs)} · ${character.name}", fontWeight = FontWeight.Bold) },
                    actions = {
                        if (logs.isNotEmpty()) {
                            TextButton(onClick = onClear) { Text(stringResource(R.string.clear_logs)) }
                        }
                    },
                )
            },
        ) { padding ->
            if (logs.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_logs))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(logs, key = { it.id }) { log ->
                        PremiumCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Casino, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Text(log.rollName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    Text(log.total.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                                }
                                Text(log.expression, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (log.detail.isNotBlank()) {
                                    Text(log.detail, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(log.timestamp)),
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
}

@Composable
private fun CharacterDialogV2(
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_character)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.character_name)) }, singleLine = true)
                OutlinedTextField(value = tag, onValueChange = { tag = it }, label = { Text(stringResource(R.string.character_tag)) }, singleLine = true)
                OutlinedTextField(
                    value = levelText,
                    onValueChange = { levelText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.level)) },
                    singleLine = true,
                )
                OutlinedButton(onClick = { imageLauncher.launch(arrayOf("image/*")) }) {
                    Text(if (image == null) stringResource(R.string.choose_image) else stringResource(R.string.image_selected))
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
                enabled = name.isNotBlank() && level != null && level >= 1,
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
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ModifierDialogV2(
    existing: CharacterModifier?,
    characterId: String,
    nextOrder: Int,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (CharacterModifier) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var valueText by remember(existing?.id) { mutableStateOf(existing?.value?.toString() ?: "0") }
    val value = valueText.toIntOrNull()
    val normalized = name.trim()
    val duplicate = existingNames.any { it.equals(normalized, ignoreCase = true) }
    val reserved = normalized.equals(RollFormulaResolver.LEVEL_VARIABLE, ignoreCase = true)
    val valid = normalized.isNotBlank() && value != null && !duplicate && !reserved

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.new_modifier else R.string.edit_modifier)) },
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
                        valueText = input.filterIndexed { index, c -> c.isDigit() || (c == '-' && index == 0) }
                    },
                    label = { Text(stringResource(R.string.modifier_value)) },
                    singleLine = true,
                    supportingText = {
                        if (duplicate || reserved) Text(stringResource(R.string.duplicate_modifier))
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
                            name = normalized,
                            value = value ?: 0,
                            order = existing?.order ?: nextOrder,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun GroupDialogV2(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
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
            Button(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun RollBuilderScreenV2(
    title: String,
    character: CharacterProfile,
    modifiers: List<CharacterModifier>,
    groups: List<RollGroup>,
    existing: RollDefinition?,
    initialGroupId: String? = null,
    onDismiss: () -> Unit,
    onSave: (RollDefinition) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var groupId by remember(existing?.id, initialGroupId) { mutableStateOf(existing?.groupId ?: initialGroupId) }
    var groupMenu by remember(existing?.id) { mutableStateOf(false) }
    var advancedMode by remember(existing?.id) { mutableStateOf(false) }
    var subgroups by remember(existing?.id) {
        mutableStateOf(
            existing?.subgroups?.takeIf { it.isNotEmpty() } ?: listOf(
                RollSubgroup(
                    id = UUID.randomUUID().toString(),
                    expression = existing?.expression ?: "1d20",
                ),
            ),
        )
    }
    val guidedExpression = remember(subgroups) {
        runCatching { RollFormulaResolver.canonicalExpression(subgroups) }.getOrDefault("")
    }
    var advancedExpression by remember(existing?.id) {
        mutableStateOf(TextFieldValue(existing?.expression ?: guidedExpression.ifBlank { "1d20" }))
    }
    LaunchedEffect(guidedExpression, advancedMode) {
        if (!advancedMode && guidedExpression.isNotBlank()) {
            advancedExpression = TextFieldValue(guidedExpression)
        }
    }

    val groupsValid = subgroups.isNotEmpty() && subgroups.all { subgroup ->
        subgroup.expression.isNotBlank() &&
            RollFormulaResolver.validateTemplate(subgroup.expression, character.level, modifiers)
    }
    val expressionToSave = if (advancedMode) advancedExpression.text.trim() else guidedExpression
    val expressionValid = RollFormulaResolver.validateTemplate(expressionToSave, character.level, modifiers)
    val valid = name.isNotBlank() && expressionValid && (advancedMode || groupsValid)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
        }

        item {
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.roll_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box {
                        OutlinedButton(onClick = { groupMenu = true }) {
                            Text(groups.firstOrNull { it.id == groupId }?.name ?: stringResource(R.string.ungrouped))
                        }
                        DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !advancedMode,
                            onClick = {
                                guidedSubgroupsFromAdvancedExpression(
                                    expression = advancedExpression.text,
                                    current = subgroups,
                                    level = character.level,
                                    modifiers = modifiers,
                                )?.let { imported ->
                                    subgroups = imported
                                    advancedMode = false
                                }
                            },
                            label = { Text(stringResource(R.string.guided_builder)) },
                        )
                        FilterChip(
                            selected = advancedMode,
                            onClick = {
                                if (!advancedMode) {
                                    advancedExpression = TextFieldValue(guidedExpression)
                                }
                                advancedMode = true
                            },
                            label = { Text(stringResource(R.string.advanced_formula)) },
                        )
                    }
                }
            }
        }

        if (advancedMode) {
            item {
                PremiumCard(Modifier.fillMaxWidth()) {
                    ParameterizedExpressionField(
                        value = advancedExpression,
                        onValueChange = { advancedExpression = it },
                        label = stringResource(R.string.expression),
                        modifiers = modifiers,
                        isValid = expressionValid,
                        helper = stringResource(R.string.expression_hint),
                        errorText = stringResource(R.string.invalid_expression),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitleV2(
                        title = stringResource(R.string.roll_subgroups),
                        subtitle = stringResource(R.string.roll_subgroups_help),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            subgroups = subgroups + RollSubgroup(
                                id = UUID.randomUUID().toString(),
                                expression = "1d6",
                            )
                        },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_subgroup))
                    }
                }
            }

            itemsIndexed(subgroups, key = { _, subgroup -> subgroup.id }) { index, subgroup ->
                PremiumCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (index > 0) {
                                FilterChip(
                                    selected = subgroup.operator == RollSubgroupOperator.ADD,
                                    onClick = {
                                        subgroups = subgroups.map {
                                            if (it.id == subgroup.id) it.copy(operator = RollSubgroupOperator.ADD) else it
                                        }
                                    },
                                    label = { Text("+") },
                                )
                                Spacer(Modifier.width(6.dp))
                                FilterChip(
                                    selected = subgroup.operator == RollSubgroupOperator.SUBTRACT,
                                    onClick = {
                                        subgroups = subgroups.map {
                                            if (it.id == subgroup.id) it.copy(operator = RollSubgroupOperator.SUBTRACT) else it
                                        }
                                    },
                                    label = { Text("−") },
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            OutlinedTextField(
                                value = subgroup.name,
                                onValueChange = { updatedName ->
                                    subgroups = subgroups.map {
                                        if (it.id == subgroup.id) it.copy(name = updatedName) else it
                                    }
                                },
                                label = { Text(stringResource(R.string.subgroup_name)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            if (subgroups.size > 1) {
                                IconButton(onClick = { subgroups = subgroups.filterNot { it.id == subgroup.id } }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                                }
                            }
                        }
                        if (subgroups.size > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    enabled = index > 0,
                                    onClick = { subgroups = moveRollSubgroup(subgroups, index, -1) },
                                ) {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.move_up),
                                    )
                                }
                                IconButton(
                                    enabled = index < subgroups.lastIndex,
                                    onClick = { subgroups = moveRollSubgroup(subgroups, index, 1) },
                                ) {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.move_down),
                                    )
                                }
                            }
                        }
                        GuidedExpressionEditorV2(
                            value = subgroup.expression,
                            character = character,
                            modifiers = modifiers,
                            onValueChange = { updatedExpression ->
                                subgroups = subgroups.map {
                                    if (it.id == subgroup.id) it.copy(expression = updatedExpression) else it
                                }
                            },
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(stringResource(R.string.resolved_expression), style = MaterialTheme.typography.labelMedium)
                        Text(expressionToSave, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val preserveSubgroups = !advancedMode || expressionToSave == guidedExpression
                        val saved = (existing ?: RollDefinition(
                            id = "",
                            characterId = character.id,
                            name = "",
                            expression = "1d20",
                        )).copy(
                            name = name.trim(),
                            expression = expressionToSave,
                            groupId = groupId,
                            subgroups = if (preserveSubgroups) subgroups else emptyList(),
                        )
                        val slots = runCatching {
                            val resolved = RollFormulaResolver.resolve(character, modifiers, saved).expression
                            DiceAppearanceResolver.slotsFor(DiceExpression.parse(resolved))
                        }.getOrDefault(emptyList())
                        onSave(
                            saved.copy(
                                diceAppearance = DiceAppearanceResolver.reconcileSlots(saved.diceAppearance, slots),
                            ),
                        )
                    },
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

@Composable
private fun GuidedExpressionEditorV2(
    value: String,
    character: CharacterProfile,
    modifiers: List<CharacterModifier>,
    onValueChange: (String) -> Unit,
) {
    var countText by remember { mutableStateOf("1") }
    var sides by remember { mutableStateOf(20) }
    var sidesMenu by remember { mutableStateOf(false) }
    var subtractNext by remember { mutableStateOf(false) }
    var constantText by remember { mutableStateOf("1") }
    var multiplierText by remember { mutableStateOf("2") }
    val valid = RollFormulaResolver.validateTemplate(value, character.level, modifiers)

    fun appendTerm(term: String) {
        onValueChange(
            when {
                value.isBlank() && subtractNext -> "-$term"
                value.isBlank() -> term
                subtractNext -> "$value-$term"
                else -> "$value+$term"
            },
        )
    }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        isError = value.isNotBlank() && !valid,
        label = { Text(stringResource(R.string.expression)) },
        supportingText = {
            Text(stringResource(if (valid) R.string.expression_hint else R.string.invalid_expression))
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = !subtractNext, onClick = { subtractNext = false }, label = { Text("+") })
        FilterChip(selected = subtractNext, onClick = { subtractNext = true }, label = { Text("−") })
        OutlinedTextField(
            value = countText,
            onValueChange = { countText = it.filter(Char::isDigit).take(3) },
            label = { Text("#") },
            singleLine = true,
            modifier = Modifier.width(68.dp),
        )
        Box {
            OutlinedButton(onClick = { sidesMenu = true }) { Text("d$sides") }
            DropdownMenu(expanded = sidesMenu, onDismissRequest = { sidesMenu = false }) {
                DiceExpression.supportedSides.sorted().forEach { option ->
                    DropdownMenuItem(
                        text = { Text("d$option") },
                        onClick = {
                            sides = option
                            sidesMenu = false
                        },
                    )
                }
            }
        }
        Button(
            enabled = (countText.toIntOrNull() ?: 0) in 1..100,
            onClick = { appendTerm("${countText.toInt()}d$sides") },
        ) { Text(stringResource(R.string.add)) }
    }

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { appendTerm("{level}d$sides") }) { Text("{level}d$sides") }
        TextButton(onClick = { appendTerm("{level}") }) { Text("{level}") }
        modifiers.forEach { modifier ->
            TextButton(onClick = { appendTerm("{${modifier.name}}") }) { Text("{${modifier.name}}") }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = constantText,
            onValueChange = { constantText = it.filter { char -> char.isDigit() }.take(5) },
            label = { Text(stringResource(R.string.constant_value)) },
            singleLine = true,
            modifier = Modifier.width(108.dp),
        )
        OutlinedButton(
            enabled = constantText.toIntOrNull() != null,
            onClick = { appendTerm(constantText) },
        ) { Text(stringResource(R.string.add)) }
        IconButton(onClick = { onValueChange("") }) {
            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.clear_expression))
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = multiplierText,
            onValueChange = { raw ->
                multiplierText = raw.filterIndexed { index, char -> char.isDigit() || (index == 0 && char == '-') }.take(5)
            },
            label = { Text(stringResource(R.string.multiplier)) },
            singleLine = true,
            modifier = Modifier.width(112.dp),
        )
        OutlinedButton(
            enabled = value.isNotBlank() && multiplierText.toIntOrNull() != null,
            onClick = { onValueChange("($value)*${multiplierText.toInt()}") },
        ) { Text("×") }
        TextButton(
            enabled = value.isNotBlank(),
            onClick = { onValueChange("($value)*{level}") },
        ) { Text("× {level}") }
    }
}

@Composable
internal fun RollDialogV2(
    title: String,
    character: CharacterProfile,
    modifiers: List<CharacterModifier>,
    groups: List<RollGroup>,
    existing: RollDefinition?,
    initialGroupId: String? = null,
    onDismiss: () -> Unit,
    onSave: (RollDefinition) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var expression by remember(existing?.id) { mutableStateOf(TextFieldValue(existing?.expression ?: "1d20")) }
    var groupId by remember(existing?.id, initialGroupId) { mutableStateOf(existing?.groupId ?: initialGroupId) }
    var groupMenu by remember(existing?.id) { mutableStateOf(false) }

    val validExpression = RollFormulaResolver.validateTemplate(expression.text, character.level, modifiers)
    val valid = name.isNotBlank() && validExpression

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.roll_name)) },
                    singleLine = true,
                )
                ParameterizedExpressionField(
                    value = expression,
                    onValueChange = { expression = it },
                    label = stringResource(R.string.expression),
                    modifiers = modifiers,
                    isValid = validExpression,
                    helper = stringResource(R.string.expression_hint),
                    errorText = stringResource(R.string.invalid_expression),
                )
                Box {
                    OutlinedButton(onClick = { groupMenu = true }) {
                        Text(groups.firstOrNull { it.id == groupId }?.name ?: stringResource(R.string.ungrouped))
                    }
                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
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
                    val saved = (existing ?: RollDefinition(
                            id = "",
                            characterId = character.id,
                            name = "",
                            expression = "",
                        )).copy(
                            name = name.trim(),
                            expression = expression.text.trim(),
                            groupId = groupId,
                            subgroups = if (expression.text.trim() == existing?.expression) {
                                existing.subgroups
                            } else {
                                emptyList()
                            },
                        )
                    val slots = runCatching {
                        val resolved = RollFormulaResolver.resolve(character, modifiers, saved).expression
                        DiceAppearanceResolver.slotsFor(DiceExpression.parse(resolved))
                    }.getOrDefault(emptyList())
                    onSave(
                        saved.copy(
                            diceAppearance = DiceAppearanceResolver.reconcileSlots(saved.diceAppearance, slots),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun LevelRuleDialogV2(
    character: CharacterProfile,
    modifiers: List<CharacterModifier>,
    onDismiss: () -> Unit,
    onSave: (RollLevelRule) -> Unit,
) {
    var kind by remember { mutableStateOf(LevelRuleKind.FROM_LEVEL) }
    var triggerText by remember { mutableStateOf("2") }
    var expression by remember { mutableStateOf(TextFieldValue("")) }
    val trigger = triggerText.toIntOrNull()
    val validExpression = RollFormulaResolver.validateTemplate(expression.text, character.level, modifiers)
    val valid = trigger != null && trigger >= 1 && validExpression

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
                    onValueChange = { triggerText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(if (kind == LevelRuleKind.FROM_LEVEL) R.string.trigger_level else R.string.interval_levels)) },
                    singleLine = true,
                )
                ParameterizedExpressionField(
                    value = expression,
                    onValueChange = { expression = it },
                    label = stringResource(R.string.rule_expression),
                    modifiers = modifiers,
                    isValid = validExpression,
                    helper = stringResource(R.string.expression_hint),
                    errorText = stringResource(R.string.invalid_expression),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        RollLevelRule(
                            id = UUID.randomUUID().toString(),
                            kind = kind,
                            trigger = trigger ?: 1,
                            expression = expression.text.trim(),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun positionLabel(position: RollButtonPosition): String = stringResource(
    when (position) {
        RollButtonPosition.TOP_LEFT -> R.string.top_left
        RollButtonPosition.TOP_CENTER -> R.string.top_center
        RollButtonPosition.TOP_RIGHT -> R.string.top_right
        RollButtonPosition.BOTTOM_LEFT -> R.string.bottom_left
        RollButtonPosition.BOTTOM_CENTER -> R.string.bottom_center
        RollButtonPosition.BOTTOM_RIGHT -> R.string.bottom_right
    },
)

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
private fun transparentTopBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
)

private fun dashboardEntries(
    groups: List<RollGroup>,
    ungroupedRolls: List<RollDefinition>,
): List<DashboardEntry> =
    (groups.map { DashboardEntry.GroupEntry(it) } + ungroupedRolls.map { DashboardEntry.RollEntry(it) })
        .sortedWith(compareBy<DashboardEntry> { it.order }.thenBy { it.key })

private fun updateRoll(
    data: AppData,
    characterId: String,
    existing: RollDefinition,
    updated: RollDefinition,
): AppData {
    val targetGroupId = updated.groupId
    val replaced = data.copy(
        rolls = data.rolls.map {
            if (it.id == updated.id) updated.copy(groupId = existing.groupId, order = existing.order) else it
        },
    )
    return if (existing.groupId == targetGroupId) {
        replaced
    } else {
        DashboardDataOperations.changeRollGroup(replaced, characterId, updated.id, targetGroupId)
    }
}

private fun modifierIsReferenced(
    modifierName: String,
    rolls: List<RollDefinition>,
): Boolean {
    val variableRegex = Regex("""\{([^{}]+)\}""")
    return rolls.any { roll ->
        (listOf(roll.expression) + roll.levelRules.map { it.expression }).any { expression ->
            variableRegex.findAll(expression).any { match ->
                match.groupValues[1].trim().equals(modifierName.trim(), ignoreCase = true)
            }
        }
    }
}
