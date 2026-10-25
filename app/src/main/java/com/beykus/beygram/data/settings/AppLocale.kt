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
    const val GERMAN = "de"
    const val SPANISH = "es"
    const val FRENCH = "fr"
    val supported = listOf(SYSTEM, RUSSIAN, ENGLISH, GERMAN, SPANISH, FRENCH)

    /** Android 13+ system App Languages can change outside BeyGram. The OS owns
     * the effective app locale; SharedPreferences is the fallback only when unavailable. */
    fun selected(context: Context): String {
        val saved = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM).orEmpty()
        val systemAppLocale = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.let { locales ->
                if (locales.isEmpty) "" else locales[0]?.toLanguageTag().orEmpty()
            }
        } else null
        return resolveAppLanguage(systemAppLocale, saved)
    }

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

/**
 * null: platform per-app language unavailable, preserve a supported stored preference.
 * empty: Android explicitly chose "Follow system", even if an old preference remains.
 * Unknown locales fall back to system instead of showing a false Russian/English choice.
 */
internal fun resolveAppLanguage(platformTag: String?, storedTag: String): String {
    val chosen = platformTag?.substringBefore('-')?.substringBefore('_') ?: storedTag
    return chosen.takeIf { it in AppLocale.supported } ?: AppLocale.SYSTEM
}
