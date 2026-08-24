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
                setTextViewText(R.id.track_widget_distance, content.trackPrimary)
                setTextViewText(R.id.track_widget_moving, content.moving)
                setTextViewText(R.id.track_widget_ascent, content.ascent)
                setTextViewText(R.id.track_widget_descent, content.descent)
                setTextViewText(R.id.track_widget_calories, "🔥 ${content.calories}")
                applyWidgetTheme(
                    context = context,
                    darkTheme = data.darkTheme,
                    rootId = R.id.track_widget_root,
                    primaryTextIds = intArrayOf(R.id.track_widget_distance),
                    secondaryTextIds = intArrayOf(
                        R.id.track_widget_moving,
                        R.id.track_widget_ascent,
                        R.id.track_widget_descent,
                        R.id.track_widget_calories,
                    ),
                )
                setWidgetColor(context, R.id.track_widget_ascent, R.color.widget_ascent)
                setWidgetColor(context, R.id.track_widget_descent, R.color.widget_descent)
                setWidgetColor(context, R.id.track_widget_calories, R.color.widget_calories)
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
