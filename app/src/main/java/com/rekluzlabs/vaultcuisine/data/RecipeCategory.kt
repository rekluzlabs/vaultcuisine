/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
package com.rekluzlabs.vaultcuisine.data

enum class RecipeCategory(val displayName: String) {
    BREAKFAST_BRUNCH("Breakfast & Brunch"),
    APPETIZERS_SNACKS("Appetizers & Snacks"),
    SOUPS_SALADS("Soups & Salads"),
    MAIN_DISHES("Main Dishes"),
    SIDE_DISHES("Side Dishes"),
    BAKED_GOODS("Baked Goods"),
    DESSERTS("Desserts"),
    DRINKS_COCKTAILS("Drinks & Cocktails"),
    SAUCES_CONDIMENTS("Sauces & Condiments"),
    OTHER("Other");

    companion object {
        fun fromDisplayName(name: String): RecipeCategory? =
            entries.find { it.displayName.equals(name, ignoreCase = true) }
    }
}
