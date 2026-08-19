package com.chelmodeev.altimeter.model

enum class AltUnit { METERS, FEET }

enum class CalibrationMode { AUTO_GPS, MANUAL_ALTITUDE, QNH }

/** Откуда взята высота над уровнем моря (MSL) из GPS. */
enum class MslSource { NONE, API34, NMEA_MSL, GEOID_CORRECTED, ELLIPSOID }

data class ChartPoint(val timeMs: Long, val altitude: Double)

enum class AdviceSeverity { INFO, CAUTION, WARNING }

enum class AdviceKind {
    PRESSURE_FALLING_FAST, PRESSURE_FALLING, PRESSURE_RISING,
    ALTITUDE_ACCLIMATIZE, ALTITUDE_HIGH, ALTITUDE_VERY_HIGH,
    FAST_ASCENT, HYDRATION,
    SPO2_LOW, SPO2_VERY_LOW, HR_HIGH,
    GPS_WEAK,
}

data class Advice(
    val kind: AdviceKind,
    val severity: AdviceSeverity,
    /** Числовое значение для подстановки в текст (тренд, SpO₂, пульс). */
    val value: String? = null,
)

data class WatchState(
    val statusText: String? = null,
    val busy: Boolean = false,
)

/** Состояние записи GPX-трека (foreground-сервис). */
data class TrackRecState(
    val recording: Boolean = false,
    val startedAtMs: Long = 0L,
    val points: Int = 0,
    val distanceM: Double = 0.0,
    val ascentM: Double = 0.0,
    val lastSavedName: String? = null,
    val lastSavedPath: String? = null,
)

/** Пульс и SpO₂, измеренные часами (через Huawei Health → Health Connect). */
data class VitalsState(
    val available: Boolean = false,
    val needsProviderInstall: Boolean = false,
    val permissionsGranted: Boolean = false,
    val heartRateBpm: Long? = null,
    val heartRateAtMs: Long? = null,
    val spo2Percent: Double? = null,
    val spo2AtMs: Long? = null,
    val hrSeries: List<Pair<Long, Long>> = emptyList(),
    val refreshing: Boolean = false,
)

data class UiState(
    val hasBarometer: Boolean = false,
    val locationPermissionGranted: Boolean = false,

    val altitude: Double? = null,          // метры MSL
    val accuracy: Double? = null,          // ± метры (null: режим ручной/QNH)
    val verticalSpeedMpm: Double? = null,  // м/мин
    val pressureHpa: Double? = null,
    val isCalibrating: Boolean = false,

    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracy: Float? = null,
    val gpsVertAccuracy: Float? = null,
    val satellitesUsed: Int = 0,
    val satellitesTotal: Int = 0,
    val hasFix: Boolean = false,
    val mslSource: MslSource = MslSource.NONE,
    val placeName: String? = null,

    val minAltitude: Double? = null,
    val maxAltitude: Double? = null,
    val totalAscent: Double = 0.0,
    val totalDescent: Double = 0.0,
    val history: List<ChartPoint> = emptyList(),

    val unit: AltUnit = AltUnit.METERS,
    val calibrationMode: CalibrationMode = CalibrationMode.AUTO_GPS,
    val manualAltitude: Double = 0.0,
    val qnhHpa: Double = 1013.25,
    val topoMap: Boolean = true,
    val keepScreenOn: Boolean = true,
    val autoSendToWatch: Boolean = false,

    val watch: WatchState = WatchState(),
    val vitals: VitalsState = VitalsState(),
    val advices: List<Advice> = emptyList(),
    val tracking: TrackRecState = TrackRecState(),
)
