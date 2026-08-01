package com.rekluzlabs.vaultcuisine.data

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    val ocrLanguage: String = "en",
    val autoOpenAfterScan: Boolean = true,
    val theme: String = "pantry",
    val defaultServings: Int = 4,
    val printPaperSize: String = "default",
    val geminiEnabled: Boolean = true,
    val showGeminiConsentDialog: Boolean = true,
    val geminiModelId: String = "gemini-3.7-flash",
    val alarmSoundUri: String = "",
    val homeTileOrder: List<String> = emptyList(),
    val pinnedHomeTiles: List<String> = emptyList(),
    val pinnedRecipeIds: List<String> = emptyList()
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
        geminiEnabled = prefs.getBoolean(KEY_GEMINI_ENABLED, true),
        showGeminiConsentDialog = prefs.getBoolean(KEY_GEMINI_CONSENT, true),
        geminiModelId = prefs.getString(KEY_GEMINI_MODEL, "gemini-3.7-flash") ?: "gemini-3.7-flash",
        alarmSoundUri = prefs.getString(KEY_ALARM_SOUND, "") ?: "",
        homeTileOrder = prefs.getString(KEY_HOME_TILE_ORDER, null)?.parseList() ?: emptyList(),
        pinnedHomeTiles = prefs.getString(KEY_PINNED_HOME_TILES, null)?.parseList() ?: emptyList(),
        pinnedRecipeIds = prefs.getString(KEY_PINNED_RECIPES, null)?.parseList() ?: emptyList()
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_OCR_LANG, settings.ocrLanguage)
            .putBoolean(KEY_AUTO_OPEN, settings.autoOpenAfterScan)
            .putString(KEY_THEME, settings.theme)
            .putInt(KEY_DEFAULT_SERVINGS, settings.defaultServings)
            .putString(KEY_PRINT_PAPER, settings.printPaperSize)
            .putBoolean(KEY_GEMINI_ENABLED, settings.geminiEnabled)
            .putBoolean(KEY_GEMINI_CONSENT, settings.showGeminiConsentDialog)
            .putString(KEY_GEMINI_MODEL, settings.geminiModelId)
            .putString(KEY_ALARM_SOUND, settings.alarmSoundUri)
            .putString(KEY_HOME_TILE_ORDER, settings.homeTileOrder.joinToString(","))
            .putString(KEY_PINNED_HOME_TILES, settings.pinnedHomeTiles.joinToString(","))
            .putString(KEY_PINNED_RECIPES, settings.pinnedRecipeIds.joinToString(","))
            .apply()
    }

    private fun String.parseList(): List<String> =
        split(',').map { it.trim() }.filter { it.isNotEmpty() }

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
        private const val KEY_GEMINI_ENABLED = "gemini_enabled"
        private const val KEY_GEMINI_CONSENT = "gemini_consent"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_ALARM_SOUND = "alarm_sound_uri"
        private const val KEY_HOME_TILE_ORDER = "home_tile_order"
        private const val KEY_PINNED_HOME_TILES = "pinned_home_tiles"
        private const val KEY_PINNED_RECIPES = "pinned_recipes"
    }
}
