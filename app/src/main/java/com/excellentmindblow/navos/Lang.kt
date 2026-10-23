package com.excellentmindblow.navos

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The app's language: English or Bahasa Melayu. "Auto" follows the phone.
 * One place, like [Units], so the screens, the route instructions and the
 * voice (inside NavigationService) all agree.
 */
object Lang {
    const val KEY = "app_language"
    const val AUTO = "auto"
    const val EN = "en"
    const val MS = "ms"
    private const val KEY_REOPEN_SETTINGS = "lang_reopen_settings"

    @Volatile
    var malay: Boolean = false
        private set

    fun choice(prefs: SharedPreferences): String = prefs.getString(KEY, AUTO) ?: AUTO

    fun load(prefs: SharedPreferences) {
        malay =
            when (choice(prefs)) {
                MS -> true
                EN -> false
                else -> Locale.getDefault().language == "ms"
            }
    }

    fun save(
        prefs: SharedPreferences,
        value: String,
    ) {
        prefs.edit().putString(KEY, value).apply()
        load(prefs)
        // The language switch re-creates the screen: come back to Settings.
        if (applyToApp(prefs)) prefs.edit().putBoolean(KEY_REOPEN_SETTINGS, true).apply()
    }

    /** True once, right after a language switch re-created the screen. */
    fun takeReopenSettings(prefs: SharedPreferences): Boolean {
        if (!prefs.getBoolean(KEY_REOPEN_SETTINGS, false)) return false
        prefs.edit().putBoolean(KEY_REOPEN_SETTINGS, false).apply()
        return true
    }

    /** Makes Android's own text (layouts, strings.xml) follow the choice too.
     * Re-creates the open screens when the language actually changes. */
    fun applyToApp(prefs: SharedPreferences): Boolean {
        val wanted =
            when (choice(prefs)) {
                MS -> LocaleListCompat.forLanguageTags("ms")
                EN -> LocaleListCompat.forLanguageTags("en")
                else -> LocaleListCompat.getEmptyLocaleList()
            }
        if (AppCompatDelegate.getApplicationLocales() == wanted) return false
        AppCompatDelegate.setApplicationLocales(wanted)
        return true
    }
}

/** The same text in English and everyday Malay; picks the app's language ([Lang]). */
fun tr(
    en: String,
    ms: String,
): String = if (Lang.malay) ms else en
