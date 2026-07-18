package com.rekluzlabs.vaultcuisine.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

class ImagePreprocessor {

    fun prepareForUpload(imageBytes: ByteArray): ByteArray {
        val stripped = stripExif(imageBytes)
        return compressForUpload(stripped)
    }

    fun stripExif(imageBytes: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw ImageProcessingException("Failed to decode image for EXIF stripping")
        return bitmapToJpeg(bitmap, 95)
    }

    fun compressForUpload(
        imageBytes: ByteArray,
        maxDimension: Int = 1800,
        quality: Int = 80
    ): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw ImageProcessingException("Failed to decode image for compression")
        val maxEdge = maxOf(bitmap.width, bitmap.height)
        val scale = if (maxEdge > maxDimension) maxDimension.toFloat() / maxEdge.toFloat() else 1f
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
        val result = bitmapToJpeg(scaled, quality)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return result
    }

    fun bitmapToJpeg(bitmap: Bitmap, quality: Int = 95): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    class ImageProcessingException(message: String) : Exception(message)
}
