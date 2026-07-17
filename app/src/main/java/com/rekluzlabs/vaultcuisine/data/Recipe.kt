package com.rekluzlabs.vaultcuisine.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import kotlinx.serialization.Serializable

/**
 * Schema versioned from day one so exported JSON files stay importable
 * across future app updates.
 */
const val CURRENT_SCHEMA_VERSION = 2

@Serializable
data class RecipeIngredient(
    val id: String,
    val amount: String?,
    val unit: String?,      // e.g. "cup", "tsp", "g" — null for countable items
    val name: String        // e.g. "large eggs", "garlic cloves"
)

@Serializable
data class RecipeStep(
    val id: String,
    val text: String,
    val timerSeconds: Int? = null // populated by AI structuring pass when a step implies waiting
)

@Serializable
@Entity(tableName = "recipes")
@TypeConverters(RecipeConverters::class)
data class Recipe(
    @PrimaryKey val id: String,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val title: String,
    val servings: Int = 4,
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val sourceImagePath: String? = null, // local path to original scanned photo, if any
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
