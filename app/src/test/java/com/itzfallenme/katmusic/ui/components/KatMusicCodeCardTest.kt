package com.itzfallenme.katmusic.ui.components

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.HybridBinarizer
import com.itzfallenme.katmusic.shared.share.KatMusicCode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Android half of KatMusic Code sharing: the tag the app draws must scan
 * back through the same ZXing setup the camera analyzer uses, and the decoded
 * text must normalize to the code that was shared.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KatMusicCodeCardTest {

    private val videoIds = listOf(
        "dQw4w9WgXcQ",
        "9bZkp7q19f0",
        "M7lc1UVf-VE",
        "a_B-c3D4e5F",
        "0123456789-",
    )

    @Test
    fun `generated scan tag reads back as the shared code`() {
        for (videoId in videoIds) {
            val code = requireNotNull(KatMusicCode.encode(videoId)) { "encode failed for $videoId" }
            val tag = requireNotNull(createKatMusicCodeTag(code)) { "tag render failed for $code" }

            assertEquals("tag for $code", code, KatMusicCode.normalize(scanCode128(tag)))
            assertEquals(videoId, KatMusicCode.decode(scanCode128(tag)))
        }
    }

    @Test
    fun `exported share card reads back as the shared code`() {
        val videoId = "dQw4w9WgXcQ"
        val code = requireNotNull(KatMusicCode.encode(videoId))
        val card = requireNotNull(
            createKatMusicCodeCard(
                title = "Never Gonna Give You Up",
                artist = "Rick Astley",
                code = code,
                album = "Whenever You Need Somebody",
            ),
        )

        assertEquals(code, KatMusicCode.normalize(scanCode128(card)))
        assertEquals(videoId, KatMusicCode.decode(scanCode128(card)))
    }
}

/** Decodes like [KatMusicCodeAnalyzer]: Code 128 only, hints applied per call. */
private fun scanCode128(bitmap: Bitmap): String {
    val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.CODE_128),
        DecodeHintType.TRY_HARDER to true,
    )
    val binary = BinaryBitmap(HybridBinarizer(BitmapLuminanceSource(bitmap)))
    return MultiFormatReader().decode(binary, hints).text
}

private class BitmapLuminanceSource(private val bitmap: Bitmap) :
    LuminanceSource(bitmap.width, bitmap.height) {

    private val pixels = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        require(y in 0 until height)
        val result = row?.takeIf { it.size >= width } ?: ByteArray(width)
        val offset = y * width
        for (x in 0 until width) result[x] = luminance(pixels[offset + x])
        return result
    }

    override fun getMatrix(): ByteArray =
        ByteArray(pixels.size) { index -> luminance(pixels[index]) }

    private fun luminance(color: Int): Byte {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        return ((red + green + green + blue) shr 2).toByte()
    }
}
