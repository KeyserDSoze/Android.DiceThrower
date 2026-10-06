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
    /** Mathematical mean of this roll shape, independent from the sampled face values. */
    fun expectedTotal(): Double = constantTotal + components.sumOf { component ->
        component.sign * component.count * (component.sides + 1) / 2.0
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

        fun parse(raw: String): DiceExpression {
            val normalized = raw.replace("\\s+".toRegex(), "")
            require(normalized.isNotBlank()) { "Expression cannot be empty" }
            val root = Parser(normalized).parse()
            require(isLinear(root)) { "Multiplication between two dice expressions is not supported" }
            return DiceExpression(source = normalized, root = root)
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

        private class Parser(private val source: String) {
            private var cursor = 0

            fun parse(): Node {
                val result = parseAddSubtract()
                require(cursor == source.length) { "Invalid dice expression near position $cursor" }
                return result
            }

            private fun parseAddSubtract(): Node {
                var result = parseMultiply()
                while (cursor < source.length && (source[cursor] == '+' || source[cursor] == '-')) {
                    val operator = source[cursor++]
                    val right = parseMultiply()
                    result = if (operator == '+') Node.Add(result, right) else Node.Subtract(result, right)
                }
                return result
            }

            private fun parseMultiply(): Node {
                var result = parseUnary()
                while (cursor < source.length && source[cursor] == '*') {
                    cursor++
                    result = Node.Multiply(result, parseUnary())
                }
                return result
            }

            private fun parseUnary(): Node {
                if (cursor < source.length && source[cursor] == '+') {
                    cursor++
                    return parseUnary()
                }
                if (cursor < source.length && source[cursor] == '-') {
                    cursor++
                    return Node.Negate(parseUnary())
                }
                return parsePrimary()
            }

            private fun parsePrimary(): Node {
                require(cursor < source.length) { "Unexpected end of dice expression" }
                if (source[cursor] == '(') {
                    cursor++
                    val nested = parseAddSubtract()
                    require(cursor < source.length && source[cursor] == ')') {
                        "Missing closing parenthesis near position $cursor"
                    }
                    cursor++
                    return nested
                }

                val countOrValue = readDigits()
                if (cursor < source.length && (source[cursor] == 'd' || source[cursor] == 'D')) {
                    cursor++
                    val sidesText = readDigits()
                    require(sidesText.isNotEmpty()) { "Missing die sides near position $cursor" }
                    val count = countOrValue.ifEmpty { "1" }.toInt()
                    val sides = sidesText.toInt()
                    require(count in 1..100) { "Dice count must be between 1 and 100" }
                    require(sides in supportedSides) { "Unsupported die d$sides" }
                    return Node.Dice(count, sides)
                }

                require(countOrValue.isNotEmpty()) { "Expected a number or die near position $cursor" }
                return Node.Constant(countOrValue.toInt())
            }

            private fun readDigits(): String {
                val start = cursor
                while (cursor < source.length && source[cursor].isDigit()) cursor++
                return source.substring(start, cursor)
            }
        }
    }
}
