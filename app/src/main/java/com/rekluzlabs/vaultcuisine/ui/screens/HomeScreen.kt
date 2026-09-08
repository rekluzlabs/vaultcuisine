/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
package com.rekluzlabs.vaultcuisine.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.RiceBowl
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.rekluzlabs.vaultcuisine.data.RecipeCategory

/**
 * Everything reachable from the home grid: ten tiles map to a RecipeCategory,
 * plus two special tiles â€” AllItems (every recipe, unfiltered) and
 * ShoppingList (separate feature, not recipe-scoped).
 *
 * Tiles are user-customizable: long-press and drag to reorder (pinned tiles
 * stay put and can't be swapped), or tap the pin icon on a tile to lock it
 * in place until it's unpinned.
 */
sealed class HomeDestination(val key: String, val label: String, val icon: ImageVector) {
    data class Category(val category: RecipeCategory) :
        HomeDestination(category.name, category.displayName, iconFor(category))

    object AllItems : HomeDestination("all_items", "All Items", Icons.Filled.Apps)
    object ShoppingList : HomeDestination("shopping_list", "Shopping List", Icons.Filled.ShoppingCart)

    companion object {
        private fun iconFor(category: RecipeCategory): ImageVector = when (category) {
            RecipeCategory.BREAKFAST_BRUNCH -> Icons.Filled.FreeBreakfast
            RecipeCategory.APPETIZERS_SNACKS -> Icons.Filled.Fastfood
            RecipeCategory.SOUPS_SALADS -> Icons.Filled.RamenDining
            RecipeCategory.MAIN_DISHES -> Icons.Filled.DinnerDining
            RecipeCategory.SIDE_DISHES -> Icons.Filled.RiceBowl
            RecipeCategory.BAKED_GOODS -> Icons.Filled.BakeryDining
            RecipeCategory.DESSERTS -> Icons.Filled.Icecream
            RecipeCategory.DRINKS_COCKTAILS -> Icons.Filled.LocalBar
            RecipeCategory.SAUCES_CONDIMENTS -> Icons.Filled.WaterDrop
            RecipeCategory.OTHER -> Icons.Filled.Category
        }

        /** Default 2-column order shown on the home grid before the user customizes it. */
        fun gridOrder(): List<HomeDestination> = listOf(
            Category(RecipeCategory.BREAKFAST_BRUNCH),
            Category(RecipeCategory.APPETIZERS_SNACKS),
            Category(RecipeCategory.SOUPS_SALADS),
            Category(RecipeCategory.MAIN_DISHES),
            Category(RecipeCategory.SIDE_DISHES),
            Category(RecipeCategory.BAKED_GOODS),
            Category(RecipeCategory.DESSERTS),
            Category(RecipeCategory.DRINKS_COCKTAILS),
            Category(RecipeCategory.SAUCES_CONDIMENTS),
            Category(RecipeCategory.OTHER),
            AllItems,
            ShoppingList
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    destinations: List<HomeDestination>,
    pinnedKeys: Set<String>,
    categoryCounts: Map<RecipeCategory, Int>,
    onDestinationClick: (HomeDestination) -> Unit,
    onReorder: (List<HomeDestination>) -> Unit,
    onTogglePin: (HomeDestination) -> Unit,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onClockClick: () -> Unit
) {
    var currentOrder by remember { mutableStateOf(destinations) }
    LaunchedEffect(destinations) { currentOrder = destinations }
    val gridState = rememberLazyGridState()
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VaultCuisine") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(com.rekluzlabs.vaultcuisine.R.drawable.background_whisk),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.19f
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentOrder, key = { it.key }) { destination ->
                    val isDragged = draggedKey == destination.key
                    val isPinned = destination.key in pinnedKeys
                    CategoryTile(
                        destination = destination,
                        isPinned = isPinned,
                        isDragged = isDragged,
                        count = (destination as? HomeDestination.Category)?.let { categoryCounts[it.category] ?: 0 },
                        onClick = { onDestinationClick(destination) },
                        onTogglePin = { onTogglePin(destination) },
                        modifier = Modifier
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                translationX = if (isDragged) dragOffset.x else 0f
                                translationY = if (isDragged) dragOffset.y else 0f
                                scaleX = if (isDragged) 1.05f else 1f
                                scaleY = if (isDragged) 1.05f else 1f
                            }
                            .animateItem()
                            .then(
                                if (!isPinned) {
                                    Modifier.pointerInput(destination.key) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { draggedKey = destination.key },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset = dragOffset + amount
                                                val layoutInfo = gridState.layoutInfo
                                                val self = layoutInfo.visibleItemsInfo
                                                    .firstOrNull { it.key == destination.key }
                                                    ?: return@detectDragGesturesAfterLongPress
                                                val centerX = self.offset.x + self.size.width / 2f + dragOffset.x
                                                val centerY = self.offset.y + self.size.height / 2f + dragOffset.y
                                                val target = layoutInfo.visibleItemsInfo
                                                    .filter { it.key != destination.key }
                                                    .filter { it.key !in pinnedKeys }
                                                    .firstOrNull { other ->
                                                        centerX >= other.offset.x &&
                                                            centerX < other.offset.x + other.size.width &&
                                                            centerY >= other.offset.y &&
                                                            centerY < other.offset.y + other.size.height
                                                    }
                                                if (target != null) {
                                                    val from = currentOrder.indexOfFirst { it.key == destination.key }
                                                    val to = currentOrder.indexOfFirst { it.key == target.key }
                                                    if (from != -1 && to != -1 && from != to) {
                                                        val newOrder = currentOrder.toMutableList().apply {
                                                            add(to, removeAt(from))
                                                        }
                                                        currentOrder = newOrder
                                                        dragOffset = Offset(
                                                            x = dragOffset.x - (target.offset.x - self.offset.x),
                                                            y = dragOffset.y - (target.offset.y - self.offset.y)
                                                        )
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                draggedKey = null
                                                dragOffset = Offset.Zero
                                                onReorder(currentOrder)
                                            },
                                            onDragCancel = {
                                                draggedKey = null
                                                dragOffset = Offset.Zero
                                            }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    )
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
}

@Composable
private fun CategoryTile(
    destination: HomeDestination,
    isPinned: Boolean,
    isDragged: Boolean,
    count: Int?,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .clickable(onClick = onClick),
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
            Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (count != null) {
                        Text(
                            text = if (count == 1) "1 recipe" else "$count recipes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (isPinned) "Unpin" else "Pin",
                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
