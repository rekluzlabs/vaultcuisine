package com.rekluzlabs.vaultcuisine.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rekluzlabs.vaultcuisine.data.backup.BackupType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    recipeCount: Int,
    pendingRestoreUri: Uri?,
    isRestoring: Boolean,
    onSaveBackup: (BackupType) -> Unit,
    onPickRestoreFile: () -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissRestore: () -> Unit,
    onDeleteAllRecipes: () -> Unit,
    onBack: () -> Unit
) {
    var showChooseType by remember { mutableStateOf(false) }
    var lastBackupType by remember { mutableStateOf<BackupType?>(null) }
    var showDeleteStep1 by remember { mutableStateOf(false) }
    var showDeleteStep2 by remember { mutableStateOf(false) }
    var deleteConfirmInput by remember { mutableStateOf("") }

    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = onDismissRestore,
            title = { Text("Restore from backup?") },
            text = {
                Text(
                    "This will merge the backup with your existing recipes.\n\n" +
                        "\u2022 New recipes will be added\n" +
                        "\u2022 Recipes with matching IDs will be updated\n" +
                        "\u2022 Your other recipes are kept\n\n" +
                        "Photos are included if this is a full (ZIP) backup."
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmRestore) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = onDismissRestore) { Text("Cancel") }
            }
        )
    }

    if (isRestoring) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Restoring backup\u2026") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Please wait\u2026")
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    if (showChooseType) {
        AlertDialog(
            onDismissRequest = { showChooseType = false },
            title = { Text("Save a backup") },
            text = { Text("Choose which backup to save.") },
            confirmButton = {
                TextButton(onClick = {
                    showChooseType = false
                    onSaveBackup(BackupType.ZIP)
                }) { Text("Full Backup (ZIP)") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showChooseType = false
                    onSaveBackup(BackupType.JSON)
                }) { Text("Data Only (JSON)") }
            }
        )
    }

    if (showDeleteStep1) {
        AlertDialog(
            onDismissRequest = { showDeleteStep1 = false },
            title = { Text("Delete all recipes?") },
            text = {
                Text(
                    "This will permanently delete $recipeCount recipes and all their photos. " +
                        "This cannot be undone.\n\n" +
                        "Consider creating a backup first if you might want these recipes later."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteStep1 = false
                    deleteConfirmInput = ""
                    showDeleteStep2 = true
                }) {
                    Text("Continue", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteStep1 = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteStep2) {
        AlertDialog(
            onDismissRequest = { showDeleteStep2 = false },
            title = { Text("Are you absolutely sure?") },
            text = {
                Column {
                    Text("Type DELETE to permanently erase all recipes and photos. This cannot be undone.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = deleteConfirmInput,
                        onValueChange = { deleteConfirmInput = it },
                        label = { Text("Type DELETE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteStep2 = false
                        deleteConfirmInput = ""
                        onDeleteAllRecipes()
                    },
                    enabled = deleteConfirmInput == "DELETE"
                ) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteStep2 = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup, Restore & Delete") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionHeader("Back Up")

            OutlinedCard(
                onClick = {
                    lastBackupType = BackupType.ZIP
                    onSaveBackup(BackupType.ZIP)
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Full Backup (ZIP)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Recipes + all photos. Recommended \u2014 restores your collection to an identical state.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    lastBackupType = BackupType.JSON
                    onSaveBackup(BackupType.JSON)
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) { Text("Data Only (JSON) \u2014 no photos") }

            OutlinedButton(
                onClick = {
                    val type = lastBackupType
                    if (type != null) onSaveBackup(type) else showChooseType = true
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save to Cloud / Save As\u2026") }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionHeader("Restore")

            OutlinedButton(
                onClick = onPickRestoreFile,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restore from Backup\u2026") }

            Text(
                text = "Choose a .zip (full backup) or .json (data only) file. Restoring merges with your existing recipes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Delete all (destructive, separated from backup/restore) ──
            OutlinedButton(
                onClick = { showDeleteStep1 = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Delete All Recipes & Photos", color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    )
}
