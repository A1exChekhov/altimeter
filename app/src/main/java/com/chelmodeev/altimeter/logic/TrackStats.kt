package com.chelmodeev.altimeter.logic

import com.chelmodeev.altimeter.model.ChartPoint
import kotlin.math.abs

/**
 * История высоты, мин/макс, суммарный подъём/спуск и вертикальная скорость.
 */
class TrackStats {

    private companion object {
        const val HISTORY_STEP_MS = 5_000L
        const val HISTORY_MAX_POINTS = 4_320 // до 6 часов
        const val SPEED_WINDOW_MS = 20_000L
        const val ASCENT_THRESHOLD_M = 2.0 // гистерезис против шума
    }

    private val history = ArrayDeque<ChartPoint>()
    private val speedWindow = ArrayDeque<ChartPoint>()

    var minAlt: Double? = null; private set
    var maxAlt: Double? = null; private set
    var ascent = 0.0; private set
    var descent = 0.0; private set

    private var lastAccepted: Double? = null
    private var lastHistoryAt = 0L

    @Synchronized
    fun onAltitude(timeMs: Long, alt: Double) {
        if (minAlt == null || alt < minAlt!!) minAlt = alt
        if (maxAlt == null || alt > maxAlt!!) maxAlt = alt

        val last = lastAccepted
        if (last == null) {
            lastAccepted = alt
        } else {
            val d = alt - last
            if (abs(d) >= ASCENT_THRESHOLD_M) {
                if (d > 0) ascent += d else descent += -d
                lastAccepted = alt
            }
        }

        speedWindow.addLast(ChartPoint(timeMs, alt))
        while (speedWindow.isNotEmpty() && speedWindow.first().timeMs < timeMs - SPEED_WINDOW_MS) {
            speedWindow.removeFirst()
        }

        if (timeMs - lastHistoryAt >= HISTORY_STEP_MS) {
            lastHistoryAt = timeMs
            history.addLast(ChartPoint(timeMs, alt))
            while (history.size > HISTORY_MAX_POINTS) history.removeFirst()
        }
    }

    @Synchronized
    fun historySnapshot(): List<ChartPoint> = history.toList()

    /** Вертикальная скорость, м/мин (линейная регрессия по 20-секундному окну). */
    @Synchronized
    fun verticalSpeedMpm(): Double? {
        if (speedWindow.size < 6) return null
        val t0 = speedWindow.first().timeMs
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        val n = speedWindow.size.toDouble()
        for (p in speedWindow) {
            val x = (p.timeMs - t0) / 1000.0
            val y = p.altitude
            sx += x; sy += y; sxx += x * x; sxy += x * y
        }
        val denom = n * sxx - sx * sx
        if (denom < 1e-6) return null
        val slopePerSec = (n * sxy - sx * sy) / denom
        return slopePerSec * 60.0
    }

    @Synchronized
    fun reset() {
        history.clear()
        speedWindow.clear()
        minAlt = null
        maxAlt = null
        ascent = 0.0
        descent = 0.0
        lastAccepted = null
        lastHistoryAt = 0L
    }
}
