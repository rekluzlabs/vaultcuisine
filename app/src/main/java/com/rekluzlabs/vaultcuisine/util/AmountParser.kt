package com.rekluzlabs.vaultcuisine.util

import kotlin.math.abs
import kotlin.math.roundToLong

object AmountParser {

    fun parseAmounts(amount: String): List<Double> {
        val trimmed = amount.trim()
        if (trimmed.isEmpty()) return emptyList()

        val range = Regex("""^(\d+(?:\.\d+)?(?:/\d+)?(?:\s+\d+/\d+)?)\s*[-–]\s*(\d+(?:\.\d+)?(?:/\d+)?(?:\s+\d+/\d+)?)$""")
            .find(trimmed)
        if (range != null) {
            val a = parseSingle(range.groupValues[1]) ?: return emptyList()
            val b = parseSingle(range.groupValues[2]) ?: return emptyList()
            return listOf(a, b)
        }

        val single = parseSingle(trimmed)
        if (single != null) return listOf(single)

        return emptyList()
    }

    private fun parseSingle(token: String): Double? {
        val t = token.trim()
        if (t.isEmpty()) return null

        val mixed = Regex("""^(\d+)\s+(\d+)/(\d+)$""").find(t)
        if (mixed != null) {
            val whole = mixed.groupValues[1].toDoubleOrNull() ?: return null
            val num = mixed.groupValues[2].toDoubleOrNull() ?: return null
            val den = mixed.groupValues[3].toDoubleOrNull() ?: return null
            if (den == 0.0) return null
            return whole + num / den
        }

        val frac = Regex("""^(\d+)/(\d+)$""").find(t)
        if (frac != null) {
            val num = frac.groupValues[1].toDoubleOrNull() ?: return null
            val den = frac.groupValues[2].toDoubleOrNull() ?: return null
            if (den == 0.0) return null
            return num / den
        }

        val dec = t.toDoubleOrNull()
        if (dec != null) return dec

        return null
    }

    fun formatAmount(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return value.toString()

        val rounded = (value * 64.0).roundToLong() / 64.0

        val whole = rounded.toLong()
        val frac = abs(rounded - whole)

        if (frac < 0.001) return whole.toString()

        val (num, den) = approxFraction(frac)
        if (num == 0L) return whole.toString()

        return if (whole == 0L) {
            "$num/$den"
        } else {
            "$whole $num/$den"
        }
    }

    private fun approxFraction(value: Double): Pair<Long, Long> {
        val candidates = listOf(
            1L to 2L,
            1L to 3L, 2L to 3L,
            1L to 4L, 3L to 4L,
            1L to 8L, 3L to 8L, 5L to 8L, 7L to 8L
        )
        var best = candidates.first()
        var bestDiff = Double.MAX_VALUE
        for ((n, d) in candidates) {
            val diff = abs(value - n.toDouble() / d)
            if (diff < bestDiff) {
                bestDiff = diff
                best = n to d
            }
        }
        return best
    }

    fun scaleAndFormat(original: String, factor: Double): String {
        val amounts = parseAmounts(original)
        if (amounts.isEmpty()) return original
        return amounts.joinToString("-") { formatAmount(it * factor) }
    }
}
