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
            val content = WidgetContent.create(context, data)

            views.setTextViewText(R.id.widget_altitude, content.altitude)
            views.setTextViewText(R.id.widget_altitude_unit, content.altitudeUnit)
            views.setTextViewText(R.id.widget_heart, "♥ ${content.heart}")
            views.setTextViewText(R.id.widget_oxygen, "O₂ ${content.oxygen}")
            views.setTextViewText(R.id.widget_steps, "👣 ${content.steps}")
            views.setTextViewText(R.id.widget_distance, content.trackPrimary)
            views.setTextViewText(R.id.widget_moving, content.moving)
            views.setTextViewText(R.id.widget_ascent, content.ascent)
            views.setTextViewText(R.id.widget_descent, content.descent)
            views.applyWidgetTheme(
                context = context,
                darkTheme = data.darkTheme,
                rootId = R.id.widget_root,
                primaryTextIds = intArrayOf(R.id.widget_altitude),
                secondaryTextIds = intArrayOf(
                    R.id.widget_altitude_unit,
                    R.id.widget_distance,
                    R.id.widget_moving,
                    R.id.widget_ascent,
                    R.id.widget_descent,
                ),
            )
            views.setWidgetColor(context, R.id.widget_heart, R.color.widget_heart)
            views.setWidgetColor(context, R.id.widget_oxygen, R.color.widget_oxygen)
            views.setWidgetColor(context, R.id.widget_steps, R.color.widget_steps)
            views.setWidgetColor(context, R.id.widget_ascent, R.color.widget_ascent)
            views.setWidgetColor(context, R.id.widget_descent, R.color.widget_descent)

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
