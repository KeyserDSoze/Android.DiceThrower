package com.keyserdsoze.dicethrower.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastShakeAt = 0L
    private var listening = false

    fun start() {
        if (listening) return
        val sensor = accelerometer ?: return
        listening = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!listening) return
        listening = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Ignore samples already queued when the owner stopped listening.
        if (!listening || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0] / SensorManager.GRAVITY_EARTH
        val y = event.values[1] / SensorManager.GRAVITY_EARTH
        val z = event.values[2] / SensorManager.GRAVITY_EARTH
        val force = sqrt(x * x + y * y + z * z)
        val now = SystemClock.elapsedRealtime()

        if (force >= SHAKE_THRESHOLD_G && now - lastShakeAt >= DEBOUNCE_MS) {
            lastShakeAt = now
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val SHAKE_THRESHOLD_G = 2.35f
        private const val DEBOUNCE_MS = 700L
    }
}
