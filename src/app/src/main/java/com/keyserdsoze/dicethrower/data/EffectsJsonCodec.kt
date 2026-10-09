package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.EffectAction
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectActivationGroup
import com.keyserdsoze.dicethrower.model.EffectComparison
import com.keyserdsoze.dicethrower.model.EffectCondition
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.EffectValueScope
import com.keyserdsoze.dicethrower.model.EffectValueSource
import com.keyserdsoze.dicethrower.model.RollEffect
import org.json.JSONArray
import org.json.JSONObject

/** Shared local, portable backup and optional Drive serialization. */
internal object EffectsJsonCodec {
    fun encode(effects: List<RollEffect>): JSONArray = JSONArray().apply {
        effects.forEach { effect ->
            put(JSONObject()
                .put("id", effect.id)
                .put("name", effect.name)
                .put("type", effect.type.name)
                .put("order", effect.order)
                .put("enabled", effect.enabled)
                .put("stopFollowingEffects", effect.stopFollowingEffects)
                .put("activationGroups", JSONArray().apply {
                    effect.activationGroups.forEach { group ->
                        put(JSONObject()
                            .put("id", group.id)
                            .put("conditions", JSONArray().apply {
                                group.conditions.forEach { condition ->
                                    put(JSONObject()
                                        .put("id", condition.id)
                                        .put("source", condition.source.name)
                                        .put("partId", condition.partId ?: JSONObject.NULL)
                                        .put("variableName", condition.variableName ?: JSONObject.NULL)
                                        .put("scope", condition.scope.name)
                                        .put("comparison", condition.comparison.name)
                                        .put("threshold", condition.threshold))
                                }
                            }))
                    }
                })
                .put("actions", JSONArray().apply {
                    effect.actions.forEach { action ->
                        put(JSONObject()
                            .put("id", action.id)
                            .put("kind", action.kind.name)
                            .put("targetPartId", action.targetPartId ?: JSONObject.NULL)
                            .put("scope", action.scope.name)
                            .put("expression", action.expression))
                    }
                }))
        }
    }

    fun decode(array: JSONArray?): List<RollEffect> {
        if (array == null) return emptyList() // Version 11 and earlier.
        return List(array.length()) { index ->
            val effect = array.getJSONObject(index)
            RollEffect(
                id = effect.getString("id"),
                name = effect.getString("name"),
                type = effect.requiredEnum("type"),
                order = effect.optInt("order", index),
                enabled = effect.optBoolean("enabled", true),
                stopFollowingEffects = effect.optBoolean("stopFollowingEffects", false),
                activationGroups = effect.optJSONArray("activationGroups").mapObjects { group ->
                    EffectActivationGroup(
                        id = group.getString("id"),
                        conditions = group.optJSONArray("conditions").mapObjects { condition ->
                            EffectCondition(
                                id = condition.getString("id"),
                                source = condition.requiredEnum<EffectValueSource>("source"),
                                partId = condition.nullable("partId"),
                                variableName = condition.nullable("variableName"),
                                scope = condition.requiredEnum<EffectValueScope>("scope"),
                                comparison = condition.requiredEnum<EffectComparison>("comparison"),
                                threshold = condition.getString("threshold"),
                            )
                        },
                    )
                },
                actions = effect.optJSONArray("actions").mapObjects { action ->
                    EffectAction(
                        id = action.getString("id"),
                        kind = action.requiredEnum<EffectActionType>("kind"),
                        targetPartId = action.nullable("targetPartId"),
                        scope = action.requiredEnum<EffectValueScope>("scope"),
                        expression = action.optString("expression"),
                    )
                },
            )
        }
    }

    private fun JSONObject.nullable(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private inline fun <reified T : Enum<T>> JSONObject.requiredEnum(key: String): T =
        enumValues<T>().firstOrNull { it.name == getString(key) }
            ?: throw IllegalArgumentException("Unsupported Effects enum $key")

    private fun <T> JSONArray?.mapObjects(convert: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return List(length()) { index -> convert(getJSONObject(index)) }
    }
}
