package com.rekluzlabs.vaultcuisine.data

import kotlinx.serialization.Serializable

@Serializable
data class RecipeExport(
    val exportSchemaVersion: Int = 1,
    val recipes: List<Recipe>
)
