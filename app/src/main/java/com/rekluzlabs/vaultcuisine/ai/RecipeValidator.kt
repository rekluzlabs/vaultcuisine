package com.rekluzlabs.vaultcuisine.ai

import com.rekluzlabs.vaultcuisine.data.Recipe

enum class Severity { WARNING, ERROR }

data class FlaggedField(
    val fieldName: String,
    val index: Int,
    val severity: Severity,
    val message: String
)

/**
 * Flags likely OCR/parsing problems for the review/edit screen to highlight
 * before save — ERROR fields should block saving, WARNING fields are
 * non-destructive nudges (duplicate ingredient, suspicious characters, etc.)
 * the user can dismiss or ignore.
 */
object RecipeValidator {

    data class ValidationReport(
        val isValid: Boolean,
        val flaggedIngredients: Map<Int, FlaggedField>,
        val flaggedSteps: Map<Int, FlaggedField>
    )

    fun validate(recipe: Recipe): ValidationReport {
        val flaggedIngredients = mutableMapOf<Int, FlaggedField>()
        val flaggedSteps = mutableMapOf<Int, FlaggedField>()
        val seenIngredients = mutableSetOf<String>()

        recipe.ingredients.forEachIndexed { index, ing ->
            val cleanName = ing.name.lowercase().trim()

            when {
                cleanName.isBlank() ->
                    flaggedIngredients[index] = FlaggedField("name", index, Severity.ERROR, "Ingredient name cannot be empty")
                cleanName.contains(Regex("[{}#\\[\\]_]")) ->
                    flaggedIngredients[index] = FlaggedField("name", index, Severity.WARNING, "Contains suspicious OCR characters")
            }

            if (ing.unit != null && ing.amount == null) {
                flaggedIngredients[index] = FlaggedField("amount", index, Severity.ERROR, "Amount needed when unit is specified")
            }

            if (cleanName.isNotEmpty() && !seenIngredients.add(cleanName)) {
                flaggedIngredients[index] = FlaggedField("name", index, Severity.WARNING, "Possible duplicate ingredient")
            }

            ing.amount?.toDoubleOrNull()?.let { num ->
                if (num > 1000.0) {
                    flaggedIngredients[index] = FlaggedField("amount", index, Severity.WARNING, "Unusually high quantity — double check this")
                }
            }
        }

        recipe.steps.forEachIndexed { index, step ->
            if (step.text.isBlank() || step.text.length < 3) {
                flaggedSteps[index] = FlaggedField("text", index, Severity.ERROR, "Step content is too short or blank")
            }
        }

        return ValidationReport(
            isValid = flaggedIngredients.values.none { it.severity == Severity.ERROR } &&
                flaggedSteps.values.none { it.severity == Severity.ERROR },
            flaggedIngredients = flaggedIngredients,
            flaggedSteps = flaggedSteps
        )
    }
}
