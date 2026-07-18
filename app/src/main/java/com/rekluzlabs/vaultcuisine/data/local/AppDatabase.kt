package com.rekluzlabs.vaultcuisine.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rekluzlabs.vaultcuisine.data.Recipe
import com.rekluzlabs.vaultcuisine.data.RecipeConverters

@Database(entities = [Recipe::class], version = 3, exportSchema = false)
@TypeConverters(RecipeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vaultcuisine.db"
                // TODO: Replace with a real Migration (or Room auto-migration)
                //  before first production release — destructive migration will
                //  silently wipe all user data on any future schema change.
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
    }
}
