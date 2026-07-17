package com.rekluzlabs.vaultcuisine.ai

/**
 * Cleans raw OCR text before it goes into either the heuristic parser or
 * the AI structuring prompt. Handles the common OCR failure modes:
 * unicode fraction glyphs, digits/letters glued together, stray section
 * headers/page numbers/decorative separator lines, and a couple of
 * digit/letter confusions specific to units ("l" -> "1" before "g/oz/etc").
 */
object TextNormalizer {

    private val fractionMap = mapOf(
        "½" to "1/2", "⅓" to "1/3", "⅔" to "2/3", "¼" to "1/4",
        "¾" to "3/4", "⅕" to "1/5", "⅖" to "2/5", "⅗" to "3/5",
        "⅘" to "4/5", "⅙" to "1/6", "⅚" to "5/6", "⅛" to "1/8",
        "⅜" to "3/8", "⅝" to "5/8", "⅞" to "7/8"
    )

    // Lines that are pure section headers or decoration — dropped entirely
    // rather than treated as an ingredient/step, since HeuristicStructurer
    // already detects "Ingredients"/"Instructions" headers itself; this list
    // catches the noisier variants (page numbers, bullet/divider lines, attribution).
    private val noisePatterns = listOf(
        Regex("^\\d+\\s*$"),               // page numbers
        Regex("^[|•~\\-*+_]+$"),            // divider/bullet-only lines
        Regex("^adapted\\s+from.*", RegexOption.IGNORE_CASE),
        Regex("^eat\\s+what\\s+you.*", RegexOption.IGNORE_CASE)
    )

    fun normalize(rawOcrText: String): String {
        var processed = rawOcrText

        // Unicode fractions -> ascii, before anything else touches digits.
        fractionMap.forEach { (unicode, ascii) -> processed = processed.replace(unicode, ascii) }

        // Fix italic '2' being read as 'Z' or 'z' when directly preceding units (glued or spaced)
        processed = processed.replace(Regex("\\b[zZ](?=\\s*(cups?|tsps?|tbsps?|g|ml|oz|tbs?|pounds?))", RegexOption.IGNORE_CASE), "2 ")

        // Fix mangled fraction symbols like '#' or '3 1/2' getting read as '#3'
        processed = processed.replace(Regex("#\\s*(\\d)"), "$1")

        // Rejoin broken words split across lines by hyphens (e.g. "all-\npurpose")
        processed = processed.replace(Regex("-\\s*\\n\\s*"), "-")

        // Split digits glued to letters ("2eggs" -> "2 eggs", "cup2" -> "cup 2").
        processed = processed.replace(Regex("(\\d)([a-zA-Z])"), "$1 $2")
        processed = processed.replace(Regex("([a-zA-Z])(\\d)"), "$1 $2")

        // Normalize spacing around fraction slashes ("1 / 2" -> "1/2").
        processed = processed.replace(Regex("(\\d)\\s*/\\s*(\\d)"), "$1/$2")

        // Common OCR digit/letter confusion right before a unit token.
        processed = processed.replace(
            Regex("(?<=\\b|\\d)[lI](?=\\s*(g|oz|ml|tsp|tbsp|cup))", RegexOption.IGNORE_CASE), "1 "
        )
        processed = processed.replace(
            Regex("(?<=\\b|\\d)[oO](?=\\s*(g|oz|ml|tsp|tbsp|cup))"), "0 "
        )

        // Common recipe word / OCR typo corrections
        val replacements = mapOf(
            Regex("\\bcnL\\b", RegexOption.IGNORE_CASE) to "mL",
            Regex("\\bcnl\\b", RegexOption.IGNORE_CASE) to "mL",
            Regex("\\bbrond tou\\b", RegexOption.IGNORE_CASE) to "bread flour",
            Regex("\\bbrond\\b", RegexOption.IGNORE_CASE) to "bread",
            Regex("\\btou\\b", RegexOption.IGNORE_CASE) to "flour",
            Regex("\\blour\\b", RegexOption.IGNORE_CASE) to "flour",
            Regex("\\bpast\\b", RegexOption.IGNORE_CASE) to "yeast",
            Regex("\\bebpuros\\b", RegexOption.IGNORE_CASE) to "all-purpose",
            Regex("\\bseast\\b", RegexOption.IGNORE_CASE) to "yeast",
            Regex("\\bnixture\\b", RegexOption.IGNORE_CASE) to "mixture",
            Regex("\\bfoaniy\\b", RegexOption.IGNORE_CASE) to "foamy",
            Regex("\\bprl\\b", RegexOption.IGNORE_CASE) to "proof",
            Regex("\\bthut\\b", RegexOption.IGNORE_CASE) to "that",
            Regex("\\breasuring\\b", RegexOption.IGNORE_CASE) to "measuring",
            Regex("\\bbuwl\\b", RegexOption.IGNORE_CASE) to "bowl",
            Regex("\\bur\\b", RegexOption.IGNORE_CASE) to "or",
            Regex("\\bcupp\\b", RegexOption.IGNORE_CASE) to "cup",
            Regex("\\bwitlh\\b", RegexOption.IGNORE_CASE) to "with",
            Regex("\\bJtonk\\b", RegexOption.IGNORE_CASE) to "hook",
            Regex("\\beonbine\\b", RegexOption.IGNORE_CASE) to "combine"
        )

        replacements.forEach { (pattern, replacement) ->
            processed = processed.replace(pattern, replacement)
        }

        val cleanedLines = processed
            .lines()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() }

        return cleanedLines
            .filterNot { line -> noisePatterns.any { it.matches(line) } }
            .joinToString("\n")
    }
}
