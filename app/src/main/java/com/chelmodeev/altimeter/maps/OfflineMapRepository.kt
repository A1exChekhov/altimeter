package com.chelmodeev.altimeter.maps

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.chelmodeev.altimeter.model.OfflineMapRegion
import com.chelmodeev.altimeter.model.OfflineMapPackage
import com.chelmodeev.altimeter.model.OfflineMapsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray

/**
 * Хранилище больших региональных карт. Файлы находятся не в кэше, поэтому
 * обновление APK их не удаляет. Android удалит их только вместе с приложением.
 */
class OfflineMapRepository(private val context: Context) {
    private val directory = context.getExternalFilesDir("maps") ?: File(context.filesDir, "maps")
    private val preferences = context.getSharedPreferences("offline_maps", Context.MODE_PRIVATE)

    fun snapshot(importing: Boolean = false, error: String? = null): OfflineMapsState {
        directory.mkdirs()
        val activeId = preferences.getString(KEY_ACTIVE, null)
        val installed = directory.listFiles { file ->
            file.isFile && file.extension.equals("pmtiles", ignoreCase = true) &&
                !file.name.endsWith("-dem.pmtiles", ignoreCase = true)
        }.orEmpty()
            .sortedBy { it.name.lowercase() }
            .map { file ->
                OfflineMapRegion(
                    id = file.name,
                    title = file.nameWithoutExtension.replace('_', ' ').replace('-', ' '),
                    path = file.absolutePath,
                    sizeBytes = file.length() + terrainFile(file).takeIf(File::isFile)
                        .let { it?.length() ?: 0L },
                    sha256 = sidecar(file).takeIf(File::isFile)?.readText()?.trim(),
                    terrainPath = terrainFile(file).takeIf(File::isFile)?.absolutePath,
                    active = file.name == activeId,
                )
            }
        val active = installed.firstOrNull { it.active }
        if (activeId != null && active == null) preferences.edit().remove(KEY_ACTIVE).apply()
        return OfflineMapsState(
            installed = installed,
            catalog = loadCatalog(),
            activePath = active?.path,
            importing = importing,
            error = error,
        )
    }

    suspend fun download(packageId: String): OfflineMapsState = withContext(Dispatchers.IO) {
        val item = loadCatalog().firstOrNull { it.id == packageId }
            ?: error("Регион отсутствует в каталоге")
        directory.mkdirs()
        val destination = File(directory, item.fileName)
        downloadFile(item.downloadUrl, item.sha256, destination)
        val terrainName = item.terrainFileName
        val terrainUrl = item.terrainDownloadUrl
        val terrainSha = item.terrainSha256
        if (terrainName != null && terrainUrl != null && terrainSha != null) {
            downloadFile(terrainUrl, terrainSha, File(directory, terrainName))
        }
        preferences.edit().putString(KEY_ACTIVE, destination.name).apply()
        snapshot()
    }

    suspend fun import(uri: Uri): OfflineMapsState = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val displayName = queryDisplayName(uri)
        val base = displayName
            .removeSuffix(".pmtiles")
            .replace(Regex("[^\\p{L}\\p{N}._-]+"), "-")
            .trim('-', '.', '_')
            .take(80)
            .ifBlank { "region-${System.currentTimeMillis()}" }
        var destination = File(directory, "$base.pmtiles")
        if (destination.exists()) {
            destination = File(directory, "$base-${System.currentTimeMillis()}.pmtiles")
        }
        val partial = File(directory, ".${destination.name}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Не удалось открыть выбранный файл")
            validatePmTiles(partial)
            check(partial.renameTo(destination)) { "Не удалось сохранить карту" }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            sidecar(destination).writeText(sha256)
            preferences.edit().putString(KEY_ACTIVE, destination.name).apply()
            snapshot()
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    fun activate(id: String?): OfflineMapsState {
        if (id == null) {
            preferences.edit().remove(KEY_ACTIVE).apply()
        } else {
            require(File(directory, id).isFile) { "Карта не найдена" }
            preferences.edit().putString(KEY_ACTIVE, id).apply()
        }
        return snapshot()
    }

    fun delete(id: String): OfflineMapsState {
        val file = File(directory, id)
        require(file.parentFile?.canonicalFile == directory.canonicalFile) { "Некорректное имя карты" }
        if (file.isFile) {
            check(file.delete()) { "Не удалось удалить карту" }
            sidecar(file).delete()
            terrainFile(file).let { terrain ->
                if (terrain.isFile) check(terrain.delete()) { "Не удалось удалить рельеф" }
                sidecar(terrain).delete()
            }
        }
        if (preferences.getString(KEY_ACTIVE, null) == id) {
            preferences.edit().remove(KEY_ACTIVE).apply()
        }
        return snapshot()
    }

    private fun queryDisplayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull().orEmpty().ifBlank { "region.pmtiles" }

    private fun validatePmTiles(file: File) {
        require(file.length() >= 127) { "Файл карты пуст или повреждён" }
        val header = ByteArray(8)
        FileInputStream(file).use { input ->
            require(input.read(header) == header.size) { "Не удалось прочитать заголовок карты" }
        }
        val magic = header.copyOfRange(0, 7).toString(Charsets.US_ASCII)
        require(magic == "PMTiles" && header[7].toInt() == 3) {
            "Нужен файл PMTiles версии 3"
        }
    }

    private fun sidecar(file: File) = File(file.parentFile, "${file.name}.sha256")

    private fun terrainFile(baseMap: File) = File(
        baseMap.parentFile,
        "${baseMap.nameWithoutExtension}-dem.pmtiles",
    )

    private fun downloadFile(url: String, expectedSha: String, destination: File) {
        val partial = File(destination.parentFile, ".${destination.name}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Errarium-Altimeter/1.5")
            }
            try {
                connection.inputStream.use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            validatePmTiles(partial)
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualSha.equals(expectedSha, ignoreCase = true)) {
                "Контрольная сумма карты не совпала"
            }
            if (destination.exists()) check(destination.delete()) { "Не удалось обновить карту" }
            check(partial.renameTo(destination)) { "Не удалось сохранить карту" }
            sidecar(destination).writeText(actualSha)
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    private fun loadCatalog(): List<OfflineMapPackage> = runCatching {
        val json = context.assets.open("offline_map_catalog.json")
            .bufferedReader()
            .use { it.readText() }
        val array = JSONArray(json)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            OfflineMapPackage(
                id = item.getString("id"),
                title = item.getString("title"),
                description = item.getString("description"),
                fileName = item.getString("fileName"),
                downloadUrl = item.getString("downloadUrl"),
                sizeBytes = item.getLong("sizeBytes"),
                sha256 = item.getString("sha256"),
                terrainFileName = item.optString("terrainFileName").takeIf { it.isNotBlank() },
                terrainDownloadUrl = item.optString("terrainDownloadUrl").takeIf { it.isNotBlank() },
                terrainSizeBytes = item.optLong("terrainSizeBytes", 0L),
                terrainSha256 = item.optString("terrainSha256").takeIf { it.isNotBlank() },
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY_ACTIVE = "active_region"
    }
}
