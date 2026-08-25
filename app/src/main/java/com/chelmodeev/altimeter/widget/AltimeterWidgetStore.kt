package com.chelmodeev.altimeter.widget

import android.content.Context
import com.chelmodeev.altimeter.model.AltUnit
import com.chelmodeev.altimeter.model.TrackRecState
import com.chelmodeev.altimeter.model.VitalsSource
import com.chelmodeev.altimeter.model.VitalsState

data class AltimeterWidgetSnapshot(
    val altitudeM: Double?,
    val pressureHpa: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val unit: AltUnit,
    val trackRecording: Boolean,
    val trackDistanceM: Double,
    val trackPoints: Int,
    val trackAscentM: Double,
    val trackDescentM: Double,
    val trackMovingTimeMs: Long,
    val trackStoppedTimeMs: Long,
    val heartRateBpm: Long?,
    val spo2Percent: Double?,
    val stepsToday: Long?,
    val activeCaloriesToday: Double?,
    val heartRateSource: VitalsSource?,
    val spo2Source: VitalsSource?,
    val stepsSource: VitalsSource?,
    val updatedAtMs: Long,
    val darkTheme: Boolean,
)

/** Последний разрешённый снимок для домашнего виджета. */
object AltimeterWidgetStore {
    private const val PREFS = "altimeter_widget"

    fun updateAltitude(
        context: Context,
        altitudeM: Double?,
        pressureHpa: Double?,
        latitude: Double?,
        longitude: Double?,
        unit: AltUnit,
    ) {
        val edit = prefs(context).edit()
            .putString("unit", unit.name)
            .putLong("updated", System.currentTimeMillis())
        if (altitudeM != null) edit.putLong("altitude", altitudeM.toBits())
        edit.putDoubleIfPresent("pressure", pressureHpa)
        edit.putDoubleIfPresent("latitude", latitude)
        edit.putDoubleIfPresent("longitude", longitude)
        edit.apply()
        updateAllWidgets(context)
    }

    fun updateTrack(context: Context, track: TrackRecState) {
        prefs(context).edit()
            .putBoolean("track_recording", track.recording)
            .putLong("track_distance", track.distanceM.toBits())
            .putInt("track_points", track.points)
            .putLong("track_ascent", track.ascentM.toBits())
            .putLong("track_descent", track.descentM.toBits())
            .putLong("track_moving_time", track.movingTimeMs)
            .putLong("track_stopped_time", track.stoppedTimeMs)
            .putLong("updated", System.currentTimeMillis())
            .apply()
        updateAllWidgets(context)
    }

    fun updateAltitudeAndTrack(
        context: Context,
        altitudeM: Double?,
        unit: AltUnit,
        track: TrackRecState,
    ) {
        val edit = prefs(context).edit()
            .putString("unit", unit.name)
            .putBoolean("track_recording", track.recording)
            .putLong("track_distance", track.distanceM.toBits())
            .putInt("track_points", track.points)
            .putLong("track_ascent", track.ascentM.toBits())
            .putLong("track_descent", track.descentM.toBits())
            .putLong("track_moving_time", track.movingTimeMs)
            .putLong("track_stopped_time", track.stoppedTimeMs)
            .putLong("updated", System.currentTimeMillis())
        if (altitudeM != null) edit.putLong("altitude", altitudeM.toBits())
        edit.apply()
        updateAllWidgets(context)
    }

    fun updateVitals(context: Context, vitals: VitalsState) {
        val edit = prefs(context).edit()
            .putLong("updated", System.currentTimeMillis())
        if (vitals.heartRateBpm != null) edit.putLong("heart_rate", vitals.heartRateBpm)
        if (vitals.spo2Percent != null) edit.putLong("spo2", vitals.spo2Percent.toBits())
        if (vitals.stepsToday != null) edit.putLong("steps", vitals.stepsToday)
        edit.putDoubleIfPresent("active_calories", vitals.activeCaloriesToday)
        putSource(edit, "heart_source", vitals.heartRateSource)
        putSource(edit, "spo2_source", vitals.spo2Source)
        putSource(edit, "steps_source", vitals.stepsSource)
        edit.apply()
        updateAllWidgets(context)
    }

    fun updateTheme(context: Context, darkTheme: Boolean) {
        prefs(context).edit().putBoolean("dark_theme", darkTheme).apply()
        updateAllWidgets(context)
    }

    fun read(context: Context): AltimeterWidgetSnapshot {
        val p = prefs(context)
        return AltimeterWidgetSnapshot(
            altitudeM = p.getLongOrNull("altitude")?.let(Double::fromBits),
            pressureHpa = p.getLongOrNull("pressure")?.let(Double::fromBits),
            latitude = p.getLongOrNull("latitude")?.let(Double::fromBits),
            longitude = p.getLongOrNull("longitude")?.let(Double::fromBits),
            unit = runCatching {
                AltUnit.valueOf(p.getString("unit", AltUnit.METERS.name).orEmpty())
            }.getOrDefault(AltUnit.METERS),
            trackRecording = p.getBoolean("track_recording", false),
            trackDistanceM = p.getLongOrNull("track_distance")?.let(Double::fromBits) ?: 0.0,
            trackPoints = p.getInt("track_points", 0),
            trackAscentM = p.getLongOrNull("track_ascent")?.let(Double::fromBits) ?: 0.0,
            trackDescentM = p.getLongOrNull("track_descent")?.let(Double::fromBits) ?: 0.0,
            trackMovingTimeMs = p.getLong("track_moving_time", 0L),
            trackStoppedTimeMs = p.getLong("track_stopped_time", 0L),
            heartRateBpm = p.getLongOrNull("heart_rate"),
            spo2Percent = p.getLongOrNull("spo2")?.let(Double::fromBits),
            stepsToday = p.getLongOrNull("steps"),
            activeCaloriesToday = p.getLongOrNull("active_calories")?.let(Double::fromBits),
            heartRateSource = p.source("heart_source"),
            spo2Source = p.source("spo2_source"),
            stepsSource = p.source("steps_source"),
            updatedAtMs = p.getLong("updated", 0L),
            darkTheme = p.getBoolean("dark_theme", true),
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun updateAllWidgets(context: Context) {
        AltimeterWidgetProvider.updateAll(context)
        AltitudeWidgetProvider.updateAll(context)
        AltitudeCompactWidgetProvider.updateAll(context)
        HealthWidgetProvider.updateAll(context)
        TrackWidgetProvider.updateAll(context)
    }

    private fun android.content.SharedPreferences.getLongOrNull(key: String): Long? =
        if (contains(key)) getLong(key, 0L) else null

    private fun android.content.SharedPreferences.Editor.putDoubleIfPresent(
        key: String,
        value: Double?,
    ) {
        if (value != null) putLong(key, value.toBits())
    }

    private fun android.content.SharedPreferences.source(key: String): VitalsSource? =
        getString(key, null)?.let { runCatching { VitalsSource.valueOf(it) }.getOrNull() }

    private fun putSource(
        edit: android.content.SharedPreferences.Editor,
        key: String,
        value: VitalsSource?,
    ) {
        if (value == null) edit.remove(key) else edit.putString(key, value.name)
    }
}
