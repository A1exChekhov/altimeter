package com.chelmodeev.altimeter

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chelmodeev.altimeter.core.AltimeterCore
import com.chelmodeev.altimeter.data.SettingsRepository
import com.chelmodeev.altimeter.health.HealthPermissionState
import com.chelmodeev.altimeter.health.HealthReader
import com.chelmodeev.altimeter.health.BluetoothHeartRateReader
import com.chelmodeev.altimeter.health.HuaweiHealthReader
import com.chelmodeev.altimeter.health.VitalsSnapshot
import com.chelmodeev.altimeter.logic.Advisor
import com.chelmodeev.altimeter.logic.AdvisorInput
import com.chelmodeev.altimeter.model.AltUnit
import com.chelmodeev.altimeter.model.BluetoothVitalsState
import com.chelmodeev.altimeter.model.SavedTrack
import com.chelmodeev.altimeter.model.UiState
import com.chelmodeev.altimeter.model.VitalsSource
import com.chelmodeev.altimeter.model.WatchState
import com.chelmodeev.altimeter.place.PlaceResolver
import com.chelmodeev.altimeter.track.TrackingService
import com.chelmodeev.altimeter.watch.WatchNotifier
import com.chelmodeev.altimeter.watch.WearEngineBridge
import com.chelmodeev.altimeter.widget.AltimeterWidgetStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
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
    private val huaweiHealthReader = HuaweiHealthReader(app)
    private val bluetoothHeartRateReader = BluetoothHeartRateReader(app)

    private var lastAutoSendAt = 0L
    private var placeJob: Job? = null
    private var vitalsJob: Job? = null
    private var lastWidgetUpdateAt = 0L

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
            var wasRecording = false
            TrackingService.state.collect { track ->
                val shouldReload = (!track.recording && wasRecording) ||
                    (!track.recording && _ui.value.savedTracks.isEmpty())
                wasRecording = track.recording
                val savedTracks = if (shouldReload) loadSavedTracks() else _ui.value.savedTracks
                _ui.update { it.copy(tracking = track, savedTracks = savedTracks) }
            }
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
        bluetoothHeartRateReader.stop()
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
        val now = System.currentTimeMillis()
        autoSendIfDue(now)
        if (now - lastWidgetUpdateAt >= 15_000L) {
            lastWidgetUpdateAt = now
            AltimeterWidgetStore.updateAltitude(
                getApplication(),
                s.altitude,
                _ui.value.unit,
            )
        }
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
            s.vitals.stepsToday?.let { put("steps", it) }
            put("ascent_m", s.totalAscent.roundToLong())
            put("descent_m", s.totalDescent.roundToLong())
        }.toString()
    }

    // ---------- пульс и SpO₂ (Huawei Health Kit + Health Connect) ----------

    fun connectBluetoothHeartRate() {
        bluetoothHeartRateReader.start(object : BluetoothHeartRateReader.Listener {
            override fun onState(state: BluetoothVitalsState, deviceName: String?) {
                _ui.update {
                    it.copy(
                        vitals = it.vitals.copy(
                            bluetoothState = state,
                            bluetoothDeviceName = deviceName ?: it.vitals.bluetoothDeviceName,
                        )
                    )
                }
            }

            override fun onHeartRate(bpm: Long, deviceName: String?) {
                val now = System.currentTimeMillis()
                _ui.update {
                    it.copy(
                        vitals = it.vitals.copy(
                            bluetoothState = BluetoothVitalsState.CONNECTED,
                            bluetoothDeviceName = deviceName ?: it.vitals.bluetoothDeviceName,
                            heartRateBpm = bpm,
                            heartRateAtMs = now,
                            heartRateSource = VitalsSource.BLUETOOTH,
                            heartRateOrigin = deviceName,
                            heartRateIsResting = false,
                        )
                    )
                }
                AltimeterWidgetStore.updateVitals(getApplication(), _ui.value.vitals)
            }
        })
    }

    private suspend fun refreshHealthStatus() {
        val available = healthReader.isAvailable()
        val needsInstall = healthReader.needsProviderInstall()
        val granted = if (available) {
            healthReader.grantedPermissions()
        } else {
            HealthPermissionState()
        }
        val huaweiInstalled = huaweiHealthReader.isInstalled()
        val huaweiConfigured = huaweiHealthReader.isConfigured()
        val huaweiAuthorized = huaweiConfigured && huaweiInstalled &&
            huaweiHealthReader.wasAuthorized()
        _ui.update {
            it.copy(
                vitals = it.vitals.copy(
                    available = available,
                    needsProviderInstall = needsInstall,
                    permissionsGranted = granted.anyGranted,
                    heartRatePermissionGranted = granted.heartRate,
                    restingHeartRatePermissionGranted = granted.restingHeartRate,
                    spo2PermissionGranted = granted.oxygenSaturation,
                    stepsPermissionGranted = granted.steps,
                    huaweiHealthInstalled = huaweiInstalled,
                    huaweiHealthConfigured = huaweiConfigured,
                    huaweiHealthAuthorized = huaweiAuthorized,
                )
            )
        }
        if (huaweiAuthorized || granted.anyGranted) refreshVitals()
    }

    private fun loadSavedTracks(): List<SavedTrack> {
        val directory = File(getApplication<Application>().getExternalFilesDir(null), "tracks")
        return directory.listFiles { file ->
            file.isFile && file.extension.equals("gpx", ignoreCase = true)
        }.orEmpty()
            .sortedByDescending(File::lastModified)
            .map { file ->
                SavedTrack(
                    name = file.name,
                    path = file.absolutePath,
                    modifiedAtMs = file.lastModified(),
                    sizeBytes = file.length(),
                )
            }
    }

    fun onHealthPermissionsResult() {
        viewModelScope.launch { refreshHealthStatus() }
    }

    fun huaweiHealthAuthorizationIntent(): Intent? =
        huaweiHealthReader.authorizationIntent()

    fun onHuaweiHealthAuthorizationUnavailable() {
        val error = when {
            !huaweiHealthReader.isInstalled() -> "HUAWEI_HEALTH_NOT_INSTALLED"
            !huaweiHealthReader.isConfigured() -> "HUAWEI_APP_ID_NOT_CONFIGURED"
            else -> "HUAWEI_AUTHORIZATION_UNAVAILABLE"
        }
        _ui.update { it.copy(vitals = it.vitals.copy(huaweiError = error)) }
    }

    fun onHuaweiHealthAuthorizationResult(data: Intent?) {
        val result = huaweiHealthReader.parseAuthorizationResult(data)
        _ui.update {
            it.copy(
                vitals = it.vitals.copy(
                    huaweiHealthAuthorized = result.success,
                    huaweiError = if (result.success) null else
                        result.errorCode?.let { code -> "Huawei Health Kit: $code" }
                            ?: "HUAWEI_AUTHORIZATION_CANCELLED",
                )
            )
        }
        if (result.success) refreshVitals()
    }

    fun refreshVitals() {
        if (vitalsJob?.isActive == true) return
        val status = _ui.value.vitals
        if (!status.permissionsGranted && !status.huaweiHealthAuthorized) return
        vitalsJob = viewModelScope.launch {
            _ui.update { it.copy(vitals = it.vitals.copy(refreshing = true)) }
            val before = _ui.value.vitals

            val huaweiResult = if (before.huaweiHealthAuthorized) {
                runCatching { huaweiHealthReader.readLatest() }
            } else {
                null
            }
            val huaweiSnapshot = huaweiResult?.getOrNull()
            val huaweiError = huaweiResult?.exceptionOrNull()
            val authorizationLost = huaweiError?.let(huaweiHealthReader::isAuthorizationError) == true
            if (authorizationLost) huaweiHealthReader.clearAuthorization()

            val healthConnectSnapshot = if (before.permissionsGranted) {
                healthReader.readLatest(
                    HealthPermissionState(
                        heartRate = before.heartRatePermissionGranted,
                        restingHeartRate = before.restingHeartRatePermissionGranted,
                        oxygenSaturation = before.spo2PermissionGranted,
                        steps = before.stepsPermissionGranted,
                    )
                )
            } else {
                null
            }

            val heartRate = newestHeartRate(huaweiSnapshot, healthConnectSnapshot, before)
            val oxygen = newestOxygen(huaweiSnapshot, healthConnectSnapshot, before)
            val steps = newestSteps(huaweiSnapshot, healthConnectSnapshot, before)
            val series = newestSeries(huaweiSnapshot, healthConnectSnapshot, before.hrSeries)
            _ui.update {
                val v = it.vitals
                it.copy(
                    vitals = v.copy(
                        refreshing = false,
                        huaweiHealthAuthorized = v.huaweiHealthAuthorized && !authorizationLost,
                        huaweiError = huaweiError?.let(huaweiHealthReader::describeError),
                        healthConnectError = healthConnectSnapshot?.readErrors
                            ?.takeIf { errors -> errors.isNotEmpty() }
                            ?.joinToString("; "),
                        heartRateBpm = heartRate?.value,
                        heartRateAtMs = heartRate?.atMs,
                        heartRateSource = heartRate?.source,
                        heartRateOrigin = heartRate?.origin,
                        heartRateIsResting = heartRate?.isResting == true,
                        spo2Percent = oxygen?.value,
                        spo2AtMs = oxygen?.atMs,
                        spo2Source = oxygen?.source,
                        spo2Origin = oxygen?.origin,
                        stepsToday = steps?.value,
                        stepsAtMs = steps?.atMs,
                        stepsSource = steps?.source,
                        stepsOrigin = steps?.origin,
                        hrSeries = series,
                    )
                )
            }
            AltimeterWidgetStore.updateVitals(getApplication(), _ui.value.vitals)
        }
    }

    private data class HeartRateValue(
        val value: Long,
        val atMs: Long,
        val source: VitalsSource,
        val origin: String?,
        val isResting: Boolean,
    )

    private data class OxygenValue(
        val value: Double,
        val atMs: Long,
        val source: VitalsSource,
        val origin: String?,
    )

    private data class StepsValue(
        val value: Long,
        val atMs: Long,
        val source: VitalsSource,
        val origin: String?,
    )

    private fun newestHeartRate(
        huawei: VitalsSnapshot?,
        healthConnect: VitalsSnapshot?,
        previous: com.chelmodeev.altimeter.model.VitalsState,
    ): HeartRateValue? = listOfNotNull(
        huawei?.heartRateBpm?.let { bpm ->
            HeartRateValue(
                bpm,
                huawei.heartRateAt?.toEpochMilli() ?: 0L,
                VitalsSource.HUAWEI_HEALTH,
                huawei.heartRateOrigin,
                huawei.heartRateIsResting,
            )
        },
        healthConnect?.heartRateBpm?.let { bpm ->
            HeartRateValue(
                bpm,
                healthConnect.heartRateAt?.toEpochMilli() ?: 0L,
                VitalsSource.HEALTH_CONNECT,
                healthConnect.heartRateOrigin,
                healthConnect.heartRateIsResting,
            )
        },
        previous.heartRateBpm?.let { bpm ->
            HeartRateValue(
                bpm,
                previous.heartRateAtMs ?: 0L,
                previous.heartRateSource ?: return@let null,
                previous.heartRateOrigin,
                previous.heartRateIsResting,
            )
        },
    ).maxByOrNull { it.atMs }

    private fun newestOxygen(
        huawei: VitalsSnapshot?,
        healthConnect: VitalsSnapshot?,
        previous: com.chelmodeev.altimeter.model.VitalsState,
    ): OxygenValue? = listOfNotNull(
        huawei?.spo2Percent?.let { value ->
            OxygenValue(
                value,
                huawei.spo2At?.toEpochMilli() ?: 0L,
                VitalsSource.HUAWEI_HEALTH,
                huawei.spo2Origin,
            )
        },
        healthConnect?.spo2Percent?.let { value ->
            OxygenValue(
                value,
                healthConnect.spo2At?.toEpochMilli() ?: 0L,
                VitalsSource.HEALTH_CONNECT,
                healthConnect.spo2Origin,
            )
        },
        previous.spo2Percent?.let { value ->
            OxygenValue(
                value,
                previous.spo2AtMs ?: 0L,
                previous.spo2Source ?: return@let null,
                previous.spo2Origin,
            )
        },
    ).maxByOrNull { it.atMs }

    private fun newestSteps(
        huawei: VitalsSnapshot?,
        healthConnect: VitalsSnapshot?,
        previous: com.chelmodeev.altimeter.model.VitalsState,
    ): StepsValue? = listOfNotNull(
        huawei?.stepsToday?.let { value ->
            StepsValue(
                value,
                huawei.stepsAt?.toEpochMilli() ?: 0L,
                VitalsSource.HUAWEI_HEALTH,
                huawei.stepsOrigin,
            )
        },
        healthConnect?.stepsToday?.let { value ->
            StepsValue(
                value,
                healthConnect.stepsAt?.toEpochMilli() ?: 0L,
                VitalsSource.HEALTH_CONNECT,
                healthConnect.stepsOrigin,
            )
        },
        previous.stepsToday?.let { value ->
            StepsValue(
                value,
                previous.stepsAtMs ?: 0L,
                previous.stepsSource ?: return@let null,
                previous.stepsOrigin,
            )
        },
    ).maxByOrNull { it.atMs }

    private fun newestSeries(
        huawei: VitalsSnapshot?,
        healthConnect: VitalsSnapshot?,
        previous: List<Pair<Long, Long>>,
    ): List<Pair<Long, Long>> = listOf(
        huawei?.hrSeries.orEmpty(),
        healthConnect?.hrSeries.orEmpty(),
        previous,
    ).maxByOrNull { series -> series.lastOrNull()?.first ?: 0L }.orEmpty()
}
