package com.rekluzlabs.vaultcuisine.data

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    val ocrLanguage: String = "en",
    val autoOpenAfterScan: Boolean = true,
    val theme: String = "dark",
    val defaultServings: Int = 4,
    val printPaperSize: String = "default"
)

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        ocrLanguage = prefs.getString(KEY_OCR_LANG, "en") ?: "en",
        autoOpenAfterScan = prefs.getBoolean(KEY_AUTO_OPEN, true),
        theme = prefs.getString(KEY_THEME, "dark") ?: "dark",
        defaultServings = prefs.getInt(KEY_DEFAULT_SERVINGS, 4),
        printPaperSize = prefs.getString(KEY_PRINT_PAPER, "default") ?: "default"
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_OCR_LANG, settings.ocrLanguage)
            .putBoolean(KEY_AUTO_OPEN, settings.autoOpenAfterScan)
            .putString(KEY_THEME, settings.theme)
            .putInt(KEY_DEFAULT_SERVINGS, settings.defaultServings)
            .putString(KEY_PRINT_PAPER, settings.printPaperSize)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "vaultcuisine_prefs"
        private const val KEY_OCR_LANG = "ocr_language"
        private const val KEY_AUTO_OPEN = "auto_open"
        private const val KEY_THEME = "theme"
        private const val KEY_DEFAULT_SERVINGS = "default_servings"
        private const val KEY_PRINT_PAPER = "print_paper"
    }
}
