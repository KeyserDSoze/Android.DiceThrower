package com.keyserdsoze.dicethrower.ui.v2

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.data.CharacterImageAssetStore
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import kotlin.math.abs

@Composable
fun ArcaneBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        scheme.background,
                        scheme.surfaceVariant.copy(alpha = 0.72f),
                        scheme.background,
                    ),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val topGlow = Offset(size.width * 0.12f, size.height * 0.06f)
            val bottomGlow = Offset(size.width * 0.92f, size.height * 0.82f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        scheme.primary.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                    center = topGlow,
                    radius = size.minDimension * 0.58f,
                ),
                radius = size.minDimension * 0.58f,
                center = topGlow,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        scheme.tertiary.copy(alpha = 0.13f),
                        Color.Transparent,
                    ),
                    center = bottomGlow,
                    radius = size.minDimension * 0.48f,
                ),
                radius = size.minDimension * 0.48f,
                center = bottomGlow,
            )
        }
        content()
    }
}

@Composable
fun BrandIcon(
    modifier: Modifier = Modifier,
    size: Int = 52,
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_art),
        contentDescription = null,
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.24f).dp)),
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
    val bitmap = remember(character.image, character.imageUri) {
        val portable = character.image?.let { ref ->
            CharacterImageAssetStore(context).loadVerified(ref)?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
        portable ?: character.imageUri?.let { raw ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(raw)).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = character.name,
            modifier = modifier.size(size.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Surface(
            modifier = modifier.size(size.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = character.name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        content()
    }
}

@Composable
fun LevelBadge(level: Int) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = "Lv $level",
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
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
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
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

    PremiumCard(
        modifier = modifier
            .graphicsLayer {
                translationY = dragOffset
                scaleX = scale
                scaleY = scale
                shadowElevation = if (abs(dragOffset) > 1f) 16f else 0f
            }
            .pointerInput(key, canMoveUp, canMoveDown) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragOffset = 0f },
                    onDragCancel = { dragOffset = 0f },
                    onDragEnd = { dragOffset = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount.y
                        if (dragOffset <= -threshold && canMoveUp) {
                            onMoveUp()
                            dragOffset = 0f
                        } else if (dragOffset >= threshold && canMoveDown) {
                            onMoveDown()
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
