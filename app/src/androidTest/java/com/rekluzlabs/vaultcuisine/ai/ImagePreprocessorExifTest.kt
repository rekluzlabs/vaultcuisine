package com.rekluzlabs.vaultcuisine.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Verifies that ImagePreprocessor.prepareForUpload() strips all EXIF metadata
 * from the output bytes, regardless of the strip/compress order.
 *
 * Run via: ./gradlew :app:connectedAndroidTest
 * Requires a connected device/emulator.
 */
class ImagePreprocessorExifTest {

    private val preprocessor = ImagePreprocessor()

    @Test
    fun prepareForUpload_stripsExifCompletely() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cacheDir = context.cacheDir

        // ── Create a test JPEG with known EXIF tags ──
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val originalFile = File(cacheDir, "exif_test_orig.jpg")

        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, FileOutputStream(originalFile))

        // Inject EXIF tags
        val exif = ExifInterface(originalFile.absolutePath)
        exif.setAttribute(ExifInterface.TAG_MAKE, "TestMaker")
        exif.setAttribute(ExifInterface.TAG_MODEL, "TestModel")
        exif.setAttribute(ExifInterface.TAG_DATETIME, "2026:01:15 10:30:00")
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "37/1,46/1,30/1")
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "122/1,25/1,30/1")
        exif.saveAttributes()

        // Read back and verify EXIF was written
        val beforeExif = ExifInterface(originalFile.absolutePath)
        assertNotNull("EXIF MAKE should exist before stripping", beforeExif.getAttribute(ExifInterface.TAG_MAKE))
        assertNotNull("EXIF GPS should exist before stripping", beforeExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))

        val imageBytes = originalFile.readBytes()

        // ── Run through prepareForUpload (compress FIRST, then strip) ──
        val preparedBytes = preprocessor.prepareForUpload(imageBytes)

        // Save output to a temp file for ExifInterface to read
        val outputFile = File(cacheDir, "exif_test_output.jpg")
        outputFile.writeBytes(preparedBytes)

        // ── Verify all EXIF is stripped ──
        val afterExif = ExifInterface(outputFile.absolutePath)
        assertEquals("TAG_MAKE should be null after prepareForUpload",
            null, afterExif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("TAG_MODEL should be null after prepareForUpload",
            null, afterExif.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals("TAG_DATETIME should be null after prepareForUpload",
            null, afterExif.getAttribute(ExifInterface.TAG_DATETIME))
        assertEquals("TAG_GPS_LATITUDE should be null after prepareForUpload",
            null, afterExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertEquals("TAG_GPS_LONGITUDE should be null after prepareForUpload",
            null, afterExif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))

        // ── Verify the image is still decodable (not corrupted) ──
        val decoded = BitmapFactory.decodeByteArray(preparedBytes, 0, preparedBytes.size)
        assertNotNull("preparedForUpload output should decode to a valid Bitmap", decoded)
        decoded.recycle()

        // ── Cleanup ──
        originalFile.delete()
        outputFile.delete()
        bitmap.recycle()
    }
}
