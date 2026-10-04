package com.keyserdsoze.dicethrower.dice

import java.util.concurrent.atomic.AtomicReference

/**
 * Optional visual event bridge.
 *
 * The dice engine owns the numerical result. A UI renderer may subscribe to observe
 * completed results, but it can never influence the generated values.
 */
object DiceRollVisualBus {
    private val listener = AtomicReference<((DiceRollResult) -> Unit)?>(null)

    fun publish(result: DiceRollResult) {
        listener.get()?.invoke(result)
    }

    fun subscribe(callback: (DiceRollResult) -> Unit) {
        listener.set(callback)
    }

    fun unsubscribe(callback: (DiceRollResult) -> Unit) {
        listener.compareAndSet(callback, null)
    }
}
