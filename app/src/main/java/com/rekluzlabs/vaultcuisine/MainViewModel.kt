package com.rekluzlabs.vaultcuisine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rekluzlabs.vaultcuisine.ai.GeminiCredentialStore
import com.rekluzlabs.vaultcuisine.ai.GeminiOcrClient
import com.rekluzlabs.vaultcuisine.ai.HeuristicStructurer
import com.rekluzlabs.vaultcuisine.ai.ImagePreprocessor
import com.rekluzlabs.vaultcuisine.ai.MissingApiKeyException
import com.rekluzlabs.vaultcuisine.ai.NetworkException
import com.rekluzlabs.vaultcuisine.ai.NotARecipeException
import com.rekluzlabs.vaultcuisine.ai.RateLimitException
import com.rekluzlabs.vaultcuisine.data.AppSettings
import com.rekluzlabs.vaultcuisine.data.FALLBACK_NOTES_MESSAGE
import com.rekluzlabs.vaultcuisine.data.Recipe
import com.rekluzlabs.vaultcuisine.data.RecipeExport
import com.rekluzlabs.vaultcuisine.data.RecipeIngredient
import com.rekluzlabs.vaultcuisine.data.RecipeStep
import com.rekluzlabs.vaultcuisine.data.tryParseGeminiImport
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
import java.util.UUID

enum class SectionType { INGREDIENT, STEP }

class MainViewModel(private val app: VaultCuisineApp) : ViewModel() {

    private val prefs = app.preferences
    private val dao = app.database.recipeDao()
    private val ocr = TextRecognizerHelper()
    val credentialStore = GeminiCredentialStore(app)
    private val imagePreprocessor = ImagePreprocessor()
    private val geminiClient = GeminiOcrClient(credentialStore, imagePreprocessor)

    private val _geminiKeyVerified = MutableStateFlow(false)
    val geminiKeyVerified: StateFlow<Boolean> = _geminiKeyVerified

    val recipes: StateFlow<List<Recipe>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _settings = MutableStateFlow(prefs.load())
    val settings: StateFlow<AppSettings> = _settings

    private val _scanMessage = MutableStateFlow("Reading your recipe…")
    val scanMessage: StateFlow<String> = _scanMessage

    private val _lastScannedImageBytes = MutableStateFlow<ByteArray?>(null)

    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessages: SharedFlow<String> = _userMessages

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
        _settings.value = _settings.value.copy(geminiConsentAccepted = true)
        prefs.save(_settings.value)
        _needsGeminiConsent.value = false
        _isConsentFromSettings.value = false
        _consentResult.trySend(true)
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

    /** Returns true if the caller should proceed with the Gemini call. */
    private suspend fun awaitGeminiConsent(): Boolean {
        if (_settings.value.geminiConsentAccepted) return true
        _needsGeminiConsent.value = true
        return _consentResult.receive()
    }

    fun updateSettings(s: AppSettings) {
        _settings.value = s
        prefs.save(s)
    }

    // ── Gemini API key ──

    fun saveGeminiApiKey(key: String) {
        credentialStore.saveApiKey(key)
        _settings.value = _settings.value.copy(geminiConsentAccepted = false)
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
            _scanMessage.value = "Trying again…"
            val modelId = settings.value.geminiModelId

            Log.d("GeminiOcrClient", "manual retry (Try Again) starting")

            val result = try {
                geminiClient.structureFromImageWithMeta(imageBytes, modelId, ocrHint = null)
            } catch (e: Exception) {
                Log.d("GeminiOcrClient", "manual retry failed: ${e.message}")
                _userMessages.tryEmit("Retry failed. Try retaking the photo.")
                return@launch
            }

            Log.d("GeminiOcrClient", "manual retry (Try Again) complete")

            val saved = result.recipe.copy(sourceImagePath = null, notes = null)
            dao.upsert(saved)
            _newRecipeIds.add(saved.id)
            _retryCompleted.send(saved.id)
        }
    }

    // ── Edit mode ──

    fun enterEditMode(recipeId: String) {
        val recipe = recipes.value.find { it.id == recipeId } ?: return
        _editingRecipe.value = recipe
        _editableLines.value = recipe.toEditableLines()
        _editingTitle.value = recipe.title
        _editingServings.value = recipe.servings
        _editingNotes.value = if (recipe.notes == FALLBACK_NOTES_MESSAGE) null else recipe.notes
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
                    updatedAt = System.currentTimeMillis()
                )
            )
            _newRecipeIds.remove(recipe.id)
            _editableLines.value = null
            _editingRecipe.value = null
            _editingTitle.value = ""
            _editingServings.value = null
            _editingNotes.value = null
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
     * reference copy before discarding.
     */
    fun processScannedImage(bitmap: Bitmap, onSaved: (recipeId: String) -> Unit) {
        viewModelScope.launch {
            _scanMessage.value = "Reading your recipe…"
            val rawText = ocr.recognizeText(bitmap)

            val recipe = if (credentialStore.hasApiKey()) {
                val imageBytes = try {
                    bitmapToJpegBytes(bitmap)
                } catch (_: Exception) {
                    null
                }
                if (imageBytes != null) {
                    // Tier 1: Gemini image mode (with consent gate)
                    if (awaitGeminiConsent()) {
                        _lastScannedImageBytes.value = imageBytes
                        runGeminiImageMode(rawText, imageBytes, settings.value.geminiModelId)
                    } else {
                        HeuristicStructurer().structure(rawText)
                    }
                } else {
                    // Tier 2: Gemini text mode (image bytes unavailable)
                    runGeminiTextMode(rawText, settings.value.geminiModelId)
                }
            } else {
                // Tier 3: heuristic
                HeuristicStructurer().structure(rawText)
            }

            val imagePath = saveOriginalImage(bitmap, recipe.id)
            val saved = recipe.copy(sourceImagePath = imagePath)
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
            HeuristicStructurer().structure(rawText)
        } catch (_: NotARecipeException) {
            _userMessages.tryEmit("This doesn't look like a recipe. Falling back to text-based parsing.")
            HeuristicStructurer().structure(rawText)
        } catch (_: RateLimitException) {
            _userMessages.tryEmit("You've hit your Gemini quota. Falling back to offline parsing.")
            HeuristicStructurer().structure(rawText)
        } catch (_: NetworkException) {
            try {
                geminiClient.structureFromImage(imageBytes)
            } catch (_: Exception) {
                HeuristicStructurer().structure(rawText)
            }
        } catch (_: Exception) {
            HeuristicStructurer().structure(rawText)
        }
    }

    private suspend fun runGeminiTextMode(rawText: String, modelId: String): Recipe {
        return try {
            geminiClient.structure(rawText, modelId)
        } catch (_: Exception) {
            HeuristicStructurer().structure(rawText)
        }
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
                    if (!anyRunning) return@launch
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

    fun clearAllData() {
        viewModelScope.launch {
            File(app.filesDir, IMAGE_DIR).deleteRecursively()
            dao.clearAll()
            prefs.clearAll()
            credentialStore.clearApiKey()
            _settings.value = AppSettings()
        }
    }

    // ── Export / Import ──

    fun exportRecipesJson(): String {
        val all = recipes.value
        return jsonPretty.encodeToString(RecipeExport.serializer(), RecipeExport(recipes = all))
    }

    fun importRecipesJson(json: String, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val recipes = try {
                jsonLenient.decodeFromString(RecipeExport.serializer(), json).recipes
            } catch (_: Exception) {
                tryParseGeminiImport(json, jsonLenient)
            }

            if (recipes != null) {
                recipes.forEach { dao.upsert(it) }
                onDone(recipes.size)
            } else {
                onDone(-1)
            }
        }
    }

    companion object {
        private const val IMAGE_DIR = "recipe_images"
        private val jsonPretty = kotlinx.serialization.json.Json { prettyPrint = true }
        private val jsonLenient = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun factory(app: VaultCuisineApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(app) as T
        }
    }
}
