package com.chelmodeev.altimeter.logic

import com.chelmodeev.altimeter.model.Advice
import com.chelmodeev.altimeter.model.AdviceKind
import com.chelmodeev.altimeter.model.AdviceSeverity
import java.util.Locale
import kotlin.math.abs

data class AdvisorInput(
    val nowMs: Long,
    val altitude: Double?,
    val verticalSpeedMpm: Double?,
    val hasFix: Boolean,
    val locationGranted: Boolean,
    val spo2: Double?,
    val spo2AtMs: Long?,
    val heartRate: Long?,
    val heartRateAtMs: Long?,
    /** Тренд приведённого к уровню моря давления, гПа/ч; null — мало данных. */
    val pressureTrendHpaPerHour: Double?,
)

/**
 * Контекстные советы: горная болезнь, темп набора, SpO₂/пульс с часов,
 * прогноз погоды по тренду барометра, качество GPS.
 */
class Advisor {

    fun evaluate(inp: AdvisorInput): List<Advice> {
        val out = mutableListOf<Advice>()

        inp.pressureTrendHpaPerHour?.let { t ->
            val v = String.format(Locale.getDefault(), "%.1f", abs(t))
            when {
                t <= -1.6 -> out += Advice(AdviceKind.PRESSURE_FALLING_FAST, AdviceSeverity.WARNING, v)
                t <= -0.8 -> out += Advice(AdviceKind.PRESSURE_FALLING, AdviceSeverity.CAUTION, v)
                t >= 1.2 -> out += Advice(AdviceKind.PRESSURE_RISING, AdviceSeverity.INFO)
                else -> Unit
            }
        }

        val alt = inp.altitude
        if (alt != null) {
            when {
                alt >= 4000 -> out += Advice(AdviceKind.ALTITUDE_VERY_HIGH, AdviceSeverity.WARNING)
                alt >= 3000 -> out += Advice(AdviceKind.ALTITUDE_HIGH, AdviceSeverity.CAUTION)
                alt >= 2500 -> out += Advice(AdviceKind.ALTITUDE_ACCLIMATIZE, AdviceSeverity.INFO)
            }
            val vs = inp.verticalSpeedMpm
            if (alt > 2000 && vs != null && vs >= 12.0) {
                out += Advice(AdviceKind.FAST_ASCENT, AdviceSeverity.CAUTION)
            }
        }

        val spo2 = inp.spo2
        if (spo2 != null && isFresh(inp.nowMs, inp.spo2AtMs, 45 * 60_000L)) {
            val v = spo2.toInt().toString()
            when {
                spo2 < 88 -> out += Advice(AdviceKind.SPO2_VERY_LOW, AdviceSeverity.WARNING, v)
                spo2 <= 92 -> out += Advice(AdviceKind.SPO2_LOW, AdviceSeverity.CAUTION, v)
            }
        }

        val hr = inp.heartRate
        if (hr != null && alt != null && alt > 2500 && hr >= 120 &&
            isFresh(inp.nowMs, inp.heartRateAtMs, 15 * 60_000L)
        ) {
            out += Advice(AdviceKind.HR_HIGH, AdviceSeverity.INFO, hr.toString())
        }

        if (alt != null && alt >= 1500) {
            out += Advice(AdviceKind.HYDRATION, AdviceSeverity.INFO)
        }

        if (inp.locationGranted && !inp.hasFix) {
            out += Advice(AdviceKind.GPS_WEAK, AdviceSeverity.INFO)
        }

        return out
            .sortedByDescending { it.severity.ordinal }
            .take(4)
    }

    private fun isFresh(nowMs: Long, atMs: Long?, maxAgeMs: Long): Boolean =
        atMs != null && nowMs - atMs in 0..maxAgeMs
}
