package com.rekluzlabs.vaultcuisine.data

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room can't store List<RecipeIngredient> / List<RecipeStep> / List<String> natively,
 * so these convert to/from JSON strings for storage in a single column.
 */
class RecipeConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromIngredients(list: List<RecipeIngredient>): String = json.encodeToString(list)

    @TypeConverter
    fun toIngredients(data: String): List<RecipeIngredient> =
        if (data.isBlank()) emptyList() else json.decodeFromString(data)

    @TypeConverter
    fun fromSteps(list: List<RecipeStep>): String = json.encodeToString(list)

    @TypeConverter
    fun toSteps(data: String): List<RecipeStep> =
        if (data.isBlank()) emptyList() else json.decodeFromString(data)

    @TypeConverter
    fun fromTags(list: List<String>): String = json.encodeToString(list)

    @TypeConverter
    fun toTags(data: String): List<String> =
        if (data.isBlank()) emptyList() else json.decodeFromString(data)
}
