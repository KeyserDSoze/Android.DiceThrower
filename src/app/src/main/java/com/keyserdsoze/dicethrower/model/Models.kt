package com.keyserdsoze.dicethrower.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ConflictPolicy { ASK, LATEST_WINS }

enum class RollButtonPosition {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
}

enum class LevelRuleKind {
    FROM_LEVEL,
    EVERY_LEVELS,
}

enum class DoubleRollMode { NORMAL, BEST, WORST }

enum class RollSubgroupOperator {
    ADD,
    SUBTRACT,
}

enum class DiceMaterial {
    GLOSSY_RESIN,
    MATTE_RESIN,
    METAL,
    GEMSTONE,
}

enum class DiceTableTheme {
    ARCANE,
    OAK,
    EMERALD,
    OBSIDIAN,
    TAVERN_WOOD,
    DUNGEON_STONE,
    ELVEN_GROVE,
    FROZEN_REALM,
    DESERT_RUINS,
    ASTRAL_VOID,
}

enum class DiceAppearanceMode {
    CHARACTER_DEFAULT,
    UNIFORM,
    PER_DIE,
    RANDOM_UNIFORM,
    RANDOM_PER_DIE,
}

data class DiceStyle(
    val id: String,
    val characterId: String,
    val name: String,
    val material: DiceMaterial = DiceMaterial.GLOSSY_RESIN,
    val primaryColorArgb: Int,
    val secondaryColorArgb: Int,
    val order: Int = 0,
)

data class RollDiceAppearance(
    val mode: DiceAppearanceMode = DiceAppearanceMode.CHARACTER_DEFAULT,
    val styleId: String? = null,
    val subgroupStyleIds: Map<String, String> = emptyMap(),
    val perDieStyleIds: Map<String, String> = emptyMap(),
    val randomStyleIds: List<String> = emptyList(),
)

data class CharacterImageRef(
    val assetId: String,
    val sha256: String,
    val mimeType: String,
    val byteSize: Long,
)

data class CharacterProfile(
    val id: String,
    val name: String,
    val imageUri: String? = null,
    val image: CharacterImageRef? = null,
    val tag: String = "",
    val level: Int = 1,
    val order: Int = 0,
    val defaultDiceStyleId: String? = null,
    /** Optional character-owned style for BEST/WORST candidate B; null = contrasting auto preset. */
    val secondaryDiceStyleId: String? = null,
    val diceTableTheme: DiceTableTheme = DiceTableTheme.ARCANE,
    val diceTableImage: CharacterImageRef? = null,
)

data class CharacterModifier(
    val id: String,
    val characterId: String,
    val name: String,
    val value: Int,
    val order: Int = 0,
)

data class RollLevelRule(
    val id: String,
    val kind: LevelRuleKind,
    val trigger: Int,
    val expression: String,
)

data class RollSubgroup(
    val id: String,
    val name: String = "",
    val expression: String,
    val operator: RollSubgroupOperator = RollSubgroupOperator.ADD,
    val includeInDoubleRoll: Boolean = false,
)

data class RollGroup(
    val id: String,
    val characterId: String,
    val name: String,
    val order: Int = 0,
)

data class RollDefinition(
    val id: String,
    val characterId: String,
    val name: String,
    val expression: String,
    val groupId: String? = null,
    val enabled: Boolean = true,
    val order: Int = 0,
    val levelRules: List<RollLevelRule> = emptyList(),
    val subgroups: List<RollSubgroup> = emptyList(),
    val diceAppearance: RollDiceAppearance = RollDiceAppearance(),
    val doubleRollEnabled: Boolean = false,
    val effects: List<RollEffect> = emptyList(),
    /** 1 keeps older Rolls available at every valid character level. */
    val minimumLevel: Int = 1,
    val visualEffects: RollVisualEffectsSettings = RollVisualEffectsSettings(),
)

/** Immutable, backup-safe roll execution trace, independent of the renderer. */
data class RollLogEffectCondition(
    val groupId: String,
    val conditionId: String,
    val actual: Int? = null,
    val threshold: Int? = null,
    val comparison: EffectComparison,
    val passed: Boolean,
    val error: String? = null,
)

data class RollLogEffectAction(
    val actionId: String,
    val kind: EffectActionType,
    val targetPartId: String? = null,
    val scope: EffectValueScope,
    val before: Int? = null,
    val after: Int? = null,
    val applied: Boolean,
    val generatedDiceDetail: String? = null,
    val error: String? = null,
)

data class RollLogEffectStep(
    val effectId: String,
    val name: String,
    val type: EffectType,
    val activated: Boolean,
    val conditions: List<RollLogEffectCondition> = emptyList(),
    val actions: List<RollLogEffectAction> = emptyList(),
)

data class RollLogPart(
    val name: String,
    val expression: String,
    val total: Int,
    val detail: String,
    val alternativeTotal: Int? = null,
    val alternativeDetail: String? = null,
    val originalTotal: Int? = null,
    val originalDetail: String? = null,
)

data class RollLog(
    val id: String,
    val characterId: String,
    val rollDefinitionId: String,
    val rollName: String,
    val expression: String,
    val total: Int,
    val detail: String,
    val timestamp: Long,
    // The legacy aggregate is retained for compatibility with older backups and clients.
    // Multiple named parts are presented independently to the user.
    val parts: List<RollLogPart> = emptyList(),
    val doubleRollMode: DoubleRollMode = DoubleRollMode.NORMAL,
    val comparisonTotal: Int? = null,
    val alternativeComparisonTotal: Int? = null,
    val effectSteps: List<RollLogEffectStep> = emptyList(),
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Legacy first-roll shake flag. Kept stable for settings/backups and reused by the V2 trigger UI.
    val shakeEnabled: Boolean = true,
    val firstRollTapEnabled: Boolean = true,
    val firstRollSwipeEnabled: Boolean = true,
    val rerollTapEnabled: Boolean = false,
    val rerollSwipeEnabled: Boolean = false,
    val rerollShakeEnabled: Boolean = false,
    // Local gesture preference; defaults on when reading pre-feature settings/backups.
    val doubleTapStatsEnabled: Boolean = true,
    val doubleRollDirectionalSwipeEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
    /** Device-wide cinematic preferences applied to every Roll. Included in JSON backup. */
    val visualEffects: RollVisualEffectsSettings = RollVisualEffectsSettings(),
    val showRollButton: Boolean = true,
    val rollButtonPosition: RollButtonPosition = RollButtonPosition.BOTTOM_RIGHT,
    val logRetention: Int = 20,
    // Device-local by design: this preference never participates in roaming settings sync.
    val conflictPolicy: ConflictPolicy = ConflictPolicy.ASK,
)

data class CharacterSyncMetadata(
    val characterId: String,
    val updatedAt: Long,
    val revision: String,
    val writerId: String,
    val baseRevision: String? = null,
)

data class AppData(
    val characters: List<CharacterProfile> = emptyList(),
    val modifiers: List<CharacterModifier> = emptyList(),
    val groups: List<RollGroup> = emptyList(),
    val rolls: List<RollDefinition> = emptyList(),
    val logs: List<RollLog> = emptyList(),
    val diceStyles: List<DiceStyle> = emptyList(),
    val characterSyncMetadata: List<CharacterSyncMetadata> = emptyList(),
)
