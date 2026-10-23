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
        applyToApp(prefs)
    }

    /** Makes Android's own text (layouts, strings.xml) follow the choice too.
     * Re-creates the open screens when the language actually changes. */
    fun applyToApp(prefs: SharedPreferences) {
        val wanted =
            when (choice(prefs)) {
                MS -> LocaleListCompat.forLanguageTags("ms")
                EN -> LocaleListCompat.forLanguageTags("en")
                else -> LocaleListCompat.getEmptyLocaleList()
            }
        if (AppCompatDelegate.getApplicationLocales() != wanted) AppCompatDelegate.setApplicationLocales(wanted)
    }
}

/** The same text in English and everyday Malay; picks the app's language ([Lang]). */
fun tr(
    en: String,
    ms: String,
): String = if (Lang.malay) ms else en
