package com.rekluzlabs.vaultcuisine.data

/**
 * Languages supported by the app. `ocrCodes` map to ML Kit text-recognition
 * models (ja/ko/zh use dedicated CJK models); the same list is reused for both
 * the OCR language setting and the recipe translation picker.
 */
object SupportedLanguages {
    data class Language(val code: String, val displayName: String)

    val all = listOf(
        Language("en", "English"),
        Language("fr", "French"),
        Language("de", "German"),
        Language("es", "Spanish"),
        Language("it", "Italian"),
        Language("ja", "Japanese"),
        Language("ko", "Korean"),
        Language("zh", "Chinese"),
    )
}