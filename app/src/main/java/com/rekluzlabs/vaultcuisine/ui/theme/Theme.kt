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

private val WarmSpiceColors = lightColorScheme(
    primary = SpicePrimary,
    onPrimary = SpiceSurface,
    secondary = SpiceSecondary,
    onSecondary = SpiceText,
    background = SpiceBackground,
    onBackground = SpiceText,
    surface = SpiceSurface,
    onSurface = SpiceText,
    surfaceVariant = SpiceBackground,
    onSurfaceVariant = SpiceText
)

private val BerryHarvestColors = darkColorScheme(
    primary = BerryPrimary,
    onPrimary = BerryBackground,
    secondary = BerrySecondary,
    onSecondary = BerryBackground,
    background = BerryBackground,
    onBackground = BerryText,
    surface = BerrySurface,
    onSurface = BerryText,
    surfaceVariant = BerryBackground,
    onSurfaceVariant = BerryText
)

private val CitrusGlowColors = lightColorScheme(
    primary = CitrusPrimary,
    onPrimary = CitrusSurface,
    secondary = CitrusSecondary,
    onSecondary = CitrusText,
    background = CitrusBackground,
    onBackground = CitrusText,
    surface = CitrusSurface,
    onSurface = CitrusText,
    surfaceVariant = CitrusBackground,
    onSurfaceVariant = CitrusText
)

private val SageOliveColors = lightColorScheme(
    primary = SagePrimary,
    onPrimary = SageSurface,
    secondary = SageSecondary,
    onSecondary = SageText,
    background = SageBackground,
    onBackground = SageText,
    surface = SageSurface,
    onSurface = SageText,
    surfaceVariant = SageBackground,
    onSurfaceVariant = SageText
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
        "warm_spice", "spice" -> WarmSpiceColors
        "berry_harvest", "berry" -> BerryHarvestColors
        "citrus_glow", "citrus" -> CitrusGlowColors
        "sage_olive", "sage" -> SageOliveColors
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