package com.rekluzlabs.vaultcuisine.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = VaultAccent,
    background = VaultSurfaceDark,
    surface = VaultSurfaceDark
)

private val LightColors = lightColorScheme(
    primary = VaultAccent,
    background = VaultSurfaceLight,
    surface = VaultSurfaceLight
)

private val AmoledColors = darkColorScheme(
    primary = VaultAccent,
    background = VaultSurfaceAmoled,
    surface = VaultSurfaceAmoled,
    surfaceContainerLow = VaultSurfaceAmoled,
    surfaceContainer = VaultSurfaceAmoled,
    surfaceContainerHigh = VaultSurfaceAmoled,
    surfaceContainerHighest = VaultSurfaceAmoled
)

@Composable
fun VaultCuisineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = when {
        amoled -> AmoledColors
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = Typography, content = content)
}
