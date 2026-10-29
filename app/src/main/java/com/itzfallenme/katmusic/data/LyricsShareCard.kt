package com.itzfallenme.katmusic.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import androidx.core.content.FileProvider
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders the lyrics around the current line as a shareable card.
 *
 * The card is drawn with the platform canvas rather than Compose so it can be produced
 * off the main thread, at a fixed 1080×1350 size, and without a window - the same result
 * on every screen density. Colours come from the track's current artwork so the card
 * matches the app, and the file lands in the cache directory, which is already exported
 * through the app's FileProvider.
 */
object LyricsShareCard {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350

    suspend fun render(
        context: Context,
        track: Track,
        lines: List<LyricLine>,
        activeIndex: Int,
        accent: Int,
        background: Int
    ): File = withContext(Dispatchers.IO) {
        val visible = window(lines, activeIndex)
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Backdrop: a soft diagonal wash from the accent into the surface colour.
        val backdrop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                intArrayOf(blend(accent, background, 0.35f), background, blend(accent, background, 0.55f)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), backdrop)

        var top = 96f
        val artwork = artworkBitmap(context, track.artwork)
        if (artwork != null) {
            val size = 220f
            val left = (WIDTH - size) / 2f
            val target = RectF(left, top, left + size, top + size)
            val radius = 42f
            canvas.save()
            val clip = android.graphics.Path().apply {
                addRoundRect(target, radius, radius, android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(clip)
            canvas.drawBitmap(artwork, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
            top += size + 46f
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(clip(track.title, 30), WIDTH / 2f, top, titlePaint)
        top += 46f
        canvas.drawText(clip(track.artist.ifBlank { "KatMusic" }, 40), WIDTH / 2f, top, artistPaint)
        top += 78f

        // Lines: the current one is bright and bold, its neighbours fade with distance.
        visible.forEach { (line, distance) ->
            val active = distance == 0
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = if (active) 52f else 44f
                color = if (active) {
                    Color.WHITE
                } else {
                    Color.argb((170 - distance * 32).coerceAtLeast(60), 255, 255, 255)
                }
                typeface = Typeface.create(
                    Typeface.DEFAULT,
                    if (active) Typeface.BOLD else Typeface.NORMAL
                )
                textAlign = Paint.Align.CENTER
            }
            val wrapped = wrap(line.text, paint, WIDTH - 160)
            wrapped.forEach { row ->
                if (top > HEIGHT - 190f) return@forEach
                canvas.drawText(row, WIDTH / 2f, top, paint)
                top += paint.textSize + 18f
            }
            top += if (active) 30f else 16f
        }

        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 255, 255, 255)
            textSize = 30f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("KatMusic", WIDTH / 2f, HEIGHT - 70f, footer)

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "lyrics-${track.id.ifBlank { "card" }}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        file
    }

    /** The active line plus its neighbours, nearest first, current line marked at 0. */
    fun window(lines: List<LyricLine>, activeIndex: Int, radius: Int = 5): List<Pair<LyricLine, Int>> {
        if (lines.isEmpty()) return emptyList()
        val centre = activeIndex.coerceIn(0, lines.lastIndex)
        val start = (centre - radius).coerceAtLeast(0)
        val end = (centre + radius).coerceAtMost(lines.lastIndex)
        val out = ArrayList<Pair<LyricLine, Int>>(end - start + 1)
        for (index in start..end) {
            val line = lines[index]
            if (line.text.isBlank()) continue
            out += line to kotlin.math.abs(index - centre)
        }
        return out
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private suspend fun artworkBitmap(context: Context, url: String): Bitmap? {
        if (url.isBlank()) return null
        return runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(440)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            (result as? SuccessResult)?.drawable?.let { drawable ->
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        }.getOrNull()
    }

    private fun wrap(text: String, paint: TextPaint, maxWidth: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val words = text.split(' ')
        val rows = ArrayList<String>()
        var current = StringBuilder()
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) rows += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) rows += current.toString()
        return rows
    }

    private fun clip(value: String, limit: Int): String =
        if (value.length > limit) value.take(limit - 1) + "…" else value

    private fun blend(from: Int, to: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        fun channel(a: Int, b: Int) = (a + (b - a) * f).toInt().coerceIn(0, 255)
        return Color.argb(
            channel(Color.alpha(from), Color.alpha(to)),
            channel(Color.red(from), Color.red(to)),
            channel(Color.green(from), Color.green(to)),
            channel(Color.blue(from), Color.blue(to))
        )
    }

}
