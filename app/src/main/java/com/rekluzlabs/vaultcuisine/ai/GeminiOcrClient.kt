/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
package com.rekluzlabs.vaultcuisine.ai

import com.rekluzlabs.vaultcuisine.data.Recipe
import com.rekluzlabs.vaultcuisine.data.RecipeCategory
import com.rekluzlabs.vaultcuisine.data.RecipeIngredient
import com.rekluzlabs.vaultcuisine.data.RecipeStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.SerialName
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

private val geminiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
private data class GeminiApiResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
private data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null
)

@Serializable
private data class Content(
    val parts: List<Part>? = null
)

@Serializable
private data class Part(
    val text: String? = null
)

@Serializable
private data class GeminiRecipeDto(
    @SerialName("is_recipe")
    val isRecipe: Boolean = true,
    val confidence: String? = null,
    val title: String = "",
    val category: String? = null,
    val ingredients: List<GeminiIngredientDto> = emptyList(),
    val steps: List<GeminiStepDto> = emptyList()
)

@Serializable
private data class GeminiIngredientDto(
    val amount: String? = null,
    val unit: String? = null,
    val name: String = "",
    val confidence: String? = null
)

@Serializable
private data class GeminiStepDto(
    val text: String = "",
    @SerialName("timer_seconds")
    val timerSeconds: Int? = null,
    val confidence: String? = null
)

@Serializable
private data class GeminiTranslationDto(
    val title: String = "",
    val notes: String? = null,
    val ingredients: List<GeminiIngredientDto> = emptyList(),
    val steps: List<GeminiStepDto> = emptyList()
)

@Serializable
private data class GeminiErrorBody(
    val error: GeminiError? = null
)

@Serializable
private data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

data class ScanResult(val recipe: Recipe, val retried: Boolean)

class GeminiOcrClient(
    private val credentialStore: GeminiCredentialStore,
    private val imagePreprocessor: ImagePreprocessor = ImagePreprocessor(),
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()
) : RecipeStructurer, ImageCapableStructurer {

    override suspend fun structure(rawText: String): Recipe {
        if (!credentialStore.hasApiKey()) throw MissingApiKeyException()
        return structure(rawText, GeminiModels.DEFAULT_MODEL_ID)
    }

    suspend fun structure(rawText: String, modelId: String): Recipe {
        val apiKey = credentialStore.getApiKey() ?: throw MissingApiKeyException()
        val prompt = buildTextPrompt(rawText)
        val responseJson = callGeminiApi(apiKey, modelId, prompt, imageBytes = null)
        val dto = parseResponseText(responseJson)
        return dto.toRecipe()
    }

    override suspend fun structureFromImage(imageBytes: ByteArray): Recipe {
        return structureFromImage(imageBytes, GeminiModels.DEFAULT_MODEL_ID, ocrHint = null)
    }

    /**
     * @param ocrHint Optional raw text already recognized on-device by ML Kit
     * for this same image. When present, Gemini is asked to cross-check its
     * own reading of the image against this text rather than transcribing
     * cold — cheap accuracy gain, especially on blurry or low-light photos.
     */
    suspend fun structureFromImage(
        imageBytes: ByteArray,
        modelId: String,
        ocrHint: String? = null
    ): Recipe = structureFromImageWithMeta(imageBytes, modelId, ocrHint).recipe

    /**
     * Like [structureFromImage] but returns [ScanResult] containing a retry
     * flag. If the first API response is low-confidence enough — empty result
     * claiming to be a recipe, overall or majority item-level "low" confidence
     * — a single second attempt is made with the same preprocessed image bytes
     * and the second result is used regardless of its confidence. The caller
     * can use [onRetry] to update UI state (e.g. loading message) without
     * knowing implementation details.
     */
    suspend fun structureFromImageWithMeta(
        imageBytes: ByteArray,
        modelId: String,
        ocrHint: String? = null,
        onRetry: () -> Unit = {}
    ): ScanResult {
        val apiKey = credentialStore.getApiKey() ?: throw MissingApiKeyException()
        val processed = imagePreprocessor.prepareForUpload(imageBytes)
        val prompt = buildImagePrompt(ocrHint)

        val firstJson = callGeminiApi(apiKey, modelId, prompt, processed)
        val firstDto = parseResponseText(firstJson)

        val willRetry = shouldRetry(firstDto)
        Log.d("GeminiOcrClient", "scan attempt 1: confidence-triggered retry=$willRetry")

        if (willRetry) {
            onRetry()
            val secondJson = callGeminiApi(apiKey, modelId, prompt, processed)
            val secondDto = parseResponseText(secondJson)
            Log.d("GeminiOcrClient", "scan attempt 2 (auto-retry) complete")
            return ScanResult(secondDto.toRecipe(), retried = true)
        }

        return ScanResult(firstDto.toRecipe(), retried = false)
    }

    suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()
        try {
            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Translates a stored recipe into [targetLanguage] via Gemini. The
     * original recipe is never modified — the caller decides how to use the
     * returned copy (e.g. view-only display cached per language). Numeric
     * amounts and timer values are preserved as-is.
     */
    suspend fun translateRecipe(recipe: Recipe, targetLanguage: String, modelId: String): Recipe {
        val apiKey = credentialStore.getApiKey() ?: throw MissingApiKeyException()
        val prompt = buildTranslatePrompt(recipe, targetLanguage)
        var dto: GeminiTranslationDto? = null
        var lastError: Exception? = null
        try {
            repeat(2) { attempt ->
                if (dto != null) return@repeat
                try {
                    val responseJson = callGeminiApi(
                        apiKey,
                        modelId,
                        prompt,
                        imageBytes = null,
                        generationConfig = buildTranslationConfig(recipe)
                    )
                    Log.d("GeminiTranslate", "model=$modelId attempt ${attempt + 1} returned ${responseJson.length} bytes")
                    dto = parseTranslationText(responseJson)
                } catch (e: MalformedResponseException) {
                    lastError = e
                    Log.w("GeminiTranslate", "model=$modelId attempt ${attempt + 1} failed, retrying: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiTranslate", "translate \"${recipe.title}\" with $modelId failed: ${e.message}")
            throw e
        }
        if (dto == null) {
            Log.e("GeminiTranslate", "translate \"${recipe.title}\" with $modelId failed: ${lastError?.message}")
            throw lastError ?: MalformedResponseException("Translation failed")
        }
        return recipe.copy(
            title = dto.title.ifBlank { recipe.title },
            notes = dto.notes?.takeIf { it.isNotBlank() } ?: recipe.notes,
            ingredients = dto.ingredients.mapIndexed { index, translated ->
                val original = recipe.ingredients.getOrNull(index)
                RecipeIngredient(
                    id = original?.id ?: UUID.randomUUID().toString(),
                    amount = translated.amount?.takeIf { it.isNotBlank() } ?: original?.amount,
                    unit = translated.unit?.takeIf { it.isNotBlank() } ?: original?.unit,
                    name = translated.name.ifBlank { original?.name ?: "" },
                    confidence = original?.confidence ?: translated.confidence
                )
            },
            steps = dto.steps.mapIndexed { index, translated ->
                val original = recipe.steps.getOrNull(index)
                RecipeStep(
                    id = original?.id ?: UUID.randomUUID().toString(),
                    text = translated.text.ifBlank { original?.text ?: "" },
                    timerSeconds = translated.timerSeconds ?: original?.timerSeconds,
                    confidence = original?.confidence ?: translated.confidence
                )
            }
        )
    }

    private suspend fun callGeminiApi(
        apiKey: String,
        modelId: String,
        textPart: String,
        imageBytes: ByteArray?,
        generationConfig: JsonObject = buildGenerationConfig()
    ): String {
        val parts = buildJsonArray {
            if (imageBytes != null) {
                val base64 = Base64.getEncoder().encodeToString(imageBytes)
                add(buildJsonObject {
                    put("inline_data", buildJsonObject {
                        put("mime_type", JsonPrimitive("image/jpeg"))
                        put("data", JsonPrimitive(base64))
                    })
                })
            }
            add(buildJsonObject {
                put("text", JsonPrimitive(textPart))
            })
        }

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", parts)
                })
            })
            put("generationConfig", generationConfig)
        }

        val bodyString = requestBody.toString()
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey")
            .post(bodyString.toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            var retries = 0
            var response = execute(request)
            while (isTransient(response.code) && retries < MAX_RETRIES) {
                val delayMs = RETRY_DELAYS_MS.getOrElse(retries) { RETRY_DELAYS_MS.last() }
                Log.w("GeminiOcrClient", "HTTP ${response.code}, retry ${retries + 1}/$MAX_RETRIES after ${delayMs}ms")
                response.close()
                Thread.sleep(delayMs)
                retries++
                response = execute(request)
            }

            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                throw when (response.code) {
                    429 -> RateLimitException(response.header("Retry-After")?.toIntOrNull())
                    else -> ApiException(response.code, geminiErrorText(responseBody, response.code))
                }
            }

            val apiResponse = try {
                geminiJson.decodeFromString<GeminiApiResponse>(responseBody)
            } catch (e: Exception) {
                throw MalformedResponseException("Failed to parse API response: ${e.message}")
            }

            val candidate = apiResponse.candidates?.firstOrNull()
            if (candidate?.finishReason == "MAX_TOKENS") {
                throw TruncatedResponseException(
                    "Gemini hit the output limit and cut the response off" +
                            " (the recipe or translation is very long)."
                )
            }
            candidate?.content?.parts?.firstOrNull()
                ?.text ?: throw MalformedResponseException("No text content in Gemini response")
        }
    }

    private fun execute(request: Request): Response = try {
        okHttpClient.newCall(request).execute()
    } catch (e: Exception) {
        throw NetworkException(e)
    }

    private fun isTransient(code: Int): Boolean = code == 429 || code == 503

    /**
     * Builds a clean, user-facing error string from a non-2xx Gemini response.
     * Only the API's own `error.status`/`error.message` are used — never the raw
     * body or the request URL, so an API key query param can't leak. HTTP 503 /
     * status UNAVAILABLE maps to plain "overloaded" wording.
     */
    private fun geminiErrorText(responseBody: String, code: Int): String {
        val detail = try {
            geminiJson.decodeFromString<GeminiErrorBody>(responseBody).error
        } catch (_: Exception) {
            null
        }
        val message = detail?.message?.takeIf { it.isNotBlank() }
        return when (detail?.status) {
            "UNAVAILABLE", "RESOURCE_EXHAUSTED" ->
                "Gemini is currently overloaded. Please try again in a moment."
            else -> message ?: "Gemini request failed (HTTP $code)."
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS_MS = longArrayOf(1000, 2000, 4000)
    }

    /**
     * Deterministic extraction settings + a JSON schema constraint. With
     * response_mime_type/response_schema set, Gemini's decoder is constrained
     * to emit matching JSON directly — no markdown fences, no preamble, no
     * malformed output falling through to HeuristicStructurer.
     *
     * temperature/topP/topK are intentionally left at their model defaults:
     * Google's guidance for Gemini 3.x is that overriding temperature (e.g.
     * to 0.1) can cause looping or degraded output, which we observed as
     * garbage text from gemini-3.6-flash.
     */
    private fun buildGenerationConfig() = buildJsonObject {
        put("maxOutputTokens", JsonPrimitive(2048))
        put("response_mime_type", JsonPrimitive("application/json"))
        put("response_schema", recipeResponseSchema())
    }

    /**
     * Translation config mirrors the scan path: a response_schema constrains
     * Gemini's decoder to emit matching JSON directly — bounded, well-formed
     * output that can't run away to maximal tokens and truncate mid-JSON
     * (the free-form config below was slow + parse-failing on long recipes).
     *
     * The output budget is raised well above the image-scan budget because a
     * translation rewrites *every* word of a recipe (input length ≠ output
     * length across languages), and even constrained JSON can be cut off at
     * `MAX_TOKENS` on very long recipes. Items are additionally pinned with
     * minItems/maxItems equal to the source counts so Gemini can't silently
     * drop or merge ingredients/steps.
     */
    private fun buildTranslationConfig(recipe: Recipe) = buildJsonObject {
        put("maxOutputTokens", JsonPrimitive(8192))
        put("response_mime_type", JsonPrimitive("application/json"))
        put("response_schema", translationResponseSchema(recipe))
    }

    private fun translationResponseSchema(recipe: Recipe) = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", buildJsonObject {
            put("title", typeSchema("string"))
            put("notes", typeSchema("string", nullable = true))
            put("ingredients", buildJsonObject {
                put("type", JsonPrimitive("array"))
                put("minItems", JsonPrimitive(recipe.ingredients.size))
                put("maxItems", JsonPrimitive(recipe.ingredients.size))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("amount", typeSchema("string", nullable = true))
                        put("unit", typeSchema("string", nullable = true))
                        put("name", typeSchema("string"))
                    })
                    put("required", buildJsonArray { add(JsonPrimitive("name")) })
                })
            })
            put("steps", buildJsonObject {
                put("type", JsonPrimitive("array"))
                put("minItems", JsonPrimitive(recipe.steps.size))
                put("maxItems", JsonPrimitive(recipe.steps.size))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("text", typeSchema("string"))
                        put("timer_seconds", typeSchema("integer", nullable = true))
                    })
                    put("required", buildJsonArray { add(JsonPrimitive("text")) })
                })
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("title"))
            add(JsonPrimitive("ingredients"))
            add(JsonPrimitive("steps"))
        })
    }

    private fun stringEnumSchema(vararg values: String) = buildJsonObject {
        put("type", JsonPrimitive("string"))
        put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
    }

    private fun typeSchema(type: String, nullable: Boolean = false) = buildJsonObject {
        put("type", JsonPrimitive(type))
        if (nullable) put("nullable", JsonPrimitive(true))
    }

    private fun recipeResponseSchema() = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", buildJsonObject {
            put("is_recipe", typeSchema("boolean"))
            put("confidence", stringEnumSchema("high", "medium", "low"))
            put("title", typeSchema("string"))
            put("category", stringEnumSchema(
                "Breakfast & Brunch", "Appetizers & Snacks", "Soups & Salads", "Main Dishes",
                "Side Dishes", "Baked Goods", "Desserts", "Drinks & Cocktails",
                "Sauces & Condiments", "Other"
            ))
            put("ingredients", buildJsonObject {
                put("type", JsonPrimitive("array"))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("amount", typeSchema("string", nullable = true))
                        put("unit", typeSchema("string", nullable = true))
                        put("name", typeSchema("string"))
                        put("confidence", stringEnumSchema("high", "medium", "low"))
                    })
                    put("required", buildJsonArray { add(JsonPrimitive("name")) })
                })
            })
            put("steps", buildJsonObject {
                put("type", JsonPrimitive("array"))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("text", typeSchema("string"))
                        put("timer_seconds", typeSchema("integer", nullable = true))
                        put("confidence", stringEnumSchema("high", "medium", "low"))
                    })
                    put("required", buildJsonArray { add(JsonPrimitive("text")) })
                })
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("is_recipe"))
            add(JsonPrimitive("title"))
            add(JsonPrimitive("ingredients"))
            add(JsonPrimitive("steps"))
        })
    }

    /**
     * Returns true when the DTO is confidently "bad enough" to warrant an
     * automatic single retry. Triggers on: empty result that still claims
     * to be a recipe (likely garbage), overall low confidence, or a majority
     * of individual items flagged low — without throwing an exception, so
     * the existing HeuristicStructurer fallback chain is undisturbed.
     */
    private fun shouldRetry(dto: GeminiRecipeDto): Boolean {
        if (!dto.isRecipe) return false

        if (dto.ingredients.isEmpty() && dto.steps.isEmpty()) return true

        if (dto.confidence == "low") return true

        val total = dto.ingredients.size + dto.steps.size
        if (total > 0) {
            val lowCount = dto.ingredients.count { it.confidence == "low" } +
                dto.steps.count { it.confidence == "low" }
            if (lowCount > total / 2) return true
        }

        return false
    }

    private fun parseResponseText(text: String): GeminiRecipeDto {
        // response_schema guarantees clean JSON, but fence-stripping stays as
        // a defensive no-op in case a future model/config change regresses this.
        return try {
            geminiJson.decodeFromString<GeminiRecipeDto>(cleanJsonText(text))
        } catch (e: Exception) {
            throw MalformedResponseException("Failed to parse recipe JSON: ${e.message}")
        }
    }

    private fun parseTranslationText(text: String): GeminiTranslationDto {
        return try {
            geminiJson.decodeFromString<GeminiTranslationDto>(cleanJsonText(text))
        } catch (e: Exception) {
            throw MalformedResponseException("Failed to parse translated recipe JSON: ${e.message}")
        }
    }

    private fun cleanJsonText(text: String): String = text.trim()
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    private fun buildTranslatePrompt(recipe: Recipe, targetLanguage: String): String = """
Translate this recipe into $targetLanguage.

ORIGINAL RECIPE JSON:
{${recipeToJson(recipe)}}

Return ONLY valid JSON, no markdown fences, no preamble, matching exactly this schema:
{
  "title": string,
  "notes": string|null,
  "ingredients": [
    {"amount": string|null, "unit": string|null, "name": string}
  ],
  "steps": [
    {"text": string, "timer_seconds": number|null}
  ]
}
Rules:
- Translate the title, notes, ingredient names/amounts/units, and every step into $targetLanguage.
- Keep the number of ingredients and steps identical to the original — do not add, remove, merge, or reorder them.
- Keep numeric amounts and values as-is (e.g. "1", "2.5", "1/2"); only translate the surrounding words (e.g. "cup", "tsp", "g").
- Keep timer_seconds identical to the original; do not change the number.
- Adapt units/measurement words naturally to $targetLanguage conventions (e.g. translate "cup" if the target language usually writes it out).
- If the original has no notes, return "notes": null.
""".trimIndent()

    private fun recipeToJson(recipe: Recipe): String = buildJsonObject {
        put("title", JsonPrimitive(recipe.title))
        recipe.notes?.let { put("notes", JsonPrimitive(it)) }
        put("ingredients", buildJsonArray {
            recipe.ingredients.forEach { ingredient ->
                add(buildJsonObject {
                    ingredient.amount?.let { put("amount", JsonPrimitive(it)) }
                    ingredient.unit?.let { put("unit", JsonPrimitive(it)) }
                    put("name", JsonPrimitive(ingredient.name))
                })
            }
        })
        put("steps", buildJsonArray {
            recipe.steps.forEach { step ->
                add(buildJsonObject {
                    put("text", JsonPrimitive(step.text))
                    step.timerSeconds?.let { put("timer_seconds", JsonPrimitive(it)) }
                })
            }
        })
    }.toString()

    private fun buildImagePrompt(ocrHint: String?): String {
        val hintBlock = if (!ocrHint.isNullOrBlank()) {
            """

On-device OCR already read this text from the same image (it may contain errors):
---
$ocrHint
---
Use it as a hint, but trust the image itself where the two disagree.
"""
        } else ""

        return """
Extract the recipe from this image of a recipe card.
Return ONLY valid JSON, no markdown fences, no preamble, matching exactly this schema:
{
  "is_recipe": boolean,
  "confidence": "high"|"medium"|"low",
  "title": string,
  "category": "Breakfast & Brunch"|"Appetizers & Snacks"|"Soups & Salads"|"Main Dishes"|"Side Dishes"|"Baked Goods"|"Desserts"|"Drinks & Cocktails"|"Sauces & Condiments"|"Other",
  "ingredients": [
    {"amount": string|null, "unit": string|null, "name": string, "confidence": "high"|"medium"|"low"}
  ],
  "steps": [
    {"text": string, "timer_seconds": number|null, "confidence": "high"|"medium"|"low"}
  ]
}
Rules:
- If servings isn't stated, use 4.
- category: one of exactly those strings, chosen based on the recipe's course or meal type (e.g. is it eaten for breakfast, served as a starter, a main course, a side, a baked good, a dessert, a drink, or a sauce/condiment). Use "Other" only when nothing else reasonably fits.
- timer_seconds: populate ONLY when the step has ONE clear, dominant, actionable wait/cook duration. Return null if the step mentions multiple different durations or covers multiple sub-actions with different timings — do not sum or guess.
- Do not invent ingredients or steps that aren't visible in the image.
- If the image isn't a recipe (blurry, wrong subject, receipt, etc), set is_recipe: false.
$hintBlock
""".trimIndent()
    }

    private fun buildTextPrompt(rawText: String): String = """
Extract the recipe from this OCR text scanned from a recipe card.
Return ONLY valid JSON, no markdown fences, no preamble, matching exactly this schema:
{
  "is_recipe": boolean,
  "confidence": "high"|"medium"|"low",
  "title": string,
  "category": "Breakfast & Brunch"|"Appetizers & Snacks"|"Soups & Salads"|"Main Dishes"|"Side Dishes"|"Baked Goods"|"Desserts"|"Drinks & Cocktails"|"Sauces & Condiments"|"Other",
  "ingredients": [
    {"amount": string|null, "unit": string|null, "name": string, "confidence": "high"|"medium"|"low"}
  ],
  "steps": [
    {"text": string, "timer_seconds": number|null, "confidence": "high"|"medium"|"low"}
  ]
}
Rules:
- If servings isn't stated, use 4.
- category: one of exactly those strings, chosen based on the recipe's course or meal type (e.g. is it eaten for breakfast, served as a starter, a main course, a side, a baked good, a dessert, a drink, or a sauce/condiment). Use "Other" only when nothing else reasonably fits.
- timer_seconds: populate ONLY when the step has ONE clear, dominant, actionable wait/cook duration. Return null if the step mentions multiple different durations or covers multiple sub-actions with different timings — do not sum or guess.
- Do not invent ingredients or steps that aren't in the text.

OCR TEXT:
$rawText
""".trimIndent()
}

private fun GeminiRecipeDto.toRecipe(): Recipe {
    if (!isRecipe) throw NotARecipeException(title.ifBlank { null })
    val partiallyBuilt = Recipe(
        id = UUID.randomUUID().toString(),
        title = title,
        servings = 4,
        ingredients = ingredients.mapIndexed { _, dto -> dto.toIngredient() },
        steps = steps.mapIndexed { _, dto -> dto.toStep() }
    )
    return partiallyBuilt.copy(category = resolveSuggestedCategory(partiallyBuilt))
}

/**
 * Category fallback chain for a Gemini-structured recipe:
 *   a. Gemini's own `category` string, if it exactly matches a valid displayName
 *      (never valueOf — Gemini can drift, e.g. "Meat & Seafood");
 *   b. offline keyword suggestion from title + parsed ingredients;
 *   c. OTHER.
 */
private fun GeminiRecipeDto.resolveSuggestedCategory(recipe: Recipe): RecipeCategory {
    category?.let { raw ->
        RecipeCategory.fromDisplayName(raw)?.let { return it }
    }
    val heuristic = CategorySuggester.suggest(title = recipe.title, recipe = recipe)
    return if (heuristic != RecipeCategory.OTHER) heuristic else RecipeCategory.OTHER
}

private fun GeminiIngredientDto.toIngredient() = RecipeIngredient(
    id = UUID.randomUUID().toString(),
    amount = amount,
    unit = unit,
    name = name,
    confidence = confidence
)

private fun GeminiStepDto.toStep() = RecipeStep(
    id = UUID.randomUUID().toString(),
    text = text,
    timerSeconds = timerSeconds,
    confidence = confidence
)
