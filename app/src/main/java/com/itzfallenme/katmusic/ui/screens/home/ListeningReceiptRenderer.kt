package com.itzfallenme.katmusic.ui.screens.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import java.util.Locale

/**
 * Editorial listening summary. Uses only measured listening data and restrained typography;
 * no decorative gradients, fake artwork, barcode or fabricated metrics.
 */
object ListeningReceiptRenderer {
    private const val WIDTH = 1080
    private const val HEIGHT = 1440

    fun render(insights: ListeningInsights): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paper = 0xFFF5F3EE.toInt()
        val ink = 0xFF171717.toInt()
        val secondary = 0xFF68665F.toInt()
        val accent = 0xFFB44B32.toInt()
        canvas.drawColor(paper)

        fun paint(size: Float, color: Int, weight: Int = Typeface.NORMAL) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = Typeface.create("sans-serif", weight)
            }
        fun label(value: String, x: Float, y: Float, size: Float = 24f, color: Int = secondary) {
            canvas.drawText(value.uppercase(Locale.ROOT), x, y, paint(size, color, Typeface.BOLD))
        }
        fun body(value: String, x: Float, y: Float, size: Float, color: Int = ink, weight: Int = Typeface.NORMAL) {
            canvas.drawText(value.take(64), x, y, paint(size, color, weight))
        }
        fun rule(y: Float, color: Int = 0xFFCBC7BE.toInt(), thickness: Float = 2f) {
            canvas.drawLine(88f, y, 992f, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = thickness
            })
        }

        label("KATMUSIC     /     LISTENING NOTES", 88f, 112f, 22f, accent)
        body("A life in", 88f, 258f, 104f, ink, Typeface.BOLD)
        body("sound.", 88f, 360f, 104f, ink, Typeface.BOLD)
        body("Your listening, measured on this device.", 92f, 416f, 28f, secondary)
        rule(472f)

        val totalMs = insights.totalListeningMs.coerceAtLeast(0L)
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L
        label("TIME SPENT LISTENING", 92f, 552f)
        body(String.format(Locale.ROOT, "%d", hours), 88f, 710f, 144f, ink, Typeface.BOLD)
        body("HOURS", 96f, 754f, 22f, secondary, Typeface.BOLD)
        body(String.format(Locale.ROOT, "%02d MIN", minutes), 420f, 704f, 54f, accent, Typeface.BOLD)
        rule(810f)

        val metrics = listOf(
            "PLAYS" to insights.completedPlays.coerceAtLeast(0).toString(),
            "TRACKS" to insights.uniqueTracks.coerceAtLeast(0).toString(),
            "SKIPS" to insights.skips.coerceAtLeast(0).toString(),
        )
        val colWidth = 300f
        metrics.forEachIndexed { index, metric ->
            val x = 92f + index * colWidth
            label(metric.first, x, 878f, 21f)
            body(metric.second, x, 958f, 64f, ink, Typeface.BOLD)
        }
        rule(1018f)

        label("ON REPEAT", 92f, 1090f, 22f, accent)
        body(insights.topTrackTitle?.takeIf(String::isNotBlank) ?: "Your next favourite", 92f, 1160f, 42f, ink, Typeface.BOLD)
        body(insights.topTrackArtist?.takeIf(String::isNotBlank) ?: "Keep listening", 92f, 1208f, 28f, secondary)
        rule(1280f)
        label(insights.topArtist?.takeIf(String::isNotBlank)?.let { "MOST PLAYED ARTIST  /  $it" } ?: "MADE WITH KATMUSIC", 92f, 1340f, 21f, ink)
        body("PERSONAL LISTENING SUMMARY", 92f, 1382f, 17f, secondary)

        return bitmap
    }
}
