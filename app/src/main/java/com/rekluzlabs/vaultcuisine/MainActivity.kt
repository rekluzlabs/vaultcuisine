package com.rekluzlabs.vaultcuisine

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rekluzlabs.vaultcuisine.data.AppSettings
import com.rekluzlabs.vaultcuisine.ui.NavRoutes
import com.rekluzlabs.vaultcuisine.ui.screens.CookingModeScreen
import com.rekluzlabs.vaultcuisine.ui.screens.HomeScreen
import com.rekluzlabs.vaultcuisine.ui.screens.RecipeDetailScreen
import com.rekluzlabs.vaultcuisine.ui.screens.ScanScreen
import com.rekluzlabs.vaultcuisine.ui.screens.SettingsScreen
import com.rekluzlabs.vaultcuisine.ui.screens.WelcomeScreen
import com.rekluzlabs.vaultcuisine.ui.theme.VaultCuisineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    LaunchedEffect(Unit) {
        vm.userMessages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    val cookingStep by remember { mutableStateOf(intent?.getIntExtra("cooking_step", 0) ?: 0) }

    LaunchedEffect(intent) {
        val openCooking = intent?.getStringExtra("open_cooking")
        if (openCooking != null) {
            navController.navigate(NavRoutes.Cooking.build(openCooking, cookingStep)) {
                popUpTo(NavRoutes.Home.route)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val json = vm.exportRecipesJson()
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Toast.makeText(context, "Recipes exported", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            if (json != null) {
                vm.importRecipesJson(json) { count ->
                    val msg = if (count >= 0) "Imported $count recipes"
                    else "Import failed — invalid file"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

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
            HomeScreen(
                recipes = recipes,
                onRecipeClick = { recipe ->
                    navController.navigate(NavRoutes.RecipeDetail.build(recipe.id))
                },
                onScanClick = { navController.navigate(NavRoutes.Scan.route) },
                onSettingsClick = { navController.navigate(NavRoutes.Settings.route) }
            )
        }

        composable(NavRoutes.Scan.route) {
            var isProcessing by remember { mutableStateOf(false) }
            val scanMessage by vm.scanMessage.collectAsState()
            ScanScreen(
                isProcessing = isProcessing,
                scanMessage = scanMessage,
                onImageCaptured = { bitmap: Bitmap ->
                    isProcessing = true
                    vm.processScannedImage(bitmap) { savedRecipeId ->
                        isProcessing = false
                        if (settings.autoOpenAfterScan) {
                            navController.navigate(NavRoutes.ReviewEdit.build(savedRecipeId, isNew = true))
                        } else {
                            Toast.makeText(context, "Recipe saved", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
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
                    navController.navigate(NavRoutes.Scan.route)
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

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                settings = settings,
                onSettingsChanged = { vm.updateSettings(it) },
                hasGeminiKey = vm.hasGeminiApiKey(),
                keyVerified = vm.geminiKeyVerified.collectAsState().value,
                onSaveGeminiKey = { vm.saveGeminiApiKey(it) },
                onClearGeminiKey = { vm.clearGeminiApiKey() },
                onValidateKey = { key -> vm.validateGeminiApiKey(key) },
                onExportRecipes = { exportLauncher.launch("vaultcuisine_recipes.json") },
                onImportRecipes = { importLauncher.launch(arrayOf("application/json")) },
                onClearAllData = {
                    vm.clearAllData()
                    Toast.makeText(context, "All data cleared", Toast.LENGTH_SHORT).show()
                },
                onReviewPrivacyInfo = { vm.reviewGeminiConsent() },
                onBack = { navController.popBackStack() }
            )
        }
    }

    val needsConsent by vm.needsGeminiConsent.collectAsState()
    val isFromSettings by vm.isConsentFromSettings.collectAsState()

    if (needsConsent) {
        GeminiConsentDialog(
            isFromSettings = isFromSettings,
            onAccept = { vm.acceptGeminiConsent() },
            onReject = { vm.rejectGeminiConsent() },
            onDismiss = {
                if (isFromSettings) vm.dismissGeminiConsentReview()
                else vm.rejectGeminiConsent()
            }
        )
    }
}

@Composable
private fun GeminiConsentDialog(
    isFromSettings: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Image Processing Consent") },
        text = {
            Column {
                Text(
                    text = "This photo will be sent to Google's Gemini API for processing. It leaves your device.",
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
            }
        },
        confirmButton = {
            TextButton(onClick = if (isFromSettings) onDismiss else onAccept) {
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
