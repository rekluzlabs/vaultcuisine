/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
package com.rekluzlabs.vaultcuisine.ai

/**
 * Strips API-key query params (and any other query params keyed `key=`) from
 * arbitrary message text so a full request URL can never reach the UI, a Toast,
 * or a log line. Applied at construction time in every exception below — the
 * single choke point all Gemini error strings flow through.
 */
fun sanitizeGeminiMessage(raw: String?): String {
    val text = raw ?: return "unexpected error"
    return text.replace(KEY_PARAM_REGEX, "")
}

private val KEY_PARAM_REGEX = Regex("""[?&]key=[^&\s]+""")

sealed class GeminiOcrException(message: String) : Exception(sanitizeGeminiMessage(message))

class MissingApiKeyException : GeminiOcrException("No Gemini API key configured")

class RateLimitException(val retryAfterSeconds: Int?) :
    GeminiOcrException("Gemini API rate limit exceeded")

class NetworkException(cause: Throwable) : GeminiOcrException("Network error: ${cause.message}")

class ApiException(val code: Int, message: String) : GeminiOcrException(message)

open class MalformedResponseException(message: String) : GeminiOcrException(message)

class TruncatedResponseException(message: String) : MalformedResponseException(message)

class NotARecipeException(val guessedTitle: String?) : GeminiOcrException(
    "Response indicates this is not a recipe: ${guessedTitle ?: "unknown"}"
)
