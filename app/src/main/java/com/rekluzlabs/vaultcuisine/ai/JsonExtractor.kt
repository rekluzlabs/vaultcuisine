package com.rekluzlabs.vaultcuisine.ai

/**
 * Defensively pulls a JSON object out of raw LLM output, in case the model
 * wraps it in markdown fences or adds commentary despite being told not to.
 * Never trust an LLM to follow "return only JSON" literally.
 */
object JsonExtractor {
    fun extractJsonOrNull(rawOutput: String): String? {
        val start = rawOutput.indexOf('{')
        val end = rawOutput.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) {
            rawOutput.substring(start, end + 1)
        } else {
            null
        }
    }
}
