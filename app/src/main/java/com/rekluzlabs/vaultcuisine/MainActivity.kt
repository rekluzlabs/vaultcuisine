package com.rekluzlabs.vaultcuisine

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rekluzlabs.vaultcuisine.ui.NavRoutes
import com.rekluzlabs.vaultcuisine.ui.screens.HomeScreen
import com.rekluzlabs.vaultcuisine.ui.screens.RecipeDetailScreen
import com.rekluzlabs.vaultcuisine.ui.screens.ScanScreen
import com.rekluzlabs.vaultcuisine.ui.theme.VaultCuisineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VaultCuisineTheme {
                VaultCuisineNavHost()
            }
        }
    }
}

@Composable
fun VaultCuisineNavHost() {
    val navController = rememberNavController()
    val app = (androidx.compose.ui.platform.LocalContext.current.applicationContext as VaultCuisineApp)
    val vm: MainViewModel = viewModel(factory = MainViewModel.factory(app))

    val recipes by vm.recipes.collectAsState()

    NavHost(navController = navController, startDestination = NavRoutes.Home.route) {

        composable(NavRoutes.Home.route) {
            HomeScreen(
                recipes = recipes,
                onRecipeClick = { recipe ->
                    navController.navigate(NavRoutes.RecipeDetail.build(recipe.id))
                },
                onScanClick = { navController.navigate(NavRoutes.Scan.route) },
                onDeleteRecipe = { recipeId -> vm.deleteRecipe(recipeId) }
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
                        navController.navigate(NavRoutes.ReviewEdit.build(savedRecipeId, isNew = true))
                    }
                }
            )
        }

        composable(NavRoutes.RecipeDetail.route) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId") ?: return@composable
            RecipeDetailScreen(
                recipeId = recipeId,
                vm = vm,
                onBack = { navController.popBackStack() }
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
    }
}
