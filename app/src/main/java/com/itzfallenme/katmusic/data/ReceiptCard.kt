package com.itzfallenme.katmusic.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders the listening stats as a receipt.
 *
 * Deliberately narrow and tall, with a dashed rule between sections and a barcode-like
 * footer, so it reads as a till receipt rather than another dashboard. Drawn on the
 * platform canvas at a fixed size, which keeps the result identical on every device and
 * safe to produce off the main thread.
 */
object ReceiptCard {

    private const val WIDTH = 1080
    private const val HEIGHT = 1920

    suspend fun render(
        context: Context,
        stats: WrappedStats,
        periodLabel: String,
        accent: Int,
        background: Int
    ): File = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val backdrop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, HEIGHT.toFloat(),
                intArrayOf(blend(accent, background, 0.25f), background),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), backdrop)

        val white = Color.WHITE
        val muted = Color.argb(168, 255, 255, 255)
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 30f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.12f
        }
        val value = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white
            textSize = 34f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val big = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white
            textSize = 132f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val section = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent.let { Color.rgb(Color.red(it), Color.green(it), Color.blue(it)) }
            textSize = 32f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.18f
        }
        val row = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white
            textSize = 36f
            typeface = Typeface.MONOSPACE
        }
        val rowRight = TextPaint(row).apply { textAlign = Paint.Align.RIGHT }
        val center = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val centerMuted = TextPaint(center).apply {
            color = muted
            textSize = 30f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.2f
        }

        val margin = 92f
        var y = 150f
        canvas.drawText("KATMUSIC", WIDTH / 2f, y, centerMuted)
        y += 72f
        canvas.drawText("Listening receipt", WIDTH / 2f, y, center)
        y += 52f
        canvas.drawText(periodLabel.uppercase(Locale.getDefault()), WIDTH / 2f, y, centerMuted)
        y += 46f
        dashedRule(canvas, margin, y, WIDTH - margin, muted)
        y += 66f

        canvas.drawText(stats.totalMinutes.toString(), margin, y, big)
        y += 44f
        canvas.drawText("MINUTES LISTENED", margin, y, label)
        y += 62f

        canvas.drawText("Plays", margin, y, row)
        canvas.drawText(stats.totalPlays.toString(), WIDTH - margin, y, rowRight)
        y += 46f
        canvas.drawText("Artists", margin, y, row)
        canvas.drawText(stats.topArtists.size.toString(), WIDTH - margin, y, rowRight)
        y += 46f
        canvas.drawText("Tracks", margin, y, row)
        canvas.drawText(stats.topTracks.size.toString(), WIDTH - margin, y, rowRight)
        y += 58f

        dashedRule(canvas, margin, y, WIDTH - margin, muted)
        y += 58f
        canvas.drawText("TOP ARTISTS", margin, y, section)
        y += 52f
        val top = stats.topArtists.take(6)
        val peak = top.firstOrNull()?.second?.coerceAtLeast(1) ?: 1
        top.forEach { (artist, count) ->
            canvas.drawText(clip(artist, 32), margin, y, row)
            canvas.drawText("$count", WIDTH - margin, y, rowRight)
            y += 16f
            val barWidth = ((WIDTH - margin * 2) * (count.toFloat() / peak)).coerceAtLeast(12f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(120, 255, 255, 255)
            }
            canvas.drawRoundRect(
                RectF(margin, y, margin + barWidth, y + 8f),
                4f, 4f, paint
            )
            y += 40f
        }

        if (stats.hourly.any { it > 0 }) {
            y += 10f
            canvas.drawText("WHEN YOU LISTEN", margin, y, section)
            y += 46f
            val max = stats.hourly.maxOrNull()?.coerceAtLeast(1) ?: 1
            val barWidth = (WIDTH - margin * 2) / 24f - 6f
            stats.hourly.forEachIndexed { hour, count ->
                val height = (count.toFloat() / max) * 120f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (count == max) accent else Color.argb(110, 255, 255, 255)
                }
                canvas.drawRoundRect(
                    RectF(
                        margin + hour * (barWidth + 6f),
                        y + 120f - height.coerceAtLeast(4f),
                        margin + hour * (barWidth + 6f) + barWidth,
                        y + 120f
                    ),
                    6f, 6f, paint
                )
            }
            y += 168f
        }

        dashedRule(canvas, margin, y, WIDTH - margin, muted)
        y += 56f
        canvas.drawText("THANK YOU FOR LISTENING", WIDTH / 2f, y, centerMuted)
        y += 60f

        // Barcode: deterministic from the totals, so the same stats print the same code.
        val barcodePaint = Paint().apply { color = Color.argb(220, 255, 255, 255) }
        var x = margin
        val seed = (stats.totalMinutes * 31 + stats.totalPlays).toInt()
        var state = seed
        while (x < WIDTH - margin) {
            state = state * 1103515245 + 12345
            val width = 3f + (kotlin.math.abs(state / 65536) % 9)
            if ((state / 4096) % 2 == 0) {
                canvas.drawRect(x, y, x + width, y + 90f, barcodePaint)
            }
            x += width + 4f
        }
        y += 130f
        val stamp = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date())
        val foot = TextPaint(centerMuted).apply { textSize = 26f }
        canvas.drawText("KatMusic • $stamp", WIDTH / 2f, y, foot)

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "receipt-${stats.totalPlays}-${stats.totalMinutes}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        file
    }

    private fun dashedRule(canvas: Canvas, left: Float, y: Float, right: Float, color: Int) {
        val paint = Paint().apply {
            this.color = color
            strokeWidth = 3f
        }
        var x = left
        while (x < right) {
            canvas.drawLine(x, y, (x + 16f).coerceAtMost(right), y, paint)
            x += 30f
        }
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
