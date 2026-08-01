package com.rekluzlabs.vaultcuisine.ui

sealed class NavRoutes(val route: String) {
    object Welcome : NavRoutes("welcome")
    object Home : NavRoutes("home")
    object Scan : NavRoutes("scan?category={category}") {
        /** @param categoryName optional recipe category to pre-select for the post-scan picker. */
        fun build(categoryName: String? = null) =
            if (categoryName == null) "scan" else "scan?category=$categoryName"
    }
    object CategoryRecipes : NavRoutes("category_recipes/{categoryName}") {
        fun build(categoryName: String) = "category_recipes/$categoryName"
    }
    object ReviewEdit : NavRoutes("review_edit/{recipeId}?isNew={isNew}") {
        fun build(recipeId: String, isNew: Boolean = false) = "review_edit/$recipeId?isNew=$isNew"
    }
    object RecipeDetail : NavRoutes("recipe_detail/{recipeId}") {
        fun build(recipeId: String) = "recipe_detail/$recipeId"
    }
    object Cooking : NavRoutes("cooking/{recipeId}?step={step}") {
        fun build(recipeId: String, step: Int = 0) = "cooking/$recipeId?step=$step"
    }
    object Settings : NavRoutes("settings")
    object BackupRestore : NavRoutes("backup_restore")
    object Clock : NavRoutes("clock")
    object ShoppingList : NavRoutes("shopping_list")
}
