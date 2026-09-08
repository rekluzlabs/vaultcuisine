/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
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