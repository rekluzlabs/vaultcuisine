package com.rekluzlabs.vaultcuisine.ai

sealed class GeminiOcrException(message: String) : Exception(message)

class MissingApiKeyException : GeminiOcrException("No Gemini API key configured")

class RateLimitException : GeminiOcrException("Gemini API rate limit exceeded")

class NetworkException(cause: Throwable) : GeminiOcrException("Network error: ${cause.message}")

class ApiException(val code: Int, message: String) : GeminiOcrException(message)

class MalformedResponseException(message: String) : GeminiOcrException(message)

class NotARecipeException(val guessedTitle: String?) : GeminiOcrException(
    "Response indicates this is not a recipe: ${guessedTitle ?: "unknown"}"
)
