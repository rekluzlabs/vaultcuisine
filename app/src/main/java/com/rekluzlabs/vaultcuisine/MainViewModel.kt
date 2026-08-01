package com.rekluzlabs.vaultcuisine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rekluzlabs.vaultcuisine.ai.CategorySuggester
import com.rekluzlabs.vaultcuisine.ai.GeminiCredentialStore
import com.rekluzlabs.vaultcuisine.ai.GeminiModels
import com.rekluzlabs.vaultcuisine.ai.GeminiOcrClient
import com.rekluzlabs.vaultcuisine.ai.HeuristicStructurer
import com.rekluzlabs.vaultcuisine.ai.ImagePreprocessor
import com.rekluzlabs.vaultcuisine.ai.MissingApiKeyException
import com.rekluzlabs.vaultcuisine.ai.NetworkException
import com.rekluzlabs.vaultcuisine.ai.NotARecipeException
import com.rekluzlabs.vaultcuisine.ai.RateLimitException
import com.rekluzlabs.vaultcuisine.ai.sanitizeGeminiMessage
import com.rekluzlabs.vaultcuisine.data.AppSettings
import com.rekluzlabs.vaultcuisine.data.CURRENT_SCHEMA_VERSION
import com.rekluzlabs.vaultcuisine.data.FALLBACK_NOTES_MESSAGE
import com.rekluzlabs.vaultcuisine.data.Recipe
import com.rekluzlabs.vaultcuisine.data.RecipeCategory
import com.rekluzlabs.vaultcuisine.data.RecipeIngredient
import com.rekluzlabs.vaultcuisine.data.RecipeStep
import com.rekluzlabs.vaultcuisine.data.SupportedLanguages
import com.rekluzlabs.vaultcuisine.data.backup.BackupManager
import com.rekluzlabs.vaultcuisine.data.backup.BackupResult
import com.rekluzlabs.vaultcuisine.ocr.TextRecognizerHelper
import com.rekluzlabs.vaultcuisine.timer.ActiveTimer
import com.rekluzlabs.vaultcuisine.timer.TimerBroadcastReceiver
import com.rekluzlabs.vaultcuisine.timer.TimerRingService
import com.rekluzlabs.vaultcuisine.ui.edit.EditableLine
import com.rekluzlabs.vaultcuisine.ui.edit.LineDetail
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class SectionType { INGREDIENT, STEP }

class MainViewModel(private val app: VaultCuisineApp) : ViewModel() {

    private val prefs = app.preferences
    private val dao = app.database.recipeDao()
    private val ocr = TextRecognizerHelper()
    val credentialStore = GeminiCredentialStore(app)
    private val imagePreprocessor = ImagePreprocessor()
    private val geminiClient = GeminiOcrClient(credentialStore, imagePreprocessor)
    private val backupManager by lazy {
        BackupManager(dao, app.database, File(app.filesDir, IMAGE_DIR), app.cacheDir)
    }

    private val _pendingRestoreUri = MutableStateFlow<Uri?>(null)
    val pendingRestoreUri: StateFlow<Uri?> = _pendingRestoreUri

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring

    private val _geminiKeyVerified = MutableStateFlow(false)
    val geminiKeyVerified: StateFlow<Boolean> = _geminiKeyVerified

    val recipes: StateFlow<List<Recipe>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _settings = MutableStateFlow(prefs.load().sanitizeGeminiModel())
    val settings: StateFlow<AppSettings> = _settings
    private val _scanMessage = MutableStateFlow("Reading your recipe…")
    val scanMessage: StateFlow<String> = _scanMessage

    private val _lastScannedImageBytes = MutableStateFlow<ByteArray?>(null)

    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessages: SharedFlow<String> = _userMessages

    // View-only recipe translations, keyed by recipe id. Originals in the DB
    // are never touched — this is a per-session cache for display purposes.
    private val _recipeTranslations = MutableStateFlow<Map<String, Recipe>>(emptyMap())
    val recipeTranslations: StateFlow<Map<String, Recipe>> = _recipeTranslations

    private val _translatingRecipeIds = MutableStateFlow<Set<String>>(emptySet())
    val translatingRecipeIds: StateFlow<Set<String>> = _translatingRecipeIds

    private val _translationErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val translationErrors: StateFlow<Map<String, String>> = _translationErrors

    private val _editableLines = MutableStateFlow<List<EditableLine>?>(null)
    val editableLines: StateFlow<List<EditableLine>?> = _editableLines

    private val _editingRecipe = MutableStateFlow<Recipe?>(null)

    private val _conversionEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val conversionEvents: SharedFlow<String> = _conversionEvents

    private val _retryCompleted = Channel<String>(Channel.CONFLATED)
    val retryCompleted: Flow<String> = _retryCompleted.receiveAsFlow()

    private val _newRecipeIds = mutableSetOf<String>()

    // ── Gemini consent ──

    private val _needsGeminiConsent = MutableStateFlow(false)
    val needsGeminiConsent: StateFlow<Boolean> = _needsGeminiConsent

    private val _isConsentFromSettings = MutableStateFlow(false)
    val isConsentFromSettings: StateFlow<Boolean> = _isConsentFromSettings

    private val _consentResult = Channel<Boolean>(Channel.CONFLATED)

    fun acceptGeminiConsent() {
        _needsGeminiConsent.value = false
        _isConsentFromSettings.value = false
        _consentResult.trySend(true)
    }

    /**
     * Sets whether the consent dialog should be shown before each image is
     * sent to Gemini. When off, images are sent without prompting.
     */
    fun setShowGeminiConsent(show: Boolean) {
        _settings.value = _settings.value.copy(showGeminiConsentDialog = show)
        prefs.save(_settings.value)
    }

    fun rejectGeminiConsent() {
        _needsGeminiConsent.value = false
        _isConsentFromSettings.value = false
        _consentResult.trySend(false)
    }

    fun reviewGeminiConsent() {
        _isConsentFromSettings.value = true
        _needsGeminiConsent.value = true
    }

    fun dismissGeminiConsentReview() {
        _needsGeminiConsent.value = false
        _isConsentFromSettings.value = false
    }

    // ── Per-scan route choice (local vs Gemini) ──

    private val _scanChoiceRequested = MutableStateFlow(false)
    val scanChoiceRequested: StateFlow<Boolean> = _scanChoiceRequested

    private val _scanRouteResult = Channel<Boolean>(Channel.CONFLATED)

    /**
     * Called by the scan-route dialog. [useGemini] true routes this scan to
     * Gemini and flips the master Gemini toggle on; false scans locally.
     */
    fun chooseScanRoute(useGemini: Boolean) {
        if (useGemini) {
            _settings.value = _settings.value.copy(geminiEnabled = true)
            prefs.save(_settings.value)
        }
        _scanRouteResult.trySend(useGemini)
    }

    /**
     * Blocks until the user answers the per-scan "scan locally or with
     * Gemini?" dialog. Only invoked when a key exists but the master toggle
     * is off, so the user can opt in to a single cloud scan without hunting
     * through Settings first.
     */
    private suspend fun awaitScanRouteChoice(): Boolean {
        _scanRouteResult.tryReceive()
        _scanChoiceRequested.value = true
        return _scanRouteResult.receive()
    }

    /** Returns true if the caller should proceed with the Gemini call. */
    private suspend fun awaitGeminiConsent(): Boolean {
        if (!_settings.value.showGeminiConsentDialog) return true
        _needsGeminiConsent.value = true
        return _consentResult.receive()
    }

    // ── Category selection (blocking step before first save) ──

    /**
     * Set to the recipe currently awaiting a category pick. UI shows
     * CategoryPickerDialog whenever this is non-null (scan completion and
     * import path both block here before the recipe is persisted).
     */
    private val _recipeAwaitingCategory = MutableStateFlow<Recipe?>(null)
    val recipeAwaitingCategory: StateFlow<Recipe?> = _recipeAwaitingCategory

    private val _categorySelectionResult = Channel<RecipeCategory?>(Channel.CONFLATED)

    fun confirmCategorySelection(category: RecipeCategory) {
        _recipeAwaitingCategory.value = null
        _categorySelectionResult.trySend(category)
    }

    fun cancelCategorySelection() {
        _recipeAwaitingCategory.value = null
        _categorySelectionResult.trySend(null)
    }

    /** Discards stale values, flags [recipe] as awaiting a pick, then blocks. */
    private suspend fun awaitCategorySelection(recipe: Recipe): RecipeCategory? {
        _categorySelectionResult.tryReceive()
        _recipeAwaitingCategory.value = recipe
        return _categorySelectionResult.receive()
    }

    /**
     * Pre-fills a freshly structured recipe with a suggested category, but
     * only when no category decision already exists. Gemini-structured
     * recipes carry a resolved category from [ai.GeminiOcrClient]'s fallback
     * chain; the heuristic-only fallback leaves the default OTHER, so that's
     * the case [ai.CategorySuggester] fills in here. An existing non-OTHER
     * category (e.g. the user previously moved the recipe) is always respected.
     */
    private fun Recipe.withSuggestedCategory(): Recipe {
        if (category != RecipeCategory.OTHER) return this
        val suggestion = CategorySuggester.suggest(title = title, recipe = this)
        return if (suggestion == RecipeCategory.OTHER) this else copy(category = suggestion)
    }

    fun updateSettings(s: AppSettings) {
        _settings.value = s
        prefs.save(s)
    }

    /**
     * Migrates a stored model ID that no longer exists (e.g. retired
     * `gemini-2.5-flash`) back to the current default so the dropdown always
     * shows a selectable, live model.
     */
    private fun AppSettings.sanitizeGeminiModel(): AppSettings {
        if (GeminiModels.variants.any { it.id == geminiModelId }) return this
        return copy(geminiModelId = GeminiModels.DEFAULT_MODEL_ID)
    }

    fun setHomeTileOrder(order: List<String>) {
        updateSettings(_settings.value.copy(homeTileOrder = order))
    }

    fun setHomeTilePinned(key: String, pinned: Boolean) {
        val current = _settings.value.pinnedHomeTiles
        val updated = if (pinned) {
            if (key in current) current else current + key
        } else {
            current - key
        }
        updateSettings(_settings.value.copy(pinnedHomeTiles = updated))
    }

    /** Pins or unpins an individual recipe so it floats to the top of its list. */
    fun setRecipePinned(recipeId: String, pinned: Boolean) {
        val current = _settings.value.pinnedRecipeIds
        val updated = if (pinned) {
            if (recipeId in current) current else current + recipeId
        } else {
            current - recipeId
        }
        updateSettings(_settings.value.copy(pinnedRecipeIds = updated))
    }

    // ── Gemini API key ──

    fun saveGeminiApiKey(key: String) {
        credentialStore.saveApiKey(key)
        _settings.value = _settings.value.copy(showGeminiConsentDialog = true)
        prefs.save(_settings.value)
        _geminiKeyVerified.value = true
    }

    fun clearGeminiApiKey() {
        credentialStore.clearApiKey()
        _geminiKeyVerified.value = false
    }

    suspend fun validateGeminiApiKey(apiKey: String): Boolean {
        return geminiClient.validateApiKey(apiKey)
    }

    fun hasGeminiApiKey(): Boolean = credentialStore.hasApiKey()

    fun clearLastScannedImageBytes() {
        _lastScannedImageBytes.value = null
    }

    /**
     * Manual "Try again" re-scan of the last captured image bytes (bypasses
     * confidence-triggered retry — the user is the judge this time).
     */
    fun rescanCurrentImage() {
        viewModelScope.launch {
            val imageBytes = _lastScannedImageBytes.value ?: return@launch
            if (!settings.value.geminiEnabled || !credentialStore.hasApiKey()) {
                _userMessages.tryEmit("Gemini API features are disabled in Settings.")
                return@launch
            }
            _scanMessage.value = "Gemini is reading your recipe"
            val modelId = settings.value.geminiModelId

            Log.d("GeminiOcrClient", "manual retry (Try Again) starting")

            val result = try {
                geminiClient.structureFromImageWithMeta(imageBytes, modelId, ocrHint = null)
            } catch (e: Exception) {
                Log.d("GeminiOcrClient", "manual retry failed: ${sanitizeGeminiMessage(e.message)}")
                _userMessages.tryEmit("Retry failed. Try retaking the photo.")
                return@launch
            }

            Log.d("GeminiOcrClient", "manual retry (Try Again) complete")

            val structured = result.recipe
                .copy(sourceImagePath = null, notes = null)
                .withSuggestedCategory()
            val category = awaitCategorySelection(structured)
            if (category == null) return@launch

            val saved = structured.copy(category = category)
            dao.upsert(saved)
            _newRecipeIds.add(saved.id)
            _retryCompleted.send(saved.id)
        }
    }

    // ── Edit mode ──

    private fun startEditSession(recipe: Recipe) {
        _editingRecipe.value = recipe
        _editableLines.value = recipe.toEditableLines()
        _editingTitle.value = recipe.title
        _editingServings.value = recipe.servings
        _editingNotes.value = if (recipe.notes == FALLBACK_NOTES_MESSAGE) null else recipe.notes
        _editingCategory.value = recipe.category
    }

    fun enterEditMode(recipeId: String) {
        val recipe = recipes.value.find { it.id == recipeId } ?: return
        startEditSession(recipe)
    }

    /** Creates a blank recipe and opens it in edit mode so the user can type it out by hand. */
    fun createManualRecipe(category: RecipeCategory = RecipeCategory.OTHER, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val recipe = Recipe(
                id = UUID.randomUUID().toString(),
                schemaVersion = CURRENT_SCHEMA_VERSION,
                title = "",
                ingredients = emptyList(),
                steps = emptyList(),
                notes = null,
                category = category
            )
            dao.upsert(recipe)
            _newRecipeIds.add(recipe.id)
            startEditSession(recipe)
            onCreated(recipe.id)
        }
    }

    // ── Edit mode: title ──

    private val _editingTitle = MutableStateFlow("")
    val editingTitle: StateFlow<String> = _editingTitle

    fun setEditingTitle(t: String) {
        _editingTitle.value = t
    }

    // ── Edit mode: servings ──

    private val _editingServings = MutableStateFlow<Int?>(null)
    val editingServings: StateFlow<Int?> = _editingServings

    fun setEditingServings(s: Int?) {
        _editingServings.value = s
    }

    // ── Edit mode: notes ──

    private val _editingNotes = MutableStateFlow<String?>(null)
    val editingNotes: StateFlow<String?> = _editingNotes

    fun setEditingNotes(text: String?) {
        _editingNotes.value = text
    }

    // ── Edit mode: category ──

    private val _editingCategory = MutableStateFlow(RecipeCategory.OTHER)
    val editingCategory: StateFlow<RecipeCategory> = _editingCategory

    fun setEditingCategory(category: RecipeCategory) {
        _editingCategory.value = category
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
                    title = _editingTitle.value,
                    servings = _editingServings.value,
                    ingredients = ingredients,
                    steps = steps,
                    notes = _editingNotes.value,
                    category = _editingCategory.value,
                    updatedAt = System.currentTimeMillis()
                )
            )
            _newRecipeIds.remove(recipe.id)
            _editableLines.value = null
            _editingRecipe.value = null
            _editingTitle.value = ""
            _editingServings.value = null
            _editingNotes.value = null
            _editingCategory.value = RecipeCategory.OTHER
        }
    }

    fun cancelEdits() {
        val recipe = _editingRecipe.value ?: return
        val wasNew = _newRecipeIds.remove(recipe.id)
        _editableLines.value = null
        _editingRecipe.value = null
        _editingTitle.value = ""
        _editingServings.value = null
        _editingNotes.value = null
        _editingCategory.value = RecipeCategory.OTHER
        if (wasNew) {
            viewModelScope.launch {
                dao.deleteById(recipe.id)
                deleteImageFile(recipe.sourceImagePath)
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
     * Runs the scan pipeline:
     *   1. Gemini image mode (key + image bytes available)
     *   2. Gemini text mode (key present, image bytes unavailable)
     *   3. HeuristicStructurer (fully offline)
     *
     * Saves the original Bitmap to app-private storage as the permanent
     * reference copy before discarding. The category picker is a blocking
     * step right after structuring completes — the recipe is not persisted
     * until the user confirms a category (or cancels the scan entirely).
     */
    /**
     * @param defaultCategory when non-null (e.g. scan launched from a category
     *   screen), the post-scan category picker pre-selects this value.
     */
    fun processScannedImage(
        bitmap: Bitmap,
        defaultCategory: RecipeCategory? = null,
        onSaved: (recipeId: String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _scanMessage.value = "Reading your recipe…"
            val rawText = ocr.recognizeText(bitmap, _settings.value.ocrLanguage)

            val hasKey = credentialStore.hasApiKey()
            val useGemini = if (hasKey && settings.value.geminiEnabled) {
                true
            } else if (hasKey) {
                // Key present but the master toggle is off — let the user
                // choose per-scan. Picking Gemini flips the toggle on.
                _scanChoiceRequested.value = false
                awaitScanRouteChoice()
            } else {
                false
            }
            val recipe = if (useGemini) {
                val imageBytes = try {
                    bitmapToJpegBytes(bitmap)
                } catch (_: Exception) {
                    null
                }
                if (imageBytes != null) {
                    // Tier 1: Gemini image mode (with consent gate)
                    if (awaitGeminiConsent()) {
                        _lastScannedImageBytes.value = imageBytes
                        _scanMessage.value = "Gemini is reading your recipe"
                        runGeminiImageMode(rawText, imageBytes, settings.value.geminiModelId)
                    } else {
                        _scanMessage.value = "Recipe being read locally on device only"
                        HeuristicStructurer().structure(rawText)
                    }
                } else {
                    // Tier 2: Gemini text mode (image bytes unavailable)
                    _scanMessage.value = "Gemini is reading your recipe"
                    runGeminiTextMode(rawText, settings.value.geminiModelId)
                }
            } else {
                // Tier 3: heuristic
                _scanMessage.value = "Recipe being read locally on device only"
                HeuristicStructurer().structure(rawText)
            }

            val imagePath = saveOriginalImage(bitmap, recipe.id)
            val withImage = recipe
                .copy(sourceImagePath = imagePath)
                .withSuggestedCategory()
                .let { if (defaultCategory != null) it.copy(category = defaultCategory) else it }

            val category = awaitCategorySelection(withImage)
            if (category == null) {
                // Scan cancelled before the recipe was ever persisted — clean up.
                deleteImageFile(imagePath)
                onCancelled()
                return@launch
            }

            val saved = withImage.copy(category = category)
            dao.upsert(saved)
            _newRecipeIds.add(saved.id)
            onSaved(saved.id)
        }
    }

    private suspend fun runGeminiImageMode(rawText: String, imageBytes: ByteArray, modelId: String): Recipe {
        return try {
            val result = geminiClient.structureFromImageWithMeta(
                imageBytes, modelId,
                ocrHint = rawText,
                onRetry = { _scanMessage.value = "Getting a clearer read…" }
            )
            result.recipe
        } catch (_: MissingApiKeyException) {
            Log.e("GeminiScan", "image mode: missing API key")
            _scanMessage.value = "Recipe being read locally on device only"
            HeuristicStructurer().structure(rawText)
        } catch (_: NotARecipeException) {
            Log.e("GeminiScan", "image mode: not a recipe")
            _userMessages.tryEmit("This doesn't look like a recipe. Falling back to text-based parsing.")
            _scanMessage.value = "Recipe being read locally on device only"
            HeuristicStructurer().structure(rawText)
        } catch (e: RateLimitException) {
            Log.e("GeminiScan", "image mode: rate limited", e)
            _userMessages.tryEmit(quotaMessage(e.retryAfterSeconds))
            _scanMessage.value = "Recipe being read locally on device only"
            HeuristicStructurer().structure(rawText)
        } catch (e: NetworkException) {
            Log.e("GeminiScan", "image mode: network error, retrying without meta", e)
            try {
                geminiClient.structureFromImage(imageBytes)
            } catch (e2: Exception) {
                Log.e("GeminiScan", "image mode: retry also failed", e2)
                _scanMessage.value = "Recipe being read locally on device only"
                HeuristicStructurer().structure(rawText)
            }
        } catch (e: Exception) {
            Log.e("GeminiScan", "image mode: $modelId failed", e)
            _scanMessage.value = "Recipe being read locally on device only"
            HeuristicStructurer().structure(rawText)
        }
    }

    private suspend fun runGeminiTextMode(rawText: String, modelId: String): Recipe {
        return try {
            geminiClient.structure(rawText, modelId)
        } catch (e: RateLimitException) {
            Log.e("GeminiScan", "text mode: rate limited", e)
            _userMessages.tryEmit(quotaMessage(e.retryAfterSeconds))
            _scanMessage.value = "Recipe being read locally on device only"
            HeuristicStructurer().structure(rawText)
        } catch (e: Exception) {
            Log.e("GeminiScan", "text mode: $modelId failed", e)
            _scanMessage.value = "Recipe being read locally on device only"
            HeuristicStructurer().structure(rawText)
        }
    }

    private fun quotaMessage(retryAfterSeconds: Int?): String {
        val retryHint = retryAfterSeconds
            ?.takeIf { it > 0 }
            ?.let { seconds ->
                val minutes = (seconds / 60).coerceAtLeast(1)
                " — try again in ~$minutes min"
            }
            ?: ""
        return "You've used up your free Gemini quota$retryHint. Falling back to offline parsing."
    }

    // ── Recipe translation (view-only, Gemini) ──

    fun translateRecipe(recipe: Recipe, targetLanguage: String) {
        if (_translatingRecipeIds.value.contains(recipe.id)) return
        viewModelScope.launch {
            _translatingRecipeIds.value = _translatingRecipeIds.value + recipe.id
            _translationErrors.value = _translationErrors.value - recipe.id
            try {
                val translated = geminiClient.translateRecipe(recipe, targetLanguage)
                _recipeTranslations.value = _recipeTranslations.value + (recipe.id to translated)
                val displayName = SupportedLanguages.all.firstOrNull { it.code == targetLanguage }
                    ?.displayName ?: targetLanguage
                _userMessages.tryEmit("Recipe translated to $displayName")
            } catch (e: RateLimitException) {
                _translationErrors.value = _translationErrors.value + (recipe.id to quotaMessage(e.retryAfterSeconds))
            } catch (e: Exception) {
                _translationErrors.value = _translationErrors.value +
                    (recipe.id to sanitizeGeminiMessage(e.message))
            } finally {
                _translatingRecipeIds.value = _translatingRecipeIds.value - recipe.id
            }
        }
    }

    fun clearRecipeTranslation(recipeId: String) {
        _recipeTranslations.value = _recipeTranslations.value - recipeId
        _translationErrors.value = _translationErrors.value - recipeId
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        return stream.toByteArray()
    }

    // ── Image persistence ──

    private fun saveOriginalImage(bitmap: Bitmap, recipeId: String): String {
        val dir = File(app.filesDir, IMAGE_DIR)
        dir.mkdirs()
        val file = File(dir, "${recipeId}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        return file.absolutePath
    }

    private fun deleteImageFile(imagePath: String?) {
        if (imagePath != null) File(imagePath).delete()
    }

    /**
     * Copies a user-picked image into app-private storage and points the
     * recipe at it. The same file is what both the home grid and the recipe
     * detail page display, so replacing it updates everywhere at once.
     */
    fun updateRecipeImage(recipeId: String, imageUri: Uri) {
        viewModelScope.launch {
            val recipe = dao.getById(recipeId) ?: return@launch
            val dir = File(app.filesDir, IMAGE_DIR)
            dir.mkdirs()
            val destFile = File(dir, "${recipeId}.jpg")
            val copied = try {
                app.contentResolver.openInputStream(imageUri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                    true
                } ?: false
            } catch (_: Exception) {
                false
            }
            if (!copied) {
                _userMessages.tryEmit("Couldn't load that image.")
                return@launch
            }
            dao.upsert(recipe.copy(sourceImagePath = destFile.absolutePath, updatedAt = System.currentTimeMillis()))
        }
    }

    fun removeRecipeImage(recipeId: String) {
        viewModelScope.launch {
            val recipe = dao.getById(recipeId) ?: return@launch
            recipe.sourceImagePath?.let { File(it).delete() }
            dao.upsert(recipe.copy(sourceImagePath = null, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteRecipe(id: String) {
        viewModelScope.launch {
            val recipe = dao.getById(id)
            if (recipe != null) {
                deleteImageFile(recipe.sourceImagePath)
                dao.deleteById(id)
            }
        }
    }

    fun setRecipeRating(recipeId: String, rating: Int?) {
        viewModelScope.launch {
            val recipe = dao.getById(recipeId) ?: return@launch
            dao.upsert(recipe.copy(rating = rating))
        }
    }

    fun setRecipeUnitSystem(recipeId: String, system: com.rekluzlabs.vaultcuisine.util.UnitSystem) {
        viewModelScope.launch {
            val recipe = dao.getById(recipeId) ?: return@launch
            dao.upsert(recipe.copy(preferredUnitSystem = system))
        }
    }

    fun moveRecipeToCategory(recipe: Recipe, newCategory: RecipeCategory) {
        viewModelScope.launch {
            dao.upsert(
                recipe.copy(
                    category = newCategory,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // ── Cooking Mode timers ──

    private val _activeTimers = MutableStateFlow<Map<String, ActiveTimer>>(emptyMap())
    val activeTimers: StateFlow<Map<String, ActiveTimer>> = _activeTimers

    private val _ringingTimers = MutableStateFlow<Set<String>>(emptySet())
    val ringingTimers: StateFlow<Set<String>> = _ringingTimers

    private var tickJob: kotlinx.coroutines.Job? = null

    private fun startTick() {
        if (tickJob?.isActive == true) return
        tickJob = viewModelScope.launch {
            try {
                while (true) {
                    delay(1.seconds)
                    val now = System.currentTimeMillis()
                    val current = _activeTimers.value
                    val updated = mutableMapOf<String, ActiveTimer>()
                    var anyRunning = false
                    for ((key, timer) in current) {
                        if (timer.endTimeMillis > now) {
                            anyRunning = true
                            updated[key] = timer.copy()
                        }
                    }
                    _activeTimers.value = updated
                    loadRingingTimers()
                    if (!anyRunning && _ringingTimers.value.isEmpty()) return@launch
                }
            } finally {
                tickJob = null
            }
        }
    }

    fun startTimer(context: Context, recipeId: String, stepIndex: Int, stepText: String, durationSeconds: Int) {
        val endTime = System.currentTimeMillis() + durationSeconds * 1000L
        val timer = ActiveTimer(recipeId, stepIndex, stepText, endTime, durationSeconds)
        val key = "${recipeId}_$stepIndex"

        _activeTimers.value += key to timer

        val prefs = context.getSharedPreferences(TimerBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putLong(timer.timerKey, endTime) }
        prefs.edit { putString("${timer.timerKey}_text", stepText) }
        prefs.edit { putInt("${timer.timerKey}_total", durationSeconds) }

        scheduleAlarm(context, recipeId, stepIndex, stepText, endTime)

        startTick()
    }

    private fun scheduleAlarm(context: Context, recipeId: String, stepIndex: Int, stepText: String, triggerAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TimerBroadcastReceiver::class.java).apply {
            action = TimerBroadcastReceiver.ACTION_TIMER_DONE
            putExtra(TimerBroadcastReceiver.EXTRA_RECIPE_ID, recipeId)
            putExtra(TimerBroadcastReceiver.EXTRA_STEP_INDEX, stepIndex)
            putExtra(TimerBroadcastReceiver.EXTRA_STEP_TEXT, stepText)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, recipeId.hashCode() + stepIndex, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelTimer(context: Context, recipeId: String, stepIndex: Int) {
        val key = "${recipeId}_$stepIndex"
        _activeTimers.value -= key
        val prefs = app.getSharedPreferences(TimerBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove("timer_${recipeId}_$stepIndex") }
        prefs.edit { remove("timer_${recipeId}_${stepIndex}_text") }
        prefs.edit { remove("timer_${recipeId}_${stepIndex}_total") }

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TimerBroadcastReceiver::class.java).apply {
            action = TimerBroadcastReceiver.ACTION_TIMER_DONE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, recipeId.hashCode() + stepIndex, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.cancel(pendingIntent)
    }

    fun clearTimer(context: Context, recipeId: String, stepIndex: Int) {
        cancelTimer(context, recipeId, stepIndex)
        dismissAlarm(context, recipeId, stepIndex)
    }

    fun loadActiveTimers() {
        val prefs = app.getSharedPreferences(TimerBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        val all = prefs.all
        val timers = mutableMapOf<String, ActiveTimer>()
        all.forEach { (key, _) ->
            if (key.startsWith("timer_") && !key.endsWith("_text") && !key.endsWith("_total")) {
                val endTime = prefs.getLong(key, 0)
                if (endTime > System.currentTimeMillis()) {
                    val parts = key.removePrefix("timer_").split("_")
                    if (parts.size >= 2) {
                        val recipeId = parts.dropLast(1).joinToString("_")
                        val stepIndex = parts.last().toIntOrNull() ?: return@forEach
                        val stepText = prefs.getString("${key}_text", "Timer") ?: "Timer"
                        val total = prefs.getInt("${key}_total", 0)
                        val mapKey = "${recipeId}_$stepIndex"
                        timers[mapKey] = ActiveTimer(recipeId, stepIndex, stepText, endTime, total)
                    }
                }
            }
        }
        _activeTimers.value = timers
        if (timers.isNotEmpty()) startTick()
        loadRingingTimers()
    }

    fun loadRingingTimers() {
        val prefs = app.getSharedPreferences(TimerBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        val all = prefs.all
        val ringing = mutableSetOf<String>()
        all.forEach { (key, _) ->
            if (key.startsWith("ringing_")) {
                val mapKey = key.removePrefix("ringing_")
                ringing.add(mapKey)
            }
        }
        _ringingTimers.value = ringing
    }

    fun dismissAlarm(context: Context, recipeId: String, stepIndex: Int) {
        val mapKey = "${recipeId}_$stepIndex"
        _ringingTimers.value -= mapKey
        val prefs = app.getSharedPreferences(TimerBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove("ringing_${recipeId}_$stepIndex") }
        context.stopService(Intent(context, TimerRingService::class.java))
    }

    private suspend fun deleteAllRecipeData() {
        File(app.filesDir, IMAGE_DIR).deleteRecursively()
        dao.clearAll()
    }

    /** Deletes only recipes and their photos. Settings and API keys are left untouched. */
    fun clearAllRecipeData() {
        viewModelScope.launch { deleteAllRecipeData() }
    }

    /** Full reset: recipes, photos, settings, and API keys. */
    fun clearAllData() {
        viewModelScope.launch {
            deleteAllRecipeData()
            prefs.clearAll()
            credentialStore.clearApiKey()
            _settings.value = AppSettings()
        }
    }

    // ── Backup / Restore ──

    fun zipBackupFileName(): String = backupManager.zipFileName()

    fun jsonBackupFileName(): String = backupManager.jsonFileName()

    suspend fun saveZipBackup(output: OutputStream): BackupResult =
        backupManager.exportZip(recipes.value, output)

    suspend fun saveJsonBackup(output: OutputStream): BackupResult =
        backupManager.exportJson(recipes.value, output)

    fun pickRestoreFile(uri: Uri) {
        _pendingRestoreUri.value = uri
    }

    fun dismissRestore() {
        _pendingRestoreUri.value = null
    }

    fun confirmRestore() {
        val uri = _pendingRestoreUri.value ?: return
        _pendingRestoreUri.value = null
        viewModelScope.launch {
            _isRestoring.value = true
            try {
                val name = queryDisplayName(uri) ?: uri.toString()
                val mime = app.contentResolver.getType(uri)
                // Bulk-import category decision: BackupManager prompts once per
                // recipe missing a `category` field. Cancelling any prompt aborts
                // the whole restore so nothing is partially imported.
                val resolveCategory: suspend (Recipe) -> RecipeCategory? = { recipe ->
                    awaitCategorySelection(recipe)
                }
                val result = when {
                    name.endsWith(".zip", ignoreCase = true) || mime == "application/zip" ->
                        app.contentResolver.openInputStream(uri)?.let { backupManager.restoreZip(it, resolveCategory) }
                    name.endsWith(".json", ignoreCase = true) || mime == "application/json" ->
                        app.contentResolver.openInputStream(uri)?.let { backupManager.restoreJson(it, resolveCategory) }
                    else -> null
                }
                when (result) {
                    is BackupResult.Success -> _userMessages.tryEmit("Restored ${result.count} recipes")
                    is BackupResult.Error -> _userMessages.tryEmit(result.message)
                    null -> _userMessages.tryEmit("Unsupported backup file. Please pick a .zip or .json backup.")
                }
            } catch (e: Exception) {
                _userMessages.tryEmit("Restore failed: ${e.message}")
            } finally {
                _isRestoring.value = false
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val IMAGE_DIR = "recipe_images"

        fun factory(app: VaultCuisineApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(app) as T
        }
    }
}
