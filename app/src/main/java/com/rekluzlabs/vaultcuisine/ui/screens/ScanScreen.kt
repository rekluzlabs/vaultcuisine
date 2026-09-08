/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
package com.rekluzlabs.vaultcuisine.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.rekluzlabs.vaultcuisine.R
import com.rekluzlabs.vaultcuisine.data.RecipeCategory
import com.rekluzlabs.vaultcuisine.ui.components.CategoryPickerDialog

// Color Palette Matched to Mockup
private val MintGreen = Color(0xFF82DBC5)
private val MintGlow = Color(0x6682DBC5)
private val WarmGoldText = Color(0xFFE8D3A7)
private val DarkCardBg = Color(0xFF1B1917)
private val DarkCardBorder = Color(0xFF322C26)
private val DarkButtonBg = Color(0xFF24201D)
private val DarkButtonBorder = Color(0xFF3F3730)
private val MutedText = Color(0xFFA1988E)

// The whisk image is portrait (565x634), so at Crop it exactly fills the
// screen height — there is no vertical overflow for `alignment` to shift.
// Pin the bottom edge and zoom to pull the image's center up into the top
// half. Raise the value to sit higher, lower it to sit nearer center.
private const val BG_ZOOM = 1.175f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    isProcessing: Boolean,
    scanMessage: String = "Reading your recipe…",
    onImageCaptured: (Bitmap) -> Unit,
    onManualEntry: (RecipeCategory) -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var manualCategory by remember { mutableStateOf(RecipeCategory.OTHER) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val page = scanResult?.pages?.firstOrNull()
            page?.imageUri?.let { uri ->
                decodeBitmap(context, uri)?.let { onImageCaptured(it) }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            decodeBitmap(context, uri)?.let { onImageCaptured(it) }
        }
    }

    val parallax = rememberInfiniteTransition(label = "bgParallax")
    val parallaxScale by parallax.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "parallaxScale"
    )
    val parallaxOffset by parallax.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "parallaxOffset"
    )

    Scaffold { padding ->

        Box(modifier = Modifier.fillMaxSize()) {

            // 🌄 Background image (slow parallax drift) — bottom edge pinned,
            // zoomed so the image content sits higher on screen.
            Image(
                painter = painterResource(R.drawable.background_whisk),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val zoom = BG_ZOOM * parallaxScale
                        scaleX = zoom
                        scaleY = zoom
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = parallaxOffset
                    }
            )

            // 🌑 Gradient overlay (matches ambient dark aesthetic)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // 👑 Top Custom Title ("Scan Recipe" with ornamental style)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Scan Recipe",
                    color = WarmGoldText,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(1.dp)
                            .background(WarmGoldText.copy(alpha = 0.5f))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("❖", color = WarmGoldText.copy(alpha = 0.7f), fontSize = 10.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(1.dp)
                            .background(WarmGoldText.copy(alpha = 0.5f))
                    )
                }
            }

            // 🔄 Processing state
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MintGreen)
                        Spacer(Modifier.height(16.dp))
                        Text(scanMessage, color = Color.White, fontFamily = FontFamily.Serif)
                    }
                }
            }

            // 📦 Bottom Card Panel
            AnimatedVisibility(
                visible = !isProcessing,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        color = DarkCardBg,
                        border = BorderStroke(1.dp, DarkCardBorder),
                        tonalElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            // 🔍 Top Ornamental Icon Divider
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(1.dp)
                                        .background(DarkButtonBorder)
                                )
                                Spacer(Modifier.width(12.dp))
                                Icon(
                                    imageVector = Icons.Outlined.DocumentScanner,
                                    contentDescription = null,
                                    tint = MintGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(1.dp)
                                        .background(DarkButtonBorder)
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            // 📄 Header Text
                            Text(
                                text = "Scan a recipe card or handwritten note",
                                color = WarmGoldText,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = "We’ll turn it into a clean, editable recipe",
                                color = MutedText,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(24.dp))

                            // 📷 Primary Mint Scan Button with Ambient Glow
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(
                                        elevation = 16.dp,
                                        shape = RoundedCornerShape(50),
                                        ambientColor = MintGlow,
                                        spotColor = MintGreen
                                    )
                            ) {
                                Button(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                                        val activity = context.findActivity() ?: return@Button

                                        val options = GmsDocumentScannerOptions.Builder()
                                            .setPageLimit(1)
                                            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                                            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER)
                                            .build()

                                        val scanner = GmsDocumentScanning.getClient(options)
                                        scanner.getStartScanIntent(activity)
                                            .addOnSuccessListener { intentSender ->
                                                val request = IntentSenderRequest.Builder(intentSender).build()
                                                scannerLauncher.launch(request)
                                            }
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MintGreen,
                                        contentColor = Color(0xFF0F2620)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PhotoCamera,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Scan Recipe",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // 🧩 Secondary Options Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, DarkButtonBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = DarkButtonBg,
                                        contentColor = MintGreen
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Gallery", fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onManualEntry(manualCategory)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, DarkButtonBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = DarkButtonBg,
                                        contentColor = MintGreen
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Enter Manually", fontSize = 13.sp)
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { showCategoryPicker = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, DarkButtonBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = DarkButtonBg,
                                    contentColor = MintGreen
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Category,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Manual category: ${manualCategory.displayName}", fontSize = 12.sp)
                            }

                            Spacer(Modifier.height(20.dp))

                            // 🔒 Privacy Statement Block
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth(0.95f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .border(1.dp, MintGreen.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = MintGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "RekluzLabs has no servers, no accounts, and collects no personal data. When you scan a recipe, only a compressed copy with ALL EXIF metadata (like location) stripped is sent to your own Gemini API key, never to us. The original photo stays on your device, and everything you save is stored only locally.",
                                    color = MutedText,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    textAlign = TextAlign.Start
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            initialCategory = manualCategory,
            onConfirm = { manualCategory = it; showCategoryPicker = false },
            onDismiss = { showCategoryPicker = false }
        )
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun decodeBitmap(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(context.contentResolver, uri)
        ) { decoder, _, _ ->
            decoder.isMutableRequired = true
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
} catch (e: Exception) {
    Toast.makeText(context, "Couldn't load that image", Toast.LENGTH_SHORT).show()
    null
}