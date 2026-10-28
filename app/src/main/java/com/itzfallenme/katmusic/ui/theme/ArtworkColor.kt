package com.itzfallenme.katmusic.ui.theme

import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/** Extracts a vibrant-but-not-screaming seed colour from album art, cached by URL. */
object ArtworkColors {

    private val cache = object : LruCache<String, Color>(48) {
        override fun sizeOf(key: String, value: Color): Int = 1
    }

    fun cached(url: String): Color? = cache.get(url)

    fun put(url: String, color: Color) {
        cache.put(url, color)
    }
}

@Composable
fun rememberArtworkSeed(url: String?, fallback: Color): Color {
    if (url.isNullOrBlank()) return fallback
    ArtworkColors.cached(url)?.let { return it }
    val context = LocalContext.current
    var seed by remember(url) { mutableStateOf(fallback) }
    LaunchedEffect(url) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(160)
            .build()
        val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
        val bitmap = (result as? SuccessResult)?.drawable?.let { drawable ->
            runCatching {
                val bmp = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }.getOrNull()
        }
        if (bitmap != null) {
            val palette = runCatching { Palette.from(bitmap).clearFilters().generate() }.getOrNull()
            val color = palette?.getVibrantColor(0)
                ?: palette?.getMutedColor(0)
                ?: palette?.getDominantColor(0)
            if (color != null && color != 0) {
                val seedColor = Color(color).copy(alpha = 1f)
                ArtworkColors.put(url, seedColor)
                seed = seedColor
            }
        }
    }
    return seed
}
