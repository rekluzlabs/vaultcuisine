package com.rekluzlabs.vaultcuisine.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rekluzlabs.vaultcuisine.data.Recipe
import com.rekluzlabs.vaultcuisine.data.RecipeCategory
import com.rekluzlabs.vaultcuisine.ui.components.CategoryPickerDialog
import com.rekluzlabs.vaultcuisine.ui.components.RecipeThumbnail

// Pin colors: blue when unpinned, red when pinned.
private val PinUnpinned = Color(0xFF2196F3)
private val PinPinned = Color(0xFFD32F2F)

/**
 * Shows recipes for one category, or every recipe when reached from the
 * "All Items" tile (pass the full unfiltered list in that case). Long-press
 * a tile to reassign its category via [CategoryPickerDialog].
 *
 * Recipes whose id is in [pinnedRecipeIds] are shown first, held at the top
 * of the list while they stay pinned, via the pin button on each tile.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryRecipesScreen(
    title: String,
    recipes: List<Recipe>,
    pinnedRecipeIds: Set<String>,
    onToggleRecipePin: (Recipe) -> Unit,
    onRecipeClick: (Recipe) -> Unit,
    onMoveRecipe: (Recipe, RecipeCategory) -> Unit,
    onScanClick: () -> Unit,
    onClockClick: () -> Unit,
    onBackClick: () -> Unit
) {
    var recipeToMove by remember { mutableStateOf<Recipe?>(null) }

    val orderedRecipes = remember(recipes, pinnedRecipeIds) {
        recipes.sortedBy { it.id !in pinnedRecipeIds }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (orderedRecipes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No recipes in this category yet.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(orderedRecipes, key = { it.id }) { recipe ->
                        val isPinned = recipe.id in pinnedRecipeIds
                        RecipeTile(
                            recipe = recipe,
                            isPinned = isPinned,
                            onClick = { onRecipeClick(recipe) },
                            onTogglePin = { onToggleRecipePin(recipe) },
                            onLongClick = { recipeToMove = recipe }
                        )
                    }
                }
            }
            FloatingActionButton(
                onClick = onClockClick,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp + padding.calculateBottomPadding())
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = "Clock")
            }
            FloatingActionButton(
                onClick = onScanClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp + padding.calculateBottomPadding())
            ) {
                Icon(Icons.Default.Add, contentDescription = "Scan new recipe")
            }
        }
    }

    recipeToMove?.let { recipe ->
        CategoryPickerDialog(
            initialCategory = recipe.category,
            recipeTitle = recipe.title,
            onConfirm = { newCategory ->
                onMoveRecipe(recipe, newCategory)
                recipeToMove = null
            },
            onDismiss = { recipeToMove = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecipeTile(
    recipe: Recipe,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, Color(0xFFD4AF37))
    ) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(com.rekluzlabs.vaultcuisine.R.drawable.background_whisk),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.19f
            )
            Row(Modifier.fillMaxSize().padding(10.dp)) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp)
                )
                Spacer(Modifier.width(10.dp))
                RecipeThumbnail(
                    path = recipe.sourceImagePath,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.42f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp)),
                    targetSize = 256,
                    alignment = Alignment.TopStart
                )
            }
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (isPinned) "Unpin" else "Pin to top",
                    tint = if (isPinned) PinPinned else PinUnpinned
                )
            }
        }
    }
}
