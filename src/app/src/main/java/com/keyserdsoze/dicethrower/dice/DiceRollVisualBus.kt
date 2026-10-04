package com.keyserdsoze.dicethrower.dice

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

data class DiceRollVisualEvent(
    val id: Long,
    val result: DiceRollResult,
    val appearances: List<ResolvedDiceAppearance>,
)

/**
 * Optional visual event bridge.
 *
 * The dice engine owns the numerical result. A UI renderer may subscribe to observe
 * completed results, but it can never influence the generated values.
 */
object DiceRollVisualBus {
    private val nextEventId = AtomicLong(0L)
    private val listener = AtomicReference<((DiceRollVisualEvent) -> Unit)?>(null)

    fun publish(result: DiceRollResult, appearances: List<ResolvedDiceAppearance>) {
        listener.get()?.invoke(
            DiceRollVisualEvent(
                id = nextEventId.incrementAndGet(),
                result = result,
                appearances = appearances,
            ),
        )
    }

    fun subscribe(callback: (DiceRollVisualEvent) -> Unit) {
        listener.set(callback)
    }

    fun unsubscribe(callback: (DiceRollVisualEvent) -> Unit) {
        listener.compareAndSet(callback, null)
    }
}
