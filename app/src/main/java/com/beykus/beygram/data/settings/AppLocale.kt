package com.beykus.beygram.data.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** One app language independent of TDLib's server-language setting. */
internal object AppLocale {
    private const val PREF = "beygram-app-locale"
    private const val KEY = "language"
    const val SYSTEM = ""
    const val RUSSIAN = "ru"
    const val ENGLISH = "en"
    val supported = listOf(SYSTEM, RUSSIAN, ENGLISH)

    fun selected(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, SYSTEM).orEmpty()
            .takeIf { it in supported } ?: SYSTEM

    fun context(base: Context): Context {
        val language = selected(base)
        if (language.isEmpty() || Build.VERSION.SDK_INT >= 33) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        return base.createConfigurationContext(config)
    }

    fun update(context: Context, language: String) {
        require(language in supported)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, language).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (language.isEmpty()) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(language)
        }
    }
}
