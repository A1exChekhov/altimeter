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
    val track: String,
    val heart: String,
    val oxygen: String,
    val steps: String,
    val heartCompact: String,
    val stepsCompact: String,
) {
    companion object {
        fun create(context: Context, data: AltimeterWidgetSnapshot): WidgetContent {
            val altitude = data.altitudeM?.let { meters ->
                val value = Fmt.altitudeValue(meters, data.unit)
                val unit = Fmt.unitLabel(context, data.unit)
                SpannableString("$value $unit").apply {
                    setSpan(
                        RelativeSizeSpan(0.46f),
                        value.length + 1,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            } ?: "—"
            val track = when {
                data.trackRecording -> context.getString(
                    R.string.widget_track_recording,
                    Fmt.distance(context, data.trackDistanceM),
                )
                data.trackPoints > 0 -> context.getString(
                    R.string.widget_track_saved,
                    Fmt.distance(context, data.trackDistanceM),
                )
                else -> context.getString(R.string.widget_track_empty)
            }
            val heart = data.heartRateBpm?.toString() ?: "—"
            val oxygen = data.spo2Percent?.roundToInt()?.let { "$it%" } ?: "—"
            val steps = data.stepsToday?.let {
                NumberFormat.getIntegerInstance(Locale.getDefault()).format(it)
            } ?: "—"
            return WidgetContent(
                altitude = altitude,
                track = track,
                heart = heart,
                oxygen = oxygen,
                steps = steps,
                heartCompact = context.getString(R.string.widget_heart_compact, heart),
                stepsCompact = context.getString(R.string.widget_steps_compact, steps),
            )
        }
    }
}
