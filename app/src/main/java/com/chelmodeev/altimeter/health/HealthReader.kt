package com.chelmodeev.altimeter.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HealthPermissionState(
    val heartRate: Boolean = false,
    val oxygenSaturation: Boolean = false,
    val steps: Boolean = false,
) {
    val anyGranted: Boolean get() = heartRate || oxygenSaturation || steps
    val allGranted: Boolean get() = heartRate && oxygenSaturation && steps
}

data class VitalsSnapshot(
    val heartRateBpm: Long?,
    val heartRateAt: Instant?,
    val heartRateOrigin: String? = null,
    val spo2Percent: Double?,
    val spo2At: Instant?,
    val spo2Origin: String? = null,
    val stepsToday: Long? = null,
    val stepsAt: Instant? = null,
    val stepsOrigin: String? = null,
    /** Пульс за последние 3 часа для мини-графика: (epochMs, bpm). */
    val hrSeries: List<Pair<Long, Long>>,
    val readErrors: List<String> = emptyList(),
)

/**
 * Пульс и SpO₂ из локальной базы Health Connect. Источник часов должен быть
 * записан туда совместимым приложением-синхронизатором до чтения Альтиметром.
 */
class HealthReader(private val context: Context) {

    companion object {
        val HEART_RATE_PERMISSION: String =
            HealthPermission.getReadPermission(HeartRateRecord::class)
        val OXYGEN_PERMISSION: String =
            HealthPermission.getReadPermission(OxygenSaturationRecord::class)
        val STEPS_PERMISSION: String =
            HealthPermission.getReadPermission(StepsRecord::class)

        val PERMISSIONS: Set<String> = setOf(
            HEART_RATE_PERMISSION,
            OXYGEN_PERMISSION,
            STEPS_PERMISSION,
        )
    }

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    fun needsProviderInstall(): Boolean =
        sdkStatus() == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    suspend fun grantedPermissions(): HealthPermissionState {
        if (!isAvailable()) return HealthPermissionState()
        val granted = runCatching {
            HealthConnectClient.getOrCreate(context)
                .permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())
        return HealthPermissionState(
            heartRate = HEART_RATE_PERMISSION in granted,
            oxygenSaturation = OXYGEN_PERMISSION in granted,
            steps = STEPS_PERMISSION in granted,
        )
    }

    suspend fun readLatest(permissions: HealthPermissionState): VitalsSnapshot? {
        if (!isAvailable()) return null
        val client = runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() ?: return null
        val now = Instant.now()
        val from = now.minusSeconds(12 * 3600)
        val errors = mutableListOf<String>()

        var hrAt: Instant? = null
        var hrBpm: Long? = null
        var hrOrigin: String? = null
        val series = mutableListOf<Pair<Long, Long>>()
        if (permissions.heartRate) runCatching {
            val seriesFrom = now.minusSeconds(3 * 3600)
            var pageToken: String? = null
            var pageCount = 0
            do {
                val resp = client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(from, now),
                        ascendingOrder = false,
                        pageSize = 1_000,
                        pageToken = pageToken,
                    )
                )
                for (rec in resp.records) {
                    for (sample in rec.samples) {
                        val cur = hrAt
                        if (cur == null || sample.time.isAfter(cur)) {
                            hrAt = sample.time
                            hrBpm = sample.beatsPerMinute
                            hrOrigin = rec.metadata.dataOrigin.packageName
                        }
                        if (!sample.time.isBefore(seriesFrom)) {
                            series += sample.time.toEpochMilli() to sample.beatsPerMinute
                        }
                    }
                }
                pageToken = resp.pageToken
                pageCount++
            } while (pageToken != null && pageCount < 20)
        }.onFailure { errors += "heart_rate:${it.shortDescription()}" }

        var spAt: Instant? = null
        var sp: Double? = null
        var spOrigin: String? = null
        if (permissions.oxygenSaturation) runCatching {
            val resp = client.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, now),
                    ascendingOrder = false,
                    pageSize = 1,
                )
            )
            resp.records.firstOrNull()?.let { record ->
                spAt = record.time
                sp = record.percentage.value
                spOrigin = record.metadata.dataOrigin.packageName
            }
        }.onFailure { errors += "oxygen:${it.shortDescription()}" }

        var stepsToday: Long? = null
        var stepsAt: Instant? = null
        var stepsOrigin: String? = null
        if (permissions.steps) runCatching {
            val zone = ZoneId.systemDefault()
            val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val aggregate = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                )
            )
            stepsToday = aggregate[StepsRecord.COUNT_TOTAL]
            if (stepsToday != null) {
                stepsAt = now
                stepsOrigin = aggregate.dataOrigins
                    .map { it.packageName }
                    .sorted()
                    .joinToString(", ")
                    .takeIf { it.isNotBlank() }
            }
        }.onFailure { errors += "steps:${it.shortDescription()}" }

        series.sortBy { it.first }
        return VitalsSnapshot(
            heartRateBpm = hrBpm,
            heartRateAt = hrAt,
            heartRateOrigin = hrOrigin,
            spo2Percent = sp,
            spo2At = spAt,
            spo2Origin = spOrigin,
            stepsToday = stepsToday,
            stepsAt = stepsAt,
            stepsOrigin = stepsOrigin,
            hrSeries = series.distinctBy { it.first },
            readErrors = errors,
        )
    }

    private fun Throwable.shortDescription(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}
