package com.rekluzlabs.vaultcuisine.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rekluzlabs.vaultcuisine.R
import com.rekluzlabs.vaultcuisine.MainViewModel
import com.rekluzlabs.vaultcuisine.SectionType
import com.rekluzlabs.vaultcuisine.data.FALLBACK_NOTES_MESSAGE
import com.rekluzlabs.vaultcuisine.data.Recipe
import com.rekluzlabs.vaultcuisine.util.AmountParser
import com.rekluzlabs.vaultcuisine.util.UnitConverter
import com.rekluzlabs.vaultcuisine.util.UnitSystem
import com.rekluzlabs.vaultcuisine.print.RecipePrinter
import com.rekluzlabs.vaultcuisine.ui.edit.EditableLine
import com.rekluzlabs.vaultcuisine.ui.edit.LineDetail
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a")
    .withZone(ZoneId.systemDefault())

private fun formatTimestamp(millis: Long): String =
    dateFormatter.format(Instant.ofEpochMilli(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    vm: MainViewModel,
    isNewRecipe: Boolean = false,
    onBack: () -> Unit,
    onReScan: ((newRecipeId: String) -> Unit)? = null,
    onStartCooking: (() -> Unit)? = null
) {
    val recipes by vm.recipes.collectAsState()
    val recipe = recipes.find { it.id == recipeId }
    val editableLines by vm.editableLines.collectAsState()

    var isEditing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(recipeId) {
        if (editableLines != null) {
            isEditing = true
        }
    }

    LaunchedEffect(Unit) {
        vm.conversionEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val handleBack = {
        if (editableLines != null || isNewRecipe) {
            vm.cancelEdits()
        }
        isEditing = false
        onBack()
    }

    BackHandler(enabled = editableLines != null || isNewRecipe, onBack = handleBack)

    if (recipe == null) return

    val context = LocalContext.current

    if (showDeleteDialog) {
        DeleteRecipeDialog(
            recipeTitle = recipe.title,
            onConfirm = {
                showDeleteDialog = false
                vm.deleteRecipe(recipeId)
                onBack()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (editableLines != null) "Edit Recipe" else "Recipe") },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (editableLines != null) {
                        TextButton(onClick = {
                            vm.cancelEdits()
                            isEditing = false
                            if (isNewRecipe) onBack()
                        }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = { vm.saveEdits(); isEditing = false }) {
                            Text("Save")
                        }
                    } else {
                        IconButton(onClick = { vm.enterEditMode(recipeId); isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit recipe")
                        }
                        if (onReScan != null && recipe.sourceImagePath != null) {
                            IconButton(onClick = { onReScan(recipeId) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Re-scan with AI")
                            }
                        }
                        if (onStartCooking != null && recipe.steps.isNotEmpty()) {
                            IconButton(onClick = onStartCooking) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Start Cooking")
                            }
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    onClick = { 
                                        showMoreMenu = false
                                        /* TODO: share sheet */ 
                                    },
                                    leadingIcon = { Icon(Icons.Default.Share, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Print") },
                                    onClick = { 
                                        showMoreMenu = false
                                        RecipePrinter.print(context, recipe) 
                                    },
                                    leadingIcon = { Icon(Icons.Default.Print, null) }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    onClick = { 
                                        showMoreMenu = false
                                        showDeleteDialog = true 
                                    },
                                    leadingIcon = { 
                                        Icon(
                                            Icons.Default.Delete, 
                                            null, 
                                            tint = MaterialTheme.colorScheme.error
                                        ) 
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (editableLines != null) {
            EditModeContent(
                lines = editableLines!!,
                recipe = recipe,
                vm = vm,
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
            )
        } else {
            ViewModeContent(
                recipe = recipe,
                vm = vm,
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
            )
        }
    }
}

@Composable
private fun DeleteRecipeDialog(
    recipeTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete recipe?") },
        text = { Text("Are you sure you want to delete \"$recipeTitle\"? This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ViewModeContent(
    recipe: Recipe,
    vm: MainViewModel,
    modifier: Modifier = Modifier
) {
    val baseServings = recipe.servings
    var targetServings by remember(baseServings) { mutableStateOf(baseServings ?: 1) }

    LazyColumn(modifier) {
        item {
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        // ── Scaling + unit conversion toggle row ──
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Servings: ${baseServings ?: "—"}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (baseServings != null && baseServings > 0) {
                        IconButton(
                            onClick = { if (targetServings > 1) targetServings-- },
                            enabled = targetServings > 1
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease servings")
                        }
                        Text("$targetServings", style = MaterialTheme.typography.bodyLarge)
                        IconButton(
                            onClick = { targetServings++ },
                            enabled = targetServings < 99
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase servings")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Units:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    UnitSystem.entries.forEach { system ->
                        val selected = recipe.preferredUnitSystem == system
                        TextButton(
                            onClick = { vm.setRecipeUnitSystem(recipe.id, system) }
                        ) {
                            Text(
                                text = when (system) {
                                    UnitSystem.AS_WRITTEN -> "As written"
                                    UnitSystem.METRIC -> "Metric"
                                    UnitSystem.IMPERIAL -> "Imperial"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        if (baseServings == null) {
            item {
                Text(
                    text = "Add a serving count in edit mode to enable scaling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val currentRating = recipe.rating
                for (i in 1..5) {
                    IconButton(
                        onClick = {
                            vm.setRecipeRating(
                                recipe.id,
                                if (currentRating == i) null else i
                            )
                        }
                    ) {
                        Icon(
                            imageVector = if (currentRating != null && i <= currentRating)
                                Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (currentRating != null && i <= currentRating)
                                "Star $i" else "Unfilled star $i",
                            tint = if (currentRating != null && i <= currentRating)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        item { Text("Ingredients", style = MaterialTheme.typography.titleMedium) }
        items(recipe.ingredients) { ing ->
            val factor = if (baseServings != null && baseServings > 0)
                targetServings.toDouble() / baseServings.toDouble() else 1.0
            val needsConversion = recipe.preferredUnitSystem != UnitSystem.AS_WRITTEN

            val displayAmount = when {
                needsConversion && ing.unit != null -> {
                    val result = UnitConverter.convertAndFormat(
                        ing.amount, ing.unit, factor, recipe.preferredUnitSystem
                    )
                    if (result != null) {
                        "${result.first} ${result.second}"
                    } else {
                        // conversion not applicable or unparseable; fall through to scaling-only
                        if (abs(factor - 1.0) > 0.001) {
                            val amounts = AmountParser.parseAmounts(ing.amount.orEmpty())
                            if (amounts.isNotEmpty()) {
                                AmountParser.scaleAndFormat(ing.amount ?: "", factor)
                            } else {
                                "~ ${ing.amount ?: ""}"
                            }
                        } else {
                            ing.amount ?: ""
                        }
                    }
                }
                abs(factor - 1.0) > 0.001 -> {
                    // scaling only
                    val amounts = AmountParser.parseAmounts(ing.amount.orEmpty())
                    if (amounts.isNotEmpty()) {
                        AmountParser.scaleAndFormat(ing.amount ?: "", factor)
                    } else {
                        "~ ${ing.amount ?: ""}"
                    }
                }
                else -> ing.amount ?: ""
            }
            Text("• $displayAmount ${ing.unit.orEmpty()} ${ing.name}".trim())
        }
        item { Spacer(Modifier.height(16.dp)) }
        item { Text("Instructions", style = MaterialTheme.typography.titleMedium) }
        items(recipe.steps.withIndex().toList()) { (index, step) ->
            Text("${index + 1}. ${step.text}")
        }
        if (recipe.notes == FALLBACK_NOTES_MESSAGE) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Couldn't automatically structure this scan. The raw text is shown below — edit the fields to fix it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            recipe.notes?.let { notes ->
                item { Spacer(Modifier.height(16.dp)) }
                item { Text("Notes", style = MaterialTheme.typography.titleMedium) }
                item { Text(notes) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
        item {
            Text(
                text = "Rekluz Labs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize().padding(bottom = 4.dp),
                textAlign = TextAlign.Center
            )
        }
        item {
            Text(
                text = formatTimestamp(recipe.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxSize(),
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class DragState(val itemId: String, val offset: Float)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditModeContent(
    lines: List<EditableLine>,
    recipe: Recipe,
    vm: MainViewModel,
    modifier: Modifier = Modifier
) {
    var dragState by remember { mutableStateOf<DragState?>(null) }

    val ingredients = lines.filter { it.detail is LineDetail.Ingredient }
    val steps = lines.filter { it.detail is LineDetail.Step }

    val editingTitle by vm.editingTitle.collectAsState()
    val editingServings by vm.editingServings.collectAsState()
    val editingNotes by vm.editingNotes.collectAsState()

    LazyColumn(modifier) {
        item {
            OutlinedTextField(
                value = editingTitle,
                onValueChange = { vm.setEditingTitle(it) },
                label = { Text("Recipe Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall
            )
        }
        item { Spacer(Modifier.height(16.dp)) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = editingServings?.toString() ?: "",
                    onValueChange = { text ->
                        vm.setEditingServings(text.toIntOrNull())
                    },
                    label = { Text("Servings") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }

        item { Text("Ingredients", style = MaterialTheme.typography.titleMedium) }

        items(ingredients, key = { it.id }) { line ->
            IngredientEditRow(
                line = line,
                lines = lines,
                dragState = dragState,
                onDragStart = { dragState = DragState(line.id, 0f) },
                onDrag = { delta -> dragState = dragState?.let { handleDragDelta(it, delta, lines, vm) } },
                onDragEnd = { dragState = null },
                onUpdateText = { vm.updateLineText(line.id, it) },
                onDelete = { vm.deleteLine(line.id) },
                onMoveUp = { vm.moveLineUp(line.id) },
                onMoveDown = { vm.moveLineDown(line.id) },
                onMoveToSection = { vm.moveToSection(line.id, it) }
            )
        }

        item {
            OutlinedButton(
                onClick = { vm.addLine(SectionType.INGREDIENT) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add ingredient")
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
        item { Text("Instructions", style = MaterialTheme.typography.titleMedium) }

        items(steps, key = { it.id }) { line ->
            StepEditRow(
                line = line,
                lines = lines,
                dragState = dragState,
                onDragStart = { dragState = DragState(line.id, 0f) },
                onDrag = { delta -> dragState = dragState?.let { handleDragDelta(it, delta, lines, vm) } },
                onDragEnd = { dragState = null },
                onUpdateText = { vm.updateLineText(line.id, it) },
                onDelete = { vm.deleteLine(line.id) },
                onMoveUp = { vm.moveLineUp(line.id) },
                onMoveDown = { vm.moveLineDown(line.id) },
                onMoveToSection = { vm.moveToSection(line.id, it) }
            )
        }

        item {
            OutlinedButton(
                onClick = { vm.addLine(SectionType.STEP) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add step")
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
        item { Text("Notes", style = MaterialTheme.typography.titleMedium) }
        item {
            OutlinedTextField(
                value = editingNotes ?: "",
                onValueChange = { vm.setEditingNotes(it.ifBlank { null }) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = { Text("Add your own notes or tweaks...") }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun handleDragDelta(
    state: DragState,
    deltaY: Float,
    lines: List<EditableLine>,
    vm: MainViewModel
): DragState {
    val newOffset = state.offset + deltaY
    val currentIndex = lines.indexOfFirst { it.id == state.itemId }
    if (currentIndex == -1) return DragState(state.itemId, 0f)

    val itemHeight = 72f
    val swaps = (newOffset / itemHeight).toInt()

    return if (swaps != 0) {
        val targetIndex = (currentIndex + swaps).coerceIn(0, lines.size - 1)
        vm.reorderLine(currentIndex, targetIndex)
        DragState(state.itemId, newOffset - swaps * itemHeight)
    } else {
        DragState(state.itemId, newOffset)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IngredientEditRow(
    line: EditableLine,
    lines: List<EditableLine>,
    dragState: DragState?,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onUpdateText: (String) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToSection: (SectionType) -> Unit
) {
    val detail = line.detail as? LineDetail.Ingredient
    val isDragging = dragState?.itemId == line.id
    val dragOffset = if (isDragging) dragState.offset else 0f

    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .combinedClickable(
                onClick = {},
                onLongClick = { showMenu = true }
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Drag to reorder",
            modifier = Modifier
                .pointerInput(line.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDrag = { change, dragAmount ->
                            onDrag(dragAmount.y)
                            change.consume()
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd
                    )
                }
                .padding(end = 8.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = line.text,
                onValueChange = onUpdateText,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text("Ingredient name") }
            )
            if (detail != null && (detail.amount != null || detail.unit != null)) {
                Text(
                    text = buildString {
                        detail.amount?.let { append(it); append(" ") }
                        detail.unit?.let { append(it) }
                    }.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete ingredient",
                tint = MaterialTheme.colorScheme.error
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options"
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Move to Instructions") },
                    onClick = { showMenu = false; onMoveToSection(SectionType.STEP) }
                )
                DropdownMenuItem(
                    text = { Text("Move up") },
                    onClick = { showMenu = false; onMoveUp() }
                )
                DropdownMenuItem(
                    text = { Text("Move down") },
                    onClick = { showMenu = false; onMoveDown() }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepEditRow(
    line: EditableLine,
    lines: List<EditableLine>,
    dragState: DragState?,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onUpdateText: (String) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToSection: (SectionType) -> Unit
) {
    val isDragging = dragState?.itemId == line.id
    val dragOffset = if (isDragging) dragState.offset else 0f

    var showMenu by remember { mutableStateOf(false) }

    val stepIndex = lines
        .filter { it.detail is LineDetail.Step }
        .indexOfFirst { it.id == line.id }
        .let { if (it == -1) null else it + 1 }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .combinedClickable(
                onClick = {},
                onLongClick = { showMenu = true }
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Drag to reorder",
            modifier = Modifier
                .pointerInput(line.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDrag = { change, dragAmount ->
                            onDrag(dragAmount.y)
                            change.consume()
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd
                    )
                }
                .padding(end = 8.dp)
        )

        Text(
            text = "${stepIndex ?: "?"}. ",
            style = MaterialTheme.typography.bodyLarge
        )

        OutlinedTextField(
            value = line.text,
            onValueChange = onUpdateText,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text("Instruction") }
        )

        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete step",
                tint = MaterialTheme.colorScheme.error
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options"
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Move to Ingredients") },
                    onClick = { showMenu = false; onMoveToSection(SectionType.INGREDIENT) }
                )
                DropdownMenuItem(
                    text = { Text("Move up") },
                    onClick = { showMenu = false; onMoveUp() }
                )
                DropdownMenuItem(
                    text = { Text("Move down") },
                    onClick = { showMenu = false; onMoveDown() }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}
