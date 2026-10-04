package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollButtonPosition
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollGroup
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.RollLog
import com.keyserdsoze.dicethrower.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

object AppDataJsonCodec {
    const val DATA_VERSION = 2

    fun encodeData(data: AppData): JSONObject = JSONObject()
        .put("version", DATA_VERSION)
        .put("characters", JSONArray().apply {
            data.characters.forEach { item ->
                put(JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("imageUri", item.imageUri ?: JSONObject.NULL)
                    .put("tag", item.tag)
                    .put("level", item.level)
                    .put("order", item.order))
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
                    }))
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

    fun decodeData(json: JSONObject): AppData = AppData(
        characters = json.optJSONArray("characters").mapObjects { item ->
            CharacterProfile(
                id = item.getString("id"),
                name = item.getString("name"),
                imageUri = item.optNullableString("imageUri"),
                tag = item.optString("tag"),
                level = item.optInt("level", 1).coerceAtLeast(1),
                order = item.optInt("order"),
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

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun <T> JSONArray?.mapObjects(block: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return List(length()) { index -> block(getJSONObject(index)) }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String,
        default: T,
    ): T = enumValues<T>().firstOrNull { it.name == value } ?: default
}
