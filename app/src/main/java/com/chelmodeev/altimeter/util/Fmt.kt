package com.chelmodeev.altimeter.util

import android.content.Context
import com.chelmodeev.altimeter.R
import com.chelmodeev.altimeter.model.AltUnit
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object Fmt {

    private const val FT_PER_M = 3.2808399

    fun toUnit(meters: Double, unit: AltUnit): Double =
        if (unit == AltUnit.FEET) meters * FT_PER_M else meters

    fun altitudeValue(meters: Double, unit: AltUnit): String {
        val v = toUnit(meters, unit).roundToLong()
        return NumberFormat.getIntegerInstance(Locale.getDefault()).format(v)
    }

    fun unitLabel(context: Context, unit: AltUnit): String =
        context.getString(if (unit == AltUnit.FEET) R.string.unit_ft else R.string.unit_m)

    fun altitude(context: Context, meters: Double, unit: AltUnit): String =
        "${altitudeValue(meters, unit)} ${unitLabel(context, unit)}"

    fun altitudeSigned(context: Context, meters: Double, unit: AltUnit): String {
        val sign = if (meters >= 0) "+" else "−"
        return "$sign${altitude(context, abs(meters), unit)}"
    }

    fun accuracy(context: Context, meters: Double, unit: AltUnit): String {
        val v = toUnit(meters, unit).roundToInt().coerceAtLeast(1)
        return context.getString(
            R.string.accuracy_fmt,
            NumberFormat.getIntegerInstance(Locale.getDefault()).format(v),
            unitLabel(context, unit),
        )
    }

    fun pressure(context: Context, hpa: Double): String {
        val nf = NumberFormat.getNumberInstance(Locale.getDefault())
        nf.minimumFractionDigits = 1
        nf.maximumFractionDigits = 1
        return "${nf.format(hpa)} ${context.getString(R.string.unit_hpa)}"
    }

    fun vspeed(context: Context, metersPerMin: Double, unit: AltUnit): String {
        val v = toUnit(metersPerMin, unit)
        val nf = NumberFormat.getNumberInstance(Locale.getDefault())
        nf.minimumFractionDigits = 1
        nf.maximumFractionDigits = 1
        val sign = if (v >= 0) "+" else "−"
        val label = context.getString(if (unit == AltUnit.FEET) R.string.vspeed_ft else R.string.vspeed_m)
        return "$sign${nf.format(abs(v))} $label"
    }

    fun coords(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lon)

    fun distance(context: Context, meters: Double): String {
        if (meters < 1000.0) {
            return "${meters.roundToInt()} ${context.getString(R.string.unit_m)}"
        }
        val nf = NumberFormat.getNumberInstance(Locale.getDefault())
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        return "${nf.format(meters / 1000.0)} ${context.getString(R.string.unit_km)}"
    }

    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    fun timeShort(context: Context, epochMs: Long): String =
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(epochMs))
}
