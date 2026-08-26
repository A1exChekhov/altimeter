package com.chelmodeev.altimeter.logic

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.exp

/**
 * Converts the noisy fused sensor altitude into a stable value for the UI,
 * statistics and GPX recording.
 *
 * At rest the displayed altitude is held. A change is accepted only after a
 * directionally consistent trend; real rapid vertical movement switches to a
 * shorter time constant so elevators and aircraft are not hidden by filtering.
 */
class AltitudeStabilizer {

    private data class Sample(val timeMs: Long, val altitude: Double)

    private enum class MotionMode { LEVEL, VERTICAL, FAST }

    private val samples = ArrayDeque<Sample>()
    private var visibleAltitude: Double? = null
    private var mode = MotionMode.LEVEL
    private var quietSinceMs: Long? = null
    private var lastUpdateMs: Long? = null

    @Synchronized
    fun update(rawAltitude: Double, timeMs: Long): Double? {
        if (!rawAltitude.isFinite()) return visibleAltitude

        val lastTime = lastUpdateMs
        val effectiveTime = if (lastTime == null) timeMs else maxOf(timeMs, lastTime + 1L)
        lastUpdateMs = effectiveTime

        if (visibleAltitude == null) {
            visibleAltitude = rawAltitude
            samples.addLast(Sample(effectiveTime, rawAltitude))
            return rawAltitude
        }

        val lastSample = samples.lastOrNull()
        if (lastSample == null || effectiveTime - lastSample.timeMs >= MIN_SAMPLE_INTERVAL_MS) {
            samples.addLast(Sample(effectiveTime, rawAltitude))
        } else {
            samples.removeLast()
            samples.addLast(Sample(effectiveTime, rawAltitude))
        }
        while (samples.isNotEmpty() && samples.first().timeMs < effectiveTime - TREND_WINDOW_MS) {
            samples.removeFirst()
        }

        val trend = analyzeTrend()
        val requestedMode = when {
            trend.fast -> MotionMode.FAST
            trend.vertical -> MotionMode.VERTICAL
            else -> MotionMode.LEVEL
        }

        if (requestedMode != MotionMode.LEVEL) {
            mode = requestedMode
            quietSinceMs = null
        } else if (mode != MotionMode.LEVEL) {
            val quietSince = quietSinceMs ?: effectiveTime.also { quietSinceMs = it }
            if (effectiveTime - quietSince >= QUIET_CONFIRMATION_MS) {
                mode = MotionMode.LEVEL
                quietSinceMs = null
            }
        }

        val current = visibleAltitude ?: rawAltitude
        if (mode == MotionMode.LEVEL) return current

        val target = median(samples.toList().takeLast(ROBUST_TARGET_SIZE).map { it.altitude })
        val dtSeconds = ((effectiveTime - (lastTime ?: effectiveTime)).coerceAtLeast(1L) / 1_000.0)
        val timeConstant = if (mode == MotionMode.FAST) FAST_TIME_CONSTANT_SECONDS else VERTICAL_TIME_CONSTANT_SECONDS
        val alpha = (1.0 - exp(-dtSeconds / timeConstant)).coerceIn(0.0, 1.0)
        val next = current + alpha * (target - current)
        if (abs(next - current) >= ACTIVE_DEADBAND_M) visibleAltitude = next
        return visibleAltitude
    }

    @Synchronized
    fun reset() {
        samples.clear()
        visibleAltitude = null
        mode = MotionMode.LEVEL
        quietSinceMs = null
        lastUpdateMs = null
    }

    private data class Trend(val vertical: Boolean, val fast: Boolean)

    private fun analyzeTrend(): Trend {
        if (samples.size < 3) return Trend(vertical = false, fast = false)
        val list = samples.toList()
        val edgeSize = minOf(3, list.size / 2)
        val first = list.take(edgeSize)
        val last = list.takeLast(edgeSize)
        val firstTime = first.map { it.timeMs }.average()
        val lastTime = last.map { it.timeMs }.average()
        val spanSeconds = (lastTime - firstTime) / 1_000.0
        if (spanSeconds <= 0) return Trend(vertical = false, fast = false)

        val netChange = median(last.map { it.altitude }) - median(first.map { it.altitude })
        val slope = netChange / spanSeconds
        var positive = 0
        var negative = 0
        list.zipWithNext().forEach { (a, b) ->
            val delta = b.altitude - a.altitude
            if (delta >= DIRECTION_DELTA_M) positive++
            if (delta <= -DIRECTION_DELTA_M) negative++
        }
        val directional = positive + negative
        val consistency = if (directional == 0) 0.0 else maxOf(positive, negative).toDouble() / directional

        val fast = spanSeconds >= FAST_MIN_SPAN_SECONDS &&
            abs(netChange) >= FAST_MIN_CHANGE_M &&
            abs(slope) >= FAST_MIN_SPEED_MPS &&
            directional >= FAST_MIN_DIRECTIONAL_SAMPLES &&
            consistency >= MIN_DIRECTION_CONSISTENCY
        val vertical = spanSeconds >= VERTICAL_MIN_SPAN_SECONDS &&
            abs(netChange) >= VERTICAL_MIN_CHANGE_M &&
            abs(slope) >= VERTICAL_MIN_SPEED_MPS &&
            directional >= VERTICAL_MIN_DIRECTIONAL_SAMPLES &&
            consistency >= MIN_DIRECTION_CONSISTENCY
        return Trend(vertical = vertical, fast = fast)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    private companion object {
        const val MIN_SAMPLE_INTERVAL_MS = 750L
        const val TREND_WINDOW_MS = 15_000L
        const val ROBUST_TARGET_SIZE = 3
        const val QUIET_CONFIRMATION_MS = 5_000L
        const val DIRECTION_DELTA_M = 0.08
        const val MIN_DIRECTION_CONSISTENCY = 0.65
        const val VERTICAL_MIN_SPAN_SECONDS = 7.0
        const val VERTICAL_MIN_CHANGE_M = 0.8
        const val VERTICAL_MIN_SPEED_MPS = 0.055
        const val VERTICAL_MIN_DIRECTIONAL_SAMPLES = 4
        const val FAST_MIN_SPAN_SECONDS = 2.0
        const val FAST_MIN_CHANGE_M = 1.5
        const val FAST_MIN_SPEED_MPS = 0.6
        const val FAST_MIN_DIRECTIONAL_SAMPLES = 2
        const val VERTICAL_TIME_CONSTANT_SECONDS = 2.5
        const val FAST_TIME_CONSTANT_SECONDS = 0.7
        const val ACTIVE_DEADBAND_M = 0.03
    }
}
