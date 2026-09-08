/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
package com.rekluzlabs.vaultcuisine.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.rekluzlabs.vaultcuisine.util.UnitSystem
import kotlinx.serialization.Serializable

/**
 * Schema versioned from day one so exported JSON files stay importable
 * across future app updates.
 *
 * v5: added `category` (RecipeCategory) so recipes can be filed under the
 * home screen's category grid instead of listed flat.
 *
 * v6: renamed RecipeCategory constants/displayNames from grocery-oriented
 * (Produce, Meats & Seafood, ...) to course/meal-type categories
 * (Breakfast & Brunch, Main Dishes, ...). Stored constant names changed,
 * so old values no longer parse and require a destructive migration.
 */
const val CURRENT_SCHEMA_VERSION = 6

/**
 * Message set by [com.rekluzlabs.vaultcuisine.ai.HeuristicStructurer]'s fallback
 * path when auto-structuring fails entirely. Used to detect and handle the
 * message in the UI — see [MainViewModel.enterEditMode] pre-clear logic and
 * [RecipeDetailScreen]'s fallback banner.
 */
const val FALLBACK_NOTES_MESSAGE = "Couldn't automatically structure this scan. Please edit the fields above manually."

@Serializable
data class RecipeIngredient(
    val id: String,
    val amount: String?,
    val unit: String?,      // e.g. "cup", "tsp", "g" — null for countable items
    val name: String,       // e.g. "large eggs", "garlic cloves"
    val confidence: String? = null
)

@Serializable
data class RecipeStep(
    val id: String,
    val text: String,
    val timerSeconds: Int? = null, // populated by AI structuring pass when a step implies waiting
    val confidence: String? = null
)

@Serializable
@Entity(tableName = "recipes")
@TypeConverters(RecipeConverters::class)
data class Recipe(
    @PrimaryKey val id: String,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val title: String,
    val category: RecipeCategory = RecipeCategory.OTHER,
    val servings: Int? = null,
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val notes: String? = null,
    val rating: Int? = null,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val preferredUnitSystem: UnitSystem = UnitSystem.AS_WRITTEN,
    val sourceImagePath: String? = null, // local path to original scanned photo, if any
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val confidence: String? = null
)
