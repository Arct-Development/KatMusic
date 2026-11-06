package com.itzfallenme.katmusic.ui.screens.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import java.util.Locale

/**
 * Soundprint is a printed listening report, not a dashboard.
 *
 * The hierarchy is deliberately editorial: paper, ink, hairline rules and
 * a serif headline. There are no neon accents, rounded metric cards or
 * decorative charts competing with the listener's actual numbers.
 */
object SoundprintRenderer {
    private const val WIDTH = 1080
    private const val HEIGHT = 1600

    fun render(insights: ListeningInsights): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paper = 0xFFF2EEE6.toInt()
        val ink = 0xFF16181B.toInt()
        val muted = 0xFF6D6C66.toInt()
        val rule = 0xFFD3CCC0.toInt()
        val accent = 0xFF1F4438.toInt()
        canvas.drawColor(paper)

        fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, stroke: Float = 1f) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                this.style = style
                strokeWidth = stroke
            }

        fun type(size: Float, color: Int, weight: Int = Typeface.NORMAL, serif: Boolean = false) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = Typeface.create(if (serif) "serif" else "sans-serif", weight)
            }

        fun fitted(text: String, p: TextPaint, maxWidth: Float): String {
            var result = text.ifBlank { "—" }
            while (result.length > 2 && p.measureText(result) > maxWidth) {
                result = result.dropLast(2).trimEnd() + "…"
            }
            return result
        }

        fun label(text: String, x: Float, y: Float, color: Int = muted, size: Float = 18f) {
            canvas.drawText(
                text.uppercase(Locale.ROOT),
                x,
                y,
                type(size, color, Typeface.BOLD),
            )
        }

        fun heading(text: String, x: Float, y: Float, size: Float, maxWidth: Float) {
            val p = type(size, ink, Typeface.BOLD, serif = true)
            canvas.drawText(fitted(text, p, maxWidth), x, y, p)
        }

        fun hairline(y: Float) {
            canvas.drawLine(80f, y, WIDTH - 80f, y, paint(rule, Paint.Style.STROKE, 2f))
        }

        val margin = 80f
        val contentWidth = WIDTH - margin * 2f

        // Masthead
        hairline(96f)
        label("KATMUSIC", margin, 142f, accent, 18f)
        label("SOUNDPRINT  /  NO. 01", WIDTH - margin - 260f, 142f, 18f)

        // Editorial headline
        heading("The music you", margin, 300f, 78f, contentWidth)
        heading("kept coming back to.", margin, 382f, 78f, contentWidth)
        canvas.drawText(
            "A personal listening report.",
            margin,
            438f,
            type(26f, muted),
        )

        // Listening time: one number, one unit, one hairline.
        label("TIME SPENT LISTENING", margin, 530f, accent, 18f)
        val totalMs = insights.totalListeningMs.coerceAtLeast(0L)
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L
        val duration = if (hours >= 100L) {
            String.format(Locale.ROOT, "%,d", hours)
        } else {
            hours.toString()
        }
        val durationPaint = type(206f, ink, Typeface.BOLD, serif = true)
        val durationText = fitted(duration, durationPaint, 640f)
        canvas.drawText(durationText, margin - 6f, 720f, durationPaint)
        val durationWidth = durationPaint.measureText(durationText)
        canvas.drawText("HOURS", margin + durationWidth + 18f, 690f, type(24f, muted, Typeface.BOLD))
        canvas.drawText(
            String.format(Locale.ROOT, "%02d MINUTES", minutes),
            margin + durationWidth + 18f,
            726f,
            type(20f, muted, Typeface.BOLD),
        )
        hairline(770f)

        // Three plain columns, separated by rules rather than cards.
        val metricTop = 842f
        val columns = listOf(
            Triple("COMPLETED PLAYS", String.format(Locale.ROOT, "%,d", insights.completedPlays.coerceAtLeast(0)), margin),
            Triple("UNIQUE TRACKS", String.format(Locale.ROOT, "%,d", insights.uniqueTracks.coerceAtLeast(0)), margin + contentWidth / 3f),
            Triple("SKIPS", String.format(Locale.ROOT, "%,d", insights.skips.coerceAtLeast(0)), margin + contentWidth * 2f / 3f),
        )
        columns.forEachIndexed { index, (name, value, x) ->
            if (index > 0) {
                canvas.drawLine(x - 30f, metricTop - 34f, x - 30f, metricTop + 82f, paint(rule, Paint.Style.STROKE, 2f))
            }
            label(name, x, metricTop)
            val valuePaint = type(58f, ink, Typeface.BOLD, serif = true)
            canvas.drawText(fitted(value, valuePaint, contentWidth / 3f - 42f), x, metricTop + 78f, valuePaint)
        }
        hairline(1002f)

        // Most played track.
        label("MOST PLAYED", margin, 1082f, accent)
        heading(
            insights.topTrackTitle?.takeIf(String::isNotBlank) ?: "Your next favourite",
            margin,
            1184f,
            64f,
            contentWidth,
        )
        canvas.drawText(
            insights.topTrackArtist?.takeIf(String::isNotBlank) ?: "Keep listening",
            margin,
            1238f,
            type(27f, muted),
        )

        // Top artist, set as a quiet secondary note.
        val artist = insights.topArtist?.takeIf(String::isNotBlank)
        if (artist != null) {
            hairline(1304f)
            label("TOP ARTIST", margin, 1362f)
            heading(artist, margin, 1436f, 42f, contentWidth)
        }

        // Colophon.
        hairline(1512f)
        label("MADE OF YOUR LISTENING  /  KATMUSIC", margin, 1560f, muted, 16f)
        canvas.drawRect(WIDTH - margin - 10f, 1548f, WIDTH - margin, 1560f, paint(accent))
        return bitmap
    }
}
