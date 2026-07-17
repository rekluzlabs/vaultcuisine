package com.rekluzlabs.vaultcuisine.data.local

import androidx.room.*
import com.rekluzlabs.vaultcuisine.data.Recipe
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: String): Recipe?

    @Query("SELECT * FROM recipes WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavorites(): Flow<List<Recipe>>

    // Basic search across title and notes. Swap for FTS4/FTS5 virtual table
    // once the library grows past a few hundred recipes.
    @Query("SELECT * FROM recipes WHERE title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<Recipe>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recipe: Recipe)

    @Delete
    suspend fun delete(recipe: Recipe)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteById(id: String)
}
