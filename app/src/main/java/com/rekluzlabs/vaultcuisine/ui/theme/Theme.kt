package com.rekluzlabs.vaultcuisine.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PantryColors = lightColorScheme(
    primary = FreshMint,
    onPrimary = PantryText,
    secondary = SoftCherry,
    onSecondary = PantryText,
    background = PantryBackground,
    onBackground = PantryText,
    surface = PantrySurface,
    onSurface = PantryText,
    surfaceVariant = PantryBackground,
    onSurfaceVariant = PantryText
)

private val CellarColors = darkColorScheme(
    primary = FreshMint,
    onPrimary = CellarBackground,
    secondary = SoftCherry,
    onSecondary = CellarBackground,
    background = CellarBackground,
    onBackground = CellarText,
    surface = CellarSurface,
    onSurface = CellarText,
    surfaceVariant = CellarBackground,
    onSurfaceVariant = CellarText
)

private val DeepVaultColors = darkColorScheme(
    primary = FreshMint,
    onPrimary = VaultBackground,
    secondary = SoftCherry,
    onSecondary = VaultBackground,
    background = VaultBackground,
    onBackground = VaultText,
    surface = VaultSurface,
    onSurface = VaultText,
    surfaceVariant = VaultBackground,
    onSurfaceVariant = VaultText,
    surfaceContainerLow = VaultBackground,
    surfaceContainer = VaultBackground,
    surfaceContainerHigh = VaultBackground,
    surfaceContainerHighest = VaultBackground
)

private val GardenFreshColors = lightColorScheme(
    primary = FreshMint,
    onPrimary = GardenText,
    secondary = SoftCherry,
    onSecondary = GardenText,
    background = GardenBackground,
    onBackground = GardenText,
    surface = GardenSurface,
    onSurface = GardenText,
    surfaceVariant = GardenBackground,
    onSurfaceVariant = GardenText
)

@Composable
fun VaultCuisineTheme(
    themeMode: String = "pantry",
    content: @Composable () -> Unit
) {
    val colors = when (themeMode) {
        "pantry" -> PantryColors
        "cellar" -> CellarColors
        "vault" -> DeepVaultColors
        "garden" -> GardenFreshColors
        else -> {
            if (isSystemInDarkTheme()) CellarColors else PantryColors
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
