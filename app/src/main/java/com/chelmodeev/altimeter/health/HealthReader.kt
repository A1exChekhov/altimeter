package com.chelmodeev.altimeter.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

data class VitalsSnapshot(
    val heartRateBpm: Long?,
    val heartRateAt: Instant?,
    val spo2Percent: Double?,
    val spo2At: Instant?,
    /** Пульс за последние 3 часа для мини-графика: (epochMs, bpm). */
    val hrSeries: List<Pair<Long, Long>>,
)

/**
 * Пульс и SpO₂ из локальной базы Health Connect. Источник часов должен быть
 * записан туда совместимым приложением-синхронизатором до чтения Альтиметром.
 */
class HealthReader(private val context: Context) {

    companion object {
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        )
    }

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    fun needsProviderInstall(): Boolean =
        sdkStatus() == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    suspend fun grantedAll(): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            HealthConnectClient.getOrCreate(context)
                .permissionController.getGrantedPermissions()
                .containsAll(PERMISSIONS)
        }.getOrDefault(false)
    }

    suspend fun readLatest(): VitalsSnapshot? {
        if (!isAvailable()) return null
        val client = runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() ?: return null
        val now = Instant.now()
        val from = now.minusSeconds(12 * 3600)

        var hrAt: Instant? = null
        var hrBpm: Long? = null
        val series = mutableListOf<Pair<Long, Long>>()
        runCatching {
            val resp = client.readRecords(
                ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(from, now))
            )
            val seriesFrom = now.minusSeconds(3 * 3600)
            for (rec in resp.records) {
                for (s in rec.samples) {
                    val cur = hrAt
                    if (cur == null || s.time.isAfter(cur)) {
                        hrAt = s.time
                        hrBpm = s.beatsPerMinute
                    }
                    if (s.time.isAfter(seriesFrom)) {
                        series += s.time.toEpochMilli() to s.beatsPerMinute
                    }
                }
            }
        }

        var spAt: Instant? = null
        var sp: Double? = null
        runCatching {
            val resp = client.readRecords(
                ReadRecordsRequest(OxygenSaturationRecord::class, TimeRangeFilter.between(from, now))
            )
            for (rec in resp.records) {
                val cur = spAt
                if (cur == null || rec.time.isAfter(cur)) {
                    spAt = rec.time
                    sp = rec.percentage.value
                }
            }
        }

        series.sortBy { it.first }
        return VitalsSnapshot(hrBpm, hrAt, sp, spAt, series)
    }
}
