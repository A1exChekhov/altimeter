package com.chelmodeev.altimeter.widget

import android.content.Context
import android.text.format.DateFormat
import com.chelmodeev.altimeter.R
import com.chelmodeev.altimeter.model.VitalsSource
import com.chelmodeev.altimeter.util.Fmt
import java.util.Date
import kotlin.math.roundToInt

internal data class WidgetContent(
    val altitude: String,
    val track: String,
    val health: String,
    val source: String,
    val updated: String,
) {
    companion object {
        fun create(context: Context, data: AltimeterWidgetSnapshot): WidgetContent {
            val altitude = data.altitudeM?.let { Fmt.altitude(context, it, data.unit) } ?: "—"
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
            val steps = data.stepsToday?.toString() ?: "—"
            val health = context.getString(R.string.widget_health_values, heart, oxygen, steps)
            val source = when {
                data.heartRateSource == VitalsSource.BLUETOOTH ->
                    context.getString(R.string.vitals_source_bluetooth)
                data.heartRateSource == VitalsSource.HUAWEI_HEALTH ||
                    data.spo2Source == VitalsSource.HUAWEI_HEALTH ||
                    data.stepsSource == VitalsSource.HUAWEI_HEALTH ->
                    context.getString(R.string.vitals_source_huawei)
                data.heartRateSource == VitalsSource.HEALTH_CONNECT ||
                    data.spo2Source == VitalsSource.HEALTH_CONNECT ||
                    data.stepsSource == VitalsSource.HEALTH_CONNECT ->
                    context.getString(R.string.vitals_source_health_connect)
                else -> context.getString(R.string.widget_health_waiting)
            }
            val updated = if (data.updatedAtMs > 0L) {
                context.getString(
                    R.string.widget_updated,
                    DateFormat.getTimeFormat(context).format(Date(data.updatedAtMs)),
                )
            } else {
                context.getString(R.string.widget_open_to_update)
            }
            return WidgetContent(altitude, track, health, source, updated)
        }
    }
}
