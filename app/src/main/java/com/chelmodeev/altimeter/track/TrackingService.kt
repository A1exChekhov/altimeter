package com.chelmodeev.altimeter.track

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.chelmodeev.altimeter.MainActivity
import com.chelmodeev.altimeter.R
import com.chelmodeev.altimeter.core.AltimeterCore
import com.chelmodeev.altimeter.model.AltUnit
import com.chelmodeev.altimeter.model.TrackRecState
import com.chelmodeev.altimeter.util.Fmt
import com.chelmodeev.altimeter.widget.AltimeterWidgetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground-сервис записи GPX-трека: держит датчики (через AltimeterCore)
 * живыми при выключенном экране и свёрнутом приложении.
 */
class TrackingService : Service() {

    companion object {
        private const val ACTION_START = "com.chelmodeev.altimeter.track.START"
        private const val ACTION_STOP = "com.chelmodeev.altimeter.track.STOP"
        private const val ACTION_PAUSE = "com.chelmodeev.altimeter.track.PAUSE"
        private const val ACTION_RESUME = "com.chelmodeev.altimeter.track.RESUME"
        private const val EXTRA_AUTOMATIC = "automatic"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 7

        private val _state = MutableStateFlow(TrackRecState())
        val state: StateFlow<TrackRecState> = _state.asStateFlow()

        fun start(context: Context, automatic: Boolean = false) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_AUTOMATIC, automatic)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TrackingService::class.java).setAction(ACTION_STOP)
            )
        }

        fun pause(context: Context) {
            context.startService(
                Intent(context, TrackingService::class.java).setAction(ACTION_PAUSE)
            )
        }

        fun resume(context: Context) {
            context.startService(
                Intent(context, TrackingService::class.java).setAction(ACTION_RESUME)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var core: AltimeterCore? = null
    private val recorder = GpxRecorder()
    private var currentFile: File? = null
    private var lastNotifyAt = 0L
    private var lastAutosaveAt = 0L
    private var lastAltitude: Double? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking(intent.getBooleanExtra(EXTRA_AUTOMATIC, false))
            ACTION_STOP -> stopTracking()
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            else -> if (!_state.value.recording) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startTracking(automatic: Boolean) {
        if (_state.value.recording) return
        ensureChannel()
        val c = AltimeterCore.get(this)
        core = c
        c.acquire()
        recorder.begin()
        currentFile = null
        _state.update {
            TrackRecState(
                recording = true,
                paused = false,
                automatic = automatic,
                startedAtMs = System.currentTimeMillis(),
                lastSavedName = it.lastSavedName,
                lastSavedPath = it.lastSavedPath,
            )
        }
        AltimeterWidgetStore.updateAltitudeAndTrack(
            this,
            lastAltitude,
            AltimeterWidgetStore.read(this).unit,
            _state.value,
        )
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        collectJob = scope.launch {
            c.state.collect { s -> onCoreState(s) }
        }
    }

    private fun onCoreState(s: AltimeterCore.CoreState) {
        if (!_state.value.recording) return
        lastAltitude = s.altitude ?: lastAltitude
        if (!_state.value.paused && recorder.offer(s)) {
            _state.update {
                it.copy(
                    points = recorder.pointCount,
                    distanceM = recorder.distanceM,
                    ascentM = recorder.ascentM,
                    route = recorder.mapPoints(),
                )
            }
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotifyAt > 15_000) {
            lastNotifyAt = now
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification())
            AltimeterWidgetStore.updateAltitudeAndTrack(
                this,
                lastAltitude,
                AltimeterWidgetStore.read(this).unit,
                _state.value,
            )
        }
        if (now - lastAutosaveAt > 60_000 && recorder.pointCount > 0) {
            lastAutosaveAt = now
            val file = trackFile()
            scope.launch(Dispatchers.IO) { runCatching { recorder.saveTo(file) } }
        }
    }

    private fun stopTracking() {
        if (_state.value.recording) {
            collectJob?.cancel()
            collectJob = null
            val file = trackFile()
            val saved = recorder.pointCount > 0 &&
                runCatching { recorder.saveTo(file) }.isSuccess
            _state.update {
                it.copy(
                    recording = false,
                    paused = false,
                    lastSavedName = if (saved) file.name else it.lastSavedName,
                    lastSavedPath = if (saved) file.absolutePath else it.lastSavedPath,
                )
            }
            AltimeterWidgetStore.updateAltitudeAndTrack(
                this,
                lastAltitude,
                AltimeterWidgetStore.read(this).unit,
                _state.value,
            )
            core?.release()
            core = null
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setPaused(paused: Boolean) {
        if (!_state.value.recording || _state.value.paused == paused) return
        _state.update { it.copy(paused = paused) }
        AltimeterWidgetStore.updateAltitudeAndTrack(
            this,
            lastAltitude,
            AltimeterWidgetStore.read(this).unit,
            _state.value,
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        // страховка: если систему «убили» — дописываем файл и отпускаем датчики
        if (_state.value.recording) {
            collectJob?.cancel()
            if (recorder.pointCount > 0) {
                runCatching { recorder.saveTo(trackFile()) }
            }
            _state.update { it.copy(recording = false) }
            AltimeterWidgetStore.updateAltitudeAndTrack(
                this,
                lastAltitude,
                AltimeterWidgetStore.read(this).unit,
                _state.value,
            )
            core?.release()
            core = null
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun trackFile(): File {
        currentFile?.let { return it }
        val dir = File(getExternalFilesDir(null), "tracks").apply { mkdirs() }
        val name = "Altimeter_" +
            SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date()) + ".gpx"
        return File(dir, name).also { currentFile = it }
    }

    private fun buildNotification(): Notification {
        val s = _state.value
        val altText = lastAltitude?.let { Fmt.altitude(this, it, AltUnit.METERS) } ?: "—"
        val prefix = if (s.paused) getString(R.string.auto_track_paused) else altText
        val text = "$prefix · ${Fmt.distance(this, s.distanceM)} · ${s.points}"

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mountain)
            .setContentTitle(getString(R.string.track_notif_title))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.track_stop), stopIntent)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_tracking),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
}
