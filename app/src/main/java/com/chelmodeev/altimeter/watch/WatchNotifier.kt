package com.chelmodeev.altimeter.watch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chelmodeev.altimeter.MainActivity
import com.chelmodeev.altimeter.R
import com.chelmodeev.altimeter.model.UiState
import com.chelmodeev.altimeter.util.Fmt

/**
 * Канал «на часы без своего watch-приложения»: обычное уведомление,
 * которое Huawei Health зеркалирует на часы Huawei.
 */
class WatchNotifier(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "altitude"
        private const val NOTIFICATION_ID = 42
    }

    fun canPost(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return true
    }

    fun send(state: UiState): Boolean {
        val altitude = state.altitude ?: return false
        if (!canPost()) return false
        ensureChannel()

        val title = "⛰ ${Fmt.altitude(context, altitude, state.unit)}"
        val parts = mutableListOf<String>()
        state.placeName?.let { parts += it }
        if (state.latitude != null && state.longitude != null) {
            parts += Fmt.coords(state.latitude, state.longitude)
        }
        state.pressureHpa?.let { parts += Fmt.pressure(context, it) }
        val text = parts.joinToString(" · ")

        val bigParts = mutableListOf(text)
        bigParts += "↑ ${Fmt.altitude(context, state.totalAscent, state.unit)}" +
            "  ↓ ${Fmt.altitude(context, state.totalDescent, state.unit)}"
        state.vitals.heartRateBpm?.let {
            bigParts += "❤ $it ${context.getString(R.string.vitals_bpm)}"
        }
        state.vitals.spo2Percent?.let {
            bigParts += "SpO₂ ${it.toInt()}%"
        }
        state.vitals.stepsToday?.let {
            bigParts += "👣 $it"
        }

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mountain)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigParts.joinToString("\n")))
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(false)
            .setAutoCancel(false)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }
}
