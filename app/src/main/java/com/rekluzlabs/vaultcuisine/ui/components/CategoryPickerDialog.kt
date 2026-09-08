/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
package com.rekluzlabs.vaultcuisine.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rekluzlabs.vaultcuisine.data.RecipeCategory

/**
 * Shown right after a scan or import completes, and again whenever the user
 * long-presses a recipe tile to move it. Selection is manual for now —
 * defaults to [RecipeCategory.OTHER] unless [initialCategory] is passed
 * (e.g. when re-categorizing an existing recipe). Auto-suggestion based on
 * recipe contents is deferred until there's a reliable on-device way to do it.
 *
 * Pass [recipeTitle] when available so the user knows which recipe they are
 * filing (especially important during backup import, when recipes are shown
 * one at a time and the title isn't visible anywhere else on screen).
 */
@Composable
fun CategoryPickerDialog(
    onConfirm: (RecipeCategory) -> Unit,
    onDismiss: () -> Unit,
    initialCategory: RecipeCategory = RecipeCategory.OTHER,
    recipeTitle: String? = null
) {
    var selected by remember { mutableStateOf(initialCategory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Where does this recipe belong?")
                if (!recipeTitle.isNullOrBlank()) {
                    Text(
                        text = recipeTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                RecipeCategory.entries.forEach { category ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == category,
                                onClick = { selected = category }
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selected == category,
                            onClick = { selected = category }
                        )
                        Text(
                            text = category.displayName,
                            modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
