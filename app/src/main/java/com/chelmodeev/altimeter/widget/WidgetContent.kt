package com.chelmodeev.altimeter.widget

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import com.chelmodeev.altimeter.R
import com.chelmodeev.altimeter.util.Fmt
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

internal data class WidgetContent(
    val altitude: CharSequence,
    val pressure: String,
    val coordinates: String,
    val distance: String,
    val track: String,
    val trackPrimary: String,
    val trackSecondary: String,
    val expeditionTrack: String,
    val heart: String,
    val oxygen: String,
    val steps: String,
    val calories: String,
    val healthTop: String,
    val healthBottom: String,
    val expeditionVitals: String,
) {
    companion object {
        fun create(context: Context, data: AltimeterWidgetSnapshot): WidgetContent {
            val altitude = data.altitudeM?.let { meters ->
                val value = Fmt.altitudeValue(meters, data.unit)
                val unit = Fmt.unitLabel(context, data.unit)
                SpannableString("$value $unit").apply {
                    setSpan(
                        RelativeSizeSpan(0.34f),
                        value.length + 1,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            } ?: "—"
            val pressure = data.pressureHpa?.let {
                String.format(Locale.getDefault(), "%.1f гПа", it)
            } ?: "— гПа"
            val coordinates = if (data.latitude != null && data.longitude != null) {
                String.format(Locale.US, "%.5f, %.5f", data.latitude, data.longitude)
            } else "—, —"
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
            val calories = estimatedCalories?.let { "≈$it" } ?: "—"
            val dailyCalories = data.activeCaloriesToday?.roundToInt()?.toString() ?: "—"
            val moving = formatDuration(data.trackMovingTimeMs)
            val ascent = data.trackAscentM.roundToInt()
            val descent = data.trackDescentM.roundToInt()
            return WidgetContent(
                altitude = altitude,
                pressure = pressure,
                coordinates = coordinates,
                distance = distance,
                track = track,
                trackPrimary = "⏱ $moving   ↔ $distance",
                trackSecondary = "↗ $ascent м   ↘ $descent м   🔥 $calories",
                expeditionTrack = "↔ $distance   ↗ $ascent м   ↘ $descent м",
                heart = heart,
                oxygen = oxygen,
                steps = steps,
                calories = calories,
                healthTop = "♥ $heart    O₂ $oxygen",
                healthBottom = "👣 $steps    🔥 $dailyCalories",
                expeditionVitals = "♥ $heart    O₂ $oxygen    👣 $steps",
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
