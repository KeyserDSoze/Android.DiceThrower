package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.DiceStylePresets
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
import java.security.MessageDigest

/** Style definitions have no character owner. Assignments are stable IDs on characters and rolls. */
object SharedDiceStyleCatalog {
    fun migrateLegacy(data: AppData): AppData {
        if (data.diceStyles.none { it.characterId != null }) return data
        // Preserve every legacy ID to avoid breaking references on old tester backups.
        return data.copy(diceStyles = data.diceStyles.map { it.copy(characterId = null) })
    }

    fun revision(styles: List<DiceStyle>): String {
        val canonical = styles.sortedBy { it.id }.joinToString(separator = "\n") {
            listOf(it.id, it.name, it.material.name, it.primaryColorArgb.toString(),
                it.secondaryColorArgb.toString(), it.order.toString()).joinToString("\u0000")
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun create(data: AppData, style: DiceStyle): AppData {
        require(style.id.isNotBlank() && !style.id.startsWith(DiceStylePresets.ID_PREFIX))
        require(data.diceStyles.none { it.id == style.id })
        require(style.name.isNotBlank())
        return data.copy(diceStyles = data.diceStyles + style.copy(
            characterId = null,
            order = (data.diceStyles.maxOfOrNull { it.order } ?: -1) + 1,
        ))
    }

    fun edit(data: AppData, style: DiceStyle): AppData {
        val previous = data.diceStyles.firstOrNull { it.id == style.id } ?: return data
        require(style.name.isNotBlank() && !style.id.startsWith(DiceStylePresets.ID_PREFIX))
        return data.copy(diceStyles = data.diceStyles.map {
            if (it.id == style.id) style.copy(characterId = null, order = previous.order) else it
        })
    }

    fun duplicate(data: AppData, sourceId: String, newId: String, name: String): AppData {
        val source = data.diceStyles.firstOrNull { it.id == sourceId }
            ?: DiceStylePresets.styleById(sourceId) ?: return data
        return create(data, source.copy(id = newId, name = name))
    }

    fun usage(data: AppData, styleId: String): DiceStyleUsage = DiceStyleUsage(
        isCharacterDefault = data.characters.any {
            it.defaultDiceStyleId == styleId || it.secondaryDiceStyleId == styleId
        },
        referencingRollIds = data.rolls.filter { roll ->
            val appearance = roll.diceAppearance
            appearance.styleId == styleId ||
                styleId in appearance.subgroupStyleIds.values ||
                styleId in appearance.perDieStyleIds.values ||
                styleId in appearance.randomStyleIds
        }.mapTo(linkedSetOf()) { it.id },
    )

    /** Replace references in one atomic AppData change; never leave dangling IDs. */
    fun delete(data: AppData, styleId: String): AppData {
        if (data.diceStyles.none { it.id == styleId }) return data
        val replacementId = data.diceStyles
            .filterNot { it.id == styleId }
            .sortedWith(compareBy<DiceStyle> { it.order }.thenBy { it.id })
            .firstOrNull()?.id ?: DiceStylePresets.fallbackId
        fun String.replaceDeleted() = if (this == styleId) replacementId else this
        return data.copy(
            diceStyles = data.diceStyles.filterNot { it.id == styleId },
            characters = data.characters.map { character ->
                character.copy(
                    defaultDiceStyleId = character.defaultDiceStyleId?.replaceDeleted(),
                    secondaryDiceStyleId = character.secondaryDiceStyleId?.replaceDeleted(),
                )
            },
            rolls = data.rolls.map { roll ->
                val appearance = roll.diceAppearance
                roll.copy(diceAppearance = appearance.copy(
                    styleId = appearance.styleId?.replaceDeleted(),
                    subgroupStyleIds = appearance.subgroupStyleIds.mapValues { it.value.replaceDeleted() },
                    perDieStyleIds = appearance.perDieStyleIds.mapValues { it.value.replaceDeleted() },
                    randomStyleIds = appearance.randomStyleIds.map { it.replaceDeleted() }.distinct(),
                ))
            },
        )
    }

    /**
     * A remote device may still have a character referencing a style deleted elsewhere.
     * Repair these references deterministically after applying the authoritative catalog.
     */
    fun repairDanglingReferences(data: AppData): AppData {
        val ids = data.diceStyles.mapTo(mutableSetOf()) { it.id } +
            DiceStylePresets.all.map { DiceStylePresets.styleId(it.key) }
        val fallback = data.diceStyles.filter { it.characterId == null }
            .sortedWith(compareBy<DiceStyle> { it.order }.thenBy { it.id })
            .firstOrNull()?.id ?: DiceStylePresets.fallbackId
        fun String?.validOrNull(): String? = this?.takeIf { it in ids } ?: if (this == null) null else fallback
        return data.copy(
            characters = data.characters.map { ch -> ch.copy(
                defaultDiceStyleId = ch.defaultDiceStyleId.validOrNull(),
                secondaryDiceStyleId = ch.secondaryDiceStyleId.validOrNull(),
            ) },
            rolls = data.rolls.map { roll ->
                val a = roll.diceAppearance
                roll.copy(diceAppearance = a.copy(
                    styleId = a.styleId.validOrNull(),
                    subgroupStyleIds = a.subgroupStyleIds.mapValues { it.value.validOrNull() ?: fallback },
                    perDieStyleIds = a.perDieStyleIds.mapValues { it.value.validOrNull() ?: fallback },
                    randomStyleIds = a.randomStyleIds.mapNotNull { it.validOrNull() }.distinct(),
                ))
            },
        )
    }

    fun move(data: AppData, styleId: String, delta: Int): AppData {
        val sorted = data.diceStyles.sortedBy { it.order }.toMutableList()
        val at = sorted.indexOfFirst { it.id == styleId }
        if (at < 0) return data
        val to = (at + delta).coerceIn(0, sorted.lastIndex)
        sorted.add(to, sorted.removeAt(at))
        val orders = sorted.mapIndexed { index, style -> style.id to index }.toMap()
        return data.copy(diceStyles = data.diceStyles.map { it.copy(order = orders.getValue(it.id)) })
    }
}
