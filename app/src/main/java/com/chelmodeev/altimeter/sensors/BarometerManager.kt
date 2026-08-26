package com.chelmodeev.altimeter.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.ArrayDeque

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
    private val recentPressure = ArrayDeque<Double>()
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
        recentPressure.addLast(p)
        while (recentPressure.size > MEDIAN_WINDOW_SIZE) recentPressure.removeFirst()
        val median = recentPressure.sorted()[recentPressure.size / 2]
        val f = filtered?.let { it + FILTER_ALPHA * (median - it) } ?: median
        filtered = f
        onPressure(f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val MEDIAN_WINDOW_SIZE = 9
        const val FILTER_ALPHA = 0.08
    }
}
