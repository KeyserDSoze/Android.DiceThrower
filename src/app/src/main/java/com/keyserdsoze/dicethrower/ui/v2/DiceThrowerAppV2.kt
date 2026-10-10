package com.keyserdsoze.dicethrower.ui.v2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.rounded.Remove
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.AppLanguageOption
import com.keyserdsoze.dicethrower.AppLocaleManager
import com.keyserdsoze.dicethrower.GoogleConnectionFailure
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.data.CharacterImageAssetStore
import com.keyserdsoze.dicethrower.data.CloudAccountState
import com.keyserdsoze.dicethrower.data.AppDataValidator
import com.keyserdsoze.dicethrower.data.DashboardDataOperations
import com.keyserdsoze.dicethrower.data.LocalStore
import com.keyserdsoze.dicethrower.data.sync.ConflictArea
import com.keyserdsoze.dicethrower.data.sync.SyncConflict
import com.keyserdsoze.dicethrower.data.sync.SyncConflictResolution
import com.keyserdsoze.dicethrower.data.sync.SyncErrorKind
import com.keyserdsoze.dicethrower.data.sync.SyncStatus
import com.keyserdsoze.dicethrower.data.sync.SyncStatusKind
import com.keyserdsoze.dicethrower.dice.DiceAppearanceResolver
import com.keyserdsoze.dicethrower.dice.DoubleRollEngine
import com.keyserdsoze.dicethrower.dice.DoubleRollVisualPlanner
import com.keyserdsoze.dicethrower.dice.DoubleRollEvaluation
import com.keyserdsoze.dicethrower.dice.EffectExecutionResult
import com.keyserdsoze.dicethrower.dice.EffectRollSnapshot
import com.keyserdsoze.dicethrower.dice.EffectSequenceExecutor
import com.keyserdsoze.dicethrower.dice.EffectRuntimeHistory
import com.keyserdsoze.dicethrower.dice.EffectsVisualTimeline
import com.keyserdsoze.dicethrower.dice.EffectsVisualStage
import com.keyserdsoze.dicethrower.dice.DiceComponent
import com.keyserdsoze.dicethrower.dice.DiceExpression
import com.keyserdsoze.dicethrower.dice.DiceRollResult
import com.keyserdsoze.dicethrower.dice.DiceRollVisualEvent
import com.keyserdsoze.dicethrower.dice.RollFormulaResolver
import com.keyserdsoze.dicethrower.dice.ResolvedRollSubgroupResult
import com.keyserdsoze.dicethrower.dice.subgroupIdByComponentIndex
import com.keyserdsoze.dicethrower.dice.subgroupResults
import com.keyserdsoze.dicethrower.dice.effectPartExpressions
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterImageRef
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.ConflictPolicy
import com.keyserdsoze.dicethrower.model.DoubleRollMode
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.RollVisualEffectsSettings
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.DiceTableTheme
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollButtonPosition
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollLevelAvailability
import com.keyserdsoze.dicethrower.model.RollGroup
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.RollLog
import com.keyserdsoze.dicethrower.model.RollLogPart
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import com.keyserdsoze.dicethrower.model.ThemeMode
import com.keyserdsoze.dicethrower.sensor.ShakeDetector
import com.keyserdsoze.dicethrower.ui.dice3d.Dice3DScene
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlin.random.Random

internal enum class RouteV2 { CHARACTERS, DICE_LIBRARY, CHARACTER, GROUP, ROLL, SETTINGS, LOGS }

internal fun previousRouteFor(
    route: RouteV2,
    hasRollReturnGroup: Boolean = false,
): RouteV2? = when (route) {
    RouteV2.CHARACTERS -> null
    RouteV2.DICE_LIBRARY -> RouteV2.CHARACTERS
    RouteV2.CHARACTER -> RouteV2.CHARACTERS
    RouteV2.GROUP -> RouteV2.CHARACTER
    RouteV2.ROLL -> if (hasRollReturnGroup) RouteV2.GROUP else RouteV2.CHARACTER
    RouteV2.SETTINGS -> RouteV2.CHARACTERS
    RouteV2.LOGS -> RouteV2.CHARACTER
}

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

    // An explicit canonical sum of parenthesized Roll Parts still represents
    // distinct results. Preserve their names and stable IDs when editing text.
    val topLevel = FormulaComposer.parse(candidate, level, modifiers)
    val separateParts = topLevel?.takeIf { terms ->
        terms.size >= 2 && terms.all { term ->
            val body = when {
                term.isGroup && term.multiplier == null -> FormulaComposer.serialize(term.grouped)
                !term.isGroup && term.expression.startsWith("(") && term.expression.endsWith(")") ->
                    term.expression.drop(1).dropLast(1)
                else -> null
            }
            body != null && RollFormulaResolver.validateTemplate(body, level, modifiers)
        }
    }
    if (separateParts != null) {
        return separateParts.mapIndexed { index, term ->
            val partExpression = if (term.isGroup) FormulaComposer.serialize(term.grouped)
                else term.expression.drop(1).dropLast(1)
            val prior = current.firstOrNull { it.expression.trim() == partExpression }
                ?: current.getOrNull(index)
            RollSubgroup(
                id = prior?.id ?: idFactory(),
                name = prior?.name.orEmpty(),
                expression = partExpression,
                operator = if (term.sign == '-') RollSubgroupOperator.SUBTRACT else RollSubgroupOperator.ADD,
            )
        }
    }

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
    var route by rememberSaveable { mutableStateOf(RouteV2.CHARACTERS) }
    var selectedCharacterId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRollId by rememberSaveable { mutableStateOf<String?>(null) }
    var rollReturnGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var editingActiveRoll by rememberSaveable { mutableStateOf(false) }
    val stateHolder = rememberSaveableStateHolder()

    fun persist(updated: AppData) {
        data = store.saveData(updated)
        onLocalDataChanged()
    }

    fun navigateBack() {
        val hasRollReturnGroup = rollReturnGroupId != null &&
            selectedCharacterId != null &&
            data.groups.any { group ->
                group.id == rollReturnGroupId && group.characterId == selectedCharacterId
            }
        when (previousRouteFor(route, hasRollReturnGroup)) {
            RouteV2.GROUP -> {
                selectedGroupId = rollReturnGroupId
                route = RouteV2.GROUP
            }
            RouteV2.CHARACTERS -> route = RouteV2.CHARACTERS
            RouteV2.DICE_LIBRARY -> route = RouteV2.DICE_LIBRARY
            RouteV2.CHARACTER -> route = RouteV2.CHARACTER
            RouteV2.SETTINGS -> route = RouteV2.SETTINGS
            RouteV2.LOGS -> route = RouteV2.LOGS
            RouteV2.ROLL -> route = RouteV2.ROLL
            null -> Unit
        }
    }

    BackHandler(enabled = route != RouteV2.CHARACTERS) {
        if (route == RouteV2.ROLL && editingActiveRoll) {
            editingActiveRoll = false
        } else {
            navigateBack()
        }
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

    // Keep each destination's LazyColumn position and saveable UI state when it leaves composition.
    val pageKey = when (route) {
        RouteV2.CHARACTERS -> "characters"
        RouteV2.DICE_LIBRARY -> "dice_library"
        RouteV2.CHARACTER -> "character:${selectedCharacterId}:${editMode}"
        RouteV2.GROUP -> "group:${selectedCharacterId}:${selectedGroupId}:${editMode}"
        RouteV2.ROLL -> "roll:${selectedRollId}"
        RouteV2.SETTINGS -> "settings"
        RouteV2.LOGS -> "logs:${selectedCharacterId}"
    }
    stateHolder.SaveableStateProvider(pageKey) {
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
            onCustomizeDice = {
                selectedCharacterId = data.characters.firstOrNull { it.id == selectedCharacterId }?.id
                    ?: data.characters.firstOrNull()?.id
                route = RouteV2.DICE_LIBRARY
            },
        )

        RouteV2.DICE_LIBRARY -> DiceCatalogScreenV2(
            data = data,
            selectedCharacterId = selectedCharacterId,
            onSelectCharacter = { selectedCharacterId = it },
            onDataChanged = ::persist,
            onBack = ::navigateBack,
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
                    onBack = ::navigateBack,
                    onToggleMode = { editMode = !editMode },
                    onOpenLogs = { route = RouteV2.LOGS },
                    onOpenRoll = { rollId ->
                        selectedRollId = rollId
                        rollReturnGroupId = null
                        editingActiveRoll = false
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
                    onBack = ::navigateBack,
                    onOpenRoll = { rollId ->
                        selectedRollId = rollId
                        rollReturnGroupId = group.id
                        editingActiveRoll = false
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
            if (character == null || roll == null || roll.characterId != character.id ||
                (!editingActiveRoll && !RollLevelAvailability.isAvailable(roll, character.level))) {
                route = RouteV2.CHARACTER
            } else {
                val characterModifiers = data.modifiers.filter { it.characterId == character.id }
                if (editingActiveRoll) {
                    RollBuilderScreenV2(
                        title = stringResource(R.string.edit_roll),
                        character = character,
                        modifiers = characterModifiers,
                        groups = data.groups.filter { it.characterId == character.id }.sortedBy { it.order },
                        existing = roll,
                        onDismiss = { editingActiveRoll = false },
                        onSave = { updated ->
                            persist(updateRoll(data, character.id, roll, updated))
                            editingActiveRoll = false
                        },
                    )
                } else {
                    RollScreenV2(
                        character = character,
                        modifiers = characterModifiers,
                        diceStyles = data.diceStyles,
                        roll = roll,
                        settings = settings,
                        onBack = ::navigateBack,
                        onEditRoll = { editingActiveRoll = true },
                        canChangeLevel = { proposed ->
                            proposed in 1..9999 &&
                                AppDataValidator.validate(data.withCharacterLevel(character.id, proposed)).isEmpty()
                        },
                        onLevelChanged = { newLevel ->
                            if (newLevel in 1..9999) {
                                val proposed = data.withCharacterLevel(character.id, newLevel)
                                // Dynamic dice counts like {level}d6 can become invalid
                                // above 100. Reject the change rather than crash in LocalStore.
                                if (AppDataValidator.validate(proposed).isEmpty()) persist(proposed)
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
            onBack = ::navigateBack,
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
                    onBack = ::navigateBack,
                    onClear = {
                        persist(data.copy(logs = data.logs.filterNot { it.characterId == character.id }))
                    },
                )
            }
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
    onCustomizeDice: () -> Unit,
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
                    item(key = "dice-library-entry") {
                        PremiumCard(
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onCustomizeDice),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Casino, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    stringResource(R.string.dice_library_entry),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
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
private fun DiceCatalogScreenV2(
    data: AppData,
    selectedCharacterId: String?,
    onSelectCharacter: (String) -> Unit,
    onDataChanged: (AppData) -> Unit,
    onBack: () -> Unit,
) {
    val characters = data.characters.sortedBy { it.order }
    val selected = characters.firstOrNull { it.id == selectedCharacterId }
        ?: characters.firstOrNull()
    var selectingCharacter by remember { mutableStateOf(false) }

    ArcaneBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = transparentTopBarColors(),
                    title = { Text(stringResource(R.string.dice_library_entry)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.dice_library_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selected == null) {
                    Text(stringResource(R.string.no_characters))
                } else {
                    Box {
                        OutlinedButton(onClick = { selectingCharacter = true }) {
                            Text(selected.name)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = selectingCharacter,
                            onDismissRequest = { selectingCharacter = false },
                        ) {
                            characters.forEach { character ->
                                DropdownMenuItem(
                                    text = { Text(character.name) },
                                    onClick = {
                                        selectingCharacter = false
                                        onSelectCharacter(character.id)
                                    },
                                )
                            }
                        }
                    }
                    DiceStyleLibraryV2(
                        character = selected,
                        data = data,
                        onDataChanged = onDataChanged,
                    )
                }
            }
        }
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
    var childEditorVisible by remember(character.id) { mutableStateOf(false) }
    ArcaneBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (!childEditorVisible) TopAppBar(
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
                    onEditorVisibilityChanged = { childEditorVisible = it },
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
    val activeRolls = RollLevelAvailability.usable(data.rolls, character.id, character.level)
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
    val resolvedFormula = runCatching { RollFormulaResolver.resolve(character, modifiers, roll) }.getOrNull()
    val resolved = resolvedFormula?.expression
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
    onEditorVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showAddModifier by remember { mutableStateOf(false) }
    var editingModifierId by remember { mutableStateOf<String?>(null) }
    var showAddGroup by remember { mutableStateOf(false) }
    var showAddRoll by remember { mutableStateOf(false) }
    var editingRollId by remember { mutableStateOf<String?>(null) }
    var addRuleRollId by remember { mutableStateOf<String?>(null) }
    var levelText by remember(character.id) { mutableStateOf(character.level.toString()) }
    // Keep the edit list's position while the nested roll builder replaces its content.
    val editListState = rememberLazyListState()

    LaunchedEffect(character.level) {
        if (levelText.toIntOrNull() != character.level) levelText = character.level.toString()
    }

    val groups = data.groups.filter { it.characterId == character.id }.sortedBy { it.order }
    val rolls = data.rolls.filter { it.characterId == character.id }
    val modifiers = data.modifiers.filter { it.characterId == character.id }.sortedBy { it.order }
    val diceStyles = data.diceStyles.filter { it.characterId == character.id }.sortedBy { it.order }
    val entries = dashboardEntries(groups, rolls.filter { it.groupId == null })

    val builderRoll = editingRollId?.let { id -> rolls.firstOrNull { it.id == id } }
    LaunchedEffect(showAddRoll, builderRoll) {
        onEditorVisibilityChanged(showAddRoll || builderRoll != null)
    }
    BackHandler(enabled = showAddRoll || builderRoll != null) {
        showAddRoll = false
        editingRollId = null
    }
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
        state = editListState,
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
                                if (it.id == character.id) {
                                    it.copy(diceTableTheme = tableTheme, diceTableImage = null)
                                } else {
                                    it
                                }
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
                        if (entry is DashboardEntry.RollEntry && entry.roll.minimumLevel > 1) {
                            Text(
                                "${stringResource(R.string.from_level)} ${entry.roll.minimumLevel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (character.level >= entry.roll.minimumLevel)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
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
internal fun DiceTablePickerV2(
    character: CharacterProfile,
    onThemeChanged: (DiceTableTheme) -> Unit,
    onImageChanged: (CharacterImageRef?) -> Unit,
) {
    val context = LocalContext.current
    val imageAssetStore = remember(context) { CharacterImageAssetStore(context) }
    var imageImportFailed by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(character.id) { mutableStateOf(false) }
    val customPreview = remember(character.diceTableImage) {
        character.diceTableImage
            ?.let(imageAssetStore::loadVerified)
            ?.let(::decodeTableBitmap)
    }
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dice_table), fontWeight = FontWeight.Bold)
                    Text(
                        if (character.diceTableImage != null) stringResource(R.string.image_selected)
                        else stringResource(character.diceTableTheme.presetNameRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.dice_table),
                )
            }
        }
        if (expanded) {
        Text(stringResource(R.string.dice_table_help), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DiceTableTheme.entries.chunked(2).forEach { rowThemes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowThemes.forEach { theme ->
                        val selected = character.diceTableImage == null && character.diceTableTheme == theme
                        val label = stringResource(theme.presetNameRes())
                        val previewBitmap = remember(context, theme) { theme.presetBitmap(context, preview = true).asImageBitmap() }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onThemeChanged(theme) },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                        ) {
                            Column {
                                Image(
                                    bitmap = previewBitmap,
                                    contentDescription = label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(118.dp),
                                )
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (customPreview != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            ) {
                Column {
                    Image(
                        bitmap = customPreview.asImageBitmap(),
                        contentDescription = stringResource(R.string.image_selected),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.9f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.image_selected),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(onClick = { onImageChanged(null) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { imageLauncher.launch(arrayOf("image/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (character.diceTableImage == null) {
                    stringResource(R.string.choose_image)
                } else {
                    stringResource(R.string.image_selected)
                },
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
    var childEditorVisible by remember(group.id) { mutableStateOf(false) }
    ArcaneBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (!childEditorVisible) TopAppBar(
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
                    onEditorVisibilityChanged = { childEditorVisible = it },
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
        .filter { it.characterId == character.id && it.groupId == group.id &&
            RollLevelAvailability.isAvailable(it, character.level) }
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
internal fun GroupEditContentV2(
    character: CharacterProfile,
    group: RollGroup,
    data: AppData,
    onDataChanged: (AppData) -> Unit,
    onEditorVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var detailedRollId by rememberSaveable(group.id) { mutableStateOf<String?>(null) }
    val editListState = rememberLazyListState()
    var showAddRoll by remember { mutableStateOf(false) }
    var editingRollId by remember { mutableStateOf<String?>(null) }
    var addRuleRollId by remember { mutableStateOf<String?>(null) }

    val groups = data.groups.filter { it.characterId == character.id }.sortedBy { it.order }
    val allCharacterRolls = data.rolls.filter { it.characterId == character.id }
    val groupRolls = allCharacterRolls.filter { it.groupId == group.id }.sortedBy { it.order }
    val modifiers = data.modifiers.filter { it.characterId == character.id }.sortedBy { it.order }
    val diceStyles = data.diceStyles.filter { it.characterId == character.id }.sortedBy { it.order }

    val builderRoll = editingRollId?.let { id -> allCharacterRolls.firstOrNull { it.id == id } }
    val detailedRoll = detailedRollId?.let { id -> groupRolls.firstOrNull { it.id == id } }
    LaunchedEffect(showAddRoll, builderRoll, detailedRoll) {
        onEditorVisibilityChanged(showAddRoll || builderRoll != null || detailedRoll != null)
    }
    BackHandler(enabled = showAddRoll || builderRoll != null || detailedRoll != null) {
        when {
            builderRoll != null -> editingRollId = null
            showAddRoll -> showAddRoll = false
            detailedRoll != null -> detailedRollId = null
        }
    }
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


    if (detailedRoll != null) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { detailedRollId = null }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                    Text(
                        detailedRoll.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item {
                RollEditorCardV2(
                    character = character,
                    modifiers = modifiers,
                    groups = groups,
                    diceStyles = diceStyles,
                    roll = detailedRoll,
                    onEnabledChanged = { enabled ->
                        onDataChanged(data.copy(rolls = data.rolls.map { roll ->
                            if (roll.id == detailedRoll.id) roll.copy(enabled = enabled) else roll
                        }))
                    },
                    onGroupChanged = { targetGroupId ->
                        detailedRollId = null
                        onDataChanged(DashboardDataOperations.changeRollGroup(
                            data, character.id, detailedRoll.id, targetGroupId
                        ))
                    },
                    onEdit = { editingRollId = detailedRoll.id },
                    onAppearanceChanged = { appearance ->
                        onDataChanged(data.copy(rolls = data.rolls.map { roll ->
                            if (roll.id == detailedRoll.id) roll.copy(diceAppearance = appearance) else roll
                        }))
                    },
                    onAddRule = { addRuleRollId = detailedRoll.id },
                    onDeleteRule = { ruleId ->
                        onDataChanged(data.copy(rolls = data.rolls.map { roll ->
                            if (roll.id == detailedRoll.id) {
                                roll.copy(levelRules = roll.levelRules.filterNot { rule -> rule.id == ruleId })
                            } else roll
                        }))
                    },
                    onDelete = {
                        detailedRollId = null
                        onDataChanged(data.copy(rolls = data.rolls.filterNot { it.id == detailedRoll.id }))
                    },
                )
            }
        }
    } else {
        LazyColumn(
            state = editListState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitleV2(
                        title = stringResource(R.string.rolls),
                        subtitle = stringResource(R.string.drag_to_reorder),
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
                var moveMenu by remember(roll.id) { mutableStateOf(false) }
                val moveUp: () -> Unit = {
                    onDataChanged(data.copy(rolls = DashboardDataOperations.moveRollInsideGroup(
                        data.rolls, character.id, group.id, roll.id, -1
                    )))
                }
                val moveDown: () -> Unit = {
                    onDataChanged(data.copy(rolls = DashboardDataOperations.moveRollInsideGroup(
                        data.rolls, character.id, group.id, roll.id, 1
                    )))
                }
                DragReorderCard(
                    key = "group-roll-" + roll.id,
                    canMoveUp = index > 0,
                    canMoveDown = index < groupRolls.lastIndex,
                    onMoveUp = moveUp,
                    onMoveDown = moveDown,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).clickable { detailedRollId = roll.id },
                        ) {
                            Text(
                                roll.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (roll.minimumLevel > 1) {
                                Text(
                                    "${stringResource(R.string.from_level)} ${roll.minimumLevel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (character.level >= roll.minimumLevel)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { moveMenu = true }) {
                                Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.group))
                            }
                            DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ungrouped)) },
                                    onClick = {
                                        moveMenu = false
                                        onDataChanged(DashboardDataOperations.changeRollGroup(
                                            data, character.id, roll.id, null
                                        ))
                                    },
                                )
                                groups.filterNot { it.id == group.id }.forEach { target ->
                                    DropdownMenuItem(
                                        text = { Text(target.name) },
                                        onClick = {
                                            moveMenu = false
                                            onDataChanged(DashboardDataOperations.changeRollGroup(
                                                data, character.id, roll.id, target.id
                                            ))
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(enabled = index > 0, onClick = moveUp) {
                            Icon(
                                Icons.Rounded.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.move_up),
                            )
                        }
                        IconButton(enabled = index < groupRolls.lastIndex, onClick = moveDown) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.move_down),
                            )
                        }
                        IconButton(onClick = { detailedRollId = roll.id }) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit))
                        }
                    }
                }
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
    val resolvedFormula = runCatching { RollFormulaResolver.resolve(character, modifiers, roll) }.getOrNull()
    val resolved = resolvedFormula?.expression

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
                    if (roll.minimumLevel > 1) {
                        Text(
                            "${stringResource(R.string.from_level)} ${roll.minimumLevel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (character.level >= roll.minimumLevel)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
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
                resolvedSubgroups = resolvedFormula?.subgroups.orEmpty(),
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

/**
 * Tap-to-throw and double-tap-to-show-stats share one recognizer, so a fast
 * double tap cannot accidentally trigger a reroll before opening statistics.
 * Single-tap rerolls are delayed by the platform double-tap timeout only when
 * the statistics shortcut is enabled and a completed roll is available.
 */
@Composable
internal fun RollTableGestureLayerV2(
    tapToRollEnabled: Boolean,
    swipeToRollEnabled: Boolean,
    canRoll: Boolean,
    statsDoubleTapEnabled: Boolean,
    canToggleStats: Boolean,
    onRollRequest: () -> Unit,
    onStatsToggle: () -> Unit,
    modifier: Modifier = Modifier,
    directionalDoubleRollEnabled: Boolean = false,
    onBestRollRequest: () -> Unit = onRollRequest,
    onWorstRollRequest: () -> Unit = onRollRequest,
) {
    val latestBestRequest by rememberUpdatedState(onBestRollRequest)
    val latestWorstRequest by rememberUpdatedState(onWorstRollRequest)
    val latestRollRequest by rememberUpdatedState(onRollRequest)
    val latestStatsToggle by rememberUpdatedState(onStatsToggle)
    Box(
        modifier = modifier
            .testTag("roll-table-gesture-layer")
            .pointerInput(tapToRollEnabled, canRoll, statsDoubleTapEnabled, canToggleStats) {
                if ((tapToRollEnabled && canRoll) || (statsDoubleTapEnabled && canToggleStats)) {
                    detectTapGestures(
                        onTap = if (tapToRollEnabled && canRoll) {
                            { latestRollRequest() }
                        } else null,
                        onDoubleTap = if (statsDoubleTapEnabled && canToggleStats) {
                            { latestStatsToggle() }
                        } else null,
                    )
                }
            }
            .pointerInput(swipeToRollEnabled, directionalDoubleRollEnabled, canRoll) {
                if (directionalDoubleRollEnabled) {
                    var horizontal = 0f
                    var vertical = 0f
                    detectDragGestures(
                        onDragStart = { horizontal = 0f; vertical = 0f },
                        onDrag = { change, delta ->
                            horizontal += delta.x
                            vertical += delta.y
                            change.consume()
                        },
                        onDragCancel = { horizontal = 0f; vertical = 0f },
                        onDragEnd = {
                            val minimum = 80.dp.toPx()
                            if (canRoll) {
                                when {
                                    horizontal >= minimum && horizontal > kotlin.math.abs(vertical) -> latestBestRequest()
                                    horizontal <= -minimum && -horizontal > kotlin.math.abs(vertical) -> latestWorstRequest()
                                    vertical <= -minimum && -vertical > kotlin.math.abs(horizontal) -> latestRollRequest()
                                }
                            }
                            horizontal = 0f
                            vertical = 0f
                        },
                    )
                } else if (swipeToRollEnabled) {
                    var verticalDistance = 0f
                    detectVerticalDragGestures(
                        onDragStart = { verticalDistance = 0f },
                        onVerticalDrag = { _, dragAmount -> verticalDistance += dragAmount },
                        onDragCancel = { verticalDistance = 0f },
                        onDragEnd = {
                            if (canRoll && verticalDistance <= -80.dp.toPx()) latestRollRequest()
                            verticalDistance = 0f
                        },
                    )
                }
            },
    )
}

/** Allows a second double tap *on the modal statistics sheet* to close it. */
@Composable
internal fun Modifier.dismissStatsOnDoubleTap(
    enabled: Boolean,
    onDismiss: () -> Unit,
): Modifier {
    val latestDismiss by rememberUpdatedState(onDismiss)
    return if (enabled) {
        pointerInput(enabled) {
            detectTapGestures(onDoubleTap = { latestDismiss() })
        }
    } else this
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
    onEditRoll: () -> Unit,
    canChangeLevel: (Int) -> Boolean,
    onLevelChanged: (Int) -> Unit,
    onLogged: (RollLog) -> Unit,
) {
    val context = LocalContext.current
    val tableImageStore = remember(context) { CharacterImageAssetStore(context) }
    val presetTableBitmap = remember(context, character.diceTableTheme) {
        character.diceTableTheme.presetBitmap(context)
    }
    val customTableBitmap = remember(character.diceTableImage) {
        character.diceTableImage
            ?.let(tableImageStore::loadVerified)
            ?.let(::decodeTableBitmap)
    }
    val tableBitmap = customTableBitmap ?: presetTableBitmap
    val tableImageKey = character.diceTableImage?.assetId ?: "preset:${character.diceTableTheme.name}"
    val formula = remember(character.level, modifiers, roll) {
        RollFormulaResolver.resolve(character, modifiers, roll)
    }
    val parsedExpression = remember(formula.expression) { DiceExpression.parse(formula.expression) }
    val previewResult = remember(formula.expression) { parsedExpression.previewResult() }
    val subgroupIdByComponentIndex = remember(formula.subgroups) {
        formula.subgroupIdByComponentIndex()
    }
    val previewAppearances = remember(character, diceStyles, roll.diceAppearance, formula.expression) {
        DiceAppearanceResolver.resolve(
            character = character,
            styles = diceStyles,
            appearance = roll.diceAppearance,
            result = previewResult,
            subgroupIdByComponentIndex = subgroupIdByComponentIndex,
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
    // Level changes can alter dice counts, modifiers and rules; never reuse a
    // previous result or settled animation for a new level/formula.
    var visualEvent by remember(roll.id, character.level, formula.expression) { mutableStateOf(previewEvent) }
    var outcome by remember(roll.id, character.level, formula.expression) { mutableStateOf<DiceRollResult?>(null) }
    var doubleEvaluation by remember(roll.id, character.level, formula.expression) {
        mutableStateOf<DoubleRollEvaluation?>(null)
    }
    var effectsExecution by remember(roll.id, character.level, formula.expression, roll.effects) {
        mutableStateOf<EffectExecutionResult?>(null)
    }
    var finalRollTotal by remember(roll.id, character.level, formula.expression, roll.effects) {
        mutableStateOf<Int?>(null)
    }
    var visualStages by remember(roll.id, character.level, formula.expression, roll.effects) {
        mutableStateOf<List<EffectsVisualStage>>(emptyList())
    }
    var visualStageIndex by remember(roll.id, character.level, formula.expression, roll.effects) {
        mutableStateOf(0)
    }
    var visualSeed by remember(roll.id) { mutableStateOf(0L) }
    var hasRolled by remember(roll.id, character.level, formula.expression) { mutableStateOf(false) }
    var resultRevealed by remember(roll.id, character.level, formula.expression) { mutableStateOf(false) }
    var showStats by remember(roll.id) { mutableStateOf(false) }
    var lastThrowMode by remember(roll.id, character.level, formula.expression) { mutableStateOf(DoubleRollMode.NORMAL) }
    val cinematic = settings.effectiveVisualEffects()

    fun throwDice(requestedMode: DoubleRollMode = DoubleRollMode.NORMAL) {
        if (hasRolled && !resultRevealed) return
        val mode = if (roll.doubleRollEnabled) requestedMode else DoubleRollMode.NORMAL
        val selectedPartIds = if (roll.subgroups.isEmpty()) setOf("single") else
            roll.subgroups.filter { it.includeInNormalRoll && it.includeInDoubleRoll }
                .map { it.id }.toSet()
        val evaluation = DoubleRollEngine.evaluate(formula, mode, selectedPartIds)
        val result = evaluation.result
        // Logical effects consume only the selected double-roll candidates.
        // The unselected dice remain visual alternatives, never trigger inputs.
        val execution = if (roll.effects.isEmpty()) null else EffectSequenceExecutor.execute(
            original = EffectRollSnapshot.fromDoubleRoll(
                evaluation,
                supportSubgroups = formula.supportSubgroups,
                variables = buildMap {
                    put("level", character.level)
                    modifiers.filter { it.characterId == character.id }.forEach { modifier ->
                        put(modifier.name, modifier.value)
                    }
                },
            ),
            effects = roll.effects,
            resolvedPartExpressions = if (formula.subgroups.isEmpty()) {
                mapOf("single" to formula.expression)
            } else {
                formula.effectPartExpressions()
            },
        )
        val adjustedTotal = execution?.let {
            runCatching { EffectRuntimeHistory.adjustedLegacyTotal(it, result.total) }
                .getOrDefault(result.total)
        } ?: result.total
        // Legacy single-expression Rolls have no stored Part rows, but the
        // logical double-roll engine still compares the canonical "single" Part.
        // Give both candidate sets explicit ownership so A/B lanes and the
        // post-settle verdict work after restoring older backups as well.
        val baseComponentCount = parsedExpression.diceShape().size
        val baselineOwners = if (formula.subgroups.isEmpty()) {
            (0 until baseComponentCount).associateWith { "single" }
        } else subgroupIdByComponentIndex
        val extraComponentOwners = buildMap {
            var offset = baseComponentCount
            if (mode != DoubleRollMode.NORMAL && formula.subgroups.isEmpty()) {
                repeat(baseComponentCount) { put(offset++, "single") }
            } else {
                formula.subgroups.filter { it.id in selectedPartIds && mode != DoubleRollMode.NORMAL }
                    .forEach { part ->
                        repeat(DiceExpression.parse(part.expression).diceShape().size) {
                            put(offset++, part.id)
                        }
                    }
            }
        }
        val candidatePlan = DoubleRollVisualPlanner.plan(
            evaluation, baselineOwners, extraComponentOwners, selectedPartIds,
        )
        val timeline = EffectsVisualTimeline.build(
            baseline = evaluation.visualResult,
            baselineOwners = baselineOwners + extraComponentOwners,
            execution = execution,
            effectTypes = roll.effects.associate { it.id to it.type },
            animate = settings.animationsEnabled,
        )
        visualStages = timeline
        visualStageIndex = 0
        lastThrowMode = evaluation.mode
        visualSeed = System.nanoTime()
        val firstStage = timeline.first()
        visualEvent = DiceRollVisualEvent(
            id = visualSeed,
            result = firstStage.result,
            appearances = DiceAppearanceResolver.resolve(
                character = character,
                styles = diceStyles,
                appearance = roll.diceAppearance,
                result = firstStage.result,
                subgroupIdByComponentIndex = firstStage.componentOwners,
                random = Random(visualSeed),
                secondaryCandidateComponentIndices = candidatePlan.groups
                    .filterValues { it == 1 }.keys.takeIf { firstStage.retainsBaseline }.orEmpty(),
            ),
            dimmedComponentIndices = (if (firstStage.retainsBaseline) evaluation.dimmedComponentIndices else emptySet()) + firstStage.rerolledComponentIndices,
            effectAccentComponents = firstStage.accentByComponentIndex,
            candidateGroupByComponent = candidatePlan.groups.takeIf { firstStage.retainsBaseline }.orEmpty(),
            chosenCandidateComponents = candidatePlan.chosen.takeIf { firstStage.retainsBaseline }.orEmpty(),
            visualSettings = cinematic,
        )
        doubleEvaluation = evaluation
        effectsExecution = execution
        finalRollTotal = adjustedTotal
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
                total = adjustedTotal,
                detail = result.detail(),
                timestamp = System.currentTimeMillis(),
                doubleRollMode = evaluation.mode,
                comparisonTotal = evaluation.comparisonTotal,
                alternativeComparisonTotal = evaluation.alternativeComparisonTotal,
                effectSteps = execution?.let { EffectRuntimeHistory.steps(it, roll.effects) }.orEmpty(),
                parts = evaluation.parts.takeIf { it.size > 1 || evaluation.mode != DoubleRollMode.NORMAL || execution != null }
                    ?.mapIndexed { index, part ->
                        RollLogPart(
                            name = part.subgroup.name.ifBlank { "${roll.name} ${index + 1}" },
                            expression = part.subgroup.expression,
                            total = execution?.finalSnapshot?.parts?.get(part.subgroup.id)?.total ?: part.chosen.total,
                            detail = part.chosen.detail(),
                            originalTotal = part.chosen.total.takeIf {
                                execution?.finalSnapshot?.parts?.get(part.subgroup.id)?.total?.let { final -> final != it } == true
                            },
                            originalDetail = part.chosen.detail().takeIf {
                                execution?.finalSnapshot?.parts?.get(part.subgroup.id)?.total?.let { final ->
                                    final != part.chosen.total
                                } == true
                            },
                            alternativeTotal = part.alternative?.total,
                            alternativeDetail = part.alternative?.detail(),
                        )
                    }.orEmpty() + EffectRuntimeHistory.supportPartResults(
                        execution, formula.supportSubgroups,
                    ).map { support ->
                        RollLogPart(
                            name = support.subgroup.name.ifBlank { roll.name },
                            expression = support.subgroup.expression,
                            total = execution?.finalSnapshot?.parts?.get(support.subgroup.id)?.total
                                ?: support.result.total,
                            detail = support.result.detail(),
                        )
                    },
            ),
        )
    }

    var lastThrowRequestAtNanos by remember(roll.id) { mutableStateOf(0L) }

    fun requestThrow(mode: DoubleRollMode = DoubleRollMode.NORMAL) {
        if (hasRolled && !resultRevealed) return
        val now = System.nanoTime()
        if (now - lastThrowRequestAtNanos < 450_000_000L) return
        lastThrowRequestAtNanos = now
        throwDice(mode)
    }

    val shakeTriggerEnabled = if (hasRolled) settings.rerollShakeEnabled else settings.shakeEnabled
    DisposableEffect(shakeTriggerEnabled, hasRolled, resultRevealed, roll.id, formula.expression) {
        val detector = if (shakeTriggerEnabled && (!hasRolled || resultRevealed)) {
            ShakeDetector(context) { requestThrow() }.also { it.start() }
        } else null
        onDispose { detector?.stop() }
    }

    ArcaneBackground {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Keep A/B identification for TalkBack without placing visible
            // chips over the dice or blocking the central play area.
            val candidateDescription = if (hasRolled && visualEvent.candidateGroupByComponent.isNotEmpty()) {
                val first = stringResource(R.string.cinematic_candidate_first)
                val second = stringResource(R.string.cinematic_candidate_second)
                val selected = visualEvent.candidateGroupByComponent.entries
                    .firstOrNull { it.key in visualEvent.chosenCandidateComponents }?.value
                val verdict = if (resultRevealed && selected != null)
                    "${stringResource(R.string.cinematic_chosen)}: ${if (selected == 0) first else second}"
                    else null
                listOfNotNull(first, second, verdict).joinToString(". ")
            } else null
            Dice3DScene(
                event = visualEvent,
                tableTheme = character.diceTableTheme,
                tableImage = tableBitmap,
                tableImageKey = tableImageKey,
                fullBleed = true,
                animateRoll = hasRolled && settings.animationsEnabled,
                winnerRevealed = resultRevealed,
                onSettled = { eventId ->
                    if (hasRolled && eventId == visualEvent.id) {
                        if (settings.animationsEnabled && visualStageIndex < visualStages.lastIndex) {
                            val nextIndex = visualStageIndex + 1
                            val stage = visualStages[nextIndex]
                            visualStageIndex = nextIndex
                            visualEvent = DiceRollVisualEvent(
                                id = System.nanoTime(),
                                result = stage.result,
                                appearances = DiceAppearanceResolver.resolve(
                                    character = character,
                                    styles = diceStyles,
                                    appearance = roll.diceAppearance,
                                    result = stage.result,
                                    subgroupIdByComponentIndex = stage.componentOwners,
                                    random = Random(visualSeed),
                                    secondaryCandidateComponentIndices = if (stage.retainsBaseline)
                                        visualEvent.candidateGroupByComponent.filterValues { it == 1 }.keys
                                        else emptySet(),
                                ),
                                dimmedComponentIndices =
                                    (if (stage.retainsBaseline) doubleEvaluation?.dimmedComponentIndices.orEmpty()
                                    else emptySet()) + stage.rerolledComponentIndices,
                                effectAccentComponents = stage.accentByComponentIndex,
                                persistentDiceCount = stage.persistentDiceCount,
                                candidateGroupByComponent = if (stage.retainsBaseline)
                                    visualEvent.candidateGroupByComponent else emptyMap(),
                                chosenCandidateComponents = if (stage.retainsBaseline)
                                    visualEvent.chosenCandidateComponents else emptySet(),
                                visualSettings = cinematic,
                            )
                        } else {
                            resultRevealed = true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize().then(
                    if (candidateDescription == null) Modifier
                    else Modifier.semantics { contentDescription = candidateDescription },
                ),
            )

            // Each triggered arithmetic action gets a brief cinematic replay
            // of its recorded before/after values. The 3D outcome never changes.
            val cinematicMoments = remember(effectsExecution, roll.effects) {
                effectsExecution?.steps.orEmpty().flatMap { step ->
                    val effect = roll.effects.firstOrNull { it.id == step.effectId }
                    if (effect == null || !step.activation.activated) emptyList()
                    else step.actions.mapNotNull { result ->
                        val action = effect.actions.firstOrNull { it.id == result.actionId }
                        if (action == null || !result.applied) null else CinematicActionMoment(
                            effectId = effect.id, type = effect.type, action = action.kind,
                            before = result.before, after = result.after,
                        )
                    }
                }
            }
            if (resultRevealed && settings.animationsEnabled && !showStats) {
                CinematicActionSequenceV2(
                    rollSeed = visualSeed,
                    moments = cinematicMoments,
                    options = cinematic,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (hasRolled && !resultRevealed && settings.animationsEnabled && !showStats) {
                val stage = visualStages.getOrNull(visualStageIndex)
                val effect = roll.effects.firstOrNull { it.id == stage?.effectId }
                if (effect != null && stage != null) {
                    CinematicEffectOverlayV2(
                        key = "$visualSeed:${visualStageIndex}:${effect.id}",
                        type = effect.type, action = stage.actionKind,
                        particles = cinematic.particles, aura = cinematic.tableAura,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (cinematic.actionCues && stage.actionKind != null) {
                        CinematicActionCueV2(
                            action = stage.actionKind, type = effect.type,
                            before = null, after = null,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }

            // Keep the center clear: a compact accessible cue appears near
            // the top while additional pre-resolved dice enter the 3D scene.
            if (hasRolled && !showStats) {
                val currentEffectId = visualStages.getOrNull(visualStageIndex)?.effectId
                val activeEffectId = currentEffectId ?: if (resultRevealed) {
                    effectsExecution?.steps?.firstOrNull { it.activation.activated }?.effectId
                } else null
                val activeEffect = roll.effects.firstOrNull { it.id == activeEffectId }
                if (activeEffect != null && cinematic.badge) {
                    EffectsVisualCueV2(
                        effectName = activeEffect.name,
                        type = activeEffect.type,
                        rollAfter = currentEffectId != null && !resultRevealed,
                        animate = settings.animationsEnabled && !resultRevealed,
                        modifier = Modifier.align(Alignment.TopCenter)
                            .statusBarsPadding().padding(top = 22.dp),
                    )
                }
            }

            val tapTriggerEnabled = if (hasRolled) settings.rerollTapEnabled else settings.firstRollTapEnabled
            val swipeTriggerEnabled = if (hasRolled) settings.rerollSwipeEnabled else settings.firstRollSwipeEnabled
            val gestureTriggerReady = !hasRolled || resultRevealed
            RollTableGestureLayerV2(
                tapToRollEnabled = tapTriggerEnabled,
                swipeToRollEnabled = swipeTriggerEnabled,
                canRoll = gestureTriggerReady,
                statsDoubleTapEnabled = settings.doubleTapStatsEnabled,
                canToggleStats = hasRolled && resultRevealed,
                onRollRequest = { requestThrow() },
                onStatsToggle = { showStats = !showStats },
                directionalDoubleRollEnabled = roll.doubleRollEnabled && settings.doubleRollDirectionalSwipeEnabled,
                onBestRollRequest = { requestThrow(DoubleRollMode.BEST) },
                onWorstRollRequest = { requestThrow(DoubleRollMode.WORST) },
                modifier = Modifier.fillMaxSize().padding(bottom = 88.dp),
            )

            if (resultRevealed) {
                outcome?.let { value ->
                    // Keep the center of the table free for the 3D dice. The result stack
                    // grows upwards from the footer, with a viewport bound on small screens.
                    RollResultsOverlayV2(
                        parts = (doubleEvaluation?.parts?.map { ResolvedRollSubgroupResult(it.subgroup, it.chosen) }
                            ?: formula.subgroupResults(value)) +
                            EffectRuntimeHistory.supportPartResults(
                                effectsExecution, formula.supportSubgroups,
                            ),
                        total = finalRollTotal ?: value.total,
                        originalTotal = value.total.takeIf { it != finalRollTotal },
                        animateValues = cinematic.resultTransitions && settings.animationsEnabled,
                        partTotalsById = effectsExecution?.finalSnapshot?.parts?.mapValues { it.value.total }.orEmpty(),
                        aboveAverage = (finalRollTotal ?: value.total) > value.expectedTotal(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, bottom = 82.dp)
                            .fillMaxWidth()
                            .heightIn(max = (maxHeight * 0.40f).coerceIn(112.dp, 300.dp)),
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
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
                    val waitingForDice = hasRolled && !resultRevealed
                    if (roll.doubleRollEnabled) {
                        IconButton(
                            onClick = { requestThrow(DoubleRollMode.WORST) },
                            enabled = !hasRolled || resultRevealed,
                        ) {
                            Icon(
                                Icons.Rounded.Casino,
                                contentDescription = stringResource(R.string.double_roll_worst),
                                modifier = Modifier.size(27.dp),
                                tint = Color(0xFFF87171).copy(alpha = if (!waitingForDice) 1f
                                    else if (lastThrowMode == DoubleRollMode.WORST) 0.72f else 0.30f),
                            )
                        }
                        IconButton(
                            onClick = { requestThrow(DoubleRollMode.BEST) },
                            enabled = !hasRolled || resultRevealed,
                        ) {
                            Icon(
                                Icons.Rounded.Casino,
                                contentDescription = stringResource(R.string.double_roll_best),
                                modifier = Modifier.size(27.dp),
                                tint = Color(0xFF86EFAC).copy(alpha = if (!waitingForDice) 1f
                                    else if (lastThrowMode == DoubleRollMode.BEST) 0.72f else 0.30f),
                            )
                        }
                    }
                    IconButton(
                        onClick = { requestThrow() },
                        enabled = !hasRolled || resultRevealed,
                    ) {
                        Icon(
                            Icons.Rounded.Casino,
                            contentDescription = stringResource(if (hasRolled) R.string.roll_again else R.string.throw_dice),
                            modifier = Modifier.size(30.dp),
                            tint = Color.White.copy(alpha = if (!waitingForDice) 1f
                                else if (lastThrowMode == DoubleRollMode.NORMAL) 0.72f else 0.30f),
                        )
                    }
                }
            }


        }
    }

    if (showStats) {
        ModalBottomSheet(onDismissRequest = { showStats = false }) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .dismissStatsOnDoubleTap(settings.doubleTapStatsEnabled) { showStats = false }
                    .padding(horizontal = 18.dp).padding(bottom = 28.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("roll-stats-sheet-content"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.statistics),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    TextButton(onClick = onEditRoll) {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.edit_roll))
                    }
                }
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${stringResource(R.string.level)} ${character.level}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            IconButton(
                                enabled = character.level > 1 && canChangeLevel(character.level - 1),
                                onClick = { onLevelChanged(character.level - 1) },
                            ) {
                                Icon(
                                    Icons.Rounded.Remove,
                                    contentDescription = "${stringResource(R.string.level)} −",
                                )
                            }
                            IconButton(
                                enabled = character.level < 9999 && canChangeLevel(character.level + 1),
                                onClick = { onLevelChanged(character.level + 1) },
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = "${stringResource(R.string.level)} +",
                                )
                            }
                        }
                    }
                }
                doubleEvaluation?.takeIf { it.mode != DoubleRollMode.NORMAL }?.let { comparison ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(
                                stringResource(
                                    if (comparison.mode == DoubleRollMode.BEST) R.string.double_roll_best
                                    else R.string.double_roll_worst,
                                ),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${stringResource(R.string.double_roll_selected)}: ${comparison.comparisonTotal}",
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${stringResource(R.string.double_roll_alternative)}: ${comparison.alternativeComparisonTotal}",
                                modifier = Modifier.graphicsLayer { alpha = 0.54f },
                            )
                        }
                    }
                }
                outcome?.let { rolled ->
                    val displayParts = doubleEvaluation?.parts?.map {
                        ResolvedRollSubgroupResult(it.subgroup, it.chosen)
                    } ?: formula.subgroupResults(rolled)
                    displayParts.forEachIndexed { index, grouped ->
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
                                    doubleEvaluation?.parts?.getOrNull(index)?.alternative?.let { alternative ->
                                        Column(Modifier.graphicsLayer { alpha = 0.54f }) {
                                            Text(
                                                "${stringResource(R.string.double_roll_alternative)}: ${alternative.total}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Text(alternative.detail(), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val finalTotal = effectsExecution?.finalSnapshot
                                        ?.parts?.get(grouped.subgroup.id)?.total ?: grouped.result.total
                                    Text(
                                        finalTotal.toString(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Black,
                                    )
                                    if (finalTotal != grouped.result.total) {
                                        Text(
                                            "${grouped.result.total} → $finalTotal",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                effectsExecution?.let { execution ->
                    EffectsTraceV2(EffectRuntimeHistory.steps(execution, roll.effects))
                }
                // Individual parts already show their own roll breakdown above.
                // A global total would misleadingly add unrelated checks and damage.
                if (formula.subgroups.size <= 1 || outcome == null) {
                    ResultContentV2(outcome, finalRollTotal)
                }
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
internal fun RollResultsOverlayV2(
    parts: List<ResolvedRollSubgroupResult>,
    total: Int,
    aboveAverage: Boolean,
    modifier: Modifier = Modifier,
    partTotalsById: Map<String, Int> = emptyMap(),
    originalTotal: Int? = null,
    animateValues: Boolean = true,
) {
    Box(
        modifier = modifier.testTag("roll-results-overlay"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (parts.size > 1) {
            RollPartsReveal(parts, partTotalsById, animateValues)
        } else {
            RollTotalReveal(total = total, aboveAverage = aboveAverage,
                originalTotal = originalTotal, animateValues = animateValues)
        }
    }
}

@Composable
private fun RollPartsReveal(
    parts: List<ResolvedRollSubgroupResult>,
    finalTotalsById: Map<String, Int> = emptyMap(),
    animateValues: Boolean = true,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(parts) { scrollState.scrollTo(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("roll-parts-list")
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        parts.forEachIndexed { index, part ->
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("roll-part-${index}"),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        part.subgroup.name.ifBlank { "${index + 1}" },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        val finalTotal = finalTotalsById[part.subgroup.id] ?: part.result.total
                        val animatedTotal = animatedRollValue(
                            original = part.result.total, final = finalTotal, enabled = animateValues,
                        )
                        Text(
                            animatedTotal.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false,
                        )
                        if (finalTotal != part.result.total) {
                            Text(
                                "${part.result.total} → $finalTotal",
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

/** Numerically inert visual interpolation; the stored roll total never changes. */
@Composable
private fun animatedRollValue(original: Int, final: Int, enabled: Boolean): Int {
    val value = remember(original, final, enabled) {
        Animatable(if (enabled) original.toFloat() else final.toFloat())
    }
    LaunchedEffect(original, final, enabled) {
        if (enabled && original != final) {
            value.animateTo(final.toFloat(), animationSpec = tween(durationMillis = 650))
        } else {
            value.snapTo(final.toFloat())
        }
    }
    // Show the original value from the first frame, not a flash of the final total.
    return if (enabled && original != final) value.value.toInt() else final
}

@Composable
private fun RollTotalReveal(total: Int, aboveAverage: Boolean,
    originalTotal: Int? = null, animateValues: Boolean = true) {
    val scale = remember(total, animateValues) { Animatable(if (animateValues) 0.72f else 1f) }
    LaunchedEffect(total, animateValues) {
        if (animateValues) scale.animateTo(1f, animationSpec = tween(durationMillis = 420))
        else scale.snapTo(1f)
    }
    val visibleTotal = animatedRollValue(originalTotal ?: total, total, animateValues)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (aboveAverage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier
            .testTag("roll-single-result")
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = scale.value
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.total), style = MaterialTheme.typography.labelMedium)
            Text(
                visibleTotal.toString(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = if (aboveAverage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ResultContentV2(outcome: DiceRollResult?, finalTotal: Int? = null) {
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
                (finalTotal ?: outcome.total).toString(),
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

                item {
                    PremiumCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(stringResource(R.string.roll_interactions), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.roll_interactions_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.first_roll_triggers),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            RollTriggerToggle(
                                label = stringResource(R.string.tap_dice_table),
                                checked = settings.firstRollTapEnabled,
                                onChecked = { onSettingsChanged(settings.copy(firstRollTapEnabled = it)) },
                            )
                            RollTriggerToggle(
                                label = stringResource(R.string.swipe_up_to_roll),
                                checked = settings.firstRollSwipeEnabled,
                                onChecked = { onSettingsChanged(settings.copy(firstRollSwipeEnabled = it)) },
                            )
                            RollTriggerToggle(
                                label = stringResource(R.string.shake_phone),
                                checked = settings.shakeEnabled,
                                onChecked = { onSettingsChanged(settings.copy(shakeEnabled = it)) },
                            )

                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.reroll_triggers),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            RollTriggerToggle(
                                label = stringResource(R.string.tap_dice_table),
                                checked = settings.rerollTapEnabled,
                                onChecked = { onSettingsChanged(settings.copy(rerollTapEnabled = it)) },
                            )
                            RollTriggerToggle(
                                label = stringResource(R.string.swipe_up_to_roll),
                                checked = settings.rerollSwipeEnabled,
                                onChecked = { onSettingsChanged(settings.copy(rerollSwipeEnabled = it)) },
                            )
                            RollTriggerToggle(
                                label = stringResource(R.string.shake_phone),
                                checked = settings.rerollShakeEnabled,
                                onChecked = { onSettingsChanged(settings.copy(rerollShakeEnabled = it)) },
                            )
                            Text(
                                stringResource(R.string.roll_icon_always_available),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            RollTriggerToggle(
                                label = stringResource(R.string.directional_double_roll_swipes),
                                checked = settings.doubleRollDirectionalSwipeEnabled,
                                onChecked = { onSettingsChanged(settings.copy(doubleRollDirectionalSwipeEnabled = it)) },
                            )
                            RollTriggerToggle(
                                label = stringResource(R.string.double_tap_statistics),
                                checked = settings.doubleTapStatsEnabled,
                                onChecked = { onSettingsChanged(settings.copy(doubleTapStatsEnabled = it)) },
                            )
                        }
                    }
                }
                item { ToggleSettingCard(stringResource(R.string.animations_enabled), settings.animationsEnabled) { onSettingsChanged(settings.copy(animationsEnabled = it)) } }
                item {
                    PremiumCard(Modifier.fillMaxWidth()) {
                        RollVisualSettingsEditorV2(
                            settings = settings.visualEffects,
                            onChange = { changed ->
                                onSettingsChanged(settings.copy(visualEffects = changed))
                            },
                            modifier = Modifier.padding(16.dp),
                        )
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
private fun RollTriggerToggle(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

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
                                    if (log.parts.isEmpty()) {
                                        Text(log.total.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                                    }
                                }
                                Text(log.expression, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (log.parts.isNotEmpty()) {
                                    log.parts.forEach { part ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(part.name, fontWeight = FontWeight.Bold)
                                                Text(part.detail, style = MaterialTheme.typography.bodySmall)
                                                part.originalTotal?.let { initial ->
                                                    Text(
                                                        "$initial → ${part.total}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                            Text(part.total.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else if (log.detail.isNotBlank()) {
                                    Text(log.detail, style = MaterialTheme.typography.bodySmall)
                                }
                                EffectsTraceV2(log.effectSteps)
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var doubleRollEnabled by remember(existing?.id) { mutableStateOf(existing?.doubleRollEnabled ?: true) }
    var minimumLevelText by remember(existing?.id) { mutableStateOf((existing?.minimumLevel ?: 1).toString()) }
    var effects by remember(existing?.id) { mutableStateOf(existing?.effects.orEmpty()) }
    var subgroups by remember(existing?.id) {
        mutableStateOf(
            existing?.subgroups?.takeIf { it.isNotEmpty() } ?: listOf(
                RollSubgroup(
                    id = UUID.randomUUID().toString(),
                    expression = existing?.expression ?: "1d20",
                    includeInDoubleRoll = existing == null,
                ),
            ),
        )
    }
    // Roll Parts are the only editable source of truth. Storage always receives
    // their canonical expression, including the parentheses required for a single part.
    val expressionToSave = runCatching {
        RollFormulaResolver.canonicalExpression(subgroups)
    }.getOrDefault("")
    val groupsValid = subgroups.any { it.includeInNormalRoll } && subgroups.all { subgroup ->
        subgroup.expression.isNotBlank() &&
            RollFormulaResolver.validateTemplate(subgroup.expression, character.level, modifiers)
    }
    val expressionValid = RollFormulaResolver.validateTemplate(expressionToSave, character.level, modifiers)
    val effectsToSave = runCatching {
        EffectEditorDraft.canonicalize(effects, subgroups, character.level, modifiers)
    }.getOrNull()
    val minimumLevel = minimumLevelText.toIntOrNull()
    val valid = name.isNotBlank() && groupsValid && expressionValid && effectsToSave != null &&
        minimumLevel != null && minimumLevel in 1..9999
    fun updateSubgroups(updated: List<RollSubgroup>) {
        subgroups = updated
    }

    // Own the full edit screen irrespective of the entry point (table, character, group).
    // An opaque surface prevents the previous dice-table/parent background from bleeding
    // through partially transparent theme backgrounds and establishes readable content.
    Surface(
        modifier = modifier.fillMaxSize().testTag("roll-editor-screen"),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        ArcaneBackground {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        colors = transparentTopBarColors(),
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                )
                            }
                        },
                        title = {
                            Text(title, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground)
                        },
                    )
                },
            ) { pagePadding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(pagePadding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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
                    RollTriggerToggle(
                        label = stringResource(R.string.double_roll_enable),
                        checked = doubleRollEnabled,
                        onChecked = { doubleRollEnabled = it },
                    )
                    Text(
                        stringResource(R.string.double_roll_explained),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = minimumLevelText,
                        onValueChange = { input ->
                            if (input.length <= 4 && input.all(Char::isDigit)) minimumLevelText = input
                        },
                        label = { Text(stringResource(R.string.from_level)) },
                        supportingText = { Text(stringResource(R.string.roll_min_level_help)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = minimumLevel == null || minimumLevel !in 1..9999,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("roll-minimum-level"),
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
            }
        }

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
                            updateSubgroups(subgroups + RollSubgroup(
                                id = UUID.randomUUID().toString(),
                                expression = "1d6",
                            ))
                        },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_subgroup))
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
                                        updateSubgroups(subgroups.map {
                                            if (it.id == subgroup.id) it.copy(operator = RollSubgroupOperator.ADD) else it
                                        })
                                    },
                                    label = { Text("+") },
                                )
                                Spacer(Modifier.width(6.dp))
                                FilterChip(
                                    selected = subgroup.operator == RollSubgroupOperator.SUBTRACT,
                                    onClick = {
                                        updateSubgroups(subgroups.map {
                                            if (it.id == subgroup.id) it.copy(operator = RollSubgroupOperator.SUBTRACT) else it
                                        })
                                    },
                                    label = { Text("−") },
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            OutlinedTextField(
                                value = subgroup.name,
                                onValueChange = { updatedName ->
                                    updateSubgroups(subgroups.map {
                                        if (it.id == subgroup.id) it.copy(name = updatedName) else it
                                    })
                                },
                                label = { Text(stringResource(R.string.subgroup_name)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            if (subgroups.size > 1) {
                                IconButton(onClick = { updateSubgroups(subgroups.filterNot { it.id == subgroup.id }) }) {
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
                                    onClick = { updateSubgroups(moveRollSubgroup(subgroups, index, -1)) },
                                ) {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.move_up),
                                    )
                                }
                                IconButton(
                                    enabled = index < subgroups.lastIndex,
                                    onClick = { updateSubgroups(moveRollSubgroup(subgroups, index, 1)) },
                                ) {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.move_down),
                                    )
                                }
                            }
                        }
                        RollTriggerToggle(
                            label = stringResource(R.string.part_in_normal_roll),
                            checked = subgroup.includeInNormalRoll,
                            onChecked = { selected ->
                                // Keep at least one initial Roll Part.
                                if (selected || subgroups.count { it.includeInNormalRoll } > 1) {
                                    updateSubgroups(subgroups.map { part ->
                                        if (part.id == subgroup.id) part.copy(
                                            includeInNormalRoll = selected,
                                            includeInDoubleRoll = if (selected) part.includeInDoubleRoll else false,
                                        ) else part
                                    })
                                }
                            },
                        )
                        if (!subgroup.includeInNormalRoll) {
                            Text(
                                stringResource(R.string.part_support_only_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (subgroup.includeInNormalRoll) {
                        RollTriggerToggle(
                            label = stringResource(R.string.double_roll_part),
                            checked = subgroup.includeInDoubleRoll,
                            onChecked = { selected ->
                                updateSubgroups(subgroups.map { part ->
                                    if (part.id == subgroup.id) part.copy(includeInDoubleRoll = selected) else part
                                })
                            },
                        )
                        }
                        GuidedExpressionEditorV2(
                            value = subgroup.expression,
                            character = character,
                            modifiers = modifiers,
                            onValueChange = { updatedExpression ->
                                updateSubgroups(subgroups.map {
                                    if (it.id == subgroup.id) it.copy(expression = updatedExpression) else it
                                })
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

        item {
            EffectsEditorSectionV2(
                effects = effects,
                parts = subgroups,
                variableNames = RollFormulaResolver.variableNames(modifiers),
                onChange = { effects = it },
            )
            if (effectsToSave == null) {
                Text(
                    stringResource(R.string.effects_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
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
                        val saved = (existing ?: RollDefinition(
                            id = "",
                            characterId = character.id,
                            name = "",
                            expression = "1d20",
                        )).copy(
                            name = name.trim(),
                            expression = expressionToSave,
                            groupId = groupId,
                            subgroups = subgroups,
                            doubleRollEnabled = doubleRollEnabled,
                            minimumLevel = minimumLevel ?: 1,
                            effects = effectsToSave.orEmpty(),
                        )
                        val slots = runCatching {
                            val resolved = RollFormulaResolver.resolve(character, modifiers, saved).expression
                            DiceAppearanceResolver.slotsFor(DiceExpression.parse(resolved))
                        }.getOrDefault(emptyList())
                        onSave(
                            saved.copy(
                                diceAppearance = DiceAppearanceResolver.reconcileSlots(
                                    saved.diceAppearance,
                                    slots,
                                    saved.subgroups.map { it.id },
                                ),
                            ),
                        )
                    },
                ) { Text(stringResource(R.string.save)) }
            }
        }
                } // LazyColumn
            } // Scaffold
        } // ArcaneBackground
    } // Opaque Surface
}

@Composable
private fun GuidedExpressionEditorV2(
    value: String,
    character: CharacterProfile,
    modifiers: List<CharacterModifier>,
    onValueChange: (String) -> Unit,
) {
    var countText by remember { mutableStateOf("1") }
    var countMenu by remember { mutableStateOf(false) }
    var constantMode by remember { mutableStateOf(false) }
    var sides by remember { mutableStateOf(20) }
    var sidesMenu by remember { mutableStateOf(false) }
    var operatorMenu by remember { mutableStateOf(false) }
    var nextOperator by remember { mutableStateOf("+") }
    val valid = RollFormulaResolver.validateTemplate(value, character.level, modifiers)
    val composer = remember(value, character.level, modifiers) {
        FormulaComposer.parse(value, character.level, modifiers)
    }
    // Editing a term must not drop the selection when its expression updates.
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    val editingIndex = selected.singleOrNull()?.takeIf { index ->
        composer?.getOrNull(index)?.let(FormulaComposer::simpleInput) != null
    }

    fun editSelected(
        amount: String = countText,
        dieSides: Int? = if (constantMode) null else sides,
        operator: String = nextOperator,
    ) {
        val terms = composer ?: return
        val index = editingIndex ?: return
        val updated = FormulaComposer.replaceSimpleTerm(
            terms, index, amount, dieSides, if (operator == "-") '-' else '+',
        ) ?: return
        val candidate = FormulaComposer.serialize(updated)
        if (RollFormulaResolver.validateTemplate(candidate, character.level, modifiers)) {
            onValueChange(candidate)
        }
    }

    fun commit(terms: List<ComposerTerm>) {
        val candidate = FormulaComposer.serialize(terms)
        if (candidate.isBlank() || RollFormulaResolver.validateTemplate(candidate, character.level, modifiers)) {
            selected = emptySet()
            onValueChange(candidate)
        }
    }

    fun appendTerm(term: String) {
        val expression = when {
            value.isBlank() && nextOperator == "-" -> "-" + term
            value.isBlank() -> term
            nextOperator == "x" -> "(" + value + ")x" + term
            else -> value + nextOperator + term
        }
        if (RollFormulaResolver.validateTemplate(expression, character.level, modifiers)) {
            selected = emptySet()
            onValueChange(expression)
        }
    }

    // Reflect builder edits in the same composition, without an asynchronous
    // effect that briefly exposes stale text. Preserve cursor during direct input.
    var editingValue by remember { mutableStateOf(TextFieldValue(value)) }
    val visibleValue = if (editingValue.text == value) editingValue else TextFieldValue(value)
    ParameterizedExpressionField(
        value = visibleValue,
        onValueChange = { updated ->
            editingValue = updated
            selected = emptySet()
            onValueChange(updated.text)
        },
        label = stringResource(R.string.expression),
        modifiers = modifiers,
        isValid = valid,
        helper = stringResource(R.string.expression_hint),
        errorText = stringResource(R.string.invalid_expression),
        fieldTestTag = "composer-expression",
    )

    if (composer != null && composer.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.composer_terms),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            composer.forEachIndexed { index, term ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (term.isGroup) {
                                Text(if (term.sign == '-') "−" else "+", fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.width(3.dp)
                                            .height((term.grouped.size * 27).dp)
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                    Column(Modifier.padding(start = 8.dp)) {
                                        term.grouped.forEachIndexed { childIndex, child ->
                                            Text(
                                                (if (child.sign == '-') "− " else if (childIndex > 0) "+ " else "") +
                                                    child.expression,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            } else {
                                FilterChip(
                                    selected = index in selected,
                                    onClick = {
                                        val next = if (index in selected) selected - index else selected + index
                                        selected = next
                                        if (next.size == 1) {
                                            FormulaComposer.simpleInput(composer[next.single()])?.let { term ->
                                                countText = term.amount
                                                constantMode = term.sides == null
                                                term.sides?.let { sides = it }
                                                nextOperator = if (term.sign == '-') "-" else "+"
                                            }
                                        }
                                    },
                                    label = {
                                        Text(
                                            (if (term.sign == '-') "− " else if (index > 0) "+ " else "") +
                                                term.expression,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    modifier = Modifier.weight(1f).testTag("composer-term-$index"),
                                )
                            }
                            IconButton(
                                enabled = index > 0,
                                onClick = { commit(FormulaComposer.move(composer, index, -1)) },
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
                            }
                            IconButton(
                                enabled = index < composer.lastIndex,
                                onClick = { commit(FormulaComposer.move(composer, index, 1)) },
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
                            }
                            IconButton(onClick = { commit(FormulaComposer.remove(composer, index)) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                        if (term.isGroup) {
                            var factor by remember(value, index) {
                                mutableStateOf(term.multiplier ?: "1")
                            }
                            var factorMenu by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = factor,
                                    onValueChange = { factor = it.take(32) },
                                    singleLine = true,
                                    label = { Text(stringResource(R.string.multiplier)) },
                                    modifier = Modifier.weight(1f),
                                )
                                Box {
                                    IconButton(onClick = { factorMenu = true }) {
                                        Icon(
                                            Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.composer_choose_variable),
                                        )
                                    }
                                    DropdownMenu(expanded = factorMenu, onDismissRequest = { factorMenu = false }) {
                                        (listOf("{level}") + modifiers.map { "{" + it.name + "}" })
                                            .distinct().forEach { variable ->
                                                DropdownMenuItem(
                                                    text = { Text(variable) },
                                                    onClick = { factor = variable; factorMenu = false },
                                                )
                                            }
                                    }
                                }
                                TextButton(
                                    onClick = { commit(FormulaComposer.multiplyGroup(composer, index, factor.trim())) },
                                    enabled = factor.isNotBlank() &&
                                        RollFormulaResolver.validateTemplate(
                                            "1x" + factor.trim(), character.level, modifiers
                                        ),
                                ) { Text("×") }
                                TextButton(onClick = { commit(FormulaComposer.ungroup(composer, index)) }) {
                                    Text(stringResource(R.string.composer_ungroup))
                                }
                            }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.composer_select_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { commit(FormulaComposer.group(composer, selected)) },
                enabled = FormulaComposer.canGroup(composer, selected),
            ) { Text(stringResource(R.string.composer_group)) }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        Box {
            OutlinedButton(onClick = { operatorMenu = true }) {
                Text(if (nextOperator == "x") "×" else if (nextOperator == "-") "−" else "+")
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
            }
            DropdownMenu(expanded = operatorMenu, onDismissRequest = { operatorMenu = false }) {
                (if (editingIndex != null) listOf("+" to "+", "-" to "−")
                 else listOf("+" to "+", "-" to "−", "x" to "×"))
                    .forEach { (operator, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            nextOperator = operator
                            operatorMenu = false
                            if (editingIndex != null && operator != "x") editSelected(operator = operator)
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = countText,
            onValueChange = {
                countText = it.take(35)
                if (editingIndex != null) editSelected(amount = countText)
            },
            singleLine = true,
            label = { Text("#") },
            modifier = Modifier.width(88.dp).testTag("composer-count"),
        )
        Box {
            IconButton(onClick = { countMenu = true }) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.composer_choose_variable))
            }
            DropdownMenu(expanded = countMenu, onDismissRequest = { countMenu = false }) {
                (listOf("1", "2", "3", "4", "5", "10", "{level}") +
                    modifiers.map { "{" + it.name + "}" }).distinct().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            countText = option
                            countMenu = false
                            if (editingIndex != null) editSelected(amount = option)
                        },
                    )
                }
            }
        }
        Box {
            OutlinedButton(onClick = { sidesMenu = true }) {
                Text(if (constantMode) stringResource(R.string.constant_value) else "d" + sides)
            }
            DropdownMenu(expanded = sidesMenu, onDismissRequest = { sidesMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.constant_value)) },
                    onClick = {
                        constantMode = true
                        sidesMenu = false
                        if (editingIndex != null) editSelected(dieSides = null)
                    },
                )
                DiceExpression.supportedSides.sorted().forEach { option ->
                    DropdownMenuItem(
                        text = { Text("d" + option) },
                        onClick = {
                            sides = option
                            constantMode = false
                            sidesMenu = false
                            if (editingIndex != null) editSelected(dieSides = option)
                        },
                    )
                }
            }
        }
        val item = if (constantMode) countText else countText + "d" + sides
        if (editingIndex != null) {
            Text(
                stringResource(R.string.edit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            enabled = RollFormulaResolver.validateTemplate(item, character.level, modifiers),
            onClick = { appendTerm(item) },
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add))
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { selected = emptySet(); onValueChange("") }) {
            Text(stringResource(R.string.clear_expression))
        }
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
                            diceAppearance = DiceAppearanceResolver.reconcileSlots(
                                saved.diceAppearance,
                                slots,
                                saved.subgroups.map { it.id },
                            ),
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

internal fun AppData.withCharacterLevel(characterId: String, requestedLevel: Int): AppData {
    val nextLevel = requestedLevel.coerceIn(1, 9999)
    return copy(
        characters = characters.map { character ->
            if (character.id == characterId) character.copy(level = nextLevel) else character
        },
    )
}

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
