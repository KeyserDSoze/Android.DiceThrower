package com.keyserdsoze.dicethrower.dice

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

enum class EffectResultRounding { FLOOR, CEIL, ROUND }

/**
 * Pure arithmetic formulas for effect thresholds and numeric actions.
 *
 * Unlike DiceExpression, this interpreter never samples dice. The only inputs
 * are numeric variables and immutable Part IDs. Existing dice expressions keep
 * their own parser and RNG; rendering cannot change these calculations.
 *
 *   f(x): mathematical floor       f(-1.2) = -2
 *   c(x): mathematical ceiling     c(-1.2) = -1
 *   r(x): nearest whole number, halves *away from zero* (r(-1.5) = -2)
 */
object EffectFormulaInterpreter {
    fun evaluate(
        expression: String,
        variables: Map<String, Double> = emptyMap(),
        partTotals: Map<String, Double> = emptyMap(),
    ): Double {
        require(expression.isNotBlank() && expression.length <= 1024) {
            "Effect formula must contain 1 to 1024 characters"
        }
        return Parser(expression, variables, partTotals).parse()
    }

    fun evaluate(expression: String, snapshot: EffectRollSnapshot): Double =
        evaluate(
            expression = expression,
            variables = snapshot.variables.mapValues { it.value.toDouble() },
            partTotals = snapshot.partsById.mapValues { it.value.total.toDouble() },
        )

    fun toInt(value: Double, policy: EffectResultRounding = EffectResultRounding.FLOOR): Int {
        require(value.isFinite()) { "Effect value is not finite" }
        val rounded = when (policy) {
            EffectResultRounding.FLOOR -> floor(value)
            EffectResultRounding.CEIL -> ceil(value)
            EffectResultRounding.ROUND -> roundHalfAwayFromZero(value)
        }
        require(rounded >= Int.MIN_VALUE && rounded <= Int.MAX_VALUE) {
            "Effect result exceeds supported integer range"
        }
        return rounded.toInt()
    }

    private fun roundHalfAwayFromZero(value: Double): Double =
        if (value < 0) ceil(value - 0.5) else floor(value + 0.5)

    private class Parser(
        private val input: String,
        variables: Map<String, Double>,
        private val partTotals: Map<String, Double>,
    ) {
        private val values = variables.mapKeys { it.key.trim().lowercase(Locale.ROOT) }
        private var cursor = 0
        private var depth = 0
        private var steps = 0

        fun parse(): Double {
            val result = expression()
            skipWhitespace()
            require(cursor == input.length) { "Unexpected formula token at position $cursor" }
            return finite(result)
        }

        private fun expression(): Double = guarded {
            var result = product()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '+' -> { cursor++; result = finite(result + product()) }
                    '-' -> { cursor++; result = finite(result - product()) }
                    else -> return@guarded result
                }
            }
            @Suppress("UNREACHABLE_CODE")
            result
        }

        private fun product(): Double = guarded {
            var result = unary()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '*' -> { cursor++; result = finite(result * unary()) }
                    '/' -> {
                        cursor++
                        val divisor = unary()
                        require(divisor != 0.0) { "Division by zero" }
                        result = finite(result / divisor)
                    }
                    else -> return@guarded result
                }
            }
            @Suppress("UNREACHABLE_CODE")
            result
        }

        private fun unary(): Double = guarded {
            skipWhitespace()
            when (peek()) {
                '+' -> { cursor++; unary() }
                '-' -> { cursor++; finite(-unary()) }
                else -> primary()
            }
        }

        private fun primary(): Double {
            skipWhitespace()
            return when (peek()) {
                '(' -> {
                    cursor++
                    val nested = expression()
                    require(consume(')')) { "Missing closing parenthesis" }
                    nested
                }
                '{' -> {
                    cursor++
                    val from = cursor
                    while (peek() != null && peek() != '}') cursor++
                    require(consume('}')) { "Missing closing variable brace" }
                    val token = input.substring(from, cursor - 1).trim()
                    lookup(token)
                }
                else -> {
                    val token = peek()
                    when {
                        token == null -> throw IllegalArgumentException("Incomplete effect formula")
                        token.isDigit() || token == '.' -> number()
                        token.isLetter() || token == '_' -> {
                            val identifier = identifier()
                            skipWhitespace()
                            if (consume('(')) {
                                val argument = expression()
                                require(consume(')')) { "Missing closing function parenthesis" }
                                when (identifier.lowercase(Locale.ROOT)) {
                                    "f" -> floor(argument)
                                    "c" -> ceil(argument)
                                    "r" -> roundHalfAwayFromZero(argument)
                                    else -> throw IllegalArgumentException("Unknown effect function $identifier")
                                }
                            } else lookup(identifier)
                        }
                        else -> throw IllegalArgumentException("Unexpected effect token at position $cursor")
                    }
                }
            }
        }

        private fun number(): Double {
            val start = cursor
            var digits = 0
            while (peek()?.isDigit() == true) { cursor++; digits++ }
            if (peek() == '.') {
                cursor++
                while (peek()?.isDigit() == true) { cursor++; digits++ }
            }
            require(digits > 0) { "Invalid decimal number" }
            return finite(input.substring(start, cursor).toDouble())
        }

        private fun identifier(): String {
            val start = cursor
            while (peek()?.let { it.isLetterOrDigit() || it == '_' } == true) cursor++
            return input.substring(start, cursor)
        }

        private fun lookup(token: String): Double {
            if (token.startsWith("partId:")) {
                val id = token.removePrefix("partId:")
                return finite(requireNotNull(partTotals[id]) { "Unknown Part ID $id" })
            }
            require(!token.startsWith("parts:")) { "Readable Part aliases must be resolved to IDs" }
            return finite(requireNotNull(values[token.trim().lowercase(Locale.ROOT)]) {
                "Unknown effect variable $token"
            })
        }

        private fun skipWhitespace() {
            while (peek()?.isWhitespace() == true) cursor++
        }

        private fun peek(): Char? = input.getOrNull(cursor)

        private fun consume(ch: Char): Boolean {
            skipWhitespace()
            return if (peek() == ch) { cursor++; true } else false
        }

        private inline fun <T> guarded(body: () -> T): T {
            require(++steps <= 4096) { "Effect formula is too complex" }
            require(++depth <= 48) { "Effect expression nesting limit exceeded" }
            try {
                return body()
            } finally {
                depth--
            }
        }

        private fun finite(value: Double): Double {
            require(value.isFinite()) { "Effect arithmetic overflow" }
            return value
        }
    }
}
