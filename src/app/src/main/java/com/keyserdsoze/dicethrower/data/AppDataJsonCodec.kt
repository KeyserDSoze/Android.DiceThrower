package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.CharacterImageRef
import com.keyserdsoze.dicethrower.model.CharacterSyncMetadata
import com.keyserdsoze.dicethrower.model.DiceAppearanceMode
import com.keyserdsoze.dicethrower.model.DiceMaterial
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollButtonPosition
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
import com.keyserdsoze.dicethrower.model.RollGroup
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.RollLog
import com.keyserdsoze.dicethrower.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

object AppDataJsonCodec {
    const val DATA_VERSION = 5

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
                    .put("defaultDiceStyleId", item.defaultDiceStyleId ?: JSONObject.NULL))
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
                    .put("order", item.order)
                    .put("levelRules", JSONArray().apply {
                        item.levelRules.forEach { rule ->
                            put(JSONObject()
                                .put("id", rule.id)
                                .put("kind", rule.kind.name)
                                .put("trigger", rule.trigger)
                                .put("expression", rule.expression))
                        }
                    })
                    .put("diceAppearance", encodeDiceAppearance(item.diceAppearance)))
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
                    .put("timestamp", item.timestamp))
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
                order = item.optInt("order"),
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
        .put("animationsEnabled", settings.animationsEnabled)
        .put("showRollButton", settings.showRollButton)
        .put("rollButtonPosition", settings.rollButtonPosition.name)
        .put("logRetention", settings.logRetention)

    fun decodeSettings(json: JSONObject): AppSettings = AppSettings(
        themeMode = enumValueOrDefault(json.optString("themeMode"), ThemeMode.SYSTEM),
        shakeEnabled = json.optBoolean("shakeEnabled", true),
        animationsEnabled = json.optBoolean("animationsEnabled", true),
        showRollButton = json.optBoolean("showRollButton", true),
        rollButtonPosition = enumValueOrDefault(
            json.optString("rollButtonPosition"),
            RollButtonPosition.BOTTOM_RIGHT,
        ),
        logRetention = json.optInt("logRetention", 20),
    )

    private fun encodeDiceAppearance(appearance: RollDiceAppearance): JSONObject = JSONObject()
        .put("mode", appearance.mode.name)
        .put("styleId", appearance.styleId ?: JSONObject.NULL)
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
