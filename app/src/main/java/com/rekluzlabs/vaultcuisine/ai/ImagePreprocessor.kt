package com.rekluzlabs.vaultcuisine.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.io.ByteArrayOutputStream

class ImagePreprocessor {

    fun prepareForUpload(
        imageBytes: ByteArray,
        maxDimension: Int = 1800,
        quality: Int = 90
    ): ByteArray {
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw ImageProcessingException("Failed to decode image for upload")

        val maxEdge = maxOf(bitmap.width, bitmap.height)
        val scale = if (maxEdge > maxDimension) maxDimension.toFloat() / maxEdge.toFloat() else 1f

        if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
            bitmap.recycle()
            bitmap = scaled
        }

        val enhanced = enhanceContrast(bitmap)
        if (enhanced !== bitmap) bitmap.recycle()
        bitmap = enhanced

        val result = bitmapToJpeg(bitmap, quality)
        bitmap.recycle()
        return result
    }

    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()

        val contrast = 1.6f
        val brightness = -60f

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

    private fun bitmapToJpeg(bitmap: Bitmap, quality: Int = 90): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    class ImageProcessingException(message: String) : Exception(message)
}
