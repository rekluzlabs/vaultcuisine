package com.rekluzlabs.vaultcuisine.ai

object TextNormalizer {

    private val fractionMap = mapOf(
        "½" to "1/2", "⅓" to "1/3", "⅔" to "2/3", "¼" to "1/4",
        "¾" to "3/4", "⅕" to "1/5", "⅖" to "2/5", "⅗" to "3/5",
        "⅘" to "4/5", "⅙" to "1/6", "⅚" to "5/6", "⅛" to "1/8",
        "⅜" to "3/8", "⅝" to "5/8", "⅞" to "7/8"
    )

    private val noisePatterns = listOf(
        Regex("^\\d+\\s*$"),
        Regex("^[|•~\\-*+_]+$"),
        Regex("^adapted\\s+from.*", RegexOption.IGNORE_CASE),
        Regex("^eat\\s+what\\s+you.*", RegexOption.IGNORE_CASE)
    )

    fun normalize(rawOcrText: String): String {
        var processed = rawOcrText

        fractionMap.forEach { (unicode, ascii) -> processed = processed.replace(unicode, ascii) }

        processed = processed.replace(Regex("\\b[zZ](?=\\s*(cups?|tsps?|tbsps?|g|ml|oz|tbs?|pounds?))", RegexOption.IGNORE_CASE), "2 ")

        processed = processed.replace(Regex("#\\s*(\\d)"), "$1")

        processed = processed.replace(Regex("-\\s*\\n\\s*"), "-")

        processed = processed.replace(Regex("(\\d)([a-zA-Z])"), "$1 $2")
        processed = processed.replace(Regex("([a-zA-Z])(\\d)"), "$1 $2")

        processed = processed.replace(Regex("(\\d)\\s*/\\s*(\\d)"), "$1/$2")

        processed = processed.replace(
            Regex("(?<=\\b|\\d)[lI](?=\\s*(g|oz|ml|tsp|tbsp|cup))", RegexOption.IGNORE_CASE), "1 "
        )
        processed = processed.replace(
            Regex("(?<=\\b|\\d)[oO](?=\\s*(g|oz|ml|tsp|tbsp|cup))"), "0 "
        )

        val cleanedLines = processed
            .lines()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() }

        return cleanedLines
            .filterNot { line -> noisePatterns.any { it.matches(line) } }
            .joinToString("\n")
    }
}
