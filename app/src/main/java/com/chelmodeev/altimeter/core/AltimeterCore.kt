package com.chelmodeev.altimeter.core

import android.content.Context
import android.os.SystemClock
import com.chelmodeev.altimeter.data.SettingsRepository
import com.chelmodeev.altimeter.logic.FusionEngine
import com.chelmodeev.altimeter.logic.AltitudeStabilizer
import com.chelmodeev.altimeter.logic.TrackStats
import com.chelmodeev.altimeter.model.ChartPoint
import com.chelmodeev.altimeter.model.MslSource
import com.chelmodeev.altimeter.sensors.BarometerManager
import com.chelmodeev.altimeter.sensors.LocationEngine
import com.chelmodeev.altimeter.sensors.LocationSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Единый «двигатель» высотомера: барометр + GPS + фьюжн + статистика.
 * Общий для экрана (MainViewModel) и фонового сервиса записи трека, чтобы
 * они видели одинаковые цифры и не дублировали GPS-подписки.
 * Датчики работают, пока держится хотя бы одна ссылка (acquire/release).
 */
class AltimeterCore private constructor(private val appContext: Context) {

    companion object {
        @Volatile
        private var instance: AltimeterCore? = null

        fun get(context: Context): AltimeterCore =
            instance ?: synchronized(AltimeterCore::class.java) {
                instance ?: AltimeterCore(context.applicationContext).also { instance = it }
            }
    }

    data class CoreState(
        val hasBarometer: Boolean = false,
        val altitude: Double? = null,
        val accuracy: Double? = null,
        val pressureHpa: Double? = null,
        val isCalibrating: Boolean = false,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val gpsAccuracy: Float? = null,
        val gpsVertAccuracy: Float? = null,
        /** Время последнего нового GPS-фикса. Не меняется на тиках UI. */
        val gpsFixTimeMs: Long = 0L,
        val satellitesUsed: Int = 0,
        val satellitesTotal: Int = 0,
        val hasFix: Boolean = false,
        val mslSource: MslSource = MslSource.NONE,
        val verticalSpeedMpm: Double? = null,
        val minAltitude: Double? = null,
        val maxAltitude: Double? = null,
        val totalAscent: Double = 0.0,
        val totalDescent: Double = 0.0,
        val history: List<ChartPoint> = emptyList(),
        val pressureTrendHpaPerHour: Double? = null,
        val timestampMs: Long = 0L,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settingsRepo = SettingsRepository(appContext)
    private val fusion = FusionEngine()
    private val altitudeStabilizer = AltitudeStabilizer()
    private val stats = TrackStats()

    private val _state = MutableStateFlow(CoreState())
    val state: StateFlow<CoreState> = _state.asStateFlow()

    private val barometer = BarometerManager(appContext) { fusion.onPressure(it) }

    private var lastPreciseFixAt = 0L

    /** (elapsedMs, давление на уровне моря) для тренда погоды. */
    private val pressureHistory = ArrayDeque<Pair<Long, Double>>()

    private val locationEngine = LocationEngine(appContext, object : LocationEngine.Listener {
        override fun onSample(sample: LocationSample) = handleLocation(sample)
        override fun onSatellites(used: Int, total: Int) {
            _state.update { it.copy(satellitesUsed = used, satellitesTotal = total) }
        }
    })

    private var refCount = 0
    private var tickerJob: Job? = null
    private var locationAllowed = false
    private var locationRunning = false

    init {
        _state.update { it.copy(hasBarometer = barometer.available) }
        scope.launch {
            settingsRepo.flow.collect {
                fusion.applySettings(it.calibrationMode, it.manualOffset, it.qnhHpa)
                altitudeStabilizer.reset()
            }
        }
    }

    @Synchronized
    fun acquire() {
        refCount++
        if (refCount == 1) startSensors()
    }

    @Synchronized
    fun release() {
        refCount--
        if (refCount <= 0) {
            refCount = 0
            stopSensors()
        }
    }

    @Synchronized
    fun onLocationPermission(granted: Boolean) {
        locationAllowed = granted
        if (granted && refCount > 0) startLocation()
    }

    fun calibrateManual(meters: Double): Double? {
        altitudeStabilizer.reset()
        return fusion.calibrateManual(meters)
    }

    fun resetStats() = stats.reset()

    private fun startSensors() {
        barometer.start()
        if (locationAllowed) startLocation()
        tickerJob = scope.launch {
            var n = 0L
            while (isActive) {
                delay(1000)
                tick(n)
                n++
            }
        }
    }

    private fun stopSensors() {
        tickerJob?.cancel()
        tickerJob = null
        barometer.stop()
        if (locationRunning) {
            locationEngine.stop()
            locationRunning = false
        }
    }

    private fun startLocation() {
        if (!locationRunning) {
            locationRunning = true
            locationEngine.start()
        }
    }

    private fun handleLocation(sample: LocationSample) {
        if (sample.isPrecise) lastPreciseFixAt = SystemClock.elapsedRealtime()
        sample.mslAltitude?.let { fusion.onGpsAltitude(it, sample.verticalAccuracy) }
        _state.update {
            it.copy(
                latitude = sample.latitude,
                longitude = sample.longitude,
                gpsAccuracy = sample.horizontalAccuracy,
                gpsVertAccuracy = sample.verticalAccuracy,
                gpsFixTimeMs = if (sample.isPrecise) sample.fixTimeMs else it.gpsFixTimeMs,
                mslSource = if (sample.mslAltitude != null) sample.mslSource else it.mslSource,
            )
        }
    }

    private fun tick(n: Long) {
        val now = System.currentTimeMillis()
        val rawAltitude = fusion.displayAltitude()
        val alt = rawAltitude?.let { altitudeStabilizer.update(it, now) }
        if (alt != null) stats.onAltitude(now, alt)
        if (n % 60L == 5L) samplePressure(alt)

        _state.update {
            it.copy(
                altitude = alt,
                accuracy = fusion.displayAccuracy(),
                pressureHpa = fusion.pressureHpa,
                isCalibrating = fusion.isCalibrating(),
                hasFix = SystemClock.elapsedRealtime() - lastPreciseFixAt < 6_000,
                verticalSpeedMpm = stats.verticalSpeedMpm(),
                minAltitude = stats.minAlt,
                maxAltitude = stats.maxAlt,
                totalAscent = stats.ascent,
                totalDescent = stats.descent,
                history = stats.historySnapshot(),
                pressureTrendHpaPerHour = pressureTrendHpaPerHour(),
                timestampMs = now,
            )
        }
    }

    private fun samplePressure(alt: Double?) {
        val p = fusion.pressureHpa ?: return
        val a = alt ?: return
        val seaLevel = p / (1.0 - a / 44330.0).pow(5.255)
        val t = SystemClock.elapsedRealtime()
        pressureHistory.addLast(t to seaLevel)
        while (pressureHistory.isNotEmpty() && pressureHistory.first().first < t - 4 * 3_600_000L) {
            pressureHistory.removeFirst()
        }
    }

    private fun pressureTrendHpaPerHour(): Double? {
        if (pressureHistory.size < 4) return null
        val first = pressureHistory.take(3)
        val last = pressureHistory.toList().takeLast(3)
        val t0 = first.map { it.first }.average()
        val t1 = last.map { it.first }.average()
        val spanH = (t1 - t0) / 3_600_000.0
        if (spanH < 0.75) return null
        val p0 = first.map { it.second }.average()
        val p1 = last.map { it.second }.average()
        return (p1 - p0) / spanH
    }
}
