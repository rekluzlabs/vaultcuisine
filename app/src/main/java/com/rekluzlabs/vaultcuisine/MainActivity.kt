/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
package com.rekluzlabs.vaultcuisine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rekluzlabs.vaultcuisine.data.AppSettings
import com.rekluzlabs.vaultcuisine.data.RecipeCategory
import com.rekluzlabs.vaultcuisine.data.backup.BackupResult
import com.rekluzlabs.vaultcuisine.data.backup.BackupType
import com.rekluzlabs.vaultcuisine.ui.NavRoutes
import com.rekluzlabs.vaultcuisine.ui.screens.BackupRestoreScreen
import com.rekluzlabs.vaultcuisine.ui.screens.CategoryRecipesScreen
import com.rekluzlabs.vaultcuisine.ui.screens.CookingModeScreen
import com.rekluzlabs.vaultcuisine.ui.screens.HomeDestination
import com.rekluzlabs.vaultcuisine.ui.screens.HomeScreen
import com.rekluzlabs.vaultcuisine.ui.screens.RecipeDetailScreen
import com.rekluzlabs.vaultcuisine.ui.screens.ScanScreen
import com.rekluzlabs.vaultcuisine.ui.screens.SettingsScreen
import com.rekluzlabs.vaultcuisine.ui.screens.ShoppingListScreen
import com.rekluzlabs.vaultcuisine.ui.screens.WelcomeScreen
import com.rekluzlabs.vaultcuisine.ui.theme.VaultCuisineTheme
import com.rekluzlabs.vaultcuisine.ui.components.CategoryPickerDialog
import com.rekluzlabs.vaultcuisine.ui.screens.ClockScreen
import com.rekluzlabs.vaultcuisine.timer.CLOCK_ALARM_MAP_KEY
import com.rekluzlabs.vaultcuisine.timer.CLOCK_ALARM_RECIPE_ID
import com.rekluzlabs.vaultcuisine.timer.CLOCK_ALARM_STEP_INDEX
import com.rekluzlabs.vaultcuisine.timer.CLOCK_TIMER_MAP_KEY
import com.rekluzlabs.vaultcuisine.timer.CLOCK_TIMER_RECIPE_ID
import com.rekluzlabs.vaultcuisine.timer.CLOCK_TIMER_STEP_INDEX
import com.rekluzlabs.vaultcuisine.timer.DraggableTimerOverlay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = (LocalContext.current.applicationContext as VaultCuisineApp)
            val vm: MainViewModel = viewModel(factory = MainViewModel.factory(app))
            val settings by vm.settings.collectAsState()
            
            VaultCuisineTheme(themeMode = settings.theme) {
                VaultCuisineNavHost(vm, intent)
            }
        }
    }
}

@Composable
fun VaultCuisineNavHost(vm: MainViewModel, intent: android.content.Intent? = null) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val recipes by vm.recipes.collectAsState()
    val settings by vm.settings.collectAsState()
    val activeTimers by vm.activeTimers.collectAsState()
    val ringingTimers by vm.ringingTimers.collectAsState()

    LaunchedEffect(Unit) {
        vm.userMessages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        vm.loadActiveTimers()
    }

    val cookingStep by remember { mutableStateOf(intent?.getIntExtra("cooking_step", 0) ?: 0) }

    LaunchedEffect(intent) {
        val openCooking = intent?.getStringExtra("open_cooking")
        if (openCooking != null) {
            if (openCooking == CLOCK_TIMER_RECIPE_ID || openCooking == CLOCK_ALARM_RECIPE_ID) {
                navController.navigate(NavRoutes.Clock.route) {
                    popUpTo(NavRoutes.Welcome.route) { inclusive = true }
                }
            } else {
                navController.navigate(NavRoutes.Cooking.build(openCooking, cookingStep)) {
                    popUpTo(NavRoutes.Home.route)
                }
            }
        }
    }

    val scope = rememberCoroutineScope()

    val zipSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val out = try { context.contentResolver.openOutputStream(uri) } catch (e: Exception) {
            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show(); null
        }
        if (out == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                showBackupResult(context, vm.saveZipBackup(out))
            } catch (e: Exception) {
                Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                try { out.close() } catch (_: Exception) {}
            }
        }
    }

    val jsonSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val out = try { context.contentResolver.openOutputStream(uri) } catch (e: Exception) {
            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show(); null
        }
        if (out == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                showBackupResult(context, vm.saveJsonBackup(out))
            } catch (e: Exception) {
                Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                try { out.close() } catch (_: Exception) {}
            }
        }
    }

    val backupOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.pickRestoreFile(uri)
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = NavRoutes.Welcome.route) {

            composable(NavRoutes.Welcome.route) {
                WelcomeScreen(
                    onStartClick = {
                        navController.navigate(NavRoutes.Home.route) {
                            popUpTo(NavRoutes.Welcome.route) { inclusive = true }
                        }
                    }
                )
            }

        composable(NavRoutes.Home.route) {
            val allDestinations = remember { HomeDestination.gridOrder() }
            val savedOrder = settings.homeTileOrder
            val pinnedKeys = settings.pinnedHomeTiles.toSet()
            val ordered = remember(savedOrder, allDestinations, pinnedKeys) {
                val byKey = allDestinations.associateBy { it.key }
                val customized = savedOrder.mapNotNull { byKey[it] }
                val full = (customized + allDestinations.filterNot { it.key in savedOrder })
                    .distinctBy { it.key }
                // Pinned tiles always float to the top, in their relative order,
                // while they stay pinned.
                val pinned = full.filter { it.key in pinnedKeys }
                val unpinned = full.filter { it.key !in pinnedKeys }
                pinned + unpinned
            }
            val categoryCounts = remember(recipes) {
                recipes.groupingBy { it.category }.eachCount()
            }
            HomeScreen(
                destinations = ordered,
                pinnedKeys = pinnedKeys,
                categoryCounts = categoryCounts,
                onDestinationClick = { destination ->
                    when (destination) {
                        is HomeDestination.Category ->
                            navController.navigate(NavRoutes.CategoryRecipes.build(destination.category.name))
                        HomeDestination.AllItems ->
                            navController.navigate(NavRoutes.CategoryRecipes.build("ALL"))
                        HomeDestination.ShoppingList ->
                            navController.navigate(NavRoutes.ShoppingList.route)
                    }
                },
                onReorder = { newOrder ->
                    vm.setHomeTileOrder(newOrder.map { it.key })
                },
                onTogglePin = { destination ->
                    vm.setHomeTilePinned(destination.key, destination.key !in pinnedKeys)
                },
                onScanClick = { navController.navigate(NavRoutes.Scan.build()) },
                onSettingsClick = { navController.navigate(NavRoutes.Settings.route) },
                onClockClick = { navController.navigate(NavRoutes.Clock.route) }
            )
        }

        composable(
            NavRoutes.CategoryRecipes.route,
            arguments = listOf(navArgument("categoryName") { type = NavType.StringType })
        ) { backStackEntry ->
            val categoryName = backStackEntry.arguments?.getString("categoryName") ?: "ALL"
            val filtered = if (categoryName == "ALL") {
                recipes
            } else {
                recipes.filter { it.category == runCatching { RecipeCategory.valueOf(categoryName) }.getOrNull() }
            }
            val pinnedRecipeIds = settings.pinnedRecipeIds.toSet()
            CategoryRecipesScreen(
                title = runCatching { RecipeCategory.valueOf(categoryName) }
                    .getOrNull()?.displayName ?: "All Items",
                recipes = filtered,
                pinnedRecipeIds = pinnedRecipeIds,
                onToggleRecipePin = { recipe ->
                    vm.setRecipePinned(
                        recipe.id,
                        recipe.id !in pinnedRecipeIds
                    )
                },
                onRecipeClick = { recipe ->
                    navController.navigate(NavRoutes.RecipeDetail.build(recipe.id))
                },
                onMoveRecipe = { recipe, newCategory -> vm.moveRecipeToCategory(recipe, newCategory) },
                onScanClick = {
                    val category = runCatching { RecipeCategory.valueOf(categoryName) }.getOrNull()
                    navController.navigate(NavRoutes.Scan.build(category?.name))
                },
                onClockClick = { navController.navigate(NavRoutes.Clock.route) },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            NavRoutes.Scan.route,
            arguments = listOf(
                navArgument("category") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            var isProcessing by remember { mutableStateOf(false) }
            val scanMessage by vm.scanMessage.collectAsState()
            val defaultCategory = backStackEntry.arguments?.getString("category")
                ?.let { runCatching { RecipeCategory.valueOf(it) }.getOrNull() }
            ScanScreen(
                isProcessing = isProcessing,
                scanMessage = scanMessage,
                onImageCaptured = { bitmap: Bitmap ->
                    isProcessing = true
                    vm.processScannedImage(
                        bitmap,
                        defaultCategory = defaultCategory,
                        onSaved = { savedRecipeId ->
                            isProcessing = false
                            if (settings.autoOpenAfterScan) {
                                navController.navigate(NavRoutes.ReviewEdit.build(savedRecipeId, isNew = true))
                            } else {
                                Toast.makeText(context, "Recipe saved", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                        },
                        onCancelled = {
                            isProcessing = false
                            Toast.makeText(context, "Scan cancelled", Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                onManualEntry = { category ->
                    vm.createManualRecipe(category) { recipeId ->
                        navController.navigate(NavRoutes.ReviewEdit.build(recipeId, isNew = true))
                    }
                }
            )
        }

        composable(NavRoutes.RecipeDetail.route) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId") ?: return@composable
            RecipeDetailScreen(
                recipeId = recipeId,
                vm = vm,
                onBack = { navController.popBackStack() },
                onReScan = { newId ->
                    navController.navigate(NavRoutes.ReviewEdit.build(newId, isNew = true))
                },
                onRetakePhoto = {
                    vm.clearLastScannedImageBytes()
                    navController.navigate(NavRoutes.Scan.build())
                },
                onStartCooking = {
                    navController.navigate(NavRoutes.Cooking.build(recipeId))
                }
            )
        }

        composable(
            NavRoutes.ReviewEdit.route,
            arguments = listOf(
                navArgument("recipeId") { type = NavType.StringType },
                navArgument("isNew") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId") ?: return@composable
            val isNew = backStackEntry.arguments?.getBoolean("isNew") ?: false
            RecipeDetailScreen(
                recipeId = recipeId,
                vm = vm,
                isNewRecipe = isNew,
                onBack = { navController.popBackStack() },
                onReScan = { newId ->
                    navController.navigate(NavRoutes.ReviewEdit.build(newId, isNew = true))
                },
                onRetakePhoto = {
                    vm.clearLastScannedImageBytes()
                    navController.popBackStack()
                }
            )
        }

        composable(
            NavRoutes.Cooking.route,
            arguments = listOf(
                navArgument("recipeId") { type = NavType.StringType },
                navArgument("step") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId") ?: return@composable
            val step = backStackEntry.arguments?.getInt("step") ?: 0
            CookingModeScreen(
                recipeId = recipeId,
                initialStep = step,
                vm = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Clock.route) {
            ClockScreen(
                vm = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.ShoppingList.route) {
            ShoppingListScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                settings = settings,
                onSettingsChanged = { vm.updateSettings(it) },
                hasGeminiKey = vm.hasGeminiApiKey(),
                keyVerified = vm.geminiKeyVerified.collectAsState().value,
                onSaveGeminiKey = { vm.saveGeminiApiKey(it) },
                onClearGeminiKey = { vm.clearGeminiApiKey() },
                onValidateKey = { key -> vm.validateGeminiApiKey(key) },
                onBackupRestore = { navController.navigate(NavRoutes.BackupRestore.route) },
                onReviewPrivacyInfo = { vm.reviewGeminiConsent() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.BackupRestore.route) {
            val pendingRestoreUri by vm.pendingRestoreUri.collectAsState()
            val isRestoring by vm.isRestoring.collectAsState()
            BackupRestoreScreen(
                recipeCount = recipes.size,
                pendingRestoreUri = pendingRestoreUri,
                isRestoring = isRestoring,
                onSaveBackup = { type ->
                    when (type) {
                        BackupType.ZIP -> zipSaveLauncher.launch(vm.zipBackupFileName())
                        BackupType.JSON -> jsonSaveLauncher.launch(vm.jsonBackupFileName())
                    }
                },
                onPickRestoreFile = { backupOpenLauncher.launch(arrayOf("*/*")) },
                onConfirmRestore = { vm.confirmRestore() },
                onDismissRestore = { vm.dismissRestore() },
                onDeleteAllRecipes = {
                    vm.clearAllRecipeData()
                    Toast.makeText(context, "All recipes deleted", Toast.LENGTH_SHORT).show()
                },
                onBack = { navController.popBackStack() }
            )
        }
    }

        val clockTimerRinging = CLOCK_TIMER_MAP_KEY in ringingTimers
        val clockAlarmRinging = CLOCK_ALARM_MAP_KEY in ringingTimers
        val canDrawOverlays =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        if ((clockTimerRinging || clockAlarmRinging) && !canDrawOverlays) {
            DraggableTimerOverlay(
                activeTimer = activeTimers[CLOCK_TIMER_MAP_KEY]
                    ?: activeTimers[CLOCK_ALARM_MAP_KEY],
                tick = 0L,
                isRinging = true,
                onStop = {
                    vm.dismissAlarm(context, CLOCK_TIMER_RECIPE_ID, CLOCK_TIMER_STEP_INDEX)
                    vm.dismissAlarm(context, CLOCK_ALARM_RECIPE_ID, CLOCK_ALARM_STEP_INDEX)
                },
                onClose = {
                    vm.dismissAlarm(context, CLOCK_TIMER_RECIPE_ID, CLOCK_TIMER_STEP_INDEX)
                    vm.dismissAlarm(context, CLOCK_ALARM_RECIPE_ID, CLOCK_ALARM_STEP_INDEX)
                }
            )
        }
    }

    val needsConsent by vm.needsGeminiConsent.collectAsState()
    val isFromSettings by vm.isConsentFromSettings.collectAsState()

    if (needsConsent) {
        GeminiConsentDialog(
            isFromSettings = isFromSettings,
            onAccept = { neverShowAgain ->
                if (neverShowAgain) vm.setShowGeminiConsent(false)
                vm.acceptGeminiConsent()
            },
            onReject = { vm.rejectGeminiConsent() },
            onDismiss = {
                if (isFromSettings) vm.dismissGeminiConsentReview()
                else vm.rejectGeminiConsent()
            }
        )
    }

    val scanChoiceRequested by vm.scanChoiceRequested.collectAsState()
    if (scanChoiceRequested) {
        AlertDialog(
            onDismissRequest = { vm.chooseScanRoute(false) },
            title = { Text("Where should this scan be read?") },
            text = {
                Text(
                    "You have a Gemini API key saved. Send this photo to Gemini for a smarter read, or scan it locally on this device?"
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.chooseScanRoute(true) }) {
                    Text("Use Gemini")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.chooseScanRoute(false) }) {
                    Text("Scan Locally")
                }
            }
        )
    }

    val recipeAwaitingCategory by vm.recipeAwaitingCategory.collectAsState()
    recipeAwaitingCategory?.let { pending ->
        CategoryPickerDialog(
            initialCategory = pending.category,
            recipeTitle = pending.title,
            onConfirm = { vm.confirmCategorySelection(it) },
            onDismiss = { vm.cancelCategorySelection() }
        )
    }
}

@Composable
private fun GeminiConsentDialog(
    isFromSettings: Boolean,
    onAccept: (neverShowAgain: Boolean) -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var neverShowAgain by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Image Processing Consent") },
        text = {
            Column {
                Text(
                    text = "This photo will be sent to Google's Gemini API for processing. A EXIF stripped down version of the image leaves your device.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "On the free tier, Google may use API inputs and outputs for model improvement. Paid-tier data is not used for training.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "See Google's Gemini API terms for details:",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "ai.google.dev/gemini-api/terms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.google.dev/gemini-api/terms"))
                        context.startActivity(intent)
                    }
                )
                if (!isFromSettings) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { neverShowAgain = !neverShowAgain }
                    ) {
                        Checkbox(
                            checked = neverShowAgain,
                            onCheckedChange = { neverShowAgain = it }
                        )
                        Text("Never show this popup again")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isFromSettings) onDismiss() else onAccept(neverShowAgain)
                }
            ) {
                Text(if (isFromSettings) "Close" else "Continue")
            }
        },
        dismissButton = {
            if (!isFromSettings) {
                TextButton(onClick = onReject) {
                    Text("Cancel")
                }
            }
        }
    )
}

private fun showBackupResult(context: Context, result: BackupResult) {
    val message = when (result) {
        is BackupResult.Success -> "Backup saved (${result.count} recipes)"
        is BackupResult.Error -> result.message
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
