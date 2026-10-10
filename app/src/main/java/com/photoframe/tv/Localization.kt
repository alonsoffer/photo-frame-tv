package com.photoframe.tv

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** A language the user can pick, shown with its flag and its own native name. */
data class AppLanguage(val code: String, val displayName: String, val flag: String)

/**
 * Languages the UI ships translations for. A blank stored code means "follow the device language";
 * picking any entry here pins the app to that language regardless of the device.
 */
val AppLanguages = listOf(
    AppLanguage("en", "English", "🇬🇧"),
    AppLanguage("iw", "עברית", "🇮🇱"),
    AppLanguage("ar", "العربية", "🇸🇦"),
    AppLanguage("es", "Español", "🇪🇸"),
    AppLanguage("fr", "Français", "🇫🇷"),
    AppLanguage("de", "Deutsch", "🇩🇪"),
    AppLanguage("ru", "Русский", "🇷🇺"),
)

/** Persists the chosen UI language in plain prefs so it can be read before the UI inflates. */
object LocaleStore {
    private const val PREFS = "app_locale"
    private const val KEY = "lang"

    fun getCode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun setCode(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
    }

    /**
     * On first launch (no language chosen yet), default to the device's language if we ship it,
     * else English — so the UI and the picker's selection agree from the start.
     */
    fun initIfUnset(context: Context) {
        if (getCode(context).isNotBlank()) return
        val deviceLang = Locale.getDefault().language // e.g. "iw", "ar", "en"
        val match = AppLanguages.firstOrNull { it.code == deviceLang }
            ?: AppLanguages.firstOrNull { it.code == "en" }
            ?: AppLanguages.first()
        setCode(context, match.code)
    }

    /** The currently selected language (falls back to English for the picker highlight). */
    fun current(context: Context): AppLanguage {
        val code = getCode(context)
        return AppLanguages.firstOrNull { it.code == code } ?: AppLanguages.first()
    }

    /** Wrap a base context so its resources resolve in the chosen language; no-op if none is set. */
    fun wrap(base: Context): Context {
        val code = getCode(base)
        if (code.isBlank()) return base
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
