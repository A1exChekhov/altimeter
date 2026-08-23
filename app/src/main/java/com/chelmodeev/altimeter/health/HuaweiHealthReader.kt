package com.chelmodeev.altimeter.health

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.chelmodeev.altimeter.BuildConfig
import com.huawei.hmf.tasks.Task
import com.huawei.hms.hihealth.HiHealthStatusCodes
import com.huawei.hms.hihealth.HuaweiHiHealth
import com.huawei.hms.hihealth.data.DataType
import com.huawei.hms.hihealth.data.Field
import com.huawei.hms.hihealth.data.HealthDataTypes
import com.huawei.hms.hihealth.data.HealthFields
import com.huawei.hms.hihealth.data.SamplePoint
import com.huawei.hms.hihealth.data.Scopes
import com.huawei.hms.hihealth.options.ReadOptions
import com.huawei.hms.hihealth.result.ReadReply
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToLong

data class HuaweiAuthResult(
    val success: Boolean,
    val errorCode: Int? = null,
)

/**
 * Прямое чтение данных Huawei Watch через HUAWEI Health Kit.
 *
 * Это основной путь для телефонов Honor/Huawei: он не зависит от того,
 * экспортирует ли установленная версия HUAWEI Health данные в Health Connect.
 */
class HuaweiHealthReader(private val context: Context) {

    companion object {
        private const val HEALTH_PACKAGE = "com.huawei.health"
        private const val PREFS = "huawei_health_kit"
        private const val KEY_AUTHORIZED = "authorized"

        val SCOPES: Array<String> = arrayOf(
            Scopes.HEALTHKIT_HEARTRATE_READ,
            Scopes.HEALTHKIT_OXYGEN_SATURATION_READ,
            Scopes.HEALTHKIT_STEP_READ,
        )
    }

    fun isConfigured(): Boolean = BuildConfig.HUAWEI_APP_ID.isNotBlank()

    fun isInstalled(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                HEALTH_PACKAGE,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(HEALTH_PACKAGE, 0)
        }
    }.isSuccess

    fun wasAuthorized(): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTHORIZED, false)

    fun authorizationIntent(): Intent? {
        if (!isConfigured() || !isInstalled()) return null
        return runCatching {
            HuaweiHiHealth.getSettingController(context)
                .requestAuthorizationIntent(SCOPES, true)
        }.getOrNull()
    }

    fun parseAuthorizationResult(data: Intent?): HuaweiAuthResult {
        val result = runCatching {
            HuaweiHiHealth.getSettingController(context)
                .parseHealthKitAuthResultFromIntent(data)
        }.getOrNull()
        val success = result?.isSuccess == true
        setAuthorized(success)
        return HuaweiAuthResult(success, result?.errorCode)
    }

    fun clearAuthorization() = setAuthorized(false)

    suspend fun readLatest(): VitalsSnapshot {
        check(isConfigured()) { "HUAWEI_APP_ID_NOT_CONFIGURED" }
        check(isInstalled()) { "HUAWEI_HEALTH_NOT_INSTALLED" }

        // DataController следует создавать заново перед каждым чтением:
        // его Context/Activity и сессия HMS Core могут устареть.
        val controller = HuaweiHiHealth.getDataController(context)
        val requestedTypes = arrayListOf(
            DataType.DT_INSTANTANEOUS_HEART_RATE,
            HealthDataTypes.DT_INSTANTANEOUS_SPO2,
            DataType.DT_CONTINUOUS_STEPS_TOTAL,
        )
        val latest = controller.readLatestData(requestedTypes).await()

        val heartPoint = latest[DataType.DT_INSTANTANEOUS_HEART_RATE]
        val oxygenPoint = latest[HealthDataTypes.DT_INSTANTANEOUS_SPO2]
        val stepsPoint = latest[DataType.DT_CONTINUOUS_STEPS_TOTAL]
        val heartAt = heartPoint?.sampleTime()
        val oxygenAt = oxygenPoint?.sampleTime()
        val stepsAt = stepsPoint?.sampleTime()
        val heartRate = heartPoint
            ?.getFieldValue(Field.FIELD_BPM)
            ?.asDoubleValue()
            ?.takeIf { it in 1.0..255.0 }
            ?.roundToLong()
        val oxygen = oxygenPoint
            ?.getFieldValue(HealthFields.FIELD_SATURATION)
            ?.asDoubleValue()
            ?.takeIf { it in 1.0..100.0 }
        val steps = stepsPoint
            ?.getFieldValue(Field.FIELD_STEPS)
            ?.asIntValue()
            ?.toLong()
            ?.takeIf { it >= 0L }

        val series = runCatching {
            readHeartRateSeries(controller.read(heartRateReadOptions()).await())
        }.getOrDefault(emptyList())

        return VitalsSnapshot(
            heartRateBpm = heartRate,
            heartRateAt = heartAt,
            heartRateOrigin = HEALTH_PACKAGE,
            spo2Percent = oxygen,
            spo2At = oxygenAt,
            spo2Origin = HEALTH_PACKAGE,
            stepsToday = steps,
            stepsAt = stepsAt,
            stepsOrigin = HEALTH_PACKAGE,
            hrSeries = series,
        )
    }

    fun isAuthorizationError(error: Throwable): Boolean {
        val code = errorCode(error) ?: return false
        return code == HiHealthStatusCodes.NO_AUTHORITY_ERROR ||
            code == HiHealthStatusCodes.HEALTH_APP_NOT_AUTHORISED ||
            code == HiHealthStatusCodes.NO_REQUIRED_PERMISSION ||
            code == HiHealthStatusCodes.HUAWEIID_NOT_LOGGED_IN
    }

    fun describeError(error: Throwable): String {
        val code = errorCode(error)
        val message = code?.let {
            runCatching { HiHealthStatusCodes.getStatusCodeMessage(it) }.getOrNull()
        }
        return when {
            code != null && !message.isNullOrBlank() -> "$code: $message"
            code != null -> code.toString()
            !error.message.isNullOrBlank() -> error.message.orEmpty()
            else -> error.javaClass.simpleName
        }
    }

    private fun heartRateReadOptions(): ReadOptions {
        val end = System.currentTimeMillis()
        val start = end - 3 * 60 * 60 * 1_000L
        return ReadOptions.Builder()
            .read(DataType.DT_INSTANTANEOUS_HEART_RATE)
            .setTimeRange(start, end, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun readHeartRateSeries(reply: ReadReply): List<Pair<Long, Long>> =
        reply.sampleSets
            .asSequence()
            .flatMap { it.samplePoints.asSequence() }
            .mapNotNull { point ->
                val bpm = runCatching {
                    point.getFieldValue(Field.FIELD_BPM).asDoubleValue().roundToLong()
                }.getOrNull()?.takeIf { it in 1..255 } ?: return@mapNotNull null
                val at = point.sampleTime()?.toEpochMilli() ?: return@mapNotNull null
                at to bpm
            }
            .distinctBy { it.first }
            .sortedBy { it.first }
            .toList()

    private fun SamplePoint.sampleTime(): Instant? {
        val candidates = longArrayOf(
            getSamplingTime(TimeUnit.MILLISECONDS),
            getEndTime(TimeUnit.MILLISECONDS),
            getStartTime(TimeUnit.MILLISECONDS),
        )
        return candidates.maxOrNull()?.takeIf { it > 0L }?.let(Instant::ofEpochMilli)
    }

    private fun errorCode(error: Throwable): Int? =
        error.message?.trim()?.toIntOrNull()

    private fun setAuthorized(value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTHORIZED, value)
            .apply()
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
