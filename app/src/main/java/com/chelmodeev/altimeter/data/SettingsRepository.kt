package com.chelmodeev.altimeter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chelmodeev.altimeter.model.AltUnit
import com.chelmodeev.altimeter.model.CalibrationMode
import com.chelmodeev.altimeter.model.TrackSamplingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    data class Settings(
        val darkTheme: Boolean,
        val autoTrackEnabled: Boolean,
        val trackSamplingMode: TrackSamplingMode,
        val unit: AltUnit,
        val calibrationMode: CalibrationMode,
        val manualOffset: Double?,
        val manualAltitude: Double,
        val qnhHpa: Double,
        val topoMap: Boolean,
        val keepScreenOn: Boolean,
        val autoSendToWatch: Boolean,
    )

    private object K {
        val UNIT = stringPreferencesKey("unit")
        val CALIB = stringPreferencesKey("calib")
        val MANUAL_OFFSET = doublePreferencesKey("manual_offset")
        val MANUAL_ALT = doublePreferencesKey("manual_alt")
        val QNH = doublePreferencesKey("qnh")
        val TOPO = booleanPreferencesKey("topo")
        val KEEP_ON = booleanPreferencesKey("keep_on")
        val AUTO_SEND = booleanPreferencesKey("auto_send")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val AUTO_TRACK = booleanPreferencesKey("auto_track")
        val TRACK_SAMPLING = stringPreferencesKey("track_sampling")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            darkTheme = p[K.DARK_THEME] ?: true,
            autoTrackEnabled = p[K.AUTO_TRACK] ?: false,
            trackSamplingMode = enumOrDefault(
                p[K.TRACK_SAMPLING],
                TrackSamplingMode.EVERY_1S,
            ),
            unit = enumOrDefault(p[K.UNIT], AltUnit.METERS),
            calibrationMode = enumOrDefault(p[K.CALIB], CalibrationMode.AUTO_GPS),
            manualOffset = p[K.MANUAL_OFFSET],
            manualAltitude = p[K.MANUAL_ALT] ?: 0.0,
            qnhHpa = p[K.QNH] ?: 1013.25,
            topoMap = p[K.TOPO] ?: true,
            keepScreenOn = p[K.KEEP_ON] ?: true,
            autoSendToWatch = p[K.AUTO_SEND] ?: false,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        runCatching { if (name == null) default else enumValueOf<T>(name) }.getOrDefault(default)

    suspend fun setUnit(v: AltUnit) = context.dataStore.edit { it[K.UNIT] = v.name }

    suspend fun setCalibrationAuto() =
        context.dataStore.edit { it[K.CALIB] = CalibrationMode.AUTO_GPS.name }

    suspend fun setCalibrationManual(offset: Double?, altitude: Double) =
        context.dataStore.edit {
            it[K.CALIB] = CalibrationMode.MANUAL_ALTITUDE.name
            if (offset != null) it[K.MANUAL_OFFSET] = offset
            it[K.MANUAL_ALT] = altitude
        }

    suspend fun setCalibrationQnh(qnh: Double) = context.dataStore.edit {
        it[K.CALIB] = CalibrationMode.QNH.name
        it[K.QNH] = qnh
    }

    suspend fun setTopo(v: Boolean) = context.dataStore.edit { it[K.TOPO] = v }
    suspend fun setKeepScreenOn(v: Boolean) = context.dataStore.edit { it[K.KEEP_ON] = v }
    suspend fun setAutoSend(v: Boolean) = context.dataStore.edit { it[K.AUTO_SEND] = v }
    suspend fun setDarkTheme(v: Boolean) = context.dataStore.edit { it[K.DARK_THEME] = v }
    suspend fun setAutoTrack(v: Boolean) = context.dataStore.edit { it[K.AUTO_TRACK] = v }
    suspend fun setTrackSampling(v: TrackSamplingMode) =
        context.dataStore.edit { it[K.TRACK_SAMPLING] = v.name }
}
