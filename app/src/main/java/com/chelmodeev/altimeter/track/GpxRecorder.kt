package com.chelmodeev.altimeter.track

import android.location.Location
import android.util.Xml
import com.chelmodeev.altimeter.core.AltimeterCore
import com.chelmodeev.altimeter.model.TrackMapPoint
import com.chelmodeev.altimeter.model.TrackSamplingMode
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import org.xmlpull.v1.XmlPullParser

/**
 * Накапливает точки трека и пишет GPX 1.1.
 * Принимает каждый новый качественный GPS-фикс примерно раз в секунду.
 * Геометрия не упрощается: все принятые точки и повороты остаются в GPX.
 * После длительной потери GPS начинается новый сегмент, поэтому карта не
 * соединяет неизвестный участок вымышленной прямой.
 */
class GpxRecorder {

    private data class Pt(
        val lat: Double,
        val lon: Double,
        val ele: Double?,
        val timeMs: Long,
        val startsNewSegment: Boolean,
    )

    private val points = mutableListOf<Pt>()
    private var lastPt: Pt? = null
    private var lastAcceptAt = 0L
    private var lastSeenFixAt = 0L
    private var lastBearingDeg: Float? = null
    private var lastEleAccepted: Double? = null
    var startedAtMs = 0L
        private set

    var distanceM = 0.0
        private set
    var ascentM = 0.0
        private set
    var descentM = 0.0
        private set
    var movingTimeMs = 0L
        private set
    var stoppedTimeMs = 0L
        private set
    val pointCount: Int
        @Synchronized get() = points.size
    private var samplingMode = TrackSamplingMode.EVERY_1S

    @Synchronized
    fun setSamplingMode(mode: TrackSamplingMode) {
        samplingMode = mode
    }

    @Synchronized
    fun mapPoints(): List<TrackMapPoint> =
        points.map {
            TrackMapPoint(
                latitude = it.lat,
                longitude = it.lon,
                startsNewSegment = it.startsNewSegment,
            )
        }

    @Synchronized
    fun begin() {
        points.clear()
        lastPt = null
        lastAcceptAt = 0L
        lastSeenFixAt = 0L
        lastBearingDeg = null
        lastEleAccepted = null
        distanceM = 0.0
        ascentM = 0.0
        descentM = 0.0
        movingTimeMs = 0L
        stoppedTimeMs = 0L
        startedAtMs = System.currentTimeMillis()
    }

    /** true — точка принята (стоит обновить счётчики в UI). */
    @Synchronized
    fun offer(s: AltimeterCore.CoreState): Boolean {
        val lat = s.latitude ?: return false
        val lon = s.longitude ?: return false
        if (!s.hasFix) return false
        val hAcc = s.gpsAccuracy
        if (hAcc != null && hAcc > 35f) return false

        // CoreState меняется и по таймеру UI. Записываем только новый GPS-фикс.
        val now = s.gpsFixTimeMs.takeIf { it > 0L } ?: return false
        if (now <= lastSeenFixAt) return false
        lastSeenFixAt = now
        val prev = lastPt
        val moved = if (prev == null) Double.MAX_VALUE else distanceBetween(prev.lat, prev.lon, lat, lon)
        val minimumMovement = (((hAcc ?: 8f) * 0.10).toDouble()).coerceIn(0.8, 2.5)
        val elapsed = if (prev == null) 0L else (now - prev.timeMs).coerceAtLeast(0L)
        val speedMps = if (prev != null && elapsed > 0L) moved / (elapsed / 1_000.0) else 0.0
        val bearing = if (prev == null) null else bearingBetween(prev.lat, prev.lon, lat, lon)
        val isSharpTurn = bearing != null && lastBearingDeg?.let {
            angularDifference(it, bearing) >= 15f && moved >= minimumMovement
        } == true
        val intervalMs = when (samplingMode) {
            TrackSamplingMode.EVERY_1S -> 1_000L
            TrackSamplingMode.EVERY_2S -> 2_000L
            TrackSamplingMode.EVERY_4S -> 4_000L
            TrackSamplingMode.AUTO -> when {
                speedMps >= 7.0 -> 1_000L
                speedMps >= 2.0 -> 2_000L
                else -> 4_000L
            }
        }
        if (lastAcceptAt > 0L && elapsed < intervalMs && !isSharpTurn) return false
        if (lastAcceptAt > 0L && elapsed < 750L) return false
        if (moved < minimumMovement && now - lastAcceptAt < 10_000L) return false

        val startsNewSegment = prev == null ||
            (elapsed > 30_000L && moved > 10.0) ||
            (moved > 250.0 && speedMps > 12.0)

        if (prev != null && !startsNewSegment && moved < 10_000) {
            distanceM += moved
            if (elapsed in 1..120_000) {
                if (speedMps >= 0.35) movingTimeMs += elapsed else stoppedTimeMs += elapsed
            }
        }

        val ele = s.altitude
        if (ele != null) {
            val lastEle = lastEleAccepted
            if (lastEle == null) {
                lastEleAccepted = ele
            } else if (abs(ele - lastEle) >= 2.0) {
                if (ele > lastEle) ascentM += ele - lastEle
                else descentM += lastEle - ele
                lastEleAccepted = ele
            }
        }

        points += Pt(lat, lon, ele, now, startsNewSegment)
        lastPt = points.last()
        lastAcceptAt = now
        lastBearingDeg = if (startsNewSegment) null else bearing
        return true
    }

    /** Восстанавливает незавершённую сессию после перезапуска процесса. */
    @Synchronized
    fun restoreFrom(file: File): Boolean {
        if (!file.isFile) return false
        val restored = runCatching { readGpx(file) }.getOrDefault(emptyList())
        if (restored.isEmpty()) return false
        begin()
        points += restored
        rebuildStats()
        return true
    }

    @Synchronized
    fun saveTo(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(buildGpx())
    }

    private fun buildGpx(): String {
        val sb = StringBuilder(points.size * 96 + 512)
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append(
            """<gpx version="1.1" creator="Errarium Altimeter Kailas" xmlns="http://www.topografix.com/GPX/1/1">"""
        ).append('\n')
        sb.append("  <metadata><time>")
            .append(Instant.ofEpochMilli(if (startedAtMs > 0) startedAtMs else System.currentTimeMillis()))
            .append("</time></metadata>\n")
        sb.append("  <trk><name>Altimeter track</name><trkseg>\n")
        points.forEachIndexed { index, p ->
            if (index > 0 && p.startsNewSegment) {
                sb.append("  </trkseg><trkseg>\n")
            }
            sb.append("    <trkpt lat=\"")
                .append(String.format(Locale.US, "%.7f", p.lat))
                .append("\" lon=\"")
                .append(String.format(Locale.US, "%.7f", p.lon))
                .append("\">")
            p.ele?.let {
                sb.append("<ele>").append(String.format(Locale.US, "%.1f", it)).append("</ele>")
            }
            sb.append("<time>").append(Instant.ofEpochMilli(p.timeMs)).append("</time>")
            sb.append("</trkpt>\n")
        }
        sb.append("  </trkseg></trk>\n</gpx>\n")
        return sb.toString()
    }

    private fun readGpx(file: File): List<Pt> {
        val restored = mutableListOf<Pt>()
        file.inputStream().buffered().use { input ->
            val parser = Xml.newPullParser().apply { setInput(input, null) }
            var segmentStart = true
            var lat: Double? = null
            var lon: Double? = null
            var ele: Double? = null
            var timeMs: Long? = null
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "trkseg" -> segmentStart = true
                        "trkpt" -> {
                            lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            ele = null
                            timeMs = null
                        }
                        "ele" -> ele = parser.nextText().toDoubleOrNull()
                        "time" -> timeMs = runCatching { Instant.parse(parser.nextText()).toEpochMilli() }
                            .getOrNull()
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "trkpt") {
                        val pointLat = lat
                        val pointLon = lon
                        if (pointLat != null && pointLon != null) {
                            restored += Pt(
                                pointLat,
                                pointLon,
                                ele,
                                timeMs ?: file.lastModified(),
                                segmentStart,
                            )
                            segmentStart = false
                        }
                    }
                }
                parser.next()
            }
        }
        return restored
    }

    private fun rebuildStats() {
        distanceM = 0.0
        ascentM = 0.0
        descentM = 0.0
        movingTimeMs = 0L
        stoppedTimeMs = 0L
        lastEleAccepted = null
        var previous: Pt? = null
        for (point in points) {
            val prev = previous
            if (prev != null && !point.startsNewSegment) {
                val moved = distanceBetween(prev.lat, prev.lon, point.lat, point.lon)
                val elapsed = (point.timeMs - prev.timeMs).coerceAtLeast(0L)
                distanceM += moved
                if (elapsed in 1..120_000) {
                    if (moved / (elapsed / 1_000.0) >= 0.35) movingTimeMs += elapsed
                    else stoppedTimeMs += elapsed
                }
            }
            point.ele?.let { elevation ->
                val last = lastEleAccepted
                if (last == null) lastEleAccepted = elevation
                else if (abs(elevation - last) >= 2.0) {
                    if (elevation > last) ascentM += elevation - last else descentM += last - elevation
                    lastEleAccepted = elevation
                }
            }
            previous = point
        }
        lastPt = points.last()
        lastAcceptAt = lastPt!!.timeMs
        lastSeenFixAt = lastAcceptAt
        lastBearingDeg = points.takeLast(2).let { tail ->
            if (tail.size == 2 && !tail.last().startsNewSegment) {
                bearingBetween(tail[0].lat, tail[0].lon, tail[1].lat, tail[1].lon)
            } else null
        }
        startedAtMs = points.first().timeMs
    }

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val out = FloatArray(3)
        Location.distanceBetween(lat1, lon1, lat2, lon2, out)
        return out[0].toDouble()
    }

    private fun bearingBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val out = FloatArray(3)
        Location.distanceBetween(lat1, lon1, lat2, lon2, out)
        return out[1]
    }

    private fun angularDifference(a: Float, b: Float): Float {
        val raw = abs(a - b) % 360f
        return if (raw > 180f) 360f - raw else raw
    }
}
