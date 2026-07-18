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

        // Transparent clickable area over the button in the image
        // The button is roughly at the bottom center of the image.
        // We'll estimate its position relative to the screen.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-40).dp) // Adjust based on image aspect ratio and crop
                .fillMaxWidth(0.6f)
                .height(80.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null // No ripple to keep the "image" feel, or add if desired
                ) {
                    onStartClick()
                }
        )
    }
}
