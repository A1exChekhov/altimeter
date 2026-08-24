package com.chelmodeev.altimeter.widget

import android.content.Context
import com.chelmodeev.altimeter.R
import com.chelmodeev.altimeter.util.Fmt
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

internal data class WidgetContent(
    val altitude: String,
    val altitudeUnit: String,
    val pressure: String,
    val coordinates: String,
    val distance: String,
    val track: String,
    val trackPrimary: String,
    val moving: String,
    val ascent: String,
    val descent: String,
    val heart: String,
    val oxygen: String,
    val steps: String,
    val calories: String,
) {
    companion object {
        fun create(context: Context, data: AltimeterWidgetSnapshot): WidgetContent {
            val altitude = data.altitudeM?.let { Fmt.altitudeValue(it, data.unit) } ?: "—"
            val altitudeUnit = data.altitudeM?.let { Fmt.unitLabel(context, data.unit) } ?: ""
            val pressure = data.pressureHpa?.let {
                String.format(Locale.getDefault(), "%.1f гПа", it)
            } ?: "давление · нет"
            val coordinates = if (data.latitude != null && data.longitude != null) {
                String.format(Locale.US, "%.5f, %.5f", data.latitude, data.longitude)
            } else "координаты · нет"
            val distance = Fmt.distance(context, data.trackDistanceM)
            val track = when {
                data.trackRecording -> context.getString(R.string.widget_track_recording, distance)
                data.trackPoints > 0 -> context.getString(R.string.widget_track_saved, distance)
                else -> context.getString(R.string.widget_track_empty)
            }
            val heart = data.heartRateBpm?.toString() ?: "—"
            val oxygen = data.spo2Percent?.roundToInt()?.let { "$it%" } ?: "—"
            val steps = data.stepsToday?.let {
                NumberFormat.getIntegerInstance(Locale.getDefault()).format(it)
            } ?: "—"
            val estimatedCalories = if (data.trackPoints > 1) {
                (data.trackDistanceM / 1_000.0 * 50.0 + data.trackAscentM * 0.1).roundToInt()
            } else null
            val calories = data.activeCaloriesToday?.roundToInt()?.toString()
                ?: estimatedCalories?.let { "≈$it" }
                ?: "—"
            val moving = formatDuration(data.trackMovingTimeMs)
            val ascent = data.trackAscentM.roundToInt()
            val descent = data.trackDescentM.roundToInt()
            return WidgetContent(
                altitude = altitude,
                altitudeUnit = altitudeUnit,
                pressure = pressure,
                coordinates = coordinates,
                distance = distance,
                track = track,
                trackPrimary = "↔ $distance",
                moving = "◷ $moving",
                ascent = "↑ $ascent м",
                descent = "↓ $descent м",
                heart = heart,
                oxygen = oxygen,
                steps = steps,
                calories = calories,
            )
        }

        private fun formatDuration(ms: Long): String {
            val totalMinutes = (ms / 60_000L).coerceAtLeast(0L)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (hours > 0) String.format(Locale.getDefault(), "%d:%02d", hours, minutes)
            else "$minutes мин"
        }
    }
}
