package com.chelmodeev.altimeter.place

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Название места: сначала системный Geocoder, при неудаче — Nominatim (OSM).
 * Работает и на телефонах без Google-сервисов. Запросы жёстко троттлятся.
 */
class PlaceResolver(private val context: Context) {

    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastAt = 0L
    private var lastName: String? = null

    suspend fun resolve(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val now = SystemClock.elapsedRealtime()
        val cached = lastName
        if (cached != null) {
            val results = FloatArray(1)
            Location.distanceBetween(lastLat, lastLon, lat, lon, results)
            if (now - lastAt < 60_000 || results[0] < 300f) return@withContext cached
        }
        val name = fromGeocoder(lat, lon)?.takeIf(::isReadableName)
            ?: fromNominatim(lat, lon)?.takeIf(::isReadableName)
        if (name != null) {
            lastName = name
            lastLat = lat
            lastLon = lon
            lastAt = now
        }
        name ?: cached
    }

    private fun fromGeocoder(lat: Double, lon: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return@runCatching null
        @Suppress("DEPRECATION")
        val a = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            ?: return@runCatching null
        val main = a.locality ?: a.subAdminArea ?: a.adminArea
        val detail = a.subLocality ?: a.thoroughfare
        listOfNotNull(main, detail).distinct().joinToString(", ").ifBlank { null }
    }.getOrNull()

    private fun fromNominatim(lat: Double, lon: Double): String? = runCatching {
        val lang = Locale.getDefault().language.ifBlank { "ru" }
        val url = URL(
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2" +
                "&lat=$lat&lon=$lon&zoom=14&accept-language=$lang,en"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty(
            "User-Agent",
            "Errarium-Altimeter/1.5 (Android; errarium.ai@gmail.com)"
        )
        conn.connectTimeout = 6_000
        conn.readTimeout = 6_000
        try {
            val body = conn.inputStream.bufferedReader().readText()
            val obj = JSONObject(body)
            val addr = obj.optJSONObject("address")
            fun pick(vararg keys: String): String? {
                if (addr == null) return null
                for (k in keys) {
                    val v = addr.optString(k)
                    if (v.isNotBlank()) return v
                }
                return null
            }
            val main = pick("city", "town", "village", "municipality", "county", "state")
            val detail = pick("suburb", "hamlet", "neighbourhood", "isolated_dwelling", "peak")
            listOfNotNull(main, detail).distinct().joinToString(", ")
                .ifBlank { obj.optString("name").ifBlank { null } }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun isReadableName(value: String): Boolean {
        if (value.isBlank()) return false
        val preferredLanguage = Locale.getDefault().language
        if (preferredLanguage !in setOf("ru", "uk", "be", "en")) return true
        return value.none { ch ->
            when (Character.UnicodeScript.of(ch.code)) {
                Character.UnicodeScript.HAN,
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA,
                Character.UnicodeScript.HANGUL -> true
                else -> false
            }
        }
    }
}
