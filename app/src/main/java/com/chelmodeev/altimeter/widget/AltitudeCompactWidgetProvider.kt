package com.chelmodeev.altimeter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.chelmodeev.altimeter.MainActivity
import com.chelmodeev.altimeter.R

class AltitudeCompactWidgetProvider : AppWidgetProvider() {

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
            val ids = manager.getAppWidgetIds(
                ComponentName(context, AltitudeCompactWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val snapshot = AltimeterWidgetStore.read(context)
            ids.forEach { id -> manager.updateAppWidget(id, views(context, snapshot)) }
        }

        private fun views(context: Context, data: AltimeterWidgetSnapshot): RemoteViews {
            val content = WidgetContent.create(context, data)
            return RemoteViews(context.packageName, R.layout.altitude_compact_widget).apply {
                setTextViewText(R.id.altitude_compact_widget_altitude, content.altitude)
                setTextViewText(R.id.altitude_compact_widget_unit, content.altitudeUnit)
                applyWidgetTheme(
                    context = context,
                    darkTheme = data.darkTheme,
                    rootId = R.id.altitude_compact_widget_root,
                    primaryTextIds = intArrayOf(R.id.altitude_compact_widget_altitude),
                    secondaryTextIds = intArrayOf(R.id.altitude_compact_widget_unit),
                )
                setOnClickPendingIntent(
                    R.id.altitude_compact_widget_root,
                    PendingIntent.getActivity(
                        context,
                        22,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
        }
    }
}
