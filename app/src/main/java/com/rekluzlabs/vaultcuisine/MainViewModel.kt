package com.rekluzlabs.vaultcuisine

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rekluzlabs.vaultcuisine.ai.AiCapability
import com.rekluzlabs.vaultcuisine.ai.GeminiNanoStructurer
import com.rekluzlabs.vaultcuisine.ai.HeuristicStructurer
import com.rekluzlabs.vaultcuisine.data.Recipe
import com.rekluzlabs.vaultcuisine.data.RecipeIngredient
import com.rekluzlabs.vaultcuisine.data.RecipeStep
import com.rekluzlabs.vaultcuisine.ocr.TextRecognizerHelper
import com.rekluzlabs.vaultcuisine.ui.edit.EditableLine
import com.rekluzlabs.vaultcuisine.ui.edit.LineDetail
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class SectionType { INGREDIENT, STEP }

class MainViewModel(private val app: VaultCuisineApp) : ViewModel() {

    private val dao = app.database.recipeDao()
    private val ocr = TextRecognizerHelper()

    val recipes: StateFlow<List<Recipe>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editableLines = MutableStateFlow<List<EditableLine>?>(null)
    val editableLines: StateFlow<List<EditableLine>?> = _editableLines

    private val _editingRecipe = MutableStateFlow<Recipe?>(null)

    private val _conversionEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val conversionEvents: SharedFlow<String> = _conversionEvents

    private val _newRecipeIds = mutableSetOf<String>()

    fun enterEditMode(recipeId: String) {
        val recipe = recipes.value.find { it.id == recipeId } ?: return
        _editingRecipe.value = recipe
        _editableLines.value = recipe.toEditableLines()
    }

    fun saveEdits() {
        val lines = _editableLines.value ?: return
        val recipe = _editingRecipe.value ?: return

        val ingredients = lines
            .filter { it.detail is LineDetail.Ingredient }
            .map { line ->
                val d = line.detail as LineDetail.Ingredient
                RecipeIngredient(
                    id = line.id,
                    amount = d.amount,
                    unit = d.unit,
                    name = line.text
                )
            }

        val steps = lines
            .filter { it.detail is LineDetail.Step }
            .map { line ->
                val d = line.detail as LineDetail.Step
                RecipeStep(
                    id = line.id,
                    text = line.text,
                    timerSeconds = d.timerSeconds
                )
            }

        viewModelScope.launch {
            dao.upsert(
                recipe.copy(
                    ingredients = ingredients,
                    steps = steps,
                    updatedAt = System.currentTimeMillis()
                )
            )
            _newRecipeIds.remove(recipe.id)
            _editableLines.value = null
            _editingRecipe.value = null
        }
    }

    fun cancelEdits() {
        val recipe = _editingRecipe.value ?: return
        val wasNew = _newRecipeIds.remove(recipe.id)
        _editableLines.value = null
        _editingRecipe.value = null
        if (wasNew) {
            viewModelScope.launch {
                dao.deleteById(recipe.id)
            }
        }
    }

    fun updateLineText(id: String, text: String) {
        _editableLines.value = _editableLines.value?.map { line ->
            if (line.id == id) line.copy(text = text) else line
        }
    }

    fun addLine(section: SectionType) {
        val lines = _editableLines.value?.toMutableList() ?: return
        val newLine = EditableLine(
            id = UUID.randomUUID().toString(),
            text = "",
            detail = when (section) {
                SectionType.INGREDIENT -> LineDetail.Ingredient(amount = null, unit = null)
                SectionType.STEP -> LineDetail.Step(timerSeconds = null)
            }
        )
        val insertIndex = when (section) {
            SectionType.INGREDIENT -> {
                val firstStep = lines.indexOfFirst { it.detail is LineDetail.Step }
                if (firstStep == -1) lines.size else firstStep
            }
            SectionType.STEP -> lines.size
        }
        lines.add(insertIndex, newLine)
        _editableLines.value = lines
    }

    fun deleteLine(id: String) {
        _editableLines.value = _editableLines.value?.filter { it.id != id }
    }

    fun moveToSection(id: String, section: SectionType) {
        val lines = _editableLines.value?.toMutableList() ?: return
        val index = lines.indexOfFirst { it.id == id }
        if (index == -1) return
        val line = lines.removeAt(index)
        val converted = line.copy(
            detail = when (section) {
                SectionType.INGREDIENT -> LineDetail.Ingredient(amount = null, unit = null)
                SectionType.STEP -> LineDetail.Step(timerSeconds = null)
            }
        )
        val insertIndex = when (section) {
            SectionType.INGREDIENT -> {
                val firstStep = lines.indexOfFirst { it.detail is LineDetail.Step }
                if (firstStep == -1) lines.size else firstStep
            }
            SectionType.STEP -> lines.size
        }
        lines.add(insertIndex, converted)
        _editableLines.value = lines
    }

    fun moveLineUp(id: String) {
        val lines = _editableLines.value?.toMutableList() ?: return
        val index = lines.indexOfFirst { it.id == id }
        if (index <= 0) return
        val item = lines.removeAt(index)
        lines.add(index - 1, item)
        _editableLines.value = enforceContiguousSections(lines)
    }

    fun moveLineDown(id: String) {
        val lines = _editableLines.value?.toMutableList() ?: return
        val index = lines.indexOfFirst { it.id == id }
        if (index == -1 || index >= lines.size - 1) return
        val item = lines.removeAt(index)
        lines.add(index + 1, item)
        _editableLines.value = enforceContiguousSections(lines)
    }

    fun reorderLine(fromIndex: Int, toIndex: Int) {
        val lines = _editableLines.value?.toMutableList() ?: return
        if (fromIndex !in lines.indices || toIndex !in lines.indices) return
        val item = lines.removeAt(fromIndex)
        lines.add(toIndex, item)
        _editableLines.value = enforceContiguousSections(lines)
    }

    private fun enforceContiguousSections(lines: List<EditableLine>): List<EditableLine> {
        val firstStep = lines.indexOfFirst { it.detail is LineDetail.Step }
        if (firstStep == -1 || firstStep == 0) return lines
        return lines.mapIndexed { index, line ->
            when {
                index < firstStep && line.detail !is LineDetail.Ingredient -> {
                    _conversionEvents.tryEmit("\"${line.text}\" — amount cleared")
                    line.copy(detail = LineDetail.Ingredient(amount = null, unit = null))
                }
                index >= firstStep && line.detail !is LineDetail.Step -> {
                    _conversionEvents.tryEmit("\"${line.text}\" — timer cleared")
                    line.copy(detail = LineDetail.Step(timerSeconds = null))
                }
                else -> line
            }
        }
    }

    private fun Recipe.toEditableLines(): List<EditableLine> {
        val ingredientLines = ingredients.map { ing ->
            EditableLine(
                id = ing.id,
                text = ing.name,
                detail = LineDetail.Ingredient(
                    amount = ing.amount,
                    unit = ing.unit
                )
            )
        }
        val stepLines = steps.map { step ->
            EditableLine(
                id = step.id,
                text = step.text,
                detail = LineDetail.Step(timerSeconds = step.timerSeconds)
            )
        }
        return ingredientLines + stepLines
    }

    /**
     * Runs the scan pipeline: OCR -> AI structuring (Gemini Nano multimodal
     * if available, text-only Nano if available but no image ref needed,
     * heuristic fallback otherwise) -> save to Room -> callback with new ID
     * so the caller can navigate to the review/edit screen.
     */
    fun processScannedImage(bitmap: Bitmap, onSaved: (recipeId: String) -> Unit) {
        viewModelScope.launch {
            val rawText = ocr.recognizeText(bitmap)

            val nano = GeminiNanoStructurer(app)
            val isAvailable = AiCapability.check(app) == AiCapability.AVAILABLE

            // Explicit routing: multimodal only on AVAILABLE, heuristic on
            // DOWNLOADABLE/UNAVAILABLE — never call Nano as an implicit fallback.
            val recipe = try {
                if (isAvailable)
                    nano.structureMultimodal(rawText, bitmap)
                else
                    HeuristicStructurer().structure(rawText)
            } catch (e: Throwable) {
                HeuristicStructurer().structure(rawText)
            }

            dao.upsert(recipe)
            _newRecipeIds.add(recipe.id)
            onSaved(recipe.id)
        }
    }

    fun deleteRecipe(id: String) {
        viewModelScope.launch {
            dao.deleteById(id)
        }
    }

    companion object {
        fun factory(app: VaultCuisineApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(app) as T
        }
    }
}
