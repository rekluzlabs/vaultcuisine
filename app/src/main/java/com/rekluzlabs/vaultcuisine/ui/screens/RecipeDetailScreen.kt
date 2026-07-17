package com.rekluzlabs.vaultcuisine.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rekluzlabs.vaultcuisine.MainViewModel
import com.rekluzlabs.vaultcuisine.SectionType
import com.rekluzlabs.vaultcuisine.data.Recipe
import com.rekluzlabs.vaultcuisine.print.RecipePrinter
import com.rekluzlabs.vaultcuisine.ui.edit.EditableLine
import com.rekluzlabs.vaultcuisine.ui.edit.LineDetail
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    onBack: () -> Unit
) {
    val recipes by vm.recipes.collectAsState()
    val recipe = recipes.find { it.id == recipeId }
    val editableLines by vm.editableLines.collectAsState()

    var isEditing by remember { mutableStateOf(false) }
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(recipe.title) },
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
                        IconButton(onClick = { /* TODO: share sheet */ }) {
                            Icon(Icons.Default.Share, contentDescription = "Share recipe")
                        }
                        IconButton(onClick = { RecipePrinter.print(context, recipe) }) {
                            Icon(Icons.Default.Print, contentDescription = "Print recipe")
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
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
            )
        }
    }
}

@Composable
private fun ViewModeContent(recipe: Recipe, modifier: Modifier = Modifier) {
    LazyColumn(modifier) {
        item { Text("Servings: ${recipe.servings}", style = MaterialTheme.typography.bodyLarge) }
        item { Spacer(Modifier.height(16.dp)) }
        item { Text("Ingredients", style = MaterialTheme.typography.titleMedium) }
        items(recipe.ingredients) { ing ->
            Text("• ${ing.amount ?: ""} ${ing.unit.orEmpty()} ${ing.name}".trim())
        }
        item { Spacer(Modifier.height(16.dp)) }
        item { Text("Instructions", style = MaterialTheme.typography.titleMedium) }
        items(recipe.steps.withIndex().toList()) { (index, step) ->
            Text("${index + 1}. ${step.text}")
        }
        recipe.notes?.let { notes ->
            item { Spacer(Modifier.height(16.dp)) }
            item { Text("Notes", style = MaterialTheme.typography.titleMedium) }
            item { Text(notes) }
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

    LazyColumn(modifier) {
        item { Text("Servings: ${recipe.servings}", style = MaterialTheme.typography.bodyLarge) }
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
