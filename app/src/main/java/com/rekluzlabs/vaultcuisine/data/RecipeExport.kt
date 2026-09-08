/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
package com.rekluzlabs.vaultcuisine.data

import kotlinx.serialization.Serializable

@Serializable
data class RecipeExport(
    val exportSchemaVersion: Int = 1,
    val recipes: List<Recipe>
)
