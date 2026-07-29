package com.rekluzlabs.vaultcuisine.util

import kotlin.math.roundToInt

enum class UnitSystem {
    AS_WRITTEN, METRIC, IMPERIAL
}

enum class UnitCategory { VOLUME, WEIGHT, COUNT }

/**
 * Canonical units after normalization. The converter only knows these —
 * any input unit string is mapped to one of these or left unconverted.
 */
enum class CanonicalUnit(val category: UnitCategory) {
    CUP(UnitCategory.VOLUME),
    TBSP(UnitCategory.VOLUME),
    TSP(UnitCategory.VOLUME),
    ML(UnitCategory.VOLUME),
    L(UnitCategory.VOLUME),
    FL_OZ(UnitCategory.VOLUME),
    G(UnitCategory.WEIGHT),
    KG(UnitCategory.WEIGHT),
    OZ(UnitCategory.WEIGHT),
    LB(UnitCategory.WEIGHT);
}

object UnitConverter {

    // ── Normalization: free-form string → CanonicalUnit ──

    fun canonicalizeUnit(raw: String?): CanonicalUnit? {
        if (raw == null) return null
        val t = raw.trim().lowercase()
        return when (t) {
            "cup", "cups", "c" -> CanonicalUnit.CUP
            "tbsp", "tablespoon", "tablespoons", "tbs" -> CanonicalUnit.TBSP
            "tsp", "teaspoon", "teaspoons" -> CanonicalUnit.TSP
            "ml", "milliliter", "milliliters", "millilitre", "millilitres" -> CanonicalUnit.ML
            "l", "liter", "liters", "litre", "litres" -> CanonicalUnit.L
            "fl oz", "fl. oz", "fluid ounce", "fluid ounces", "floz" -> CanonicalUnit.FL_OZ
            "g", "gram", "grams" -> CanonicalUnit.G
            "kg", "kilogram", "kilograms", "kilogramme", "kilogrammes" -> CanonicalUnit.KG
            "oz", "ounce", "ounces" -> CanonicalUnit.OZ
            "lb", "lbs", "lb.", "pound", "pounds" -> CanonicalUnit.LB
            else -> null
        }
    }

    fun classifyUnit(raw: String?): UnitCategory {
        val canon = canonicalizeUnit(raw)
        return if (canon != null) canon.category else UnitCategory.COUNT
    }

    // ── Conversion factors (US customary) ──
    // Sources:
    //   NIST SP 811 (https://www.nist.gov/pml/special-publication-811)
    //   1 cup (US) = 236.5882365 mL  (rounded to 236.588 for display)
    //   1 tbsp (US) = 14.7867648 mL  (rounded to 14.787)
    //   1 tsp (US)  =  4.9289216 mL  (rounded to  4.929)
    //   1 fl oz (US)= 29.5735296 mL
    //   1 oz (avdp) = 28.3495231 g   (rounded to 28.350)
    //   1 lb (avdp) = 453.59237   g

    private const val CUP_TO_ML = 236.588
    private const val TBSP_TO_ML = 14.787
    private const val TSP_TO_ML = 4.929
    private const val FL_OZ_TO_ML = 29.574
    private const val OZ_TO_G = 28.350
    private const val LB_TO_G = 453.592

    // ── Conversion: canonical value + unit → target system ──

    data class ConversionResult(
        val value: Double,
        val unit: CanonicalUnit
    )

    fun convert(value: Double, from: CanonicalUnit, toSystem: UnitSystem): ConversionResult? {
        if (toSystem == UnitSystem.AS_WRITTEN) return null
        val targetIsMetric = toSystem == UnitSystem.METRIC
        val sourceIsMetric = from in listOf(CanonicalUnit.ML, CanonicalUnit.L, CanonicalUnit.G, CanonicalUnit.KG)

        if (sourceIsMetric == targetIsMetric) return null

        return when (from.category) {
            UnitCategory.VOLUME -> convertVolume(value, from, targetIsMetric)
            UnitCategory.WEIGHT -> convertWeight(value, from, targetIsMetric)
            UnitCategory.COUNT -> null
        }
    }

    private fun convertVolume(value: Double, from: CanonicalUnit, toMetric: Boolean): ConversionResult? {
        if (toMetric) {
            val ml = when (from) {
                CanonicalUnit.CUP -> value * CUP_TO_ML
                CanonicalUnit.TBSP -> value * TBSP_TO_ML
                CanonicalUnit.TSP -> value * TSP_TO_ML
                CanonicalUnit.FL_OZ -> value * FL_OZ_TO_ML
                else -> return null
            }
            return if (ml >= 1000.0) {
                ConversionResult(roundVolume(ml / 1000.0, isLiters = true), CanonicalUnit.L)
            } else {
                ConversionResult(roundVolume(ml, isLiters = false), CanonicalUnit.ML)
            }
        } else {
            // metric → imperial volume
            val ml = when (from) {
                CanonicalUnit.ML -> value
                CanonicalUnit.L -> value * 1000.0
                else -> return null
            }
            // Choose the most natural imperial unit
            val cups = ml / CUP_TO_ML
            return if (cups >= 0.25) {
                ConversionResult(roundImperial(cups, "cup"), CanonicalUnit.CUP)
            } else {
                val tbsp = ml / TBSP_TO_ML
                if (tbsp >= 0.5) {
                    ConversionResult(roundImperial(tbsp, "tbsp"), CanonicalUnit.TBSP)
                } else {
                    ConversionResult(roundImperial(ml / TSP_TO_ML, "tsp"), CanonicalUnit.TSP)
                }
            }
        }
    }

    private fun convertWeight(value: Double, from: CanonicalUnit, toMetric: Boolean): ConversionResult? {
        if (toMetric) {
            val g = when (from) {
                CanonicalUnit.OZ -> value * OZ_TO_G
                CanonicalUnit.LB -> value * LB_TO_G
                else -> return null
            }
            return if (g >= 1000.0) {
                ConversionResult(roundWeight(g / 1000.0, isKg = true), CanonicalUnit.KG)
            } else {
                ConversionResult(roundWeight(g, isKg = false), CanonicalUnit.G)
            }
        } else {
            // metric → imperial weight
            val g = when (from) {
                CanonicalUnit.G -> value
                CanonicalUnit.KG -> value * 1000.0
                else -> return null
            }
            val lb = g / LB_TO_G
            return if (lb >= 2.0) {
                ConversionResult(roundImperial(lb, "lb"), CanonicalUnit.LB)
            } else {
                ConversionResult(roundImperial(g / OZ_TO_G, "oz"), CanonicalUnit.OZ)
            }
        }
    }

    // ── Rounding ──

    private fun roundVolume(value: Double, isLiters: Boolean): Double {
        return if (isLiters) {
            (value * 10.0).roundToInt() / 10.0  // 1 decimal for L
        } else {
            // ml: nearest 5 for <100, nearest 10 for 100+
            if (value < 100.0) {
                (value / 5.0).roundToInt() * 5.0
            } else {
                (value / 10.0).roundToInt() * 10.0
            }
        }
    }

    private fun roundWeight(value: Double, isKg: Boolean): Double {
        return if (isKg) {
            (value * 10.0).roundToInt() / 10.0  // 1 decimal for kg
        } else {
            // g: nearest 5
            (value / 5.0).roundToInt() * 5.0
        }
    }

    private fun roundImperial(value: Double, unit: String): Double {
        return when (unit) {
            "cup" -> (value * 4.0).roundToInt() / 4.0   // nearest 1/4 cup
            "tbsp" -> (value * 2.0).roundToInt() / 2.0   // nearest 1/2 tbsp
            "tsp" -> (value * 4.0).roundToInt() / 4.0    // nearest 1/4 tsp
            "lb" -> (value * 4.0).roundToInt() / 4.0     // nearest 1/4 lb
            "oz" -> (value * 2.0).roundToInt() / 2.0     // nearest 1/2 oz
            else -> (value * 10.0).roundToInt() / 10.0
        }
    }

    // ── Display helpers ──

    fun displayUnit(canonical: CanonicalUnit): String = when (canonical) {
        CanonicalUnit.CUP -> "cup"
        CanonicalUnit.TBSP -> "tbsp"
        CanonicalUnit.TSP -> "tsp"
        CanonicalUnit.ML -> "ml"
        CanonicalUnit.L -> "l"
        CanonicalUnit.FL_OZ -> "fl oz"
        CanonicalUnit.G -> "g"
        CanonicalUnit.KG -> "kg"
        CanonicalUnit.OZ -> "oz"
        CanonicalUnit.LB -> "lb"
    }

    fun displayUnitPlural(canonical: CanonicalUnit, value: Double): String = when (canonical) {
        CanonicalUnit.CUP -> if (value == 1.0) "cup" else "cups"
        CanonicalUnit.TBSP -> "tbsp"
        CanonicalUnit.TSP -> "tsp"
        CanonicalUnit.ML -> "ml"
        CanonicalUnit.L -> "l"
        CanonicalUnit.FL_OZ -> "fl oz"
        CanonicalUnit.G -> "g"
        CanonicalUnit.KG -> "kg"
        CanonicalUnit.OZ -> "oz"
        CanonicalUnit.LB -> if (value == 1.0) "lb" else "lbs"
    }

    /**
     * Full display pipeline for one ingredient line.
     * Returns (formattedAmount, formattedUnit) or null if no conversion applies.
     */
    fun convertAndFormat(
        amountString: String?,
        unitString: String?,
        scaleFactor: Double,
        toSystem: UnitSystem
    ): Pair<String, String>? {
        if (amountString == null) return null
        val canonUnit = canonicalizeUnit(unitString) ?: return null
        if (toSystem == UnitSystem.AS_WRITTEN) return null
        val targetIsMetric = toSystem == UnitSystem.METRIC
        val sourceIsMetric = canonUnit in listOf(CanonicalUnit.ML, CanonicalUnit.L, CanonicalUnit.G, CanonicalUnit.KG)
        if (sourceIsMetric == targetIsMetric) return null

        val amounts = AmountParser.parseAmounts(amountString)
        if (amounts.isEmpty()) return null

        val scaled = amounts.map { it * scaleFactor }
        val firstConverted = convert(scaled.first(), canonUnit, toSystem) ?: return null
        val displayValue = firstConverted.value
        val displayUnitStr = displayUnitPlural(firstConverted.unit, displayValue)

        return if (scaled.size == 1) {
            Pair(AmountParser.formatAmount(displayValue), displayUnitStr)
        } else {
            val secondConverted = convert(scaled.last(), canonUnit, toSystem) ?: return null
            val low = AmountParser.formatAmount(firstConverted.value)
            val high = AmountParser.formatAmount(secondConverted.value)
            Pair("$low-$high", displayUnitStr)
        }
    }
}
