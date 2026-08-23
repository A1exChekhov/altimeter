package com.chelmodeev.altimeter.widget

import android.content.Context
import com.chelmodeev.altimeter.model.AltUnit
import com.chelmodeev.altimeter.model.TrackRecState
import com.chelmodeev.altimeter.model.VitalsSource
import com.chelmodeev.altimeter.model.VitalsState

data class AltimeterWidgetSnapshot(
    val altitudeM: Double?,
    val unit: AltUnit,
    val trackRecording: Boolean,
    val trackDistanceM: Double,
    val trackPoints: Int,
    val heartRateBpm: Long?,
    val spo2Percent: Double?,
    val stepsToday: Long?,
    val heartRateSource: VitalsSource?,
    val spo2Source: VitalsSource?,
    val stepsSource: VitalsSource?,
    val updatedAtMs: Long,
)

/** Последний разрешённый снимок для домашнего виджета. */
object AltimeterWidgetStore {
    private const val PREFS = "altimeter_widget"

    fun updateAltitude(context: Context, altitudeM: Double?, unit: AltUnit) {
        val edit = prefs(context).edit()
            .putString("unit", unit.name)
            .putLong("updated", System.currentTimeMillis())
        if (altitudeM == null) edit.remove("altitude")
        else edit.putLong("altitude", altitudeM.toBits())
        edit.apply()
        AltimeterWidgetProvider.updateAll(context)
    }

    fun updateTrack(context: Context, track: TrackRecState) {
        prefs(context).edit()
            .putBoolean("track_recording", track.recording)
            .putLong("track_distance", track.distanceM.toBits())
            .putInt("track_points", track.points)
            .putLong("updated", System.currentTimeMillis())
            .apply()
        AltimeterWidgetProvider.updateAll(context)
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
            .putLong("updated", System.currentTimeMillis())
        if (altitudeM == null) edit.remove("altitude")
        else edit.putLong("altitude", altitudeM.toBits())
        edit.apply()
        AltimeterWidgetProvider.updateAll(context)
    }

    fun updateVitals(context: Context, vitals: VitalsState) {
        val edit = prefs(context).edit()
            .putLong("updated", System.currentTimeMillis())
        if (vitals.heartRateBpm == null) edit.remove("heart_rate")
        else edit.putLong("heart_rate", vitals.heartRateBpm)
        if (vitals.spo2Percent == null) edit.remove("spo2")
        else edit.putLong("spo2", vitals.spo2Percent.toBits())
        if (vitals.stepsToday == null) edit.remove("steps")
        else edit.putLong("steps", vitals.stepsToday)
        putSource(edit, "heart_source", vitals.heartRateSource)
        putSource(edit, "spo2_source", vitals.spo2Source)
        putSource(edit, "steps_source", vitals.stepsSource)
        edit.apply()
        AltimeterWidgetProvider.updateAll(context)
    }

    fun read(context: Context): AltimeterWidgetSnapshot {
        val p = prefs(context)
        return AltimeterWidgetSnapshot(
            altitudeM = p.getLongOrNull("altitude")?.let(Double::fromBits),
            unit = runCatching {
                AltUnit.valueOf(p.getString("unit", AltUnit.METERS.name).orEmpty())
            }.getOrDefault(AltUnit.METERS),
            trackRecording = p.getBoolean("track_recording", false),
            trackDistanceM = p.getLongOrNull("track_distance")?.let(Double::fromBits) ?: 0.0,
            trackPoints = p.getInt("track_points", 0),
            heartRateBpm = p.getLongOrNull("heart_rate"),
            spo2Percent = p.getLongOrNull("spo2")?.let(Double::fromBits),
            stepsToday = p.getLongOrNull("steps"),
            heartRateSource = p.source("heart_source"),
            spo2Source = p.source("spo2_source"),
            stepsSource = p.source("steps_source"),
            updatedAtMs = p.getLong("updated", 0L),
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun android.content.SharedPreferences.getLongOrNull(key: String): Long? =
        if (contains(key)) getLong(key, 0L) else null

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
