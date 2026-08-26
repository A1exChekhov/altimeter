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
 * Нейтральная полевая информация: вода, тренд погоды и качество GPS.
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

}
