package com.itzfallenme.katmusic.ui.screens.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import java.util.Locale

/**
 * Soundprint is an editorial listening report, not a synthetic audio visualizer.
 * Its hierarchy is built around the user's actual listening data.
 */
object SoundprintRenderer {
    private const val WIDTH = 1080
    private const val HEIGHT = 1600

    fun render(insights: ListeningInsights): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = 0xFF101216.toInt()
        val panel = 0xFF1A1E24.toInt()
        val ink = 0xFFF4F3EF.toInt()
        val muted = 0xFF9CA3AD.toInt()
        val rule = 0xFF343A43.toInt()
        val accent = 0xFFB9F36A.toInt()
        canvas.drawColor(background)

        fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, stroke: Float = 1f) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                this.style = style
                strokeWidth = stroke
            }

        fun type(size: Float, color: Int, weight: Int = Typeface.NORMAL) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = Typeface.create("sans-serif", weight)
            }

        fun fitted(text: String, p: TextPaint, maxWidth: Float): String {
            var result = text.ifBlank { "—" }
            while (result.length > 2 && p.measureText(result) > maxWidth) {
                result = result.dropLast(2).trimEnd() + "…"
            }
            return result
        }

        fun label(text: String, x: Float, y: Float, color: Int = muted) {
            canvas.drawText(text.uppercase(Locale.ROOT), x, y, type(20f, color, Typeface.BOLD))
        }

        fun headline(text: String, x: Float, y: Float, size: Float, maxWidth: Float = 900f) {
            val p = type(size, ink, Typeface.BOLD)
            canvas.drawText(fitted(text, p, maxWidth), x, y, p)
        }

        val margin = 84f
        canvas.drawRect(margin, 68f, WIDTH - margin, 72f, paint(accent))
        label("KATMUSIC     /     LISTENING REPORT", margin, 126f, accent)
        label("SOUNDPRINT  01", 790f, 126f)
        headline("Your time, well spent.", margin, 285f, 82f)
        canvas.drawText("A personal record of the music you came back to.", margin, 340f, type(27f, muted))

        val totalMs = insights.totalListeningMs.coerceAtLeast(0L)
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L
        val duration = if (hours >= 100L) String.format(Locale.ROOT, "%,d", hours) else hours.toString()

        canvas.drawRoundRect(RectF(margin, 410f, WIDTH - margin, 830f), 24f, 24f, paint(panel))
        label("TIME SPENT LISTENING", margin + 42f, 480f, accent)
        val durationPaint = type(220f, ink, Typeface.BOLD)
        val durationText = fitted(duration, durationPaint, 770f)
        canvas.drawText(durationText, margin + 38f, 700f, durationPaint)
        canvas.drawText("HOURS", margin + 46f, 766f, type(28f, muted, Typeface.BOLD))
        canvas.drawText(String.format(Locale.ROOT, "%02d MINUTES", minutes), 690f, 766f, type(24f, muted, Typeface.BOLD))

        canvas.drawLine(margin, 900f, WIDTH - margin, 900f, paint(rule, Paint.Style.STROKE, 2f))
        val metricTop = 970f
        val metricColumns = listOf(
            Triple("COMPLETED PLAYS", String.format(Locale.ROOT, "%,d", insights.completedPlays.coerceAtLeast(0)), margin),
            Triple("UNIQUE TRACKS", String.format(Locale.ROOT, "%,d", insights.uniqueTracks.coerceAtLeast(0)), 430f),
            Triple("SKIPS", String.format(Locale.ROOT, "%,d", insights.skips.coerceAtLeast(0)), 755f),
        )
        metricColumns.forEach { (name, value, x) ->
            label(name, x, metricTop)
            val p = type(56f, ink, Typeface.BOLD)
            canvas.drawText(fitted(value, p, 245f), x, metricTop + 82f, p)
        }

        canvas.drawLine(margin, 1110f, WIDTH - margin, 1110f, paint(rule, Paint.Style.STROKE, 2f))
        label("ON REPEAT", margin, 1190f, accent)
        headline(insights.topTrackTitle?.takeIf(String::isNotBlank) ?: "Your next favourite", margin, 1280f, 54f)
        canvas.drawText(
            insights.topTrackArtist?.takeIf(String::isNotBlank) ?: "Keep listening",
            margin, 1330f, type(29f, muted),
        )

        val artist = insights.topArtist?.takeIf(String::isNotBlank)
        if (artist != null) {
            canvas.drawRoundRect(RectF(margin, 1390f, WIDTH - margin, 1490f), 16f, 16f, paint(panel))
            label("TOP ARTIST", margin + 28f, 1430f)
            headline(artist, margin + 28f, 1470f, 34f, 820f)
        }

        canvas.drawLine(margin, 1530f, WIDTH - margin, 1530f, paint(rule, Paint.Style.STROKE, 2f))
        label("MADE OF YOUR LISTENING  /  KATMUSIC", margin, 1570f, muted)
        canvas.drawRect(WIDTH - margin - 12f, 1558f, WIDTH - margin, 1570f, paint(accent))
        return bitmap
    }
}
