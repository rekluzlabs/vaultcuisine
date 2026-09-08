package com.rekluzlabs.vaultcuisine.data.backup

import androidx.room.withTransaction
import com.rekluzlabs.vaultcuisine.data.Recipe
import com.rekluzlabs.vaultcuisine.data.RecipeCategory
import com.rekluzlabs.vaultcuisine.data.RecipeExport
import com.rekluzlabs.vaultcuisine.data.local.AppDatabase
import com.rekluzlabs.vaultcuisine.data.local.RecipeDao
import com.rekluzlabs.vaultcuisine.data.tryParseGeminiImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class BackupType { ZIP, JSON }

sealed interface BackupResult {
    data class Success(val count: Int) : BackupResult
    data class Error(val message: String) : BackupResult
}

/**
 * Backup & restore for recipes. Photos are stored one file per recipe as
 * "<recipeId>.jpg" inside [imagesDir] (reused from the app's existing photo
 * storage location).
 *
 * ZIP layout:
 *   recipes.json            — RecipeExport with image refs rewritten to
 *                            relative "images/<recipeId>.jpg" paths
 *   images/<recipeId>.jpg   — streamed straight from disk, never buffered
 *                            fully in memory
 *
 * Restore merges by recipe ID (upsert): recipes with a matching ID are
 * updated, new ones are added, and everything else in the app is kept.
 */
class BackupManager(
    private val dao: RecipeDao,
    private val db: AppDatabase,
    private val imagesDir: File,
    private val cacheDir: File
) {
    private val jsonPretty = Json { prettyPrint = true }
    private val jsonLenient = Json { ignoreUnknownKeys = true }

    fun zipFileName(): String = "vaultcuisine-backup-${timestamp()}.zip"

    fun jsonFileName(): String = "vaultcuisine-recipes-${timestamp()}.json"

    suspend fun exportZip(recipes: List<Recipe>, output: OutputStream): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                    val zipRecipes = recipes.map { recipe ->
                        recipe.copy(sourceImagePath = recipe.sourceImagePath?.let { "images/${recipe.id}.jpg" })
                    }
                    recipes.forEach { recipe ->
                        val src = recipe.sourceImagePath?.let(::File)
                        if (src != null && src.exists()) {
                            zip.putNextEntry(ZipEntry("images/${recipe.id}.jpg"))
                            src.inputStream().use { input -> input.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                    zip.putNextEntry(ZipEntry(RECIPES_JSON))
                    val json = jsonPretty.encodeToString(RecipeExport.serializer(), RecipeExport(recipes = zipRecipes))
                    zip.write(json.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                BackupResult.Success(recipes.size)
            } catch (e: Exception) {
                BackupResult.Error("Backup failed: ${e.message}")
            }
        }

    suspend fun exportJson(recipes: List<Recipe>, output: OutputStream): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val json = jsonPretty.encodeToString(RecipeExport.serializer(), RecipeExport(recipes = recipes))
                output.write(json.toByteArray(Charsets.UTF_8))
                output.flush()
                BackupResult.Success(recipes.size)
            } catch (e: Exception) {
                BackupResult.Error("Backup failed: ${e.message}")
            }
        }

    suspend fun restoreJson(
        input: InputStream,
        resolveCategory: suspend (Recipe) -> RecipeCategory?
    ): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val json = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val recipes = try {
                    jsonLenient.decodeFromString(RecipeExport.serializer(), json).recipes
                } catch (_: SerializationException) {
                    tryParseGeminiImport(json, jsonLenient)
                } ?: return@withContext BackupResult.Error("Not a valid VaultCuisine backup file.")
                upsertAll(recipes, idsMissingCategory(json), resolveCategory)
            } catch (e: Exception) {
                BackupResult.Error("Restore failed: ${e.message}")
            }
        }

    suspend fun restoreZip(
        input: InputStream,
        resolveCategory: suspend (Recipe) -> RecipeCategory?
    ): BackupResult =
        withContext(Dispatchers.IO) {
            val tempDir = File(cacheDir, "restore_${System.currentTimeMillis()}")
            try {
                extractZip(input, tempDir)

                val jsonFile = File(tempDir, RECIPES_JSON)
                if (!jsonFile.exists()) {
                    return@withContext BackupResult.Error("Not a valid VaultCuisine backup — missing recipes.json.")
                }
                val jsonText = jsonFile.readText()
                val recipes = try {
                    jsonLenient.decodeFromString(RecipeExport.serializer(), jsonText).recipes
                } catch (_: Exception) {
                    return@withContext BackupResult.Error("Not a valid VaultCuisine backup — recipes.json is malformed.")
                }
                if (recipes.isEmpty()) {
                    return@withContext BackupResult.Error("The backup contains no recipes.")
                }

                recipes.forEach { recipe ->
                    val ref = recipe.sourceImagePath ?: return@forEach
                    if (resolveIn(tempDir, ref) == null) {
                        return@withContext BackupResult.Error(
                            "Backup is missing image for '${recipe.title}'. Restore cancelled — nothing was changed."
                        )
                    }
                }

                imagesDir.mkdirs()
                val restored = recipes.map { recipe ->
                    var newPath: String? = null
                    val ref = recipe.sourceImagePath
                    if (ref != null) {
                        val src = resolveIn(tempDir, ref) ?: error("Image reference escapes backup: $ref")
                        val dest = File(imagesDir, src.name)
                        src.copyTo(dest, overwrite = true)
                        newPath = dest.absolutePath
                    }
                    recipe.copy(sourceImagePath = newPath, updatedAt = System.currentTimeMillis())
                }

                upsertAll(restored, idsMissingCategory(jsonText), resolveCategory)
            } catch (e: Exception) {
                BackupResult.Error("Restore failed: ${e.message}")
            } finally {
                tempDir.deleteRecursively()
            }
        }

    /**
     * Bulk-import category decision: prompt once per recipe that lacks a
     * `category` field (pre-v5 exports and Gemini imports), reusing the same
     * blocking [CategoryPickerDialog] as the scan flow. An "apply to all"
     * fast-path isn't wired into the restore UI yet, so per-recipe prompting
     * keeps the flow simple; cancelling any prompt aborts the whole restore
     * so nothing is partially imported.
     */
    private suspend fun upsertAll(
        recipes: List<Recipe>,
        missingCategoryIds: Set<String>?,
        resolveCategory: suspend (Recipe) -> RecipeCategory?
    ): BackupResult {
        val resolved = mutableListOf<Recipe>()
        for (recipe in recipes) {
            val needsPrompt = missingCategoryIds == null || recipe.id in missingCategoryIds
            if (!needsPrompt) {
                resolved.add(recipe)
            } else {
                val category = resolveCategory(recipe)
                    ?: return BackupResult.Error("Restore cancelled — no changes were made.")
                resolved.add(recipe.copy(category = category))
            }
        }
        var count = 0
        db.withTransaction {
            resolved.forEach { dao.upsert(it) }
            count = resolved.size
        }
        return BackupResult.Success(count)
    }

    /**
     * Returns the set of recipe IDs whose JSON representation has no
     * parseable `category` field, so the import flow can prompt for them.
     * Returns null when the payload isn't a RecipeExport wrapper (i.e. a
     * Gemini-style import) — in that case every recipe is treated as missing
     * a category.
     */
    private fun idsMissingCategory(json: String): Set<String>? {
        return try {
            val root = jsonLenient.decodeFromString<JsonObject>(json)
            val array = root["recipes"]?.jsonArray ?: return null
            array.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content
                val category = obj["category"]?.jsonPrimitive?.content
                if (id != null && (category == null || !isValidCategoryName(category))) id else null
            }.toSet()
        } catch (_: Exception) {
            null
        }
    }

    private fun isValidCategoryName(name: String): Boolean =
        runCatching { RecipeCategory.valueOf(name) }.isSuccess

    private fun extractZip(input: InputStream, destDir: File) {
        val destRoot = destDir.canonicalFile
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = File(destDir, entry.name).canonicalFile
                if (target.startsWith(destRoot)) {
                    if (!entry.isDirectory) {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out -> zip.copyTo(out) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    /** Resolves a zip-internal path relative to [dir], guarding against path traversal. */
    private fun resolveIn(dir: File, path: String): File? {
        val root = dir.canonicalFile
        val target = File(dir, path).canonicalFile
        return if (target.startsWith(root)) target else null
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    companion object {
        private const val RECIPES_JSON = "recipes.json"
    }
}
