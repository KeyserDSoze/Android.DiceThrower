package com.keyserdsoze.dicethrower.dice

import kotlin.random.Random

data class DiceComponent(
    val count: Int,
    val sides: Int,
    val sign: Int,
    val rolls: List<Int>,
) {
    val subtotal: Int get() = rolls.sum() * sign
}

data class DiceRollResult(
    val total: Int,
    val components: List<DiceComponent>,
    val constantTotal: Int,
) {
    fun detail(): String {
        val dice = components.joinToString(" | ") { component ->
            val prefix = if (component.sign < 0) "-" else "+"
            "$prefix${component.count}d${component.sides}[${component.rolls.joinToString(",")}]"
        }
        val constant = if (constantTotal == 0) "" else {
            val prefix = if (constantTotal > 0) "+" else ""
            " $prefix$constantTotal"
        }
        return (dice + constant).trim().removePrefix("+")
    }
}

class DiceExpression private constructor(
    val source: String,
    private val terms: List<Term>,
) {
    sealed interface Term {
        val sign: Int

        data class Dice(
            val count: Int,
            val sides: Int,
            override val sign: Int,
        ) : Term

        data class Constant(
            val value: Int,
            override val sign: Int,
        ) : Term
    }

    fun evaluate(random: Random = Random.Default): DiceRollResult {
        val components = mutableListOf<DiceComponent>()
        var constantTotal = 0

        terms.forEach { term ->
            when (term) {
                is Term.Constant -> constantTotal += term.value * term.sign
                is Term.Dice -> {
                    val rolls = List(term.count) { random.nextInt(1, term.sides + 1) }
                    components += DiceComponent(
                        count = term.count,
                        sides = term.sides,
                        sign = term.sign,
                        rolls = rolls,
                    )
                }
            }
        }

        return DiceRollResult(
            total = components.sumOf { it.subtotal } + constantTotal,
            components = components,
            constantTotal = constantTotal,
        )
    }

    companion object {
        val supportedSides = setOf(2, 3, 4, 6, 10, 12, 20, 100)
        private val tokenRegex = Regex("""([+-]?)(?:(\d*)[dD](\d+)|(\d+))""")

        fun parse(raw: String): DiceExpression {
            val normalized = raw.replace("\\s+".toRegex(), "")
            require(normalized.isNotBlank()) { "Expression cannot be empty" }

            val terms = mutableListOf<Term>()
            var cursor = 0

            tokenRegex.findAll(normalized).forEach { match ->
                require(match.range.first == cursor) { "Invalid dice expression near position $cursor" }
                cursor = match.range.last + 1

                val sign = if (match.groupValues[1] == "-") -1 else 1
                val sidesText = match.groupValues[3]

                if (sidesText.isNotEmpty()) {
                    val count = match.groupValues[2].ifEmpty { "1" }.toInt()
                    val sides = sidesText.toInt()
                    require(count in 1..100) { "Dice count must be between 1 and 100" }
                    require(sides in supportedSides) { "Unsupported die d$sides" }
                    terms += Term.Dice(count = count, sides = sides, sign = sign)
                } else {
                    terms += Term.Constant(
                        value = match.groupValues[4].toInt(),
                        sign = sign,
                    )
                }
            }

            require(cursor == normalized.length && terms.isNotEmpty()) {
                "Invalid dice expression"
            }

            return DiceExpression(source = normalized, terms = terms)
        }
    }
}
