package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.ui.theme.DiceTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val supportedGlyphSides = setOf(2, 3, 4, 6, 8, 10, 12, 20, 100)
private val variableToken = Regex("\\{[^}]*\\}")
private val dieToken = Regex("[dD](\\d+)")

/** The most characteristic die of an expression (the one with most sides), for its icon. */
internal fun dominantDieSides(expression: String): Int? =
    dieToken.findAll(variableToken.replace(expression, " "))
        .mapNotNull { it.groupValues[1].toIntOrNull() }
        .filter { it in supportedGlyphSides }
        .maxOrNull()

/** Line-art silhouette of a real die seen from above (d4 tetrahedron, d20 icosahedron …). */
@Composable
internal fun DieGlyph(
    sides: Int?,
    modifier: Modifier = Modifier,
    tint: Color = DiceTheme.accents.gold,
    glow: Color = DiceTheme.accents.arcane,
) {
    Canvas(modifier) { drawDie(sides ?: 20, tint, glow) }
}

/** A die glyph in a softly gilded tile, used as the leading visual of rolls and pickers. */
@Composable
internal fun DieTile(
    sides: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
) {
    val accents = DiceTheme.accents
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(accents.gold.copy(alpha = 0.55f), accents.arcane.copy(alpha = 0.25f))),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            DieGlyph(sides, Modifier.padding(size * 0.17f).size(size))
        }
    }
}

/** Best/worst throw control: die silhouette plus a direction arrow (never colour alone). */
@Composable
internal fun CandidateThrowIcon(
    sides: Int?,
    tint: Color,
    arrow: ImageVector,
    contentDescription: String,
) {
    Box(Modifier.size(36.dp).semantics { this.contentDescription = contentDescription }) {
        DieGlyph(sides, Modifier.size(30.dp).align(Alignment.Center), tint = tint, glow = tint)
        Icon(
            arrow,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(16.dp)
                .background(Color(0xF20B0C16), CircleShape)
                .padding(1.dp),
        )
    }
}

/** Two stacked dice: a group of rolls. */
@Composable
internal fun DieGroupTile(
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
) {
    val accents = DiceTheme.accents
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(accents.arcane.copy(alpha = 0.55f), accents.gold.copy(alpha = 0.30f))),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            DieGlyph(6, Modifier.offset(x = -size * 0.13f, y = size * 0.10f).size(size * 0.46f), tint = accents.arcane, glow = accents.gold)
            DieGlyph(20, Modifier.offset(x = size * 0.12f, y = -size * 0.09f).size(size * 0.52f))
        }
    }
}

private fun DrawScope.drawDie(sides: Int, tint: Color, glow: Color) {
    val radius = size.minDimension * 0.46f
    val center = Offset(size.width / 2f, size.height / 2f)
    fun p(x: Float, y: Float) = Offset(center.x + x * radius, center.y + y * radius)
    fun polar(angleDegrees: Float, r: Float = 1f): Offset {
        val a = angleDegrees * PI.toFloat() / 180f
        return p(cos(a) * r, sin(a) * r)
    }
    val strokeWidth = size.minDimension * 0.06f
    val fill = Brush.linearGradient(
        colors = listOf(tint.copy(alpha = 0.32f), glow.copy(alpha = 0.16f)),
        start = Offset(center.x - radius, center.y - radius),
        end = Offset(center.x + radius, center.y + radius),
    )
    val lines = mutableListOf<Pair<Offset, Offset>>()
    val dots = mutableListOf<Offset>()
    val outline = Path()
    fun polygon(points: List<Offset>) {
        outline.moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { outline.lineTo(it.x, it.y) }
        outline.close()
    }
    when (sides) {
        2 -> {
            outline.addOval(androidx.compose.ui.geometry.Rect(center, radius * 0.92f))
            val inner = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, radius * 0.58f)) }
            drawPath(outline, fill)
            drawPath(outline, tint, style = Stroke(strokeWidth))
            drawPath(inner, tint.copy(alpha = 0.7f), style = Stroke(strokeWidth * 0.7f))
            return
        }
        3, 6 -> {
            val half = radius * 0.80f
            outline.addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = center.x - half, top = center.y - half, right = center.x + half, bottom = center.y + half,
                    radiusX = half * 0.34f, radiusY = half * 0.34f,
                ),
            )
            dots += if (sides == 6) listOf(p(-0.42f, -0.42f), p(0.42f, -0.42f), p(0f, 0f), p(-0.42f, 0.42f), p(0.42f, 0.42f))
                else listOf(p(-0.42f, -0.42f), p(0f, 0f), p(0.42f, 0.42f))
        }
        4 -> {
            val top = p(0f, -1f)
            val left = p(-0.94f, 0.64f)
            val right = p(0.94f, 0.64f)
            val middle = p(0f, 0.18f)
            polygon(listOf(top, right, left))
            lines += listOf(middle to top, middle to left, middle to right)
        }
        8 -> {
            val top = p(0f, -1f)
            val right = p(0.78f, 0f)
            val bottom = p(0f, 1f)
            val left = p(-0.78f, 0f)
            polygon(listOf(top, right, bottom, left))
            lines += listOf(left to right, top to center)
        }
        10, 100 -> {
            val top = p(0f, -1f)
            val right = p(0.92f, -0.06f)
            val bottom = p(0f, 0.92f)
            val left = p(-0.92f, -0.06f)
            val innerLeft = p(-0.44f, 0.12f)
            val innerRight = p(0.44f, 0.12f)
            val innerBottom = p(0f, 0.34f)
            polygon(listOf(top, right, bottom, left))
            lines += listOf(
                top to innerLeft, top to innerRight, innerLeft to innerBottom, innerRight to innerBottom,
                left to innerLeft, right to innerRight, innerBottom to bottom,
            )
            if (sides == 100) dots += listOf(p(-0.18f, -0.30f), p(0.18f, -0.08f))
        }
        12 -> {
            val outer = (0 until 10).map { polar(-90f + it * 36f) }
            val inner = (0 until 5).map { polar(-90f + it * 72f, 0.50f) }
            polygon(outer)
            inner.indices.forEach { i ->
                lines += inner[i] to inner[(i + 1) % 5]
                lines += inner[i] to outer[i * 2]
            }
        }
        else -> {
            val hex = (0 until 6).map { polar(-90f + it * 60f) }
            val tri = listOf(polar(-90f, 0.52f), polar(30f, 0.52f), polar(150f, 0.52f))
            polygon(hex)
            lines += listOf(tri[0] to tri[1], tri[1] to tri[2], tri[2] to tri[0])
            lines += listOf(tri[0] to hex[0], tri[0] to hex[5], tri[0] to hex[1])
            lines += listOf(tri[1] to hex[1], tri[1] to hex[2], tri[1] to hex[3])
            lines += listOf(tri[2] to hex[3], tri[2] to hex[4], tri[2] to hex[5])
        }
    }
    drawPath(outline, fill)
    lines.forEach { (a, b) ->
        drawLine(tint.copy(alpha = 0.62f), a, b, strokeWidth = strokeWidth * 0.62f, cap = StrokeCap.Round)
    }
    dots.forEach { drawCircle(tint, radius = radius * 0.13f, center = it) }
    drawPath(outline, tint, style = Stroke(strokeWidth, join = StrokeJoin.Round))
}
