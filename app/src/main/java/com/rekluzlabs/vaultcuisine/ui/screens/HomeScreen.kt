package com.rekluzlabs.vaultcuisine.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.rekluzlabs.vaultcuisine.data.Recipe
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a")
    .withZone(ZoneId.systemDefault())

private fun formatTimestamp(millis: Long): String =
    dateFormatter.format(Instant.ofEpochMilli(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recipes: List<Recipe>,
    onRecipeClick: (Recipe) -> Unit,
    onScanClick: () -> Unit,
    onDeleteRecipe: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    var recipeToDelete by remember { mutableStateOf<Recipe?>(null) }

    if (recipeToDelete != null) {
        AlertDialog(
            onDismissRequest = { recipeToDelete = null },
            title = { Text("Delete recipe?") },
            text = { Text("Are you sure you want to delete \"${recipeToDelete!!.title}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRecipe(recipeToDelete!!.id)
                    recipeToDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { recipeToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onScanClick) {
                Icon(Icons.Default.Add, contentDescription = "Scan new recipe")
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(com.rekluzlabs.vaultcuisine.R.drawable.background_whisk),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.3f
            )
            if (recipes.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No recipes yet — tap + to scan your first one.")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    items(recipes) { recipe ->
                        ListItem(
                            headlineContent = { Text(recipe.title) },
                            supportingContent = {
                                Text("${recipe.ingredients.size} ingredients  ·  ${formatTimestamp(recipe.createdAt)}")
                            },
                            leadingContent = {
                                IconButton(onClick = { recipeToDelete = recipe }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete ${recipe.title}"
                                    )
                                }
                            },
                            modifier = Modifier.clickable { onRecipeClick(recipe) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
