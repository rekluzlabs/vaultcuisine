package com.rekluzlabs.vaultcuisine.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.rekluzlabs.vaultcuisine.R
import com.rekluzlabs.vaultcuisine.ai.GeminiModels
import com.rekluzlabs.vaultcuisine.ai.GeminiModelVariant
import com.rekluzlabs.vaultcuisine.data.AppSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    hasGeminiKey: Boolean,
    keyVerified: Boolean,
    onSaveGeminiKey: (String) -> Unit,
    onClearGeminiKey: () -> Unit,
    onValidateKey: suspend (String) -> Boolean,
    onReviewPrivacyInfo: () -> Unit = {},
    onExportRecipes: () -> Unit,
    onImportRecipes: () -> Unit,
    onClearAllData: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearKeysDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all data?") },
            text = { Text("This will permanently delete all recipes and reset settings. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showClearDialog = false; onClearAllData() }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearKeysDialog) {
        AlertDialog(
            onDismissRequest = { showClearKeysDialog = false },
            title = { Text("Delete all API keys?") },
            text = { Text("This will remove all stored AI provider keys. You will need to re-enter them to use AI features.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearKeysDialog = false
                    onClearGeminiKey()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearKeysDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showApiKeyDialog) {
        GeminiApiKeyDialog(
            currentKey = if (hasGeminiKey) "PRESENT" else "",
            onDismiss = { showApiKeyDialog = false },
            onSave = {
                onSaveGeminiKey(it)
                showApiKeyDialog = false
            },
            onClear = {
                onClearGeminiKey()
                showApiKeyDialog = false
            },
            onValidate = onValidateKey
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            // ── Scanning ──
            SectionHeader("Scanning")

            SettingsSwitch(
                label = "Auto-open after scan",
                description = "Automatically open the recipe detail screen after scanning.",
                checked = settings.autoOpenAfterScan,
                onCheckedChange = { onSettingsChanged(settings.copy(autoOpenAfterScan = it)) }
            )

            SettingsDropdown(
                label = "OCR language",
                value = settings.ocrLanguage,
                options = listOf("en" to "English", "fr" to "French", "de" to "German", "es" to "Spanish", "it" to "Italian"),
                onValueChanged = { onSettingsChanged(settings.copy(ocrLanguage = it)) }
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Gemini API ──
            SectionHeader("Gemini API")

            Text(
                text = "Used to read and structure scanned recipe images. Images are sent to Google's Gemini API for processing when configured.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            SettingsDropdown(
                label = "AI Model",
                value = settings.geminiModelId,
                options = GeminiModels.variants.map { it.id to it.displayName },
                onValueChanged = {
                    onSettingsChanged(settings.copy(geminiModelId = it))
                    showApiKeyDialog = true
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasGeminiKey) {
                    OutlinedButton(
                        onClick = { showApiKeyDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Update API Key")
                    }
                } else {
                    Button(
                        onClick = { showApiKeyDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Configure API Key")
                    }
                }
                if (hasGeminiKey) {
                    Spacer(Modifier.width(8.dp))
                    if (keyVerified) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "API key verified",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Verified",
                            color = Color(0xFF4CAF50),
                            style = MaterialTheme.typography.labelMedium
                        )
                    } else {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = "API key not verified",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Unverified",
                            color = Color(0xFFE53935),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            if (hasGeminiKey) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showClearKeysDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete API Key", color = MaterialTheme.colorScheme.error)
                }
            }

            Text(
                text = "Review privacy info",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable { onReviewPrivacyInfo() }
                    .padding(top = 8.dp)
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Display ──
            SectionHeader("Display")

            SettingsDropdown(
                label = "Theme",
                value = settings.theme,
                options = listOf(
                    "pantry" to "Pantry (Light)",
                    "cellar" to "Cantina (Dark)",
                    "vault" to "Cast Iron (AMOLED)",
                    "garden" to "Garden Fresh (Green)",
                    "warm_spice" to "Autumn Harvest",
                    "berry_harvest" to "Berry Season",
                    "citrus_glow" to "Citrus Glow",
                    "sage_olive" to "Sage & Olive"
                ),
                onValueChanged = { onSettingsChanged(settings.copy(theme = it)) }
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Data ──
            SectionHeader("Data")

            OutlinedButton(
                onClick = onExportRecipes,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Export all recipes as JSON") }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onImportRecipes,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Import recipes from JSON") }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Clear all data", color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Print ──
            SectionHeader("Print")

            SettingsDropdown(
                label = "Paper size",
                value = settings.printPaperSize,
                options = listOf("default" to "Default", "letter" to "Letter", "a4" to "A4"),
                onValueChanged = { onSettingsChanged(settings.copy(printPaperSize = it)) }
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── About ──
            SectionHeader("About")

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rekluz Labs \u00A9",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "rekluzlabs@gmail.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:rekluzlabs@gmail.com")
                            }
                            context.startActivity(Intent.createChooser(intent, "Send email"))
                        }
                    )
                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "https://rekluzlabs.github.io/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://rekluzlabs.github.io/"))
                            context.startActivity(intent)
                        }
                    )
                    Spacer(Modifier.height(4.dp))

                    val versionName = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    } catch (_: Exception) { "?" }
                    Text(
                        text = "Version $versionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(16.dp))

                Image(
                    painter = painterResource(id = R.drawable.rl_transparent),
                    contentDescription = "Rekluz Labs logo",
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun GeminiApiKeyDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onValidate: suspend (String) -> Boolean
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var apiKeyInput by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<Boolean?>(null) }
    var showValidationError by remember { mutableStateOf(false) }

    if (showValidationError) {
        AlertDialog(
            onDismissRequest = { showValidationError = false },
            title = { Text("Validation Failed") },
            text = { Text("The API key you entered was not accepted by Gemini. Please check the key and your connection and try again.") },
            confirmButton = {
                TextButton(onClick = { showValidationError = false }) { Text("OK") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Gemini API") },
        text = {
            Column {
                Text(
                    text = GeminiModels.KEY_INSTRUCTIONS,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "Get API Key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GeminiModels.KEY_OBTAIN_URL))
                            context.startActivity(intent)
                        }
                        .padding(vertical = 8.dp)
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { 
                        apiKeyInput = it
                        validationResult = null // Reset validation on change
                    },
                    label = { Text("API Key") },
                    placeholder = { if (currentKey.isNotEmpty()) Text("••••••••••••••••") else Text("Enter key") },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isValidating) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else if (validationResult == true) {
                                Icon(Icons.Filled.CheckCircle, "Verified", tint = androidx.compose.ui.graphics.Color(0xFF4CAF50))
                                Text("Verified", color = androidx.compose.ui.graphics.Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.width(8.dp))
                            }
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle Visibility"
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (apiKeyInput.isNotBlank()) {
                        scope.launch {
                            isValidating = true
                            val isValid = onValidate(apiKeyInput.trim())
                            isValidating = false
                            validationResult = isValid
                            if (isValid) {
                                onSave(apiKeyInput.trim())
                            } else {
                                showValidationError = true
                            }
                        }
                    }
                },
                enabled = apiKeyInput.isNotBlank() && !isValidating
            ) {
                if (isValidating) {
                    Text("Verifying...")
                } else {
                    Text("Verify & Save")
                }
            }
        },
        dismissButton = {
            Row {
                if (currentKey.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("Clear Key", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun SettingsSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.padding(horizontal = 8.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChanged: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == value }?.second ?: value

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, display) ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = { onValueChanged(key); expanded = false }
                )
            }
        }
    }
}
