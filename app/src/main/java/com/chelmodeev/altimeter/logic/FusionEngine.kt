package com.chelmodeev.altimeter.logic

import com.chelmodeev.altimeter.model.CalibrationMode
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Слияние барометра и GPS.
 *
 * Барометр даёт очень стабильную ОТНОСИТЕЛЬНУЮ высоту (шум ~0.3–0.5 м),
 * но его ноль плавает с погодой. GPS даёт АБСОЛЮТНУЮ высоту, но шумную (±5–30 м).
 * Здесь барометрическая высота по стандартной атмосфере привязывается к GPS
 * медленным скалярным фильтром Калмана по поправке (offset):
 * итог — гладкая и при этом абсолютно верная высота.
 */
class FusionEngine {

    @Volatile
    var mode: CalibrationMode = CalibrationMode.AUTO_GPS
        private set

    var pressureHpa: Double? = null
        private set

    private var qnh: Double = 1013.25
    private var manualOffset: Double? = null
    private var pendingManualAltitude: Double? = null

    private var baroStdAlt: Double? = null // высота по стандартной атмосфере (1013.25)

    // Авто-поправка: msl ≈ baroStdAlt + offset
    private var offset: Double? = null
    private var offsetVar: Double = 1600.0

    // Оценка только по GPS (для устройств без барометра)
    private var gpsAlt: Double? = null
    private var gpsVar: Double = 1600.0

    @Synchronized
    fun onPressure(hpa: Double) {
        pressureHpa = hpa
        val alt = stdAltitude(hpa, 1013.25)
        baroStdAlt = alt
        pendingManualAltitude?.let {
            manualOffset = it - alt
            pendingManualAltitude = null
        }
    }

    @Synchronized
    fun onGpsAltitude(msl: Double, vAccMeters: Float?) {
        val sigma = (vAccMeters ?: 25f).toDouble().coerceIn(3.0, 100.0)
        if (sigma > 60.0) return // слишком неточный фикс не используем
        val r = sigma * sigma

        // GPS-only оценка: допускаем реальное движение по вертикали (+2 м²/апдейт)
        val g = gpsAlt
        if (g == null) {
            gpsAlt = msl
            gpsVar = r
        } else {
            gpsVar += 2.0
            val k = gpsVar / (gpsVar + r)
            gpsAlt = g + k * (msl - g)
            gpsVar *= (1 - k)
        }

        // Поправка барометра: почти константа, дрейфует только с погодой
        val b = baroStdAlt ?: return
        val z = msl - b
        val o = offset
        if (o == null) {
            offset = z
            offsetVar = r
        } else {
            offsetVar += 0.03
            val k = offsetVar / (offsetVar + r)
            offset = o + k * (z - o)
            offsetVar *= (1 - k)
        }
    }

    @Synchronized
    fun applySettings(mode: CalibrationMode, manualOffset: Double?, qnhHpa: Double) {
        this.mode = mode
        this.qnh = qnhHpa
        if (manualOffset != null) this.manualOffset = manualOffset
    }

    /**
     * Ручная калибровка «я знаю, что здесь N метров».
     * Возвращает поправку для сохранения (или null, если давление ещё не пришло —
     * тогда поправка вычислится при первом замере).
     */
    @Synchronized
    fun calibrateManual(knownAltitude: Double): Double? {
        val b = baroStdAlt
        return if (b == null) {
            pendingManualAltitude = knownAltitude
            null
        } else {
            val off = knownAltitude - b
            manualOffset = off
            off
        }
    }

    @Synchronized
    fun displayAltitude(): Double? {
        val b = baroStdAlt
        return when (mode) {
            CalibrationMode.AUTO_GPS ->
                if (b != null && offset != null) b + offset!! else gpsAlt
            CalibrationMode.MANUAL_ALTITUDE ->
                if (b != null && manualOffset != null) b + manualOffset!! else gpsAlt
            CalibrationMode.QNH ->
                pressureHpa?.let { stdAltitude(it, qnh) } ?: gpsAlt
        }
    }

    /** Оценка погрешности «±N м»; null — режим без оценки (ручной/QNH). */
    @Synchronized
    fun displayAccuracy(): Double? {
        val b = baroStdAlt
        return when (mode) {
            CalibrationMode.AUTO_GPS ->
                if (b != null && offset != null) sqrt(offsetVar).coerceAtLeast(1.0) + 0.5
                else gpsOnlyAccuracy()
            CalibrationMode.MANUAL_ALTITUDE ->
                if (b != null && manualOffset != null) null else gpsOnlyAccuracy()
            CalibrationMode.QNH ->
                if (pressureHpa != null) null else gpsOnlyAccuracy()
        }
    }

    /** true, пока барометр ждёт первой привязки к GPS. */
    @Synchronized
    fun isCalibrating(): Boolean =
        mode == CalibrationMode.AUTO_GPS && baroStdAlt != null && offset == null

    private fun gpsOnlyAccuracy(): Double? =
        if (gpsAlt != null) sqrt(gpsVar).coerceAtLeast(2.0) else null

    private fun stdAltitude(p: Double, p0: Double): Double =
        44330.0 * (1.0 - (p / p0).pow(0.1902949))
}
