package com.keyserdsoze.dicethrower.model

/**
 * Effects are game-independent rules applied by the logical roll engine, never by 3D physics.
 * The schema is intentionally separate from the evaluator/renderer (issues #104-#110).
 */
enum class EffectType { BONUS, MALUS }
enum class EffectValueScope { DICE_ONLY, MODIFIERS_ONLY, TOTAL }
enum class EffectValueSource { PART, ROLL, VARIABLE }
enum class EffectComparison { GREATER_OR_EQUAL, LESS_OR_EQUAL, GREATER, LESS, EQUAL, NOT_EQUAL }
enum class EffectActionType { ADD, SUBTRACT, MULTIPLY, REPLACE, REROLL, ROLL_AFTER }

data class EffectCondition(
    val id: String,
    val source: EffectValueSource = EffectValueSource.PART,
    /** Immutable Part ID, not a user-editable Part name. */
    val partId: String? = null,
    val variableName: String? = null,
    val scope: EffectValueScope = EffectValueScope.DICE_ONLY,
    val comparison: EffectComparison = EffectComparison.GREATER_OR_EQUAL,
    /** Formula/template evaluated by the later expression engine; "20" is a valid threshold. */
    val threshold: String = "20",
)

/** Activation groups are OR-ed; the conditions inside each group are AND-ed. */
data class EffectActivationGroup(
    val id: String,
    val conditions: List<EffectCondition> = emptyList(),
)

data class EffectAction(
    val id: String,
    val kind: EffectActionType,
    /** Stable target Part ID; null means the complete Roll when the action supports it. */
    val targetPartId: String? = null,
    val scope: EffectValueScope = EffectValueScope.TOTAL,
    /** Expression may refer to other Parts via persisted {partId:<id>} tokens. */
    val expression: String = "",
)

data class RollEffect(
    val id: String,
    val name: String,
    val type: EffectType,
    val order: Int = 0,
    val enabled: Boolean = true,
    /** Stop subsequent effects only if this effect actually activates. */
    val stopFollowingEffects: Boolean = false,
    val activationGroups: List<EffectActivationGroup> = emptyList(),
    val actions: List<EffectAction> = emptyList(),
)

/**
 * Converts human-friendly {parts:Damage} references to stable {partId:UUID}
 * references for persistence, then projects them back for display after renaming.
 * No stored expressions need to change when a Part is renamed.
 */
object PartReferenceAliases {
    private val readable = Regex("""\{parts:([^{}]+)\}""")
    private val stable = Regex("""\{partId:([^{}]+)\}""")

    fun store(expression: String, parts: List<RollSubgroup>): String {
        val result = readable.replace(expression) { matched ->
            val name = matched.groupValues[1].trim()
            val candidates = parts.filter { it.name.trim().equals(name, ignoreCase = true) }
            require(candidates.size == 1) {
                "Part name '$name' must identify exactly one Part"
            }
            "{partId:${candidates.single().id}}"
        }
        validateStoredReferences(result, parts)
        return result
    }

    fun display(expression: String, parts: List<RollSubgroup>): String =
        stable.replace(expression) { matched ->
            val id = matched.groupValues[1]
            val part = parts.singleOrNull { it.id == id }
                ?: return@replace matched.value
            val unique = part.name.isNotBlank() &&
                parts.count { it.name.trim().equals(part.name.trim(), ignoreCase = true) } == 1
            if (unique) "{parts:${part.name.trim()}}" else matched.value
        }

    fun referencedPartIds(expression: String): Set<String> =
        stable.findAll(expression).map { it.groupValues[1] }.toSet()

    fun validateStoredReferences(expression: String, parts: List<RollSubgroup>) {
        val ids = parts.map { it.id }.toSet()
        require(referencedPartIds(expression).all { it in ids }) {
            "Effect expression references a missing Part"
        }
        require(!readable.containsMatchIn(expression)) {
            "Unresolved readable Part reference: store it by stable ID"
        }
    }
}
