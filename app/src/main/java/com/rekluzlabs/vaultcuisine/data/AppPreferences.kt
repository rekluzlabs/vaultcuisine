package com.rekluzlabs.vaultcuisine.data

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    val ocrLanguage: String = "en",
    val autoOpenAfterScan: Boolean = true,
    val theme: String = "pantry",
    val defaultServings: Int = 4,
    val printPaperSize: String = "default",
    val geminiConsentAccepted: Boolean = false,
    val geminiModelId: String = "gemini-2.5-flash"
)

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        ocrLanguage = prefs.getString(KEY_OCR_LANG, "en") ?: "en",
        autoOpenAfterScan = prefs.getBoolean(KEY_AUTO_OPEN, true),
        theme = prefs.getString(KEY_THEME, "pantry") ?: "pantry",
        defaultServings = prefs.getInt(KEY_DEFAULT_SERVINGS, 4),
        printPaperSize = prefs.getString(KEY_PRINT_PAPER, "default") ?: "default",
        geminiConsentAccepted = prefs.getBoolean(KEY_GEMINI_CONSENT, false),
        geminiModelId = prefs.getString(KEY_GEMINI_MODEL, "gemini-2.5-flash") ?: "gemini-2.5-flash"
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_OCR_LANG, settings.ocrLanguage)
            .putBoolean(KEY_AUTO_OPEN, settings.autoOpenAfterScan)
            .putString(KEY_THEME, settings.theme)
            .putInt(KEY_DEFAULT_SERVINGS, settings.defaultServings)
            .putString(KEY_PRINT_PAPER, settings.printPaperSize)
            .putBoolean(KEY_GEMINI_CONSENT, settings.geminiConsentAccepted)
            .putString(KEY_GEMINI_MODEL, settings.geminiModelId)
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
        private const val KEY_GEMINI_CONSENT = "gemini_consent"
        private const val KEY_GEMINI_MODEL = "gemini_model"
    }
}
