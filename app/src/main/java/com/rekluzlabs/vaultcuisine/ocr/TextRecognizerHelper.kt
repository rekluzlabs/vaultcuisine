package com.rekluzlabs.vaultcuisine.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions as LatinTextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Wraps ML Kit's on-device Text Recognition v2. Fully offline — no network
 * call, no fragmentation concerns (works on any device running the app).
 *
 * The recognizer is selected per requested OCR language:
 *  - ja -> Japanese model,  ko -> Korean model,  zh -> Chinese model,
 *  - everything else (en/fr/de/es/it) -> Latin model.
 */
class TextRecognizerHelper {

    private val recognizers = mutableMapOf<String, TextRecognizer>()

    /** Returns raw recognized text from a bitmap. Throws on recognition failure. */
    suspend fun recognizeText(bitmap: Bitmap, language: String = "en"): String {
        val cleanBitmap = preProcess(bitmap)
        val image = InputImage.fromBitmap(cleanBitmap, 0)
        val result = recognizerFor(language).process(image).await()
        return result.text
    }

    private fun recognizerFor(language: String): TextRecognizer {
        val key = when (language) {
            "ja" -> "ja"
            "ko" -> "ko"
            "zh" -> "zh"
            else -> "latin"
        }
        return recognizers.getOrPut(key) {
            when (key) {
                "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                else -> TextRecognition.getClient(LatinTextRecognizerOptions.DEFAULT_OPTIONS)
            }
        }
    }

    private fun preProcess(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()

        // Grayscale conversion with high contrast boost to sharpen text and remove yellow/gray shadows
        val contrast = 2.0f
        val brightness = -80f

        val matrix = floatArrayOf(
            0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, brightness,
            0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, brightness,
            0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        )

        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }
}