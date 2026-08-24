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

class TrackWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val snapshot = AltimeterWidgetStore.read(context)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views(context, snapshot)) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TrackWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val snapshot = AltimeterWidgetStore.read(context)
            ids.forEach { id -> manager.updateAppWidget(id, views(context, snapshot)) }
        }

        private fun views(context: Context, data: AltimeterWidgetSnapshot): RemoteViews =
            RemoteViews(context.packageName, R.layout.track_widget).apply {
                val content = WidgetContent.create(context, data)
                setTextViewText(R.id.track_widget_primary, content.trackPrimary)
                setTextViewText(R.id.track_widget_secondary, content.trackSecondary)
                setOnClickPendingIntent(
                    R.id.track_widget_root,
                    PendingIntent.getActivity(
                        context,
                        23,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
    }
}
