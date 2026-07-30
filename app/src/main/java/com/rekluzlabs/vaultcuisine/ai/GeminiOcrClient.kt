package com.rekluzlabs.vaultcuisine.ai

import com.rekluzlabs.vaultcuisine.data.Recipe
import com.rekluzlabs.vaultcuisine.data.RecipeIngredient
import com.rekluzlabs.vaultcuisine.data.RecipeStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    val content: Content? = null
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

data class ScanResult(val recipe: Recipe, val retried: Boolean)

class GeminiOcrClient(
    private val credentialStore: GeminiCredentialStore,
    private val imagePreprocessor: ImagePreprocessor = ImagePreprocessor(),
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : RecipeStructurer, ImageCapableStructurer {

    override suspend fun structure(rawText: String): Recipe {
        if (!credentialStore.hasApiKey()) throw MissingApiKeyException()
        return structure(rawText, "gemini-2.5-flash")
    }

    suspend fun structure(rawText: String, modelId: String): Recipe {
        val apiKey = credentialStore.getApiKey() ?: throw MissingApiKeyException()
        val prompt = buildTextPrompt(rawText)
        val responseJson = callGeminiApi(apiKey, modelId, prompt, imageBytes = null)
        val dto = parseResponseText(responseJson)
        return dto.toRecipe()
    }

    override suspend fun structureFromImage(imageBytes: ByteArray): Recipe {
        return structureFromImage(imageBytes, "gemini-2.5-flash", ocrHint = null)
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

    private suspend fun callGeminiApi(apiKey: String, modelId: String, textPart: String, imageBytes: ByteArray?): String {
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
            put("generationConfig", buildGenerationConfig())
        }

        val bodyString = requestBody.toString()
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey")
            .post(bodyString.toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            val response = try {
                okHttpClient.newCall(request).execute()
            } catch (e: Exception) {
                throw NetworkException(e)
            }

            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw when (response.code) {
                    429 -> RateLimitException()
                    in 400..499 -> ApiException(response.code, "Client error $response.code: $responseBody")
                    else -> ApiException(response.code, "Server error $response.code: $responseBody")
                }
            }

            val apiResponse = try {
                geminiJson.decodeFromString<GeminiApiResponse>(responseBody)
            } catch (e: Exception) {
                throw MalformedResponseException("Failed to parse API response: ${e.message}")
            }

            apiResponse.candidates?.firstOrNull()
                ?.content?.parts?.firstOrNull()
                ?.text ?: throw MalformedResponseException("No text content in Gemini response")
        }
    }

    /**
     * Deterministic extraction settings + a JSON schema constraint. With
     * response_mime_type/response_schema set, Gemini's decoder is constrained
     * to emit matching JSON directly — no markdown fences, no preamble, no
     * malformed output falling through to HeuristicStructurer.
     */
    private fun buildGenerationConfig() = buildJsonObject {
        put("temperature", JsonPrimitive(0.1))
        put("topP", JsonPrimitive(0.95))
        put("maxOutputTokens", JsonPrimitive(2048))
        put("response_mime_type", JsonPrimitive("application/json"))
        put("response_schema", recipeResponseSchema())
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
        val cleaned = text.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return try {
            geminiJson.decodeFromString<GeminiRecipeDto>(cleaned)
        } catch (e: Exception) {
            throw MalformedResponseException("Failed to parse recipe JSON: ${e.message}")
        }
    }

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
  "ingredients": [
    {"amount": string|null, "unit": string|null, "name": string, "confidence": "high"|"medium"|"low"}
  ],
  "steps": [
    {"text": string, "timer_seconds": number|null, "confidence": "high"|"medium"|"low"}
  ]
}
Rules:
- If servings isn't stated, use 4.
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
  "ingredients": [
    {"amount": string|null, "unit": string|null, "name": string, "confidence": "high"|"medium"|"low"}
  ],
  "steps": [
    {"text": string, "timer_seconds": number|null, "confidence": "high"|"medium"|"low"}
  ]
}
Rules:
- If servings isn't stated, use 4.
- timer_seconds: populate ONLY when the step has ONE clear, dominant, actionable wait/cook duration. Return null if the step mentions multiple different durations or covers multiple sub-actions with different timings — do not sum or guess.
- Do not invent ingredients or steps that aren't in the text.

OCR TEXT:
$rawText
""".trimIndent()
}

private fun GeminiRecipeDto.toRecipe(): Recipe {
    if (!isRecipe) throw NotARecipeException(title.ifBlank { null })
    return Recipe(
        id = UUID.randomUUID().toString(),
        title = title,
        servings = 4,
        ingredients = ingredients.mapIndexed { _, dto -> dto.toIngredient() },
        steps = steps.mapIndexed { _, dto -> dto.toStep() }
    )
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
