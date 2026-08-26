package com.chelmodeev.altimeter

import android.app.Application
import android.content.Context
import com.chelmodeev.altimeter.localization.AppLanguage
import org.maplibre.android.MapLibre
import org.osmdroid.config.Configuration
import java.io.File

class AltimeterApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppLanguage.syncProcessLocale(this)
        MapLibre.getInstance(this)
        val cfg = Configuration.getInstance()
        cfg.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        cfg.userAgentValue = packageName
        // noBackupFilesDir не очищается Android как обычный cacheDir: просмотренные
        // участки карты остаются доступны без сети до очистки данных/удаления приложения.
        val offlineMaps = File(noBackupFilesDir, "offline-maps")
        cfg.osmdroidBasePath = offlineMaps
        cfg.osmdroidTileCache = File(offlineMaps, "tiles")
        cfg.tileFileSystemCacheMaxBytes = 512L * 1024L * 1024L
        cfg.tileFileSystemCacheTrimBytes = 450L * 1024L * 1024L
    }
}
