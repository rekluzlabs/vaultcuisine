package com.rekluzlabs.vaultcuisine

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rekluzlabs.vaultcuisine.data.AppSettings
import com.rekluzlabs.vaultcuisine.ui.NavRoutes
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
            val darkTheme = when (settings.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val amoled = settings.theme == "amoled"
            VaultCuisineTheme(darkTheme = darkTheme, amoled = amoled) {
                VaultCuisineNavHost(vm)
            }
        }
    }
}

@Composable
fun VaultCuisineNavHost(vm: MainViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val recipes by vm.recipes.collectAsState()
    val settings by vm.settings.collectAsState()

    LaunchedEffect(Unit) {
        vm.userMessages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
                onDeleteRecipe = { recipeId -> vm.deleteRecipe(recipeId) },
                onSettingsClick = { navController.navigate(NavRoutes.Settings.route) }
            )
        }

        composable(NavRoutes.Scan.route) {
            var isProcessing by remember { mutableStateOf(false) }
            ScanScreen(
                isProcessing = isProcessing,
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
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                settings = settings,
                onSettingsChanged = { vm.updateSettings(it) },
                hasGeminiKey = vm.hasGeminiApiKey(),
                onSaveGeminiKey = { vm.saveGeminiApiKey(it) },
                onClearGeminiKey = { vm.clearGeminiApiKey() },
                onExportRecipes = { exportLauncher.launch("vaultcuisine_recipes.json") },
                onImportRecipes = { importLauncher.launch(arrayOf("application/json")) },
                onClearAllData = {
                    vm.clearAllData()
                    Toast.makeText(context, "All data cleared", Toast.LENGTH_SHORT).show()
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
