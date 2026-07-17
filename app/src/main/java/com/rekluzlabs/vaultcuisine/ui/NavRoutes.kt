package com.rekluzlabs.vaultcuisine.ui

sealed class NavRoutes(val route: String) {
    object Home : NavRoutes("home")
    object Scan : NavRoutes("scan")
    object ReviewEdit : NavRoutes("review_edit/{recipeId}?isNew={isNew}") {
        fun build(recipeId: String, isNew: Boolean = false) = "review_edit/$recipeId?isNew=$isNew"
    }
    object RecipeDetail : NavRoutes("recipe_detail/{recipeId}") {
        fun build(recipeId: String) = "recipe_detail/$recipeId"
    }
}
