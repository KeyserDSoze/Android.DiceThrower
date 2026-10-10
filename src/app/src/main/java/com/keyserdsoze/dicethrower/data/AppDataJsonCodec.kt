package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.CharacterImageRef
import com.keyserdsoze.dicethrower.model.CharacterSyncMetadata
import com.keyserdsoze.dicethrower.model.ConflictPolicy
import com.keyserdsoze.dicethrower.model.DiceAppearanceMode
import com.keyserdsoze.dicethrower.model.DoubleRollMode
import com.keyserdsoze.dicethrower.model.DiceMaterial
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.DiceTableTheme
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollButtonPosition
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
import com.keyserdsoze.dicethrower.model.RollGroup
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.RollLog
import com.keyserdsoze.dicethrower.model.RollLogPart
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import com.keyserdsoze.dicethrower.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

object AppDataJsonCodec {
    const val DATA_VERSION = 15

    fun encodeData(data: AppData): JSONObject = encodeData(data, includeLegacyImageUris = true)

    fun encodeDataForSync(data: AppData): JSONObject = encodeData(data, includeLegacyImageUris = false)

    private fun encodeData(data: AppData, includeLegacyImageUris: Boolean): JSONObject = JSONObject()
        .put("version", DATA_VERSION)
        .put("characters", JSONArray().apply {
            data.characters.forEach { item ->
                put(JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("imageUri", item.imageUri?.takeIf { includeLegacyImageUris } ?: JSONObject.NULL)
                    .put("image", item.image?.let(::encodeCharacterImage) ?: JSONObject.NULL)
                    .put("tag", item.tag)
                    .put("level", item.level)
                    .put("order", item.order)
                    .put("defaultDiceStyleId", item.defaultDiceStyleId ?: JSONObject.NULL)
                    .put("secondaryDiceStyleId", item.secondaryDiceStyleId ?: JSONObject.NULL)
                    .put("diceTableTheme", item.diceTableTheme.name)
                    .put("diceTableImage", item.diceTableImage?.let(::encodeCharacterImage) ?: JSONObject.NULL))
            }
        })
        .put("modifiers", JSONArray().apply {
            data.modifiers.forEach { item ->
                put(JSONObject()
                    .put("id", item.id)
                    .put("characterId", item.characterId)
                    .put("name", item.name)
                    .put("value", item.value)
                    .put("order", item.order))
            }
        })
        .put("groups", JSONArray().apply {
            data.groups.forEach { item ->
                put(JSONObject()
                    .put("id", item.id)
                    .put("characterId", item.characterId)
                    .put("name", item.name)
                    .put("order", item.order))
            }
        })
        .put("rolls", JSONArray().apply {
            data.rolls.forEach { item ->
                put(JSONObject()
                    .put("id", item.id)
                    .put("characterId", item.characterId)
                    .put("name", item.name)
                    .put("expression", item.expression)
                    .put("groupId", item.groupId ?: JSONObject.NULL)
                    .put("enabled", item.enabled)
                    .put("minimumLevel", item.minimumLevel)
                    .put("order", item.order)
                    .put("doubleRollEnabled", item.doubleRollEnabled)
                    .put("levelRules", JSONArray().apply {
                        item.levelRules.forEach { rule ->
                            put(JSONObject()
                                .put("id", rule.id)
                                .put("kind", rule.kind.name)
                                .put("trigger", rule.trigger)
                                .put("expression", rule.expression))
                        }
                    })
                    .put("subgroups", JSONArray().apply {
                        item.subgroups.forEach { subgroup ->
                            put(JSONObject()
                                .put("id", subgroup.id)
                                .put("name", subgroup.name)
                                .put("expression", subgroup.expression)
                                .put("operator", subgroup.operator.name)
                                .put("includeInDoubleRoll", subgroup.includeInDoubleRoll)
                                .put("includeInNormalRoll", subgroup.includeInNormalRoll))
                        }
                    })
                    .put("diceAppearance", encodeDiceAppearance(item.diceAppearance))
                    .put("effects", EffectsJsonCodec.encode(item.effects))
                    .put("visualEffects", encodeRollVisualEffects(item.visualEffects)))
            }
        })
        .put("logs", JSONArray().apply {
            data.logs.forEach { item ->
                put(JSONObject()
                    .put("id", item.id)
                    .put("characterId", item.characterId)
                    .put("rollDefinitionId", item.rollDefinitionId)
                    .put("rollName", item.rollName)
                    .put("expression", item.expression)
                    .put("total", item.total)
                    .put("detail", item.detail)
                    .put("timestamp", item.timestamp)
                    .put("doubleRollMode", item.doubleRollMode.name)
                    .put("comparisonTotal", item.comparisonTotal ?: JSONObject.NULL)
                    .put("alternativeComparisonTotal", item.alternativeComparisonTotal ?: JSONObject.NULL)
                    .put("effectSteps", RollLogEffectsJsonCodec.encode(item.effectSteps))
                    .put("parts", JSONArray().apply {
                        item.parts.forEach { part ->
                            put(JSONObject()
                                .put("name", part.name)
                                .put("expression", part.expression)
                                .put("total", part.total)
                                .put("detail", part.detail)
                                .put("alternativeTotal", part.alternativeTotal ?: JSONObject.NULL)
                                .put("alternativeDetail", part.alternativeDetail ?: JSONObject.NULL)
                                .put("originalTotal", part.originalTotal ?: JSONObject.NULL)
                                .put("originalDetail", part.originalDetail ?: JSONObject.NULL))
                        }
                    }))
            }
        })
        .put("diceStyles", JSONArray().apply {
            data.diceStyles.forEach { style ->
                put(JSONObject()
                    .put("id", style.id)
                    .put("characterId", style.characterId)
                    .put("name", style.name)
                    .put("material", style.material.name)
                    .put("primaryColorArgb", style.primaryColorArgb)
                    .put("secondaryColorArgb", style.secondaryColorArgb)
                    .put("order", style.order))
            }
        })
        .put("characterSyncMetadata", JSONArray().apply {
            data.characterSyncMetadata.sortedBy { it.characterId }.forEach { metadata ->
                put(JSONObject()
                    .put("characterId", metadata.characterId)
                    .put("updatedAt", metadata.updatedAt)
                    .put("revision", metadata.revision)
                    .put("writerId", metadata.writerId)
                    .put("baseRevision", metadata.baseRevision ?: JSONObject.NULL))
            }
        })

    private fun encodeRollVisualEffects(settings: com.keyserdsoze.dicethrower.model.RollVisualEffectsSettings) =
        JSONObject()
            .put("profile", settings.profile.name)
            .put("badge", settings.badge)
            .put("groupLanes", settings.groupLanes)
            .put("winnerSpotlight", settings.winnerSpotlight)
            .put("tableAura", settings.tableAura)
            .put("particles", settings.particles)
            .put("actionCues", settings.actionCues)
            .put("resultTransitions", settings.resultTransitions)
            .put("cameraImpact", settings.cameraImpact)

    private fun decodeRollVisualEffects(json: JSONObject?): com.keyserdsoze.dicethrower.model.RollVisualEffectsSettings {
        val profile = runCatching {
            com.keyserdsoze.dicethrower.model.RollVisualProfile.valueOf(
                json?.optString("profile") ?: "BALANCED",
            )
        }.getOrDefault(com.keyserdsoze.dicethrower.model.RollVisualProfile.BALANCED)
        val preset = com.keyserdsoze.dicethrower.model.RollVisualEffectsSettings.preset(profile)
        return if (json == null) preset else preset.copy(
            badge = json.optBoolean("badge", preset.badge),
            groupLanes = json.optBoolean("groupLanes", preset.groupLanes),
            winnerSpotlight = json.optBoolean("winnerSpotlight", preset.winnerSpotlight),
            tableAura = json.optBoolean("tableAura", preset.tableAura),
            particles = json.optBoolean("particles", preset.particles),
            actionCues = json.optBoolean("actionCues", preset.actionCues),
            resultTransitions = json.optBoolean("resultTransitions", preset.resultTransitions),
            cameraImpact = json.optBoolean("cameraImpact", preset.cameraImpact),
        )
    }

    fun decodeData(json: JSONObject): AppData = AppData(
        characters = json.optJSONArray("characters").mapObjects { item ->
            CharacterProfile(
                id = item.getString("id"),
                name = item.getString("name"),
                imageUri = item.optNullableString("imageUri"),
                image = item.optJSONObject("image")?.let(::decodeCharacterImage),
                tag = item.optString("tag"),
                level = item.optInt("level", 1).coerceAtLeast(1),
                order = item.optInt("order"),
                defaultDiceStyleId = item.optNullableString("defaultDiceStyleId"),
                secondaryDiceStyleId = item.optNullableString("secondaryDiceStyleId"),
                diceTableTheme = enumValueOrDefault(
                    item.optString("diceTableTheme"),
                    DiceTableTheme.ARCANE,
                ),
                diceTableImage = item.optJSONObject("diceTableImage")?.let(::decodeCharacterImage),
            )
        },
        modifiers = json.optJSONArray("modifiers").mapObjects { item ->
            CharacterModifier(
                id = item.getString("id"),
                characterId = item.getString("characterId"),
                name = item.getString("name"),
                value = item.optInt("value"),
                order = item.optInt("order"),
            )
        },
        groups = json.optJSONArray("groups").mapObjects { item ->
            RollGroup(
                id = item.getString("id"),
                characterId = item.getString("characterId"),
                name = item.getString("name"),
                order = item.optInt("order"),
            )
        },
        rolls = json.optJSONArray("rolls").mapObjects { item ->
            RollDefinition(
                id = item.getString("id"),
                characterId = item.getString("characterId"),
                name = item.getString("name"),
                expression = item.getString("expression"),
                groupId = item.optNullableString("groupId"),
                enabled = item.optBoolean("enabled", true),
                minimumLevel = if (item.has("minimumLevel")) item.getInt("minimumLevel") else 1,
                order = item.optInt("order"),
                doubleRollEnabled = item.optBoolean("doubleRollEnabled", false),
                effects = EffectsJsonCodec.decode(item.optJSONArray("effects")),
                visualEffects = decodeRollVisualEffects(item.optJSONObject("visualEffects")),
                levelRules = item.optJSONArray("levelRules").mapObjects { rule ->
                    RollLevelRule(
                        id = rule.getString("id"),
                        kind = enumValueOrDefault(
                            rule.optString("kind"),
                            LevelRuleKind.FROM_LEVEL,
                        ),
                        trigger = rule.optInt("trigger", 1).coerceAtLeast(1),
                        expression = rule.getString("expression"),
                    )
                },
                subgroups = item.optJSONArray("subgroups").mapObjects { subgroup ->
                    RollSubgroup(
                        id = subgroup.getString("id"),
                        name = subgroup.optString("name"),
                        expression = subgroup.getString("expression"),
                        operator = enumValueOrDefault(
                            subgroup.optString("operator"),
                            RollSubgroupOperator.ADD,
                        ),
                        includeInDoubleRoll = subgroup.optBoolean("includeInDoubleRoll", false),
                    )
                },
                diceAppearance = item.optJSONObject("diceAppearance")?.let(::decodeDiceAppearance)
                    ?: RollDiceAppearance(),
            )
        },
        logs = json.optJSONArray("logs").mapObjects { item ->
            RollLog(
                id = item.getString("id"),
                characterId = item.getString("characterId"),
                rollDefinitionId = item.getString("rollDefinitionId"),
                rollName = item.getString("rollName"),
                expression = item.getString("expression"),
                total = item.getInt("total"),
                detail = item.optString("detail"),
                timestamp = item.getLong("timestamp"),
                doubleRollMode = enumValueOrDefault(item.optString("doubleRollMode"), DoubleRollMode.NORMAL),
                comparisonTotal = item.optIntOrNull("comparisonTotal"),
                alternativeComparisonTotal = item.optIntOrNull("alternativeComparisonTotal"),
                effectSteps = RollLogEffectsJsonCodec.decode(item.optJSONArray("effectSteps")),
                parts = item.optJSONArray("parts").mapObjects { part ->
                    RollLogPart(
                        name = part.optString("name"),
                        expression = part.optString("expression"),
                        total = part.getInt("total"),
                        detail = part.optString("detail"),
                        alternativeTotal = part.optIntOrNull("alternativeTotal"),
                        alternativeDetail = part.optNullableString("alternativeDetail"),
                        originalTotal = part.optIntOrNull("originalTotal"),
                        originalDetail = part.optNullableString("originalDetail"),
                    )
                },
            )
        },
        diceStyles = json.optJSONArray("diceStyles").mapObjects { item ->
            DiceStyle(
                id = item.getString("id"),
                characterId = item.getString("characterId"),
                name = item.getString("name"),
                material = enumValueOrDefault(item.optString("material"), DiceMaterial.GLOSSY_RESIN),
                primaryColorArgb = item.optInt("primaryColorArgb", DEFAULT_PRIMARY_COLOR_ARGB),
                secondaryColorArgb = item.optInt("secondaryColorArgb", DEFAULT_SECONDARY_COLOR_ARGB),
                order = item.optInt("order"),
            )
        },
        characterSyncMetadata = json.optJSONArray("characterSyncMetadata").mapObjects { item ->
            CharacterSyncMetadata(
                characterId = item.getString("characterId"),
                updatedAt = item.optLong("updatedAt"),
                revision = item.getString("revision"),
                writerId = item.getString("writerId"),
                baseRevision = item.optNullableString("baseRevision"),
            )
        },
    )

    fun encodeSettings(settings: AppSettings): JSONObject = JSONObject()
        .put("themeMode", settings.themeMode.name)
        .put("shakeEnabled", settings.shakeEnabled)
        .put("firstRollTapEnabled", settings.firstRollTapEnabled)
        .put("firstRollSwipeEnabled", settings.firstRollSwipeEnabled)
        .put("rerollTapEnabled", settings.rerollTapEnabled)
        .put("rerollSwipeEnabled", settings.rerollSwipeEnabled)
        .put("rerollShakeEnabled", settings.rerollShakeEnabled)
        .put("doubleTapStatsEnabled", settings.doubleTapStatsEnabled)
        .put("doubleRollDirectionalSwipeEnabled", settings.doubleRollDirectionalSwipeEnabled)
        .put("animationsEnabled", settings.animationsEnabled)
        .put("visualEffects", encodeRollVisualEffects(settings.visualEffects))
        .put("showRollButton", settings.showRollButton)
        .put("rollButtonPosition", settings.rollButtonPosition.name)
        .put("logRetention", settings.logRetention)
        .put("conflictPolicy", settings.conflictPolicy.name)

    fun decodeSettings(json: JSONObject): AppSettings = AppSettings(
        themeMode = enumValueOrDefault(json.optString("themeMode"), ThemeMode.SYSTEM),
        shakeEnabled = json.optBoolean("shakeEnabled", true),
        firstRollTapEnabled = json.optBoolean("firstRollTapEnabled", true),
        firstRollSwipeEnabled = json.optBoolean("firstRollSwipeEnabled", true),
        rerollTapEnabled = json.optBoolean("rerollTapEnabled", false),
        rerollSwipeEnabled = json.optBoolean("rerollSwipeEnabled", false),
        rerollShakeEnabled = json.optBoolean("rerollShakeEnabled", false),
        doubleTapStatsEnabled = json.optBoolean("doubleTapStatsEnabled", true),
        doubleRollDirectionalSwipeEnabled = json.optBoolean("doubleRollDirectionalSwipeEnabled", true),
        animationsEnabled = json.optBoolean("animationsEnabled", true),
        visualEffects = decodeRollVisualEffects(json.optJSONObject("visualEffects")),
        showRollButton = json.optBoolean("showRollButton", true),
        rollButtonPosition = enumValueOrDefault(
            json.optString("rollButtonPosition"),
            RollButtonPosition.BOTTOM_RIGHT,
        ),
        logRetention = json.optInt("logRetention", 20),
        conflictPolicy = enumValueOrDefault(json.optString("conflictPolicy"), ConflictPolicy.ASK),
    )

    private fun encodeDiceAppearance(appearance: RollDiceAppearance): JSONObject = JSONObject()
        .put("mode", appearance.mode.name)
        .put("styleId", appearance.styleId ?: JSONObject.NULL)
        .put("subgroupStyleIds", JSONArray().apply {
            appearance.subgroupStyleIds.toSortedMap().forEach { (subgroupId, styleId) ->
                put(JSONObject().put("subgroupId", subgroupId).put("styleId", styleId))
            }
        })
        .put("perDieStyleIds", JSONArray().apply {
            appearance.perDieStyleIds.toSortedMap().forEach { (slot, styleId) ->
                put(JSONObject().put("slot", slot).put("styleId", styleId))
            }
        })
        .put("randomStyleIds", JSONArray().apply {
            appearance.randomStyleIds.forEach(::put)
        })

    private fun decodeDiceAppearance(json: JSONObject): RollDiceAppearance = RollDiceAppearance(
        mode = enumValueOrDefault(json.optString("mode"), DiceAppearanceMode.CHARACTER_DEFAULT),
        styleId = json.optNullableString("styleId"),
        subgroupStyleIds = json.optJSONArray("subgroupStyleIds").mapObjects { item ->
            item.getString("subgroupId") to item.getString("styleId")
        }.toMap(),
        perDieStyleIds = json.optJSONArray("perDieStyleIds").mapObjects { item ->
            item.getString("slot") to item.getString("styleId")
        }.toMap(),
        randomStyleIds = json.optJSONArray("randomStyleIds").mapStrings(),
    )

    private fun encodeCharacterImage(image: CharacterImageRef): JSONObject = JSONObject()
        .put("assetId", image.assetId)
        .put("sha256", image.sha256)
        .put("mimeType", image.mimeType)
        .put("byteSize", image.byteSize)

    private fun decodeCharacterImage(json: JSONObject): CharacterImageRef = CharacterImageRef(
        assetId = json.getString("assetId"),
        sha256 = json.getString("sha256"),
        mimeType = json.getString("mimeType"),
        byteSize = json.getLong("byteSize"),
    )

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun <T> JSONArray?.mapObjects(block: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return List(length()) { index -> block(getJSONObject(index)) }
    }

    private fun JSONArray?.mapStrings(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { index -> getString(index) }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String,
        default: T,
    ): T = enumValues<T>().firstOrNull { it.name == value } ?: default

    private const val DEFAULT_PRIMARY_COLOR_ARGB = -14384101
    private const val DEFAULT_SECONDARY_COLOR_ARGB = -11751600
}
