/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
package com.rekluzlabs.vaultcuisine.ai

import com.rekluzlabs.vaultcuisine.data.Recipe
import com.rekluzlabs.vaultcuisine.data.RecipeCategory

/**
 * Suggests a starting category for a recipe using keyword matching against
 * the title and parsed ingredient names — no LLM involved, same spirit as
 * HeuristicStructurer's regex-based fallback parsing.
 *
 * Categories are course/meal-type based (Breakfast & Brunch, Main Dishes,
 * Desserts, etc.), so matching leans on dish-type and preparation words in
 * the title rather than raw ingredients — a "chicken" ingredient alone
 * doesn't tell you if something's a main dish or a soup, but "curry" or
 * "casserole" in the title does.
 *
 * This is a SUGGESTION only, used as a fallback when Gemini's own category
 * field is missing or unparseable. Wire its output into
 * CategoryPickerDialog's initialCategory param; never auto-save without
 * the user confirming.
 */
object CategorySuggester {

    // Order matters as a final tiebreaker only (first category wins ties).
    // Keep keyword lists lowercase; matching is done on lowercased text.
    private val keywordsByCategory: List<Pair<RecipeCategory, List<String>>> = listOf(
        RecipeCategory.BREAKFAST_BRUNCH to listOf(
            "pancake", "waffle", "omelette", "omelet", "french toast", "granola",
            "oatmeal", "breakfast", "brunch", "hash brown", "frittata", "scrambled egg",
            "breakfast burrito", "breakfast bowl", "porridge", "crepe"
        ),
        RecipeCategory.APPETIZERS_SNACKS to listOf(
            "appetizer", "starter", "dip", "wings", "nachos", "snack", "finger food",
            "bruschetta", "spring roll", "dumpling", "popcorn", "canape", "skewer",
            "deviled egg", "meatball appetizer"
        ),
        RecipeCategory.SOUPS_SALADS to listOf(
            "soup", "stew", "chowder", "bisque", "salad", "coleslaw", "slaw",
            "broth", "gazpacho", "minestrone"
        ),
        RecipeCategory.DESSERTS to listOf(
            "cake", "cookie", "pie", "brownie", "ice cream", "pudding", "tart",
            "cheesecake", "dessert", "sundae", "custard", "trifle", "mousse",
            "sorbet", "gelato"
        ),
        RecipeCategory.BAKED_GOODS to listOf(
            "bread", "muffin", "biscuit", "dinner roll", "bagel", "focaccia",
            "baguette", "cornbread", "scone", "loaf", "cinnamon roll", "flatbread"
        ),
        RecipeCategory.DRINKS_COCKTAILS to listOf(
            "smoothie", "juice", "cocktail", "mocktail", "latte", "lemonade",
            "punch", "margarita", "sangria", "milkshake", "iced tea", "coffee drink"
        ),
        RecipeCategory.SAUCES_CONDIMENTS to listOf(
            "sauce", "gravy", "dressing", "salsa", "chutney", "pesto", "aioli",
            "glaze", "marinade", "jam", "relish", "condiment", "vinaigrette",
            "bbq sauce", "hollandaise"
        ),
        RecipeCategory.SIDE_DISHES to listOf(
            "side dish", "mashed potato", "rice pilaf", "roasted vegetable",
            "gratin", "stuffing", "au gratin", "baked beans"
        ),
        RecipeCategory.MAIN_DISHES to listOf(
            "casserole", "curry", "roast", "entree", "dinner", "main course",
            "stir fry", "chicken", "beef", "pork", "steak", "salmon", "fish",
            "turkey", "lamb", "meatloaf", "lasagna", "stroganoff"
        )
    )

    /**
     * Title first (cheap, often decisive), then ingredient-name scoring as
     * fallback/tiebreaker. Falls back to OTHER when nothing scores.
     */
    fun suggest(title: String, recipe: Recipe? = null): RecipeCategory {
        val lowerTitle = title.lowercase()

        // Pass 1: strong single-keyword title match.
        for ((category, keywords) in keywordsByCategory) {
            if (keywords.any { lowerTitle.contains(it) }) {
                return category
            }
        }

        // Pass 2: score by ingredient name matches (weaker signal for
        // course-based categories, but still useful as a tiebreaker).
        val ingredientNames = recipe?.ingredients?.map { it.name.lowercase() } ?: emptyList()
        if (ingredientNames.isEmpty()) return RecipeCategory.OTHER

        val scores = keywordsByCategory.associate { (category, keywords) ->
            category to ingredientNames.count { name -> keywords.any { name.contains(it) } }
        }

        val best = scores.maxByOrNull { it.value }
        return if (best != null && best.value > 0) best.key else RecipeCategory.OTHER
    }
}
