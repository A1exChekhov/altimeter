package com.chelmodeev.altimeter.track

import android.location.Location
import com.chelmodeev.altimeter.core.AltimeterCore
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

/**
 * Накапливает точки трека и пишет GPX 1.1.
 * Точка принимается: не чаще раза в 2 с; при движении ≥ 2 м — сразу,
 * на месте — раз в 15 с (чтобы файл не пух от стояния).
 */
class GpxRecorder {

    private data class Pt(val lat: Double, val lon: Double, val ele: Double?, val timeMs: Long)

    private val points = mutableListOf<Pt>()
    private var lastPt: Pt? = null
    private var lastAcceptAt = 0L
    private var lastEleAccepted: Double? = null
    private var startedAtMs = 0L

    var distanceM = 0.0
        private set
    var ascentM = 0.0
        private set
    val pointCount: Int
        @Synchronized get() = points.size

    @Synchronized
    fun begin() {
        points.clear()
        lastPt = null
        lastAcceptAt = 0L
        lastEleAccepted = null
        distanceM = 0.0
        ascentM = 0.0
        startedAtMs = System.currentTimeMillis()
    }

    /** true — точка принята (стоит обновить счётчики в UI). */
    @Synchronized
    fun offer(s: AltimeterCore.CoreState): Boolean {
        val lat = s.latitude ?: return false
        val lon = s.longitude ?: return false
        if (!s.hasFix) return false
        val hAcc = s.gpsAccuracy
        if (hAcc != null && hAcc > 50f) return false

        val now = s.timestampMs
        if (now - lastAcceptAt < 2_000) return false

        val prev = lastPt
        val moved = if (prev == null) Double.MAX_VALUE else distanceBetween(prev.lat, prev.lon, lat, lon)
        if (moved < 2.0 && now - lastAcceptAt < 15_000) return false

        if (prev != null && moved < 10_000) distanceM += moved

        val ele = s.altitude
        if (ele != null) {
            val lastEle = lastEleAccepted
            if (lastEle == null) {
                lastEleAccepted = ele
            } else if (abs(ele - lastEle) >= 2.0) {
                if (ele > lastEle) ascentM += ele - lastEle
                lastEleAccepted = ele
            }
        }

        points += Pt(lat, lon, ele, now)
        lastPt = points.last()
        lastAcceptAt = now
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
            """<gpx version="1.1" creator="Errarium Altimeter" xmlns="http://www.topografix.com/GPX/1/1">"""
        ).append('\n')
        sb.append("  <metadata><time>")
            .append(Instant.ofEpochMilli(if (startedAtMs > 0) startedAtMs else System.currentTimeMillis()))
            .append("</time></metadata>\n")
        sb.append("  <trk><name>Altimeter track</name><trkseg>\n")
        for (p in points) {
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

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val out = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, out)
        return out[0].toDouble()
    }
}
