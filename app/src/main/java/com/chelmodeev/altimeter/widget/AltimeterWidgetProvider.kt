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
            views.setTextViewText(R.id.widget_vitals, content.expeditionVitals)
            views.setTextViewText(R.id.widget_track, content.expeditionTrack)
            views.applyWidgetTheme(
                context = context,
                darkTheme = data.darkTheme,
                rootId = R.id.widget_root,
                primaryTextIds = intArrayOf(R.id.widget_altitude, R.id.widget_vitals),
                secondaryTextIds = intArrayOf(R.id.widget_track),
            )

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
