package com.rekluzlabs.vaultcuisine.ui.components

/**
 * Handwriting OCR frequently reads two adjacent ingredient lines as one
 * ("1 cup sugar 2 eggs"). This splits on a lookahead for a new number
 * preceded by a space, so the review screen can offer a "Split Row" action
 * when a merged line is detected (heuristic: the ingredient name itself
 * contains a digit).
 */
object UiParsingHelpers {
    fun splitMergedIngredient(rawLine: String): List<String> =
        rawLine.split(Regex("(?=\\s\\d+\\s)"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
