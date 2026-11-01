package com.itzfallenme.katmusic.ui.screens.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Editorial, print-inspired Soundprint artwork using only measured listening data. */
object SoundprintRenderer {
    private const val WIDTH = 1080
    private const val HEIGHT = 1600

    fun render(insights: ListeningInsights): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paper = 0xFFF1F0EA.toInt()
        val ink = 0xFF17191D.toInt()
        val secondary = 0xFF62666D.toInt()
        val rule = 0xFFD1D0C9.toInt()
        val accent = 0xFF2858C8.toInt()
        canvas.drawColor(paper)

        fun paint(color: Int, width: Float = 1f, style: Paint.Style = Paint.Style.FILL) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = width
                this.style = style
                strokeCap = Paint.Cap.SQUARE
            }
        fun type(size: Float, color: Int, weight: Int = Typeface.NORMAL) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = Typeface.create("sans-serif", weight)
            }
        fun fitted(value: String, p: TextPaint, maxWidth: Float): String {
            var result = value.ifBlank { "—" }
            while (result.length > 2 && p.measureText(result) > maxWidth) {
                result = result.dropLast(2).trimEnd() + "…"
            }
            return result
        }
        fun label(value: String, x: Float, y: Float, color: Int = secondary) {
            canvas.drawText(value.uppercase(Locale.ROOT), x, y, type(20f, color, Typeface.BOLD))
        }
        fun value(value: String, x: Float, y: Float, size: Float, maxWidth: Float = 880f) {
            val p = type(size, ink, Typeface.BOLD)
            canvas.drawText(fitted(value, p, maxWidth), x, y, p)
        }

        val margin = 84f
        canvas.drawRect(margin, 70f, WIDTH - margin, 73f, paint(ink))
        label("KATMUSIC  /  LISTENING ARCHIVE", margin, 120f, accent)
        label("SOUNDPRINT     NO. 01", 770f, 120f)
        value("A record of", margin, 270f, 104f)
        value("what you return to.", margin, 378f, 88f)
        canvas.drawText("Your listening, translated into a personal signal.", margin + 4f, 430f, type(27f, secondary))

        val seed = abs(
            insights.completedPlays.coerceAtLeast(0).toLong() * 13L +
                insights.uniqueTracks.coerceAtLeast(0).toLong() * 31L +
                insights.skips.coerceAtLeast(0).toLong() * 7L +
                insights.totalListeningMs.coerceAtLeast(0L) / 60_000L,
        )
        val centerY = 690f
        val barCount = 88
        val barWidth = 6f
        val gap = 4.5f
        val totalWidth = barCount * (barWidth + gap) - gap
        val startX = (WIDTH - totalWidth) / 2f
        canvas.drawLine(margin, centerY, WIDTH - margin, centerY, paint(rule, 2f))
        for (i in 0 until barCount) {
            val wave = abs(sin(i * 0.31) * cos(i * 0.087 + seed % 19))
            val detail = ((seed + i * 47L) % 37L).toFloat() / 37f
            val height = 24f + wave.toFloat() * 118f + detail * 32f
            val x = startX + i * (barWidth + gap)
            val color = if (i % 11 == 0 || i % 11 == 1) accent else ink
            canvas.drawRoundRect(RectF(x, centerY - height, x + barWidth, centerY + height), 3f, 3f, paint(color))
        }
        label("LISTENING FREQUENCY", margin, 900f, accent)
        canvas.drawText("A visual mark, not a score.", margin, 936f, type(22f, secondary))

        canvas.drawLine(margin, 1000f, WIDTH - margin, 1000f, paint(ink, 2f))
        val totalMs = insights.totalListeningMs.coerceAtLeast(0L)
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L
        val columns = listOf(
            Triple("LISTENING TIME", "${hours}h ${minutes}m", margin),
            Triple("COMPLETED PLAYS", String.format(Locale.ROOT, "%,d", insights.completedPlays.coerceAtLeast(0)), 420f),
            Triple("UNIQUE TRACKS", String.format(Locale.ROOT, "%,d", insights.uniqueTracks.coerceAtLeast(0)), 755f),
        )
        columns.forEach { (heading, metric, x) ->
            label(heading, x, 1060f)
            val p = type(49f, ink, Typeface.BOLD)
            canvas.drawText(fitted(metric, p, 250f), x, 1132f, p)
        }
        canvas.drawLine(margin, 1190f, WIDTH - margin, 1190f, paint(rule, 2f))
        label("MOST PLAYED", margin, 1260f, accent)
        value(insights.topTrackTitle?.takeIf(String::isNotBlank) ?: "The next favourite", margin, 1332f, 48f, 900f)
        canvas.drawText(insights.topTrackArtist?.takeIf(String::isNotBlank) ?: "Keep listening", margin, 1378f, type(27f, secondary))
        insights.topArtist?.takeIf(String::isNotBlank)?.let {
            label("TOP ARTIST", margin, 1460f)
            value(it, margin, 1512f, 34f, 900f)
        }
        canvas.drawLine(margin, 1540f, WIDTH - margin, 1540f, paint(ink, 2f))
        canvas.drawText("A snapshot of your listening.   /   KATMUSIC", margin, 1572f, type(18f, secondary, Typeface.BOLD))
        return bitmap
    }
}
