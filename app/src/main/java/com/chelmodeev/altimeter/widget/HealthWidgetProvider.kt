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

class HealthWidgetProvider : AppWidgetProvider() {

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
                ComponentName(context, HealthWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val snapshot = AltimeterWidgetStore.read(context)
            ids.forEach { id -> manager.updateAppWidget(id, views(context, snapshot)) }
        }

        private fun views(context: Context, data: AltimeterWidgetSnapshot): RemoteViews {
            val content = WidgetContent.create(context, data)
            return RemoteViews(context.packageName, R.layout.health_widget).apply {
                setTextViewText(R.id.health_widget_heart, "♥ ${content.heart}")
                setTextViewText(R.id.health_widget_oxygen, "O₂ ${content.oxygen}")
                setTextViewText(R.id.health_widget_steps, "👣 ${content.steps}")
                setTextViewText(R.id.health_widget_calories, "🔥 ${content.calories}")
                applyWidgetTheme(
                    context = context,
                    darkTheme = data.darkTheme,
                    rootId = R.id.health_widget_root,
                    primaryTextIds = intArrayOf(
                        R.id.health_widget_heart,
                        R.id.health_widget_oxygen,
                        R.id.health_widget_steps,
                        R.id.health_widget_calories,
                    ),
                )
                setWidgetColor(context, R.id.health_widget_heart, R.color.widget_heart)
                setWidgetColor(context, R.id.health_widget_oxygen, R.color.widget_oxygen)
                setWidgetColor(context, R.id.health_widget_steps, R.color.widget_steps)
                setWidgetColor(context, R.id.health_widget_calories, R.color.widget_calories)
                setOnClickPendingIntent(
                    R.id.health_widget_root,
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
