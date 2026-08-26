package com.chelmodeev.altimeter.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.chelmodeev.altimeter.model.MslSource
import kotlin.math.abs

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracy: Float?,
    val verticalAccuracy: Float?,
    /** Высота над уровнем моря, метры (уже с поправкой геоида, если возможно). */
    val mslAltitude: Double?,
    val mslSource: MslSource,
    /** true — точный GPS-фикс; false — грубая позиция (сеть/кэш) только для карты. */
    val isPrecise: Boolean,
    /** Время именно этого GPS-фикса, а не очередного обновления интерфейса. */
    val fixTimeMs: Long,
)

/**
 * GPS через LocationManager (работает и на телефонах Huawei без Google-сервисов).
 * Высоту MSL получает по приоритету:
 *  1) Location.getMslAltitudeMeters() (Android 14+),
 *  2) поле MSL из NMEA GGA,
 *  3) эллипсоидная высота минус разделение геоида из NMEA,
 *  4) сырая эллипсоидная высота.
 */
class LocationEngine(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onSample(sample: LocationSample)
        fun onSatellites(used: Int, total: Int)
    }

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())

    private var geoidSeparation: Double? = null
    private var nmeaMsl: Double? = null
    private var nmeaMslAt = 0L
    private var lastPreciseAt = 0L
    private var running = false

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = handleLocation(location, precise = true)
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private val networkListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // грубая позиция нужна, только пока нет свежего GPS-фикса (для карты/места)
            if (SystemClock.elapsedRealtime() - lastPreciseAt > 10_000) {
                handleLocation(location, precise = false)
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private val nmeaListener = OnNmeaMessageListener { message, _ -> parseNmea(message) }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) {
                if (status.usedInFix(i)) used++
            }
            listener.onSatellites(used, status.satelliteCount)
        }
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val gpsRegistered = runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                val request = LocationRequest.Builder(1_000L)
                    .setMinUpdateIntervalMillis(750L)
                    .setMaxUpdateDelayMillis(2_000L)
                    .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                    .build()
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    request,
                    context.mainExecutor,
                    gpsListener,
                )
            } else {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1_000L,
                    0f,
                    gpsListener,
                    Looper.getMainLooper(),
                )
            }
        }.isSuccess
        val networkRegistered = runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 5000L, 0f, networkListener, Looper.getMainLooper()
            )
        }.isSuccess
        if (!gpsRegistered) {
            if (networkRegistered) runCatching { locationManager.removeUpdates(networkListener) }
            return false
        }
        running = true
        runCatching { locationManager.addNmeaListener(nmeaListener, handler) }
        runCatching { locationManager.registerGnssStatusCallback(gnssCallback, handler) }
        // стартовая точка для карты
        runCatching {
            val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            last?.let { handleLocation(it, precise = false) }
        }
        return true
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { locationManager.removeUpdates(gpsListener) }
        runCatching { locationManager.removeUpdates(networkListener) }
        runCatching { locationManager.removeNmeaListener(nmeaListener) }
        runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
    }

    private fun handleLocation(location: Location, precise: Boolean) {
        if (precise) lastPreciseAt = SystemClock.elapsedRealtime()

        var msl: Double? = null
        var source = MslSource.NONE
        if (precise && location.hasAltitude()) {
            when {
                Build.VERSION.SDK_INT >= 34 && location.hasMslAltitude() -> {
                    msl = location.mslAltitudeMeters
                    source = MslSource.API34
                }
                nmeaMsl != null && SystemClock.elapsedRealtime() - nmeaMslAt < 5_000 -> {
                    msl = nmeaMsl
                    source = MslSource.NMEA_MSL
                }
                geoidSeparation != null -> {
                    msl = location.altitude - geoidSeparation!!
                    source = MslSource.GEOID_CORRECTED
                }
                else -> {
                    msl = location.altitude
                    source = MslSource.ELLIPSOID
                }
            }
        }

        listener.onSample(
            LocationSample(
                latitude = location.latitude,
                longitude = location.longitude,
                horizontalAccuracy = if (location.hasAccuracy()) location.accuracy else null,
                verticalAccuracy =
                    if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters else null,
                mslAltitude = msl,
                mslSource = source,
                isPrecise = precise,
                fixTimeMs = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
        )
    }

    /** $GPGGA / $GNGGA: [9] — высота MSL, [11] — разделение геоида. */
    private fun parseNmea(sentence: String) {
        if (!sentence.startsWith("$")) return
        val type = sentence.substringBefore(',')
        if (!type.endsWith("GGA")) return
        val parts = sentence.substringBefore('*').split(',')
        if (parts.size < 12) return
        val fixQuality = parts[6].toIntOrNull() ?: 0
        if (fixQuality <= 0) return
        parts[9].toDoubleOrNull()?.let {
            if (abs(it) < 10_000) {
                nmeaMsl = it
                nmeaMslAt = SystemClock.elapsedRealtime()
            }
        }
        parts[11].toDoubleOrNull()?.let {
            if (abs(it) < 200) geoidSeparation = it
        }
    }
}
