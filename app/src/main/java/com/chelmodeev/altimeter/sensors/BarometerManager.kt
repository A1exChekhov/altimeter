package com.chelmodeev.altimeter.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.ArrayDeque
import kotlin.math.exp

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
    private var lastTimestampNs: Long? = null

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
        val previousTimestamp = lastTimestampNs
        lastTimestampNs = event.timestamp
        val dtSeconds = if (previousTimestamp == null) {
            DEFAULT_SAMPLE_SECONDS
        } else {
            ((event.timestamp - previousTimestamp) / 1_000_000_000.0)
                .coerceIn(MIN_SAMPLE_SECONDS, MAX_SAMPLE_SECONDS)
        }
        val alpha = 1.0 - exp(-dtSeconds / FILTER_TIME_CONSTANT_SECONDS)
        val f = filtered?.let { it + alpha * (median - it) } ?: median
        filtered = f
        onPressure(f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val MEDIAN_WINDOW_SIZE = 9
        const val FILTER_TIME_CONSTANT_SECONDS = 2.2
        const val DEFAULT_SAMPLE_SECONDS = 0.2
        const val MIN_SAMPLE_SECONDS = 0.02
        const val MAX_SAMPLE_SECONDS = 1.5
    }
}
