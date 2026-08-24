package com.chelmodeev.altimeter.track

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.chelmodeev.altimeter.MainActivity
import com.chelmodeev.altimeter.R
import com.chelmodeev.altimeter.core.AltimeterCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Низкоуровневый монитор авторека без зависимости от Google Play Services.
 * Датчик шага отсекает автомобиль, GPS подтверждает 120 м реального движения.
 * Сервис запускается пользователем из настроек и остаётся видимым уведомлением.
 */
class AutoTrackService : Service(), SensorEventListener {

    companion object {
        private const val ACTION_ENABLE = "com.chelmodeev.altimeter.track.AUTO_ENABLE"
        private const val ACTION_DISABLE = "com.chelmodeev.altimeter.track.AUTO_DISABLE"
        private const val CHANNEL_ID = "auto_tracking"
        private const val NOTIFICATION_ID = 8

        private const val START_CONFIRM_MS = 90_000L
        private const val START_DISTANCE_M = 120.0
        private const val START_STEPS = 60
        private const val CANDIDATE_GAP_MS = 45_000L

        fun setEnabled(context: Context, enabled: Boolean) {
            val intent = Intent(context, AutoTrackService::class.java)
                .setAction(if (enabled) ACTION_ENABLE else ACTION_DISABLE)
            if (enabled) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sensors: SensorManager
    private var stepDetector: Sensor? = null
    private var collectJob: Job? = null
    private var core: AltimeterCore? = null
    private var monitoring = false

    private var latestLat: Double? = null
    private var latestLon: Double? = null
    private var candidateStartedAt = 0L
    private var candidateSteps = 0
    private var candidateDistanceM = 0.0
    private var candidateLastLat: Double? = null
    private var candidateLastLon: Double? = null
    private var lastStepAt = 0L
    private var autoStartRequestedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISABLE) {
            stopMonitoring()
            stopSelf()
            return START_NOT_STICKY
        }
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        if (monitoring) return
        monitoring = true
        ensureChannel()
        startVisible(buildNotification(R.string.auto_track_ready))

        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepDetector = sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        stepDetector?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        } ?: notifyStatus(R.string.auto_track_no_step_sensor)

        val c = AltimeterCore.get(this)
        core = c
        c.onLocationPermission(hasLocationPermission())
        c.acquire()
        collectJob = scope.launch {
            c.state.collectLatest { state ->
                latestLat = state.latitude
                latestLon = state.longitude
                onLocation(state.latitude, state.longitude, state.hasFix)
            }
        }
    }

    private fun onLocation(lat: Double?, lon: Double?, hasFix: Boolean) {
        val now = System.currentTimeMillis()
        val track = TrackingService.state.value

        if (!hasFix || lat == null || lon == null || candidateStartedAt == 0L) return
        val prevLat = candidateLastLat
        val prevLon = candidateLastLon
        if (prevLat != null && prevLon != null) {
            val segment = distance(prevLat, prevLon, lat, lon)
            if (segment in 1.0..80.0) candidateDistanceM += segment
        }
        candidateLastLat = lat
        candidateLastLon = lon

        if (now - lastStepAt > CANDIDATE_GAP_MS) {
            resetCandidate()
            return
        }

        if (track.recording || now - autoStartRequestedAt < 30_000L) return
        val elapsed = now - candidateStartedAt
        if (elapsed < START_CONFIRM_MS || candidateSteps < START_STEPS ||
            candidateDistanceM < START_DISTANCE_M
        ) return

        val speedMps = candidateDistanceM / (elapsed / 1_000.0)
        if (speedMps !in 0.45..4.2) {
            resetCandidate()
            return
        }
        autoStartRequestedAt = now
        TrackingService.start(this, automatic = true)
        resetCandidate()
        notifyStatus(R.string.auto_track_started)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_DETECTOR) return
        val now = System.currentTimeMillis()
        lastStepAt = now
        if (candidateStartedAt == 0L) {
            candidateStartedAt = now
            candidateSteps = 0
            candidateDistanceM = 0.0
            candidateLastLat = latestLat
            candidateLastLon = latestLon
        }
        candidateSteps++
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        stopMonitoring()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopMonitoring() {
        if (!monitoring) return
        monitoring = false
        collectJob?.cancel()
        collectJob = null
        if (::sensors.isInitialized) sensors.unregisterListener(this)
        core?.release()
        core = null
        resetCandidate()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun resetCandidate() {
        candidateStartedAt = 0L
        candidateSteps = 0
        candidateDistanceM = 0.0
        candidateLastLat = null
        candidateLastLon = null
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, result)
        return result[0].toDouble()
    }

    private fun startVisible(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyStatus(message: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun buildNotification(message: Int): Notification {
        val open = PendingIntent.getActivity(
            this,
            8,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mountain)
            .setContentTitle(getString(R.string.auto_track_title))
            .setContentText(getString(message))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.auto_track_title),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }
}
