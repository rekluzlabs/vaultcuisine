package com.rekluzlabs.vaultcuisine.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rekluzlabs.vaultcuisine.R

@Composable
fun WelcomeScreen(
    onStartClick: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        // Background Image
        Image(
            painter = painterResource(id = R.drawable.welcome_background),
            contentDescription = "Welcome to VaultCuisine",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Full-width clickable area covering the bottom third of the screen.
        // The welcome_background.webp image is ContentScale.Crop, so the
        // visual button position shifts with aspect ratio. A large generous
        // zone ensures the tap target always covers it regardless of crop.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(screenHeight * 0.35f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onStartClick()
                }
        )
    }
}
