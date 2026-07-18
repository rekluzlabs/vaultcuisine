package com.rekluzlabs.vaultcuisine.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

@Serializable
data class GeminiRecipeImport(
    val recipe_name: String? = null,
    val title: String? = null,
    val rating: String? = null,
    val times: Map<String, String>? = null,
    val servings: String? = null,
    val handwritten_notes: List<String>? = null,
    val notes: String? = null,
    val ingredients: List<String> = emptyList(),
    val directions: List<String>? = null,
    val steps: List<Map<String, kotlinx.serialization.json.JsonElement>>? = null
)

fun GeminiRecipeImport.toRecipes(): List<Recipe> {
    val recipeTitle = recipe_name ?: title ?: "Imported Recipe"
    val recipeServings = parseServings(servings)

    val ingredientLines = if (ingredients.isNotEmpty()) {
        ingredients
    } else {
        emptyList()
    }

    val stepLines = directions ?: emptyList()

    val recipeIngredients = ingredientLines.map { parseIngredient(it) }

    val recipeSteps = stepLines.mapIndexed { index, text ->
        RecipeStep(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            timerSeconds = null
        )
    }

    val notesText = notes ?: handwritten_notes?.joinToString("\n")

    return listOf(
        Recipe(
            id = UUID.randomUUID().toString(),
            schemaVersion = CURRENT_SCHEMA_VERSION,
            title = recipeTitle,
            servings = recipeServings,
            ingredients = recipeIngredients,
            steps = recipeSteps,
            notes = notesText
        )
    )
}

private fun parseServings(raw: String?): Int {
    if (raw == null) return 4
    val digits = raw.trim().replace(Regex("[^0-9].*"), "")
    return digits.toIntOrNull() ?: 4
}

private fun parseIngredient(line: String): RecipeIngredient {
    val cleaned = line.trim()

    val stripped = cleaned.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()

    val match = Regex("""^([\d.]+(?:[\s]*[-–/][\s]*[\d.]+)?)\s+(\S+)\s+(.*)""").find(stripped)
    if (match != null) {
        return RecipeIngredient(
            id = UUID.randomUUID().toString(),
            amount = match.groupValues[1].trim(),
            unit = match.groupValues[2].trim(),
            name = match.groupValues[3].trim()
        )
    }

    return RecipeIngredient(
        id = UUID.randomUUID().toString(),
        amount = null,
        unit = null,
        name = cleaned
    )
}

internal fun tryParseGeminiImport(json: String, lenientJson: Json): List<Recipe>? {
    return try {
        val obj = lenientJson.decodeFromString<JsonObject>(json)
        val hasRecipeName = obj.containsKey("recipe_name")
        val hasHandwrittenNotes = obj.containsKey("handwritten_notes") || obj.containsKey("notes")
        val hasIngredients = obj.containsKey("ingredients")
        val hasDirections = obj.containsKey("directions") || obj.containsKey("steps")

        if (!hasRecipeName && !hasHandwrittenNotes && !hasIngredients) {
            return null
        }

        val gemini = GeminiRecipeImport(
            recipe_name = obj["recipe_name"]?.jsonPrimitive?.content,
            title = obj["title"]?.jsonPrimitive?.content,
            rating = obj["rating"]?.jsonPrimitive?.content,
            times = null,
            servings = obj["servings"]?.jsonPrimitive?.content,
            handwritten_notes = obj["handwritten_notes"]?.jsonArray?.map {
                it.jsonPrimitive.content
            },
            notes = obj["notes"]?.jsonPrimitive?.content,
            ingredients = obj["ingredients"]?.jsonArray?.map { it.jsonPrimitive.content }
                ?: emptyList(),
            directions = obj["directions"]?.jsonArray?.map { it.jsonPrimitive.content },
            steps = null
        )
        gemini.toRecipes()
    } catch (e: Exception) {
        null
    }
}
