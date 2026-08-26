package com.chelmodeev.altimeter.localization

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AppLanguage {
    const val SYSTEM = ""
    const val RUSSIAN = "ru"
    const val ENGLISH = "en"
    const val CHINESE_SIMPLIFIED = "zh-CN"
    const val FRENCH = "fr"

    val supportedTags = listOf(SYSTEM, RUSSIAN, ENGLISH, CHINESE_SIMPLIFIED, FRENCH)

    private const val PREFS = "app_language"
    private const val KEY_TAG = "language_tag"

    fun currentTag(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            return locales.get(0)?.toLanguageTag().orEmpty().normalizeTag()
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, SYSTEM)
            .orEmpty()
            .normalizeTag()
    }

    fun set(activity: Activity, tag: String) {
        val normalized = tag.normalizeTag()
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (normalized.isEmpty()) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(normalized)
            return
        }

        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAG, normalized)
            .apply()
        updateLegacyResources(activity.applicationContext, normalized)
        activity.recreate()
    }

    fun syncProcessLocale(context: Context) {
        val locales = context.resources.configuration.locales
        if (!locales.isEmpty) Locale.setDefault(locales[0])
    }

    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val tag = currentTag(context)
        if (tag.isEmpty()) return context
        val locale = Locale.forLanguageTag(tag)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        return context.createConfigurationContext(configuration)
    }

    @Suppress("DEPRECATION")
    private fun updateLegacyResources(context: Context, tag: String) {
        val configuration = Configuration(context.resources.configuration)
        if (tag.isEmpty()) {
            configuration.setLocales(LocaleList.getDefault())
        } else {
            val locale = Locale.forLanguageTag(tag)
            configuration.setLocale(locale)
            configuration.setLocales(LocaleList(locale))
        }
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }

    private fun String.normalizeTag(): String = when {
        startsWith("ru", ignoreCase = true) -> RUSSIAN
        startsWith("zh", ignoreCase = true) -> CHINESE_SIMPLIFIED
        startsWith("fr", ignoreCase = true) -> FRENCH
        startsWith("en", ignoreCase = true) -> ENGLISH
        else -> SYSTEM
    }
}
