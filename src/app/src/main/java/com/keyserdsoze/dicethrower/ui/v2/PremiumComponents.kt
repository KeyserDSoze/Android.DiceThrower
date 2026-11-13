package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.ui.theme.Cinzel
import com.keyserdsoze.dicethrower.ui.theme.DiceTheme
import kotlin.math.abs

@Composable
fun ArcaneBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accents = DiceTheme.accents
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent,
        contentColor = scheme.onBackground,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val dark = accents.isDark
                    val base = Brush.verticalGradient(
                        listOf(scheme.surfaceContainerLow, scheme.background, scheme.surfaceContainerLowest),
                    )
                    val gilding = Brush.radialGradient(
                        colors = listOf(accents.gold.copy(alpha = if (dark) 0.15f else 0.20f), Color.Transparent),
                        center = Offset(size.width * 0.06f, size.height * 0.0f),
                        radius = size.maxDimension * 0.55f,
                    )
                    val arcane = Brush.radialGradient(
                        colors = listOf(accents.arcane.copy(alpha = if (dark) 0.13f else 0.10f), Color.Transparent),
                        center = Offset(size.width * 1.02f, size.height * 0.80f),
                        radius = size.maxDimension * 0.50f,
                    )
                    val vignette = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.38f else 0.05f)),
                        center = Offset(size.width / 2f, size.height * 0.42f),
                        radius = size.maxDimension * 0.78f,
                    )
                    // A deterministic, sparse starfield: atmosphere without visual noise.
                    val random = java.util.Random(1977L)
                    val stars = List(if (dark) 70 else 0) {
                        Triple(
                            Offset(random.nextFloat() * size.width, random.nextFloat() * size.height),
                            0.6f + random.nextFloat() * 1.6f,
                            0.08f + random.nextFloat() * 0.30f,
                        )
                    }
                    // A faint gilded d20 sigil anchors the brand in the lower corner.
                    val sigilCenter = Offset(size.width * 0.86f, size.height * 0.90f)
                    val sigilRadius = size.minDimension * 0.42f
                    fun corner(index: Int, r: Float): Offset {
                        val angle = Math.toRadians(-90.0 + index * 60.0 + 8.0)
                        return Offset(
                            sigilCenter.x + (kotlin.math.cos(angle) * r).toFloat(),
                            sigilCenter.y + (kotlin.math.sin(angle) * r).toFloat(),
                        )
                    }
                    val sigil = Path().apply {
                        val hex = (0 until 6).map { corner(it, sigilRadius) }
                        moveTo(hex[0].x, hex[0].y)
                        hex.drop(1).forEach { lineTo(it.x, it.y) }
                        close()
                        val tri = listOf(corner(0, sigilRadius * 0.52f), corner(2, sigilRadius * 0.52f), corner(4, sigilRadius * 0.52f))
                        moveTo(tri[0].x, tri[0].y)
                        tri.drop(1).forEach { lineTo(it.x, it.y) }
                        close()
                        tri.forEachIndexed { i, point ->
                            listOf(i * 2, (i * 2 + 5) % 6, (i * 2 + 1) % 6).forEach { h ->
                                moveTo(point.x, point.y)
                                corner(h, sigilRadius).let { lineTo(it.x, it.y) }
                            }
                        }
                    }
                    onDrawBehind {
                        drawRect(base)
                        drawRect(gilding)
                        drawRect(arcane)
                        stars.forEach { (center, radius, alpha) ->
                            drawCircle(accents.goldBright.copy(alpha = alpha), radius = radius, center = center)
                        }
                        drawPath(
                            sigil,
                            color = accents.gold.copy(alpha = if (dark) 0.07f else 0.10f),
                            style = Stroke(width = 1.2.dp.toPx()),
                        )
                        drawRect(vignette)
                    }
                },
        ) {
            content()
        }
    }
}

@Composable
fun BrandIcon(
    modifier: Modifier = Modifier,
    size: Int = 52,
) {
    val shape = RoundedCornerShape((size * 0.26f).dp)
    Image(
        painter = painterResource(R.drawable.ic_launcher_art),
        contentDescription = null,
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .border(
                1.dp,
                Brush.linearGradient(listOf(DiceTheme.accents.goldBright.copy(alpha = 0.8f), DiceTheme.accents.goldDeep.copy(alpha = 0.4f))),
                shape,
            ),
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun CharacterAvatar(
    character: CharacterProfile,
    modifier: Modifier = Modifier,
    size: Int = 64,
) {
    val context = LocalContext.current
    // Read, verified and decoded off the main thread, sampled to the avatar size and cached.
    val sizePx = with(LocalDensity.current) { size.dp.roundToPx() }
    val bitmap = rememberAvatarBitmap(context, character.image, character.imageUri, sizePx)
    val image = remember(bitmap) { bitmap?.asImageBitmap() }

    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = character.name,
            modifier = modifier
                .size(size.dp)
                .clip(CircleShape)
                .border(1.5.dp, Brush.linearGradient(listOf(DiceTheme.accents.goldBright, DiceTheme.accents.goldDeep)), CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        val accents = DiceTheme.accents
        Box(
            modifier = modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ),
                )
                .border(1.5.dp, Brush.linearGradient(listOf(accents.goldBright, accents.goldDeep)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = character.name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = (size * 0.42f).sp),
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accents = DiceTheme.accents
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainer.copy(alpha = if (accents.isDark) 0.90f else 0.97f),
        // Alpha-adjusted surfaces are not equal to colorScheme.surface, so
        // contentColorFor may be unspecified. Always provide readable text/icons.
        contentColor = scheme.onSurface,
        shadowElevation = if (accents.isDark) 0.dp else 1.dp,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    accents.gold.copy(alpha = if (accents.isDark) 0.38f else 0.45f),
                    scheme.outlineVariant.copy(alpha = 0.55f),
                    accents.arcane.copy(alpha = 0.20f),
                ),
            ),
        ),
    ) {
        Box(
            Modifier.drawWithCache {
                val sheen = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (accents.isDark) 0.045f else 0.35f), Color.Transparent),
                    endY = size.height * 0.55f,
                )
                onDrawBehind { drawRect(sheen) }
            },
        ) {
            content()
        }
    }
}

@Composable
fun LevelBadge(level: Int) {
    val accents = DiceTheme.accents
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.linearGradient(
                    listOf(accents.goldBright, accents.gold, accents.goldDeep),
                ),
            ),
    ) {
        Text(
            text = "Lv $level",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = Cinzel),
            fontWeight = FontWeight.Black,
            color = if (accents.isDark) Color(0xFF241800) else Color(0xFFFFFBF2),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParameterizedExpressionField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifiers: List<CharacterModifier>,
    isValid: Boolean,
    helper: String,
    errorText: String,
    modifier: Modifier = Modifier,
    fieldTestTag: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().then(
                if (fieldTestTag != null) Modifier.testTag(fieldTestTag) else Modifier
            ),
            label = { Text(label) },
            singleLine = true,
            isError = value.text.isNotBlank() && !isValid,
            supportingText = {
                Text(if (value.text.isBlank() || isValid) helper else errorText)
            },
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            VariableChip(
                name = "level",
                onClick = { onValueChange(insertToken(value, "{level}")) },
            )
            modifiers.forEach { characterModifier ->
                VariableChip(
                    name = characterModifier.name,
                    onClick = {
                        onValueChange(insertToken(value, "{${characterModifier.name}}"))
                    },
                )
            }
        }
    }
}

@Composable
private fun VariableChip(
    name: String,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text("{$name}") },
    )
}

private fun insertToken(
    value: TextFieldValue,
    token: String,
): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    val text = value.text.replaceRange(start, end, token)
    val cursor = start + token.length
    return TextFieldValue(text = text, selection = TextRange(cursor))
}

@Composable
fun DragReorderCard(
    key: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val threshold = with(density) { 48.dp.toPx() }
    var dragOffset by remember(key) { mutableFloatStateOf(0f) }
    val scale by animateFloatAsState(
        targetValue = if (abs(dragOffset) > 1f) 1.02f else 1f,
        label = "drag-scale",
    )
    // The gesture keeps running across reorders; always use the latest position and data.
    val currentCanMoveUp by rememberUpdatedState(canMoveUp)
    val currentCanMoveDown by rememberUpdatedState(canMoveDown)
    val currentOnMoveUp by rememberUpdatedState(onMoveUp)
    val currentOnMoveDown by rememberUpdatedState(onMoveDown)

    PremiumCard(
        modifier = modifier
            .graphicsLayer {
                translationY = dragOffset
                scaleX = scale
                scaleY = scale
                shadowElevation = if (abs(dragOffset) > 1f) 16f else 0f
            }
            .pointerInput(key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragOffset = 0f },
                    onDragCancel = { dragOffset = 0f },
                    onDragEnd = { dragOffset = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount.y
                        if (dragOffset <= -threshold && currentCanMoveUp) {
                            currentOnMoveUp()
                            dragOffset = 0f
                        } else if (dragOffset >= threshold && currentCanMoveDown) {
                            currentOnMoveDown()
                            dragOffset = 0f
                        }
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.DragIndicator,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Box(Modifier.weight(1f)) {
                content()
            }
        }
    }
}
