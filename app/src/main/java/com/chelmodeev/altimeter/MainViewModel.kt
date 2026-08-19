package com.chelmodeev.altimeter

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chelmodeev.altimeter.core.AltimeterCore
import com.chelmodeev.altimeter.data.SettingsRepository
import com.chelmodeev.altimeter.health.HealthReader
import com.chelmodeev.altimeter.logic.Advisor
import com.chelmodeev.altimeter.logic.AdvisorInput
import com.chelmodeev.altimeter.model.AltUnit
import com.chelmodeev.altimeter.model.UiState
import com.chelmodeev.altimeter.model.WatchState
import com.chelmodeev.altimeter.place.PlaceResolver
import com.chelmodeev.altimeter.track.TrackingService
import com.chelmodeev.altimeter.watch.WatchNotifier
import com.chelmodeev.altimeter.watch.WearEngineBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToLong

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val core = AltimeterCore.get(app)
    private val settingsRepo = SettingsRepository(app)
    private val advisor = Advisor()
    private val notifier = WatchNotifier(app)
    private val wearEngine = WearEngineBridge(app)
    private val placeResolver = PlaceResolver(app)
    private val healthReader = HealthReader(app)

    private var lastAutoSendAt = 0L
    private var placeJob: Job? = null
    private var vitalsJob: Job? = null

    init {
        wearEngine.onStatus = { msg ->
            _ui.update { it.copy(watch = WatchState(statusText = msg, busy = false)) }
        }
        core.acquire()

        viewModelScope.launch { core.state.collect { onCoreState(it) } }

        viewModelScope.launch {
            settingsRepo.flow.collect { s ->
                _ui.update {
                    it.copy(
                        unit = s.unit,
                        calibrationMode = s.calibrationMode,
                        manualAltitude = s.manualAltitude,
                        qnhHpa = s.qnhHpa,
                        topoMap = s.topoMap,
                        keepScreenOn = s.keepScreenOn,
                        autoSendToWatch = s.autoSendToWatch,
                    )
                }
            }
        }

        viewModelScope.launch {
            TrackingService.state.collect { t -> _ui.update { it.copy(tracking = t) } }
        }

        viewModelScope.launch {
            refreshHealthStatus()
            while (true) {
                delay(300_000)
                refreshVitals()
            }
        }
    }

    override fun onCleared() {
        core.release()
        super.onCleared()
    }

    // ---------- состояние ядра ----------

    private fun onCoreState(s: AltimeterCore.CoreState) {
        val prev = _ui.value
        val advices = advisor.evaluate(
            AdvisorInput(
                nowMs = if (s.timestampMs > 0) s.timestampMs else System.currentTimeMillis(),
                altitude = s.altitude,
                verticalSpeedMpm = s.verticalSpeedMpm,
                hasFix = s.hasFix,
                locationGranted = prev.locationPermissionGranted,
                spo2 = prev.vitals.spo2Percent,
                spo2AtMs = prev.vitals.spo2AtMs,
                heartRate = prev.vitals.heartRateBpm,
                heartRateAtMs = prev.vitals.heartRateAtMs,
                pressureTrendHpaPerHour = s.pressureTrendHpaPerHour,
            )
        )
        _ui.update {
            it.copy(
                hasBarometer = s.hasBarometer,
                altitude = s.altitude,
                accuracy = s.accuracy,
                pressureHpa = s.pressureHpa,
                isCalibrating = s.isCalibrating,
                verticalSpeedMpm = s.verticalSpeedMpm,
                latitude = s.latitude,
                longitude = s.longitude,
                gpsAccuracy = s.gpsAccuracy,
                gpsVertAccuracy = s.gpsVertAccuracy,
                satellitesUsed = s.satellitesUsed,
                satellitesTotal = s.satellitesTotal,
                hasFix = s.hasFix,
                mslSource = s.mslSource,
                minAltitude = s.minAltitude,
                maxAltitude = s.maxAltitude,
                totalAscent = s.totalAscent,
                totalDescent = s.totalDescent,
                history = s.history,
                advices = advices,
            )
        }
        if (s.latitude != null && s.longitude != null) resolvePlace(s.latitude, s.longitude)
        autoSendIfDue(System.currentTimeMillis())
    }

    fun onLocationPermission(granted: Boolean) {
        _ui.update { it.copy(locationPermissionGranted = granted) }
        core.onLocationPermission(granted)
    }

    private fun resolvePlace(lat: Double, lon: Double) {
        if (placeJob?.isActive == true) return
        placeJob = viewModelScope.launch {
            placeResolver.resolve(lat, lon)?.let { name ->
                _ui.update { it.copy(placeName = name) }
            }
        }
    }

    // ---------- настройки ----------

    fun setUnit(u: AltUnit) = launchIo { settingsRepo.setUnit(u) }
    fun setCalibrationAuto() = launchIo { settingsRepo.setCalibrationAuto() }
    fun setTopo(v: Boolean) = launchIo { settingsRepo.setTopo(v) }
    fun setKeepScreenOn(v: Boolean) = launchIo { settingsRepo.setKeepScreenOn(v) }
    fun setAutoSend(v: Boolean) = launchIo { settingsRepo.setAutoSend(v) }

    fun calibrateManual(text: String) {
        val v = text.replace(',', '.').trim().toDoubleOrNull() ?: return
        val meters = if (_ui.value.unit == AltUnit.FEET) v / 3.2808399 else v
        if (meters < -500 || meters > 10_000) return
        val offset = core.calibrateManual(meters)
        launchIo { settingsRepo.setCalibrationManual(offset, meters) }
    }

    fun setQnh(text: String) {
        val v = text.replace(',', '.').trim().toDoubleOrNull() ?: return
        if (v < 850 || v > 1100) return
        launchIo { settingsRepo.setCalibrationQnh(v) }
    }

    fun resetStats() = core.resetStats()

    private fun launchIo(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    // ---------- часы ----------

    fun sendToWatch() {
        val app = getApplication<Application>()
        val state = _ui.value
        val msg = when {
            state.altitude == null -> app.getString(R.string.watch_no_data)
            !notifier.canPost() -> app.getString(R.string.watch_notif_denied)
            notifier.send(state) -> app.getString(R.string.watch_notif_sent)
            else -> app.getString(R.string.watch_notif_denied)
        }
        _ui.update { it.copy(watch = WatchState(statusText = msg)) }
    }

    fun sendViaWearEngine(uiContext: Context) {
        _ui.update { it.copy(watch = it.watch.copy(busy = true)) }
        wearEngine.sendJson(uiContext, buildWatchPayload())
    }

    private fun autoSendIfDue(now: Long) {
        val state = _ui.value
        if (!state.autoSendToWatch) return
        if (state.altitude == null) return
        if (now - lastAutoSendAt < 300_000) return
        if (!notifier.canPost()) return
        lastAutoSendAt = now
        notifier.send(state)
    }

    private fun buildWatchPayload(): String {
        val s = _ui.value
        fun r1(v: Double) = (v * 10).roundToLong() / 10.0
        return JSONObject().apply {
            put("type", "altimeter")
            put("ts", System.currentTimeMillis())
            s.altitude?.let { put("altitude_m", r1(it)) }
            s.accuracy?.let { put("accuracy_m", r1(it)) }
            s.pressureHpa?.let { put("pressure_hpa", r1(it)) }
            s.latitude?.let { put("lat", it) }
            s.longitude?.let { put("lon", it) }
            s.placeName?.let { put("place", it) }
            s.verticalSpeedMpm?.let { put("vspeed_mpm", r1(it)) }
            s.vitals.heartRateBpm?.let { put("hr_bpm", it) }
            s.vitals.spo2Percent?.let { put("spo2", it) }
            put("ascent_m", s.totalAscent.roundToLong())
            put("descent_m", s.totalDescent.roundToLong())
        }.toString()
    }

    // ---------- пульс и SpO₂ (Health Connect) ----------

    private suspend fun refreshHealthStatus() {
        val available = healthReader.isAvailable()
        val needsInstall = healthReader.needsProviderInstall()
        val granted = if (available) healthReader.grantedAll() else false
        _ui.update {
            it.copy(
                vitals = it.vitals.copy(
                    available = available,
                    needsProviderInstall = needsInstall,
                    permissionsGranted = granted,
                )
            )
        }
        if (granted) refreshVitals()
    }

    fun onHealthPermissionsResult() {
        viewModelScope.launch { refreshHealthStatus() }
    }

    fun refreshVitals() {
        if (vitalsJob?.isActive == true) return
        if (!_ui.value.vitals.permissionsGranted) return
        vitalsJob = viewModelScope.launch {
            _ui.update { it.copy(vitals = it.vitals.copy(refreshing = true)) }
            val snap = healthReader.readLatest()
            _ui.update {
                val v = it.vitals
                it.copy(
                    vitals = v.copy(
                        refreshing = false,
                        heartRateBpm = snap?.heartRateBpm ?: v.heartRateBpm,
                        heartRateAtMs = snap?.heartRateAt?.toEpochMilli() ?: v.heartRateAtMs,
                        spo2Percent = snap?.spo2Percent ?: v.spo2Percent,
                        spo2AtMs = snap?.spo2At?.toEpochMilli() ?: v.spo2AtMs,
                        hrSeries = if (snap != null && snap.hrSeries.isNotEmpty()) snap.hrSeries else v.hrSeries,
                    )
                )
            }
        }
    }
}
