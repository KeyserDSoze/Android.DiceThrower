package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.keyserdsoze.dicethrower.data.EffectsJsonCodec
import com.keyserdsoze.dicethrower.model.CharacterImageRef
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollEffect
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Unsaved editor state, including incomplete input. Never written to the character database. */
internal data class RollBuilderDraft(
    val name: String,
    val groupId: String?,
    val doubleRollEnabled: Boolean,
    val minimumLevelText: String,
    val subgroups: List<RollSubgroup>,
    val effects: List<RollEffect>,
) {
    companion object {
        fun create(
            existing: RollDefinition?,
            initialGroupId: String?,
            idFactory: () -> String = { UUID.randomUUID().toString() },
        ) = RollBuilderDraft(
            name = existing?.name.orEmpty(),
            groupId = existing?.groupId ?: initialGroupId,
            doubleRollEnabled = existing?.doubleRollEnabled ?: true,
            minimumLevelText = (existing?.minimumLevel ?: 1).toString(),
            subgroups = existing?.subgroups?.takeIf { it.isNotEmpty() } ?: listOf(
                RollSubgroup(
                    id = idFactory(),
                    expression = existing?.expression ?: "1d20",
                    includeInDoubleRoll = existing == null,
                ),
            ),
            effects = existing?.effects.orEmpty(),
        )

        val StateSaver = Saver<RollBuilderDraft, String>(
            save = { RollBuilderDraftCodec.encode(it) },
            restore = { runCatching { RollBuilderDraftCodec.decode(it) }.getOrNull() },
        )
    }
}

/** UI-only format: unlike backup/import validation, it must accept partially typed formulas. */
internal object RollBuilderDraftCodec {
    fun encode(draft: RollBuilderDraft): String = JSONObject()
        .put("version", 1)
        .put("name", draft.name)
        .put("groupId", draft.groupId ?: JSONObject.NULL)
        .put("doubleRollEnabled", draft.doubleRollEnabled)
        .put("minimumLevelText", draft.minimumLevelText)
        .put("subgroups", JSONArray().apply {
            draft.subgroups.forEach { part ->
                put(JSONObject()
                    .put("id", part.id)
                    .put("name", part.name)
                    .put("expression", part.expression)
                    .put("operator", part.operator.name)
                    .put("includeInDoubleRoll", part.includeInDoubleRoll)
                    .put("includeInNormalRoll", part.includeInNormalRoll))
            }
        })
        .put("effects", EffectsJsonCodec.encode(draft.effects))
        .toString()

    fun decode(raw: String): RollBuilderDraft {
        val json = JSONObject(raw)
        require(json.getInt("version") == 1) { "Unsupported editor state" }
        val parts = json.getJSONArray("subgroups")
        return RollBuilderDraft(
            name = json.getString("name"),
            groupId = if (json.isNull("groupId")) null else json.getString("groupId"),
            doubleRollEnabled = json.getBoolean("doubleRollEnabled"),
            minimumLevelText = json.getString("minimumLevelText"),
            subgroups = List(parts.length()) { index ->
                val part = parts.getJSONObject(index)
                RollSubgroup(
                    id = part.getString("id"),
                    name = part.getString("name"),
                    expression = part.getString("expression"),
                    operator = RollSubgroupOperator.valueOf(part.getString("operator")),
                    includeInDoubleRoll = part.getBoolean("includeInDoubleRoll"),
                    includeInNormalRoll = part.getBoolean("includeInNormalRoll"),
                )
            },
            effects = EffectsJsonCodec.decode(json.getJSONArray("effects")),
        )
    }
}

internal val CharacterImageStateSaver = listSaver<CharacterImageRef?, Any>(
    save = { image ->
        image?.let { listOf(it.assetId, it.sha256, it.mimeType, it.byteSize) } ?: emptyList()
    },
    restore = { values ->
        if (values.isEmpty()) null else CharacterImageRef(
            assetId = values[0] as String,
            sha256 = values[1] as String,
            mimeType = values[2] as String,
            byteSize = values[3] as Long,
        )
    },
)

internal val ComposerSelectionStateSaver = listSaver<Set<Int>, Int>(
    save = { it.sorted() },
    restore = { it.toSet() },
)
