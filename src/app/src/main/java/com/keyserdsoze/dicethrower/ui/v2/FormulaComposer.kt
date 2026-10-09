package com.keyserdsoze.dicethrower.ui.v2

import com.keyserdsoze.dicethrower.dice.RollFormulaResolver
import com.keyserdsoze.dicethrower.model.CharacterModifier

/**
 * A lossless, deliberately shallow visual projection of one Roll Part expression.
 * It does not alter persisted RollSubgroups: those represent separately reported results.
 * Unsupported/nested expressions remain editable in the text editor, never rewritten.
 */
internal data class ComposerTerm(
    val sign: Char,
    val expression: String,
    val grouped: List<ComposerTerm> = emptyList(),
    val multiplier: String? = null,
) {
    val isGroup: Boolean get() = grouped.isNotEmpty()
}

/**
 * Editable fields for a single atomic composer term. Complex expressions remain
 * represented as opaque terms and are never rewritten by the simple controls.
 */
internal data class ComposerSimpleInput(
    val sign: Char,
    val amount: String,
    val sides: Int? = null,
)

internal object FormulaComposer {
    private val diceTerm = Regex("""^(\\{[^{}]+\\}|[0-9]*)[dD]([0-9]+)$""")
    private val scalarTerm = Regex("""^(\\{[^{}]+\\}|[0-9]+)$""")

    fun simpleInput(term: ComposerTerm): ComposerSimpleInput? {
        if (term.isGroup) return null
        diceTerm.matchEntire(term.expression)?.let {
            val amount = it.groupValues[1].ifBlank { "1" }
            val sides = it.groupValues[2].toIntOrNull() ?: return null
            return ComposerSimpleInput(term.sign, amount, sides)
        }
        if (scalarTerm.matches(term.expression)) {
            return ComposerSimpleInput(term.sign, term.expression)
        }
        return null
    }

    fun replaceSimpleTerm(
        terms: List<ComposerTerm>,
        index: Int,
        amount: String,
        sides: Int?,
        sign: Char,
    ): List<ComposerTerm>? {
        val old = terms.getOrNull(index) ?: return null
        if (simpleInput(old) == null || sign != '+' && sign != '-') return null
        if (!scalarTerm.matches(amount)) return null
        if (sides != null && sides !in com.keyserdsoze.dicethrower.dice.DiceExpression.supportedSides) return null
        val next = amount + (sides?.let { "d$it" } ?: "")
        return terms.toMutableList().apply {
            set(index, old.copy(sign = sign, expression = next))
        }
    }

    fun parse(
        expression: String,
        level: Int,
        modifiers: List<CharacterModifier>,
    ): List<ComposerTerm>? {
        if (!RollFormulaResolver.validateTemplate(expression, level, modifiers)) return null
        return parseTerms(expression, allowGroups = true)
    }

    fun serialize(terms: List<ComposerTerm>): String = buildString {
        terms.forEachIndexed { index, term ->
            if (term.sign == '-') append('-') else if (index > 0) append('+')
            if (term.isGroup) {
                append('(')
                append(serialize(term.grouped))
                append(')')
                term.multiplier?.let { append('x'); append(it) }
            } else {
                append(term.expression)
            }
        }
    }

    fun canGroup(terms: List<ComposerTerm>, selected: Set<Int>): Boolean {
        if (selected.size < 2 || selected.any {
                it !in terms.indices || terms[it].isGroup ||
                    terms[it].expression.contains('(') || terms[it].expression.contains(')')
            }) return false
        val ordered = selected.sorted()
        return ordered.last() - ordered.first() + 1 == ordered.size
    }

    fun group(terms: List<ComposerTerm>, selected: Set<Int>): List<ComposerTerm> {
        if (!canGroup(terms, selected)) return terms
        val indices = selected.sorted()
        val start = indices.first()
        val end = indices.last()
        val firstSign = terms[start].sign
        val children = terms.subList(start, end + 1).mapIndexed { index, term ->
            term.copy(sign = when {
                index == 0 -> '+'
                firstSign == '-' -> flip(term.sign)
                else -> term.sign
            })
        }
        return terms.take(start) + ComposerTerm(firstSign, "", grouped = children) + terms.drop(end + 1)
    }

    fun ungroup(terms: List<ComposerTerm>, index: Int): List<ComposerTerm> {
        val group = terms.getOrNull(index)?.takeIf { it.isGroup } ?: return terms
        val children = group.grouped.map { child ->
            val sign = if (group.sign == '-') flip(child.sign) else child.sign
            val value = group.multiplier?.let { factor ->
                if (factor == "1") child.expression else "(${child.expression})x$factor"
            } ?: child.expression
            ComposerTerm(sign, value)
        }
        return terms.take(index) + children + terms.drop(index + 1)
    }

    fun move(terms: List<ComposerTerm>, index: Int, offset: Int): List<ComposerTerm> {
        if (index !in terms.indices || index + offset !in terms.indices) return terms
        return terms.toMutableList().apply {
            val term = removeAt(index)
            add(index + offset, term)
        }
    }

    fun remove(terms: List<ComposerTerm>, index: Int): List<ComposerTerm> =
        if (index in terms.indices) terms.filterIndexed { i, _ -> i != index } else terms

    fun multiplyGroup(terms: List<ComposerTerm>, index: Int, multiplier: String?): List<ComposerTerm> =
        terms.mapIndexed { position, term ->
            if (position == index && term.isGroup) {
                term.copy(multiplier = multiplier?.takeIf { it.isNotBlank() && it != "1" })
            } else term
        }

    private fun parseTerms(raw: String, allowGroups: Boolean): List<ComposerTerm>? {
        val fragments = splitTopLevel(raw) ?: return null
        return fragments.map { (sign, body) ->
            // An unsupported but valid atomic expression is still movable and
            // removable; never discard its original formula or invent a group.
            val group = if (allowGroups && body.contains('(')) parseGroup(body) else null
            if (group == null) ComposerTerm(sign, body)
            else ComposerTerm(sign, "", grouped = group.first, multiplier = group.second)
        }
    }

    private fun parseGroup(body: String): Pair<List<ComposerTerm>, String?>? {
        val suffix = Regex("""^\((.+)\)[xX×*](\{[^{}]+\}|[0-9]+)$""").matchEntire(body)
        val prefix = Regex("""^(\{[^{}]+\}|[0-9]+)[xX×*]\((.+)\)$""").matchEntire(body)
        val simple = Regex("""^\((.+)\)$""").matchEntire(body)
        val inner: String
        val factor: String?
        when {
            suffix != null -> {
                inner = suffix.groupValues[1]
                factor = suffix.groupValues[2]
            }
            prefix != null -> {
                inner = prefix.groupValues[2]
                factor = prefix.groupValues[1]
            }
            simple != null -> {
                inner = simple.groupValues[1]
                factor = null
            }
            else -> return null
        }
        if (inner.contains('(') || inner.contains(')')) return null
        val children = parseTerms(inner, allowGroups = false) ?: return null
        if (children.size < 2) return null
        return children to factor
    }

    private fun splitTopLevel(raw: String): List<Pair<Char, String>>? {
        val input = raw.trim()
        if (input.isBlank()) return null
        val result = mutableListOf<Pair<Char, String>>()
        var sign = '+'
        var start = 0
        var depth = 0
        var braces = 0
        input.forEachIndexed { index, char ->
            when (char) {
                '{' -> braces++
                '}' -> braces--
                '(' -> if (braces == 0) depth++
                ')' -> if (braces == 0) depth--
            }
            if (depth < 0 || braces < 0) return null
            if (depth == 0 && braces == 0 && char in "+-" &&
                (index == 0 || (index > start && input.substring(start, index)
                    .trimEnd().lastOrNull()?.let { it != 'x' && it != 'X' && it != '×' && it != '*' } != false))
            ) {
                if (index > start) {
                    val previous = input.substring(start, index).trim()
                    if (previous.isBlank()) return null
                    result += sign to previous
                }
                sign = char
                start = index + 1
            }
        }
        if (depth != 0 || braces != 0) return null
        val last = input.substring(start).trim()
        if (last.isBlank()) return null
        result += sign to last
        return result
    }

    private fun flip(sign: Char): Char = if (sign == '-') '+' else '-'
}
