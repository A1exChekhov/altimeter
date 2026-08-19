package com.chelmodeev.altimeter.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Барометр: отдаёт сглаженное давление в гПа.
 */
class BarometerManager(
    context: Context,
    private val onPressure: (Double) -> Unit,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val available: Boolean get() = sensor != null

    private var filtered: Double? = null
    private var running = false

    fun start() {
        val s = sensor ?: return
        if (running) return
        running = true
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val p = event.values.firstOrNull()?.toDouble() ?: return
        if (p < 300.0 || p > 1200.0) return // мусорные значения
        val f = filtered?.let { it + 0.25 * (p - it) } ?: p
        filtered = f
        onPressure(f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
