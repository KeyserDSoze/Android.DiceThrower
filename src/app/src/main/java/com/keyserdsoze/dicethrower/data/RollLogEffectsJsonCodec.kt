package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectComparison
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.EffectValueScope
import com.keyserdsoze.dicethrower.model.RollLogEffectAction
import com.keyserdsoze.dicethrower.model.RollLogEffectCondition
import com.keyserdsoze.dicethrower.model.RollLogEffectStep
import org.json.JSONArray
import org.json.JSONObject

/** Renderer-independent per-throw audit trail, portable via backup and sync. */
internal object RollLogEffectsJsonCodec {
    fun encode(steps: List<RollLogEffectStep>): JSONArray = JSONArray().apply {
        steps.forEach { step ->
            put(JSONObject()
                .put("effectId", step.effectId)
                .put("name", step.name)
                .put("type", step.type.name)
                .put("activated", step.activated)
                .put("conditions", JSONArray().apply {
                    step.conditions.forEach { condition ->
                        put(JSONObject()
                            .put("groupId", condition.groupId)
                            .put("conditionId", condition.conditionId)
                            .put("actual", condition.actual ?: JSONObject.NULL)
                            .put("threshold", condition.threshold ?: JSONObject.NULL)
                            .put("comparison", condition.comparison.name)
                            .put("passed", condition.passed)
                            .put("error", condition.error ?: JSONObject.NULL))
                    }
                })
                .put("actions", JSONArray().apply {
                    step.actions.forEach { action ->
                        put(JSONObject()
                            .put("actionId", action.actionId)
                            .put("kind", action.kind.name)
                            .put("targetPartId", action.targetPartId ?: JSONObject.NULL)
                            .put("scope", action.scope.name)
                            .put("before", action.before ?: JSONObject.NULL)
                            .put("after", action.after ?: JSONObject.NULL)
                            .put("applied", action.applied)
                            .put("generatedDiceDetail", action.generatedDiceDetail ?: JSONObject.NULL)
                            .put("error", action.error ?: JSONObject.NULL))
                    }
                }))
        }
    }

    fun decode(array: JSONArray?): List<RollLogEffectStep> {
        if (array == null) return emptyList()
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            RollLogEffectStep(
                effectId = item.getString("effectId"),
                name = item.getString("name"),
                type = item.requiredEnum("type"),
                activated = item.optBoolean("activated", false),
                conditions = item.optJSONArray("conditions").mapObjects { condition ->
                    RollLogEffectCondition(
                        groupId = condition.getString("groupId"),
                        conditionId = condition.getString("conditionId"),
                        actual = condition.optionalInt("actual"),
                        threshold = condition.optionalInt("threshold"),
                        comparison = condition.requiredEnum("comparison"),
                        passed = condition.optBoolean("passed", false),
                        error = condition.optionalString("error"),
                    )
                },
                actions = item.optJSONArray("actions").mapObjects { action ->
                    RollLogEffectAction(
                        actionId = action.getString("actionId"),
                        kind = action.requiredEnum<EffectActionType>("kind"),
                        targetPartId = action.optionalString("targetPartId"),
                        scope = action.requiredEnum<EffectValueScope>("scope"),
                        before = action.optionalInt("before"),
                        after = action.optionalInt("after"),
                        applied = action.optBoolean("applied", false),
                        generatedDiceDetail = action.optionalString("generatedDiceDetail"),
                        error = action.optionalString("error"),
                    )
                },
            )
        }
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun JSONObject.optionalInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else getInt(key)

    private inline fun <reified T : Enum<T>> JSONObject.requiredEnum(key: String): T =
        enumValues<T>().firstOrNull { it.name == getString(key) }
            ?: throw IllegalArgumentException("Unsupported Effects trace value for $key")

    private fun <T> JSONArray?.mapObjects(convert: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return List(length()) { convert(getJSONObject(it)) }
    }
}
