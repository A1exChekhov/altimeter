package com.chelmodeev.altimeter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import com.chelmodeev.altimeter.MainActivity
import com.chelmodeev.altimeter.R
import com.chelmodeev.altimeter.model.VitalsSource
import com.chelmodeev.altimeter.util.Fmt
import java.util.Date
import kotlin.math.roundToInt

class AltimeterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val snapshot = AltimeterWidgetStore.read(context)
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, views(context, snapshot))
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, AltimeterWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val snapshot = AltimeterWidgetStore.read(context)
            ids.forEach { id -> manager.updateAppWidget(id, views(context, snapshot)) }
        }

        private fun views(context: Context, data: AltimeterWidgetSnapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.altimeter_widget)
            val altitude = data.altitudeM?.let { Fmt.altitude(context, it, data.unit) } ?: "—"
            val track = if (data.trackRecording) {
                context.getString(
                    R.string.widget_track_recording,
                    Fmt.distance(context, data.trackDistanceM),
                )
            } else if (data.trackPoints > 0) {
                context.getString(
                    R.string.widget_track_saved,
                    Fmt.distance(context, data.trackDistanceM),
                )
            } else {
                context.getString(R.string.widget_track_empty)
            }
            val heart = data.heartRateBpm?.toString() ?: "—"
            val oxygen = data.spo2Percent?.roundToInt()?.let { "$it%" } ?: "—"
            val source = when {
                data.heartRateSource == VitalsSource.HUAWEI_HEALTH ||
                    data.spo2Source == VitalsSource.HUAWEI_HEALTH ->
                    context.getString(R.string.vitals_source_huawei)
                data.heartRateSource == VitalsSource.HEALTH_CONNECT ||
                    data.spo2Source == VitalsSource.HEALTH_CONNECT ->
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

            views.setTextViewText(R.id.widget_altitude, altitude)
            views.setTextViewText(R.id.widget_track, track)
            views.setTextViewText(
                R.id.widget_health,
                context.getString(R.string.widget_health_values, heart, oxygen),
            )
            views.setTextViewText(R.id.widget_health_source, source)
            views.setTextViewText(R.id.widget_updated, updated)

            val openApp = PendingIntent.getActivity(
                context,
                20,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            return views
        }
    }
}
