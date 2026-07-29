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
            id = "gemini-3.5-pro",
            displayName = "Gemini 3.5 Pro",
            description = "Flagship reasoning model for complex tasks."
        ),
        GeminiModelVariant(
            id = "gemini-3.5-flash",
            displayName = "Gemini 3.5 Flash",
            description = "Optimized for speed and high-volume agentic workflows."
        ),
        GeminiModelVariant(
            id = "gemini-3.1-flash-lite",
            displayName = "Gemini 3.1 Flash-Lite",
            description = "Ultra-fast, lowest footprint for simple scanning."
        ),
        GeminiModelVariant(
            id = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            description = "Reliable stable model. Best for the Free Tier."
        )
    )

    const val DEFAULT_MODEL_ID = "gemini-2.5-flash"

    const val KEY_OBTAIN_URL = "https://aistudio.google.com/apikey"

    const val KEY_INSTRUCTIONS = "1. Go to Google AI Studio (aistudio.google.com).\n" +
            "2. Sign in with your Google account.\n" +
            "3. Click \"Create API key\" and copy the key.\n" +
            "4. Paste it here.\n\n" +
            "✓ Free tier available — no credit card needed to start."
}
