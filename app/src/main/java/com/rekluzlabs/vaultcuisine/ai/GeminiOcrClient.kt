package com.rekluzlabs.vaultcuisine.ai

import com.rekluzlabs.vaultcuisine.data.Recipe
import com.rekluzlabs.vaultcuisine.data.RecipeIngredient
import com.rekluzlabs.vaultcuisine.data.RecipeStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.int
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerialName
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
        // Default to a sane model if none provided (legacy compatibility or safety)
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
        // Default to a sane model if none provided
        return structureFromImage(imageBytes, "gemini-2.5-flash")
    }

    suspend fun structureFromImage(imageBytes: ByteArray, modelId: String): Recipe {
        val apiKey = credentialStore.getApiKey() ?: throw MissingApiKeyException()
        val processed = imagePreprocessor.prepareForUpload(imageBytes)
        val prompt = buildImagePrompt()
        val responseJson = callGeminiApi(apiKey, modelId, prompt, processed)
        val dto = parseResponseText(responseJson)
        return dto.toRecipe()
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
                        put("mime_type", "image/jpeg")
                        put("data", base64)
                    })
                })
            }
            add(buildJsonObject {
                put("text", textPart)
            })
        }

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", parts)
                })
            })
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

    private fun parseResponseText(text: String): GeminiRecipeDto {
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

    private fun buildImagePrompt(): String = """
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
""".trimIndent()

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
