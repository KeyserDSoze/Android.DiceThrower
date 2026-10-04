package com.keyserdsoze.dicethrower.ui.dice3d

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keyserdsoze.dicethrower.dice.DiceRollResult
import com.keyserdsoze.dicethrower.dice.DiceRollVisualBus
import kotlinx.coroutines.delay

@Composable
fun Dice3DOverlayHost(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var activeResult by remember { mutableStateOf<DiceRollResult?>(null) }
    var eventId by remember { mutableIntStateOf(0) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val callback = remember {
        { result: DiceRollResult ->
            if (result.components.any { it.rolls.isNotEmpty() }) {
                mainHandler.post {
                    activeResult = result
                    eventId += 1
                }
            }
            Unit
        }
    }

    DisposableEffect(enabled, callback) {
        if (enabled) {
            DiceRollVisualBus.subscribe(callback)
        } else {
            activeResult = null
        }
        onDispose {
            DiceRollVisualBus.unsubscribe(callback)
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

    LaunchedEffect(enabled, eventId) {
        if (enabled && eventId > 0) {
            delay(1_350)
            activeResult = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()

        AnimatedVisibility(
            visible = enabled && activeResult != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.97f),
        ) {
            activeResult?.let { result ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    shape = RoundedCornerShape(30.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    tonalElevation = 12.dp,
                    shadowElevation = 16.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Dice3DScene(
                            result = result,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                        )
                        Text(
                            text = result.total.toString(),
                            modifier = Modifier.padding(bottom = 16.dp),
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
