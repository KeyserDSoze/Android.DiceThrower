package com.keyserdsoze.dicethrower.ui.dice3d

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.R
import com.keyserdsoze.dicethrower.dice.DiceRollVisualBus
import com.keyserdsoze.dicethrower.dice.DiceRollVisualEvent
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Dice3DOverlayHost(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var activeEvent by remember { mutableStateOf<DiceRollVisualEvent?>(null) }
    var showBreakdown by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val callback = remember {
        { event: DiceRollVisualEvent ->
            if (event.result.components.any { it.rolls.isNotEmpty() }) {
                mainHandler.post {
                    activeEvent = event
                    showBreakdown = false
                }
            }
            Unit
        }
    }

    DisposableEffect(enabled, callback) {
        if (enabled) {
            DiceRollVisualBus.subscribe(callback)
        } else {
            activeEvent = null
            showBreakdown = false
        }
        onDispose {
            DiceRollVisualBus.unsubscribe(callback)
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

    LaunchedEffect(enabled, activeEvent?.id) {
        if (enabled && activeEvent != null) {
            delay(650)
            showBreakdown = true
            delay(1_500)
            activeEvent = null
            showBreakdown = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()

        AnimatedVisibility(
            visible = enabled && activeEvent != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.97f),
        ) {
            activeEvent?.let { event ->
                val result = event.result
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .clickable {
                            activeEvent = null
                            showBreakdown = false
                        },
                    shape = RoundedCornerShape(30.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    tonalElevation = 12.dp,
                    shadowElevation = 16.dp,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Dice3DScene(
                            event = event,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                        )

                        AnimatedVisibility(
                            visible = showBreakdown,
                            enter = fadeIn() + scaleIn(initialScale = 0.96f),
                            exit = fadeOut(),
                        ) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                result.components
                                    .flatMap { component ->
                                        component.rolls.map { value -> component.sides to value }
                                    }
                                    .take(MAX_RESULT_CHIPS)
                                    .forEach { (sides, value) ->
                                        Surface(
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                            shape = RoundedCornerShape(999.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        ) {
                                            Text(
                                                text = "d$sides  $value",
                                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                            }
                        }

                        Text(
                            text = stringResource(R.string.total),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = result.total.toString(),
                            modifier = Modifier.padding(bottom = 18.dp),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_RESULT_CHIPS = 12
