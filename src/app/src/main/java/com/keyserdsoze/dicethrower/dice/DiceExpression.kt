package com.keyserdsoze.dicethrower.dice

import kotlin.math.abs
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
    /** Mathematical mean of this roll shape, independent from the sampled face values. */
    fun expectedTotal(): Double = constantTotal.toDouble() + components.sumOf { component ->
        component.sign.toDouble() * component.count * (component.sides + 1) / 2.0
    }

    fun detail(): String {
        val dice = components.joinToString(" | ") { component ->
            val prefix = when (component.sign) {
                1 -> "+"
                -1 -> "-"
                else -> "${if (component.sign > 0) "+" else ""}${component.sign}x"
            }
            "$prefix${component.count}d${component.sides}[${component.rolls.joinToString(",")}]"
        }
        val constant = if (constantTotal == 0) "" else {
            val prefix = if (constantTotal > 0) "+" else ""
            " $prefix$constantTotal"
        }
        return (dice + constant).trim().removePrefix("+")
    }
}

data class DiceTermShape(
    val count: Int,
    val sides: Int,
    val sign: Int,
)

class DiceExpression private constructor(
    val source: String,
    private val root: Node,
    /** Physical dice sampled by one evaluation, summed over every dice term. */
    val diceCount: Int,
    /** Number of integer and dice terms. */
    val termCount: Int,
) {
    private sealed interface Node {
        data class Dice(val count: Int, val sides: Int) : Node
        data class Constant(val value: Int) : Node
        data class Add(val left: Node, val right: Node) : Node
        data class Subtract(val left: Node, val right: Node) : Node
        data class Multiply(val left: Node, val right: Node) : Node
        data class Negate(val value: Node) : Node
    }

    fun evaluate(random: Random = Random.Default): DiceRollResult {
        val evaluated = evaluateNode(root, random)
        return DiceRollResult(
            total = evaluated.total,
            components = evaluated.components,
            constantTotal = evaluated.constantTotal,
        )
    }

    fun diceShape(): List<DiceTermShape> = buildList {
        collectDice(root, 1, this)
    }

    /** Constant contribution after all linear operators/multipliers have been applied. */
    fun constantTotal(): Int = constantContribution(root)

    private data class EvaluatedNode(
        val total: Int,
        val constantTotal: Int,
        val components: List<DiceComponent>,
    )

    private fun evaluateNode(node: Node, random: Random): EvaluatedNode = when (node) {
        is Node.Constant -> EvaluatedNode(node.value, node.value, emptyList())
        is Node.Dice -> {
            val rolls = List(node.count) { random.nextInt(1, node.sides + 1) }
            val component = DiceComponent(node.count, node.sides, 1, rolls)
            EvaluatedNode(component.subtotal, 0, listOf(component))
        }
        is Node.Add -> combine(evaluateNode(node.left, random), evaluateNode(node.right, random), 1)
        is Node.Subtract -> combine(evaluateNode(node.left, random), evaluateNode(node.right, random), -1)
        is Node.Negate -> evaluateNode(node.value, random).scale(-1)
        is Node.Multiply -> {
            val leftConstant = constantValue(node.left)
            val rightConstant = constantValue(node.right)
            when {
                leftConstant != null -> evaluateNode(node.right, random).scale(leftConstant)
                rightConstant != null -> evaluateNode(node.left, random).scale(rightConstant)
                else -> error("Dice-to-dice multiplication is not supported")
            }
        }
    }

    private fun combine(left: EvaluatedNode, right: EvaluatedNode, rightScale: Int): EvaluatedNode {
        val scaledRight = right.scale(rightScale)
        return EvaluatedNode(
            total = left.total + scaledRight.total,
            constantTotal = left.constantTotal + scaledRight.constantTotal,
            components = left.components + scaledRight.components,
        )
    }

    private fun EvaluatedNode.scale(factor: Int): EvaluatedNode = EvaluatedNode(
        total = total * factor,
        constantTotal = constantTotal * factor,
        components = components.map { it.copy(sign = it.sign * factor) },
    )

    private fun collectDice(node: Node, multiplier: Int, output: MutableList<DiceTermShape>) {
        when (node) {
            is Node.Constant -> Unit
            is Node.Dice -> output += DiceTermShape(node.count, node.sides, multiplier)
            is Node.Add -> {
                collectDice(node.left, multiplier, output)
                collectDice(node.right, multiplier, output)
            }
            is Node.Subtract -> {
                collectDice(node.left, multiplier, output)
                collectDice(node.right, -multiplier, output)
            }
            is Node.Negate -> collectDice(node.value, -multiplier, output)
            is Node.Multiply -> {
                val leftConstant = constantValue(node.left)
                val rightConstant = constantValue(node.right)
                when {
                    leftConstant != null -> collectDice(node.right, multiplier * leftConstant, output)
                    rightConstant != null -> collectDice(node.left, multiplier * rightConstant, output)
                    else -> error("Dice-to-dice multiplication is not supported")
                }
            }
        }
    }

    private fun constantValue(node: Node): Int? = when (node) {
        is Node.Constant -> node.value
        is Node.Dice -> null
        is Node.Add -> combineConstants(node.left, node.right, Int::plus)
        is Node.Subtract -> combineConstants(node.left, node.right, Int::minus)
        is Node.Multiply -> combineConstants(node.left, node.right, Int::times)
        is Node.Negate -> constantValue(node.value)?.let { -it }
    }

    private fun constantContribution(node: Node): Int = when (node) {
        is Node.Constant -> node.value
        is Node.Dice -> 0
        is Node.Add -> constantContribution(node.left) + constantContribution(node.right)
        is Node.Subtract -> constantContribution(node.left) - constantContribution(node.right)
        is Node.Negate -> -constantContribution(node.value)
        is Node.Multiply -> {
            val leftConstant = constantValue(node.left)
            val rightConstant = constantValue(node.right)
            when {
                leftConstant != null -> leftConstant * constantContribution(node.right)
                rightConstant != null -> rightConstant * constantContribution(node.left)
                else -> error("Dice-to-dice multiplication is not supported")
            }
        }
    }

    private fun combineConstants(left: Node, right: Node, operation: (Int, Int) -> Int): Int? {
        val leftValue = constantValue(left) ?: return null
        val rightValue = constantValue(right) ?: return null
        return operation(leftValue, rightValue)
    }

    companion object {
        val supportedSides = setOf(2, 3, 4, 6, 8, 10, 12, 20, 100)

        const val MAX_DICE_PER_TERM = 100
        const val MAX_EXPRESSION_LENGTH = 4096
        const val MAX_NESTING_DEPTH = 32
        const val MAX_TOTAL_DICE = 500
        const val MAX_TERMS = 1000

        fun parse(raw: String): DiceExpression {
            require(raw.length <= MAX_EXPRESSION_LENGTH) {
                "Expression exceeds $MAX_EXPRESSION_LENGTH characters"
            }
            // Whitespace only separates tokens: "3 1d6" or "2 4" are errors, never 31d6 or 24.
            val tokens = tokenize(raw)
            require(tokens.isNotEmpty()) { "Expression cannot be empty" }
            val parser = Parser(tokens)
            val root = parser.parse()
            require(isLinear(root)) { "Multiplication between two dice expressions is not supported" }
            bound(root)
            return DiceExpression(
                source = tokens.joinToString("") { it.text },
                root = root,
                diceCount = parser.diceCount,
                termCount = parser.termCount,
            )
        }

        private fun isLinear(node: Node): Boolean = when (node) {
            is Node.Constant, is Node.Dice -> true
            is Node.Add -> isLinear(node.left) && isLinear(node.right)
            is Node.Subtract -> isLinear(node.left) && isLinear(node.right)
            is Node.Negate -> isLinear(node.value)
            is Node.Multiply -> isLinear(node.left) && isLinear(node.right) &&
                !(containsDice(node.left) && containsDice(node.right))
        }

        private fun containsDice(node: Node): Boolean = when (node) {
            is Node.Constant -> false
            is Node.Dice -> true
            is Node.Add -> containsDice(node.left) || containsDice(node.right)
            is Node.Subtract -> containsDice(node.left) || containsDice(node.right)
            is Node.Multiply -> containsDice(node.left) || containsDice(node.right)
            is Node.Negate -> containsDice(node.value)
        }

        /**
         * [magnitude] bounds every total, constant, die sign and die subtotal that evaluation can
         * compute beneath a node (dice-free nodes are folded exactly into [exact]). Requiring it to
         * fit an Int at every node means Int arithmetic during evaluation can never wrap around.
         */
        private class Bound(val magnitude: Long, val exact: Long?)

        private fun bound(node: Node): Bound {
            val result = when (node) {
                is Node.Constant -> Bound(node.value.toLong(), node.value.toLong())
                is Node.Dice -> Bound(node.count.toLong() * node.sides, null)
                is Node.Negate -> bound(node.value).let { Bound(it.magnitude, it.exact?.unaryMinus()) }
                is Node.Add -> sumBound(bound(node.left), bound(node.right)) { a, b -> a + b }
                is Node.Subtract -> sumBound(bound(node.left), bound(node.right)) { a, b -> a - b }
                is Node.Multiply -> {
                    val left = bound(node.left)
                    val right = bound(node.right)
                    if (left.exact != null && right.exact != null) exactBound(left.exact * right.exact)
                    else Bound(left.magnitude * right.magnitude, null)
                }
            }
            require(result.magnitude <= Int.MAX_VALUE) {
                "Dice expression exceeds the supported integer range"
            }
            return result
        }

        private inline fun sumBound(left: Bound, right: Bound, operation: (Long, Long) -> Long): Bound =
            if (left.exact != null && right.exact != null) exactBound(operation(left.exact, right.exact))
            else Bound(left.magnitude + right.magnitude, null)

        private fun exactBound(value: Long) = Bound(abs(value), value)

        private enum class TokenType { NUMBER, DIE, PLUS, MINUS, TIMES, OPEN, CLOSE }

        private class Token(val type: TokenType, val text: String, val position: Int)

        /** Only ASCII digits form numbers, consistently with RollFormulaResolver and Effect formulas. */
        private fun tokenize(raw: String): List<Token> {
            val tokens = ArrayList<Token>()
            var index = 0
            while (index < raw.length) {
                val char = raw[index]
                when {
                    char.isWhitespace() -> index++
                    char in '0'..'9' -> {
                        val start = index
                        while (index < raw.length && raw[index] in '0'..'9') index++
                        tokens += Token(TokenType.NUMBER, raw.substring(start, index), start)
                    }
                    else -> {
                        val type = when (char) {
                            'd', 'D' -> TokenType.DIE
                            '+' -> TokenType.PLUS
                            '-' -> TokenType.MINUS
                            '*', 'x', 'X', '×' -> TokenType.TIMES
                            '(' -> TokenType.OPEN
                            ')' -> TokenType.CLOSE
                            else -> throw IllegalArgumentException("Unexpected character near position $index")
                        }
                        tokens += Token(type, char.toString(), index)
                        index++
                    }
                }
            }
            return tokens
        }

        private class Parser(private val tokens: List<Token>) {
            private var cursor = 0
            private var depth = 0
            var diceCount = 0
                private set
            var termCount = 0
                private set

            fun parse(): Node {
                val result = parseAddSubtract()
                require(cursor == tokens.size) {
                    "Invalid dice expression near position ${tokens[cursor].position}"
                }
                return result
            }

            private fun peek(): TokenType? = tokens.getOrNull(cursor)?.type

            private fun positionText(): String =
                tokens.getOrNull(cursor)?.position?.toString() ?: "end"

            private fun parseAddSubtract(): Node {
                var result = parseMultiply()
                while (true) {
                    result = when (peek()) {
                        TokenType.PLUS -> {
                            cursor++
                            Node.Add(result, parseMultiply())
                        }
                        TokenType.MINUS -> {
                            cursor++
                            Node.Subtract(result, parseMultiply())
                        }
                        else -> return result
                    }
                }
            }

            private fun parseMultiply(): Node {
                var result = parseUnary()
                while (peek() == TokenType.TIMES) {
                    cursor++
                    result = Node.Multiply(result, parseUnary())
                }
                return result
            }

            private fun parseUnary(): Node = when (peek()) {
                TokenType.PLUS -> nested {
                    cursor++
                    parseUnary()
                }
                TokenType.MINUS -> nested {
                    cursor++
                    Node.Negate(parseUnary())
                }
                else -> parsePrimary()
            }

            private fun parsePrimary(): Node = when (peek()) {
                null -> throw IllegalArgumentException("Unexpected end of dice expression")
                TokenType.OPEN -> nested {
                    cursor++
                    val inner = parseAddSubtract()
                    require(peek() == TokenType.CLOSE) {
                        "Missing closing parenthesis near position ${positionText()}"
                    }
                    cursor++
                    inner
                }
                TokenType.NUMBER, TokenType.DIE -> parseTerm()
                else -> throw IllegalArgumentException(
                    "Expected a number or die near position ${positionText()}",
                )
            }

            private fun parseTerm(): Node {
                require(++termCount <= MAX_TERMS) { "Expression exceeds $MAX_TERMS terms" }
                val countText = if (peek() == TokenType.NUMBER) tokens[cursor++].text else null
                if (peek() != TokenType.DIE) {
                    return Node.Constant(integer(requireNotNull(countText)))
                }
                cursor++
                require(peek() == TokenType.NUMBER) { "Missing die sides near position ${positionText()}" }
                val sides = integer(tokens[cursor++].text)
                val count = countText?.let(::integer) ?: 1
                require(count in 1..MAX_DICE_PER_TERM) {
                    "Dice count must be between 1 and $MAX_DICE_PER_TERM"
                }
                require(sides in supportedSides) { "Unsupported die d$sides" }
                diceCount += count
                require(diceCount <= MAX_TOTAL_DICE) { "Expression exceeds $MAX_TOTAL_DICE dice" }
                return Node.Dice(count, sides)
            }

            private fun integer(digits: String): Int = digits.toIntOrNull()
                ?: throw IllegalArgumentException("Number exceeds the supported integer range")

            private inline fun <T> nested(block: () -> T): T {
                require(++depth <= MAX_NESTING_DEPTH) {
                    "Expression nesting exceeds $MAX_NESTING_DEPTH levels"
                }
                try {
                    return block()
                } finally {
                    depth--
                }
            }
        }
    }
}
