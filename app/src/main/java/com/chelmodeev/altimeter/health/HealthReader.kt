package com.chelmodeev.altimeter.health

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.Duration

data class HealthPermissionState(
    val heartRate: Boolean = false,
    val restingHeartRate: Boolean = false,
    val oxygenSaturation: Boolean = false,
    val steps: Boolean = false,
) {
    val anyHeartRateGranted: Boolean get() = heartRate || restingHeartRate
    val anyGranted: Boolean get() = anyHeartRateGranted || oxygenSaturation || steps
    val allGranted: Boolean get() = anyHeartRateGranted && oxygenSaturation && steps
}

data class VitalsSnapshot(
    val heartRateBpm: Long?,
    val heartRateAt: Instant?,
    val heartRateOrigin: String? = null,
    val heartRateIsResting: Boolean = false,
    val spo2Percent: Double?,
    val spo2At: Instant?,
    val spo2Origin: String? = null,
    val stepsToday: Long? = null,
    val stepsAt: Instant? = null,
    val stepsOrigin: String? = null,
    /** Пульс за последние 3 часа для мини-графика: (epochMs, bpm). */
    val hrSeries: List<Pair<Long, Long>>,
    val spo2Series: List<Pair<Long, Double>> = emptyList(),
    val stepsSeries: List<Pair<Long, Long>> = emptyList(),
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
        val RESTING_HEART_RATE_PERMISSION: String =
            HealthPermission.getReadPermission(RestingHeartRateRecord::class)
        val OXYGEN_PERMISSION: String =
            HealthPermission.getReadPermission(OxygenSaturationRecord::class)
        val STEPS_PERMISSION: String =
            HealthPermission.getReadPermission(StepsRecord::class)

        val PERMISSIONS: Set<String> = setOf(
            HEART_RATE_PERMISSION,
            RESTING_HEART_RATE_PERMISSION,
            OXYGEN_PERMISSION,
            STEPS_PERMISSION,
        )
        private const val GRAPH_WINDOW_HOURS = 6L
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
            heartRate = HEART_RATE_PERMISSION in granted && platformPermissionGranted(
                HEART_RATE_PERMISSION
            ),
            restingHeartRate = RESTING_HEART_RATE_PERMISSION in granted &&
                platformPermissionGranted(RESTING_HEART_RATE_PERMISSION),
            oxygenSaturation = OXYGEN_PERMISSION in granted && platformPermissionGranted(
                OXYGEN_PERMISSION
            ),
            steps = STEPS_PERMISSION in granted && platformPermissionGranted(STEPS_PERMISSION),
        )
    }

    suspend fun revokeAllPermissions() {
        if (!isAvailable()) return
        runCatching {
            HealthConnectClient.getOrCreate(context).permissionController.revokeAllPermissions()
        }
    }

    suspend fun readLatest(permissions: HealthPermissionState): VitalsSnapshot? {
        if (!isAvailable()) return null
        val client = runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() ?: return null
        val now = Instant.now()
        val from = now.minusSeconds(12 * 3600)
        val graphFrom = now.minusSeconds(GRAPH_WINDOW_HOURS * 3600)
        val errors = mutableListOf<String>()

        var hrAt: Instant? = null
        var hrBpm: Long? = null
        var hrOrigin: String? = null
        var hrIsResting = false
        val series = mutableListOf<Pair<Long, Long>>()
        if (permissions.heartRate) runCatching {
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
                        if (!sample.time.isBefore(graphFrom)) {
                            series += sample.time.toEpochMilli() to sample.beatsPerMinute
                        }
                    }
                }
                pageToken = resp.pageToken
                pageCount++
            } while (pageToken != null && pageCount < 20)
        }.onFailure { errors += it.readError("heart_rate") }

        // Некоторые синхронизаторы пишут суточный пульс Huawei как отдельный
        // RestingHeartRateRecord, а не как серию HeartRateRecord.
        if (permissions.restingHeartRate) runCatching {
            val restingFrom = now.minusSeconds(36 * 3600)
            val resp = client.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(restingFrom, now),
                    ascendingOrder = false,
                    pageSize = 10,
                )
            )
            resp.records.maxByOrNull { it.time }?.let { record ->
                val current = hrAt
                if (current == null || record.time.isAfter(current)) {
                    hrAt = record.time
                    hrBpm = record.beatsPerMinute
                    hrOrigin = record.metadata.dataOrigin.packageName
                    hrIsResting = true
                }
            }
        }.onFailure { errors += it.readError("resting_heart_rate") }

        var spAt: Instant? = null
        var sp: Double? = null
        var spOrigin: String? = null
        val spo2Series = mutableListOf<Pair<Long, Double>>()
        if (permissions.oxygenSaturation) runCatching {
            val resp = client.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, now),
                    ascendingOrder = false,
                    pageSize = 1_000,
                )
            )
            resp.records.firstOrNull()?.let { record ->
                spAt = record.time
                sp = record.percentage.value
                spOrigin = record.metadata.dataOrigin.packageName
            }
            resp.records.forEach { record ->
                if (!record.time.isBefore(graphFrom)) {
                    spo2Series += record.time.toEpochMilli() to record.percentage.value
                }
            }
        }.onFailure { errors += it.readError("oxygen") }

        var stepsToday: Long? = null
        var stepsAt: Instant? = null
        var stepsOrigin: String? = null
        val stepsSeries = mutableListOf<Pair<Long, Long>>()
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
            val buckets = client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(graphFrom, now),
                    timeRangeSlicer = Duration.ofMinutes(15),
                )
            )
            var cumulative = 0L
            buckets.sortedBy { it.endTime }.forEach { bucket ->
                cumulative += bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L
                stepsSeries += bucket.endTime.toEpochMilli() to cumulative
            }
        }.onFailure { errors += it.readError("steps") }

        series.sortBy { it.first }
        spo2Series.sortBy { it.first }
        return VitalsSnapshot(
            heartRateBpm = hrBpm,
            heartRateAt = hrAt,
            heartRateOrigin = hrOrigin,
            heartRateIsResting = hrIsResting,
            spo2Percent = sp,
            spo2At = spAt,
            spo2Origin = spOrigin,
            stepsToday = stepsToday,
            stepsAt = stepsAt,
            stepsOrigin = stepsOrigin,
            hrSeries = series.distinctBy { it.first },
            spo2Series = spo2Series.distinctBy { it.first },
            stepsSeries = stepsSeries.distinctBy { it.first },
            readErrors = errors,
        )
    }

    private fun platformPermissionGranted(permission: String): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun Throwable.readError(metric: String): String = when (this) {
        is SecurityException -> "$metric:permission"
        else -> "$metric:${javaClass.simpleName}"
    }
}
