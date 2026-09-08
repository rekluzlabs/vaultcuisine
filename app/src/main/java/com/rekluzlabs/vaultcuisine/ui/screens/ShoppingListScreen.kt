package com.rekluzlabs.vaultcuisine.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private val DEFAULT_SHOPPING_ITEMS = listOf(
    "Almond flour",
    "Whipping cream",
    "Mozzarella cheese",
    "Super sleep",
    "Advil",
    "Almond extract",
    "Almonds",
    "Apple",
    "Avocados",
    "Bacon",
    "Bananas",
    "Batteries AA",
    "Bean Sprouts",
    "Beef",
    "Bleach",
    "Bread",
    "Bread flour",
    "Broccoli",
    "brown sugar",
    "Bubly",
    "buns",
    "Butter",
    "Cake",
    "Calamari",
    "Canned ham",
    "Car wash soap",
    "Carrots",
    "Cashews",
    "Cauliflower",
    "Cereal",
    "Chicken breast",
    "Chicken stock",
    "Chickpeas",
    "CLR cleaner",
    "Coconut milk",
    "Coffee",
    "Coldcuts",
    "Corn starch",
    "creamo",
    "Dad's cookies",
    "Dish soap",
    "Dove men's soap",
    "Eggplant",
    "eggs",
    "Epson salt",
    "Fairlife milk",
    "Flour",
    "Garlic",
    "Garlic powder",
    "Ginger",
    "Grapefruit",
    "Green beans",
    "Green onions",
    "Ground beef",
    "Gyoza",
    "Umami spice",
    "Panko bread crumbs",
    "apple cider vinegar",
    "Gum",
    "Hand soap refill",
    "Honey",
    "C batteries",
    "Humous",
    "Iced tea mix",
    "Icing sugar",
    "Imodium",
    "Instant mashed potatoes",
    "Italian parsley",
    "Jams",
    "Kleenex",
    "Lemons",
    "Light bulbs",
    "Lotto",
    "Lysol wipes",
    "Magic eraser",
    "Margarine",
    "Milk",
    "Mushrooms",
    "White caulking",
    "Olive oil",
    "Olives",
    "Onion powder",
    "Oranges",
    "Paper towels",
    "Parmesan cheese",
    "Vaseline",
    "Toothpaste",
    "Parsley",
    "Peas",
    "Pita bread",
    "Poppy seeds",
    "Pork belly",
    "Pork cutlets",
    "propane",
    "Prune juice",
    "Raspberries",
    "Red pepper",
    "Rice",
    "Robax",
    "Salmon",
    "Sausage for pizza",
    "Sesame oil",
    "Sesame seeds",
    "Shampoo",
    "Soap",
    "Soft drinks",
    "Spaghetti",
    "Spam",
    "Spinach",
    "Spring mix salad",
    "Strawberries",
    "Sugar",
    "Thermacare",
    "Tide",
    "Toilet paper",
    "Tomato paste",
    "Tomato sauce",
    "Peanut butter",
    "Tomatoes",
    "Trail mix",
    "Turkey",
    "Rice vinegar",
    "WD-40",
    "Werther's",
    "Yogurt"
)

/**
 * A simple shopping list shown when the user taps the Shopping List tile on
 * the home grid. Items start unchecked; tap an item to mark it purchased,
 * which strikes it out and moves it to the bottom of the list. Tapping it
 * again unchecks it and moves it back into the active list. "Reset" restores
 * every item to the unchecked state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(onBack: () -> Unit) {
    val checked = remember { mutableStateMapOf<String, Boolean>() }

    val orderedItems = DEFAULT_SHOPPING_ITEMS.filter { checked[it] != true } +
        DEFAULT_SHOPPING_ITEMS.filter { checked[it] == true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping List") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { checked.clear() }) {
                        Text("Reset")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(orderedItems, key = { it }) { item ->
                val isChecked = checked[item] == true
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { checked[item] = !isChecked }
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isChecked) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                        contentDescription = if (isChecked) "Checked" else "Unchecked",
                        tint = if (isChecked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (isChecked) TextDecoration.LineThrough else null
                    )
                }
            }
        }
    }
}
