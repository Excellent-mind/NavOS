package com.excellentmindblow.navos

import android.content.SharedPreferences
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
    }
}
