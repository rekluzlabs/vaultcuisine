package com.rekluzlabs.vaultcuisine.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.rekluzlabs.vaultcuisine.data.Recipe
import com.rekluzlabs.vaultcuisine.data.RecipeIngredient
import com.rekluzlabs.vaultcuisine.data.RecipeStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

private val nanoJson = Json { ignoreUnknownKeys = true }

class GeminiNanoStructurer(private val context: Context) : RecipeStructurer {

    private val generativeModel = Generation.getClient()

    override suspend fun structure(rawText: String): Recipe {
        val normalized = TextNormalizer.normalize(rawText)
        val fullPrompt = PROMPT_TEMPLATE + normalized

        val response = generativeModel.generateContent(fullPrompt)
        return parseResponse(response.candidates.firstOrNull()?.text)
    }

    suspend fun structureMultimodal(rawText: String, image: Bitmap): Recipe {
        val maxDim = maxOf(image.width, image.height)
        val scale = MULTIMODAL_MAX_PX.toFloat() / maxDim.toFloat()
        val scaled = if (maxDim <= MULTIMODAL_MAX_PX) {
            image
        } else {
            Bitmap.createScaledBitmap(
                image,
                (image.width * scale).toInt(),
                (image.height * scale).toInt(),
                true
            )
        }
        val normalized = TextNormalizer.normalize(rawText)
        val fullPrompt = MULTIMODAL_PROMPT_TEMPLATE + normalized

        val response = generativeModel.generateContent(
            generateContentRequest(ImagePart(scaled), TextPart(fullPrompt)) { }
        )
        return parseResponse(response.candidates.firstOrNull()?.text)
    }

    private fun parseResponse(rawText: String?): Recipe {
        val text = rawText ?: throw IllegalStateException("Empty response from Gemini Nano")
        val jsonString = JsonExtractor.extractJsonOrNull(text)
            ?: throw IllegalStateException("No JSON found in Gemini Nano output")
        val recipeDto = nanoJson.decodeFromString<RecipeDto>(jsonString)
        return recipeDto.toRecipe()
    }

    companion object {
         const val PROMPT_TEMPLATE = """
You are extracting a recipe from OCR text scanned from a handwritten or printed recipe card.
Return ONLY valid JSON, no markdown fences, no commentary, matching exactly this shape:

{
  "title": string,
  "servings": number,
  "ingredients": [ { "amount": string|null, "unit": string|null, "name": string } ],
  "steps": [ { "text": string, "timerSeconds": number|null } ],
  "notes": string|null
}

Rules:
- If servings isn't stated, use 4.
- Write the amount as a string, e.g. "2", "one and a half", "a pinch".
- If a step implies a wait/cook time (e.g. "simmer for 20 minutes"), set timerSeconds accordingly; otherwise null.
- Do not invent ingredients or steps that aren't in the text.

OCR TEXT:
"""

        private const val MULTIMODAL_MAX_PX = 1024

        val MULTIMODAL_PROMPT_TEMPLATE = """
You are a chef transcribing a recipe from a photo of a handwritten or printed recipe card.
You will receive:
  1. The photo of the recipe card (this is the ground truth — trust it).
  2. A rough OCR text draft below (may contain errors, omissions, or garbled text).

Use the photo to correct any mistakes in the OCR draft, then return ONLY valid JSON, no markdown fences, no commentary, matching exactly this shape:

{
  "title": string,
  "servings": number,
  "ingredients": [ { "amount": string|null, "unit": string|null, "name": string } ],
  "steps": [ { "text": string, "timerSeconds": number|null } ],
  "notes": string|null
}

Rules:
- If servings isn't stated, use 4.
- Write the amount as a string, e.g. "2", "one and a half", "a pinch".
- If a step implies a wait/cook time (e.g. "simmer for 20 minutes"), set timerSeconds accordingly; otherwise null.
- Do not invent ingredients or steps that aren't visible in the photo.

OCR DRAFT (may be inaccurate):
"""
    }
}

enum class AiCapability {
    AVAILABLE, DOWNLOADABLE, UNAVAILABLE;

    companion object {
        private const val TAG = "AiCapability"

        @Volatile
        private var downloadInitiated = false

        suspend fun check(context: Context): AiCapability {
            return try {
                val model = Generation.getClient()
                when (val status = model.checkStatus()) {
                    FeatureStatus.AVAILABLE -> AVAILABLE
                    FeatureStatus.DOWNLOADABLE -> {
                        if (!downloadInitiated) {
                            downloadInitiated = true
                            triggerDownload(model)
                        }
                        DOWNLOADABLE
                    }
                    FeatureStatus.DOWNLOADING -> DOWNLOADABLE
                    FeatureStatus.UNAVAILABLE -> UNAVAILABLE
                    else -> UNAVAILABLE
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check AICore status", e)
                UNAVAILABLE
            }
        }

        private fun triggerDownload(model: com.google.mlkit.genai.prompt.GenerativeModel) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    model.download().collect { status ->
                        when (status) {
                            is DownloadStatus.DownloadStarted ->
                                Log.d(TAG, "Gemini Nano download started")
                            is DownloadStatus.DownloadProgress ->
                                Log.d(TAG, "Downloaded ${status.totalBytesDownloaded} bytes")
                            DownloadStatus.DownloadCompleted ->
                                Log.d(TAG, "Gemini Nano download complete")
                            is DownloadStatus.DownloadFailed ->
                                Log.w(TAG, "Gemini Nano download failed", status.e)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Download exception", e)
                }
            }
        }
    }
}

@kotlinx.serialization.Serializable
private data class RecipeDto(
    val title: String,
    val servings: Int = 4,
    val ingredients: List<IngredientDto> = emptyList(),
    val steps: List<StepDto> = emptyList(),
    val notes: String? = null
)

@kotlinx.serialization.Serializable
private data class IngredientDto(
    val amount: JsonElement? = null,
    val unit: String? = null,
    val name: String
)

@kotlinx.serialization.Serializable
private data class StepDto(
    val text: String,
    val timerSeconds: Int? = null
)

private fun RecipeDto.toRecipe(): Recipe = Recipe(
    id = UUID.randomUUID().toString(),
    title = title,
    servings = servings,
    ingredients = ingredients.map { ing ->
        RecipeIngredient(
            id = UUID.randomUUID().toString(),
            amount = ing.amount?.jsonPrimitive?.content,
            unit = ing.unit,
            name = ing.name
        )
    },
    steps = steps.map { step ->
        RecipeStep(
            id = UUID.randomUUID().toString(),
            text = step.text,
            timerSeconds = step.timerSeconds
        )
    },
    notes = notes
)
