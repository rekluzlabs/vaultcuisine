/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
package com.rekluzlabs.vaultcuisine.ai

/**
 * Metadata for a specific Gemini model variant.
 */
data class GeminiModelVariant(
    val id: String,
    val displayName: String,
    val description: String
)

/**
 * Supported Google Gemini models and instructions for obtaining an API key.
 */
object GeminiModels {
    val variants = listOf(
        GeminiModelVariant(
            id = "gemini-3.7-flash",
            displayName = "Gemini 3.7 Flash",
            description = "Latest flagship Flash model for the best scanning quality."
        ),
        GeminiModelVariant(
            id = "gemini-3.6-flash",
            displayName = "Gemini 3.6 Flash",
            description = "Stable, balanced flagship Flash model for agentic and multimodal tasks."
        ),
        GeminiModelVariant(
            id = "gemini-3.5-flash",
            displayName = "Gemini 3.5 Flash",
            description = "Stable, optimized for speed and high-volume agentic workflows."
        ),
        GeminiModelVariant(
            id = "gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash-Lite",
            description = "Ultra-fast, lowest footprint for simple scanning."
        ),
        GeminiModelVariant(
            id = "gemini-3.1-flash-lite",
            displayName = "Gemini 3.1 Flash-Lite",
            description = "Reliable stable model. Best for the Free Tier."
        )
    )

    const val DEFAULT_MODEL_ID = "gemini-3.7-flash"

    const val KEY_OBTAIN_URL = "https://aistudio.google.com/apikey"

    const val KEY_INSTRUCTIONS = "1. Go to Google AI Studio (aistudio.google.com).\n" +
            "2. Sign in with your Google account.\n" +
            "3. Open left tab menu & Click the key icon \n(\"Get API key\") in the bottom-left corner.\n" +
            "4. Click \"Create API key\"\n" +
            "5. Copy your generated key and paste it here.\n\n" +
            "✓ Free tier available — no credit card required for standard free limits."
}
