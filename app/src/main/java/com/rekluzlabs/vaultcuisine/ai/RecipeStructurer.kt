package com.rekluzlabs.vaultcuisine.ai

import com.rekluzlabs.vaultcuisine.data.Recipe
import com.rekluzlabs.vaultcuisine.data.RecipeIngredient
import com.rekluzlabs.vaultcuisine.data.RecipeStep
import java.util.UUID

/**
 * Turns raw OCR text into a structured Recipe. Multiple implementations:
 *  - GeminiNanoStructurer: on-device LLM via AICore (best quality, flagship devices only)
 *  - HeuristicStructurer: regex/pattern fallback (works everywhere, lower quality)
 *
 * Callers should run TextNormalizer.normalize() on raw OCR text before
 * passing it to either implementation — see MainViewModel.
 */
interface RecipeStructurer {
    suspend fun structure(rawText: String): Recipe
}

/**
 * Zone-isolated fallback parser: splits the (already normalized) text into
 * an ingredients zone and a steps zone up front, then runs extraction only
 * within the matching zone. This avoids the cross-contamination that a
 * single-pass line scan is prone to (e.g. a step sentence that happens to
 * start with a number getting misread as an ingredient).
 *
 * Guaranteed to return a usable Recipe even on a total parse failure —
 * see absoluteFallback() — so a scan never dead-ends the user with nothing
 * to edit.
 */
class HeuristicStructurer : RecipeStructurer {

    private val ingredientPattern = Regex(
        "^\\s*(\\d+(?:[./]\\d+)?\\s*(?:-\\s*\\d+(?:[./]\\d+)?)?)\\s*(cup|cups|tsp|tbsp|g|kg|ml|l|oz|lbs?|lb|cloves?|pinch(?:es)?|cans?|pieces?)?\\s+(.*)",
        RegexOption.IGNORE_CASE
    )
    private val newStepStartPattern = Regex("^\\d+[:.)]\\s*.*")
    private val stepKeywordPattern = Regex("(?i)\\bstep\\b")
    private val sectionHeaderPattern = Regex("(?i)^\\s*(ingredients|instructions|directions|method|steps)\\s*:?\\s*$")

    override suspend fun structure(rawText: String): Recipe {
        val normalized = TextNormalizer.normalize(rawText)
        val rawLines = normalized.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (rawLines.isEmpty()) return absoluteFallback(rawText)

        val ingredientLines = mutableListOf<String>()
        val stepLines = mutableListOf<String>()
        var inSteps = false

        // First short, non-numeric-leading line becomes the title candidate.
        val titleCandidate = rawLines.firstOrNull { line -> line.firstOrNull()?.isDigit() != true }
            ?.takeIf { it.length < 50 }
        val title = titleCandidate ?: "Scanned Recipe"

        val actionVerbs = setOf(
            "in", "whisk", "set", "combine", "mix", "add", "bake", "heat",
            "stir", "pour", "place", "preheat", "blend", "chop", "sift"
        )

        for (line in rawLines) {
            if (line == title) continue

            val header = sectionHeaderPattern.find(line)?.groupValues?.get(1)?.lowercase()
            if (header != null) {
                inSteps = header.startsWith("instr") || header.startsWith("direct") ||
                    header.startsWith("method") || header.startsWith("step")
                continue
            }

            // Zone splitter: once we see a step-like marker or semantic action verb start,
            // everything after is treated as steps, even without an explicit header.
            val cleanLine = line.trim().lowercase()
            val firstWord = cleanLine.split(" ").firstOrNull()?.replace(Regex("[^a-z]"), "") ?: ""
            val isActionVerbStart = actionVerbs.contains(firstWord) && cleanLine.length > 20
            val isNumberedStep = cleanLine.matches(Regex("^\\d+[:.)].*")) || cleanLine.startsWith("step")

            if (!inSteps && (stepKeywordPattern.containsMatchIn(line) || isNumberedStep || isActionVerbStart)) {
                inSteps = true
            }

            if (inSteps) stepLines.add(line) else ingredientLines.add(line)
        }

        val ingredients = extractIngredients(ingredientLines)
        val steps = extractSteps(stepLines)

        // If zone splitting produced nothing usable, don't silently return an
        // empty recipe — fall back to dumping normalized text into one step
        // so the user still has something to correct.
        if (ingredients.isEmpty() && steps.isEmpty()) {
            return absoluteFallback(rawText, title)
        }

        return Recipe(
            id = UUID.randomUUID().toString(),
            title = title,
            ingredients = ingredients,
            steps = steps
        )
    }

    private fun extractIngredients(lines: List<String>): List<RecipeIngredient> =
        lines.mapNotNull { line ->
            val match = ingredientPattern.find(line)
            when {
                match != null -> RecipeIngredient(
                    id = UUID.randomUUID().toString(),
                    amount = parseAmount(match.groupValues[1]),
                    unit = match.groupValues[2].ifBlank { null },
                    name = match.groupValues[3].trim().ifBlank { line }
                )
                line.length < 60 && !line.contains(".") -> RecipeIngredient(
                    id = UUID.randomUUID().toString(),
                    amount = null,
                    unit = null,
                    name = line
                )
                else -> null
            }
        }

    /** Merges continuation lines (wrapped sentences) into the step they belong to. */
    private fun extractSteps(lines: List<String>): List<RecipeStep> {
        val merged = mutableListOf<String>()
        var buffer = ""

        for (line in lines) {
            val isNewStepStart = newStepStartPattern.matches(line) || line.startsWith("step", ignoreCase = true)
            if (isNewStepStart) {
                if (buffer.isNotEmpty()) merged.add(buffer.trim())
                buffer = line
            } else if (buffer.isNotEmpty()) {
                buffer += " $line"
            } else {
                merged.add(line)
            }
        }
        if (buffer.isNotEmpty()) merged.add(buffer.trim())

        return merged.map { raw ->
            val cleaned = raw
                .replace(Regex("^\\s*\\d+[:.)]\\s*"), "")
                .replace(Regex("(?i)^step\\s*\\d+[:.]?\\s*"), "")
            RecipeStep(
                id = UUID.randomUUID().toString(),
                text = cleaned,
                timerSeconds = extractTimerSeconds(cleaned)
            )
        }
    }

    private fun parseAmount(raw: String): String? {
        val trimmed = raw.trim()
        return trimmed.ifBlank { null }
    }

    /** Sums minutes/seconds/hours mentioned in a step into one timer duration. */
    private fun extractTimerSeconds(text: String): Int? {
        val minuteMatch = Regex("(\\d+)\\s*(mins?|minutes?)", RegexOption.IGNORE_CASE).find(text)
        val secondMatch = Regex("(\\d+)\\s*(secs?|seconds?)", RegexOption.IGNORE_CASE).find(text)
        val hourMatch = Regex("(\\d+)\\s*(hrs?|hours?)", RegexOption.IGNORE_CASE).find(text)

        var totalSeconds = 0
        minuteMatch?.groupValues?.get(1)?.toIntOrNull()?.let { totalSeconds += it * 60 }
        secondMatch?.groupValues?.get(1)?.toIntOrNull()?.let { totalSeconds += it }
        hourMatch?.groupValues?.get(1)?.toIntOrNull()?.let { totalSeconds += it * 3600 }

        return totalSeconds.takeIf { it > 0 }
    }

    /**
     * Unbreakable fallback: if parsing produces nothing usable, hand the
     * user the normalized raw text as a single step with a note explaining
     * why, rather than a blank/broken recipe. ReviewEditScreen should
     * surface this notice prominently when notes != null.
     */
    private fun absoluteFallback(rawText: String, title: String = "Unparsed Recipe"): Recipe {
        val normalized = TextNormalizer.normalize(rawText)
        return Recipe(
            id = UUID.randomUUID().toString(),
            title = title,
            ingredients = emptyList(),
            steps = listOf(
                RecipeStep(id = UUID.randomUUID().toString(), text = normalized, timerSeconds = null)
            ),
            notes = "Couldn't automatically structure this scan. Please edit the fields above manually."
        )
    }
}
