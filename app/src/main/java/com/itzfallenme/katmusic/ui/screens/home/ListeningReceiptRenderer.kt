package com.itzfallenme.katmusic.ui.screens.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Original KatMusic share artwork: a Soundprint.
 *
 * The orbit pattern is a deterministic visual signature derived from the
 * listener's real totals. All displayed numbers come directly from
 * ListeningInsights; the orbit is visual identity, not another metric.
 */
object SoundprintRenderer {
    private const val WIDTH = 1080
    private const val HEIGHT = 1600

    fun render(insights: ListeningInsights): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val ink = 0xFFF5F6FF.toInt()
        val muted = 0xFFB9C1D7.toInt()
        val violet = 0xFF9E8BFF.toInt()
        val cyan = 0xFF6BE4D2.toInt()
        val pink = 0xFFFF91C8.toInt()
        val palette = intArrayOf(violet, cyan, pink, 0xFFFFC979.toInt())

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                intArrayOf(0xFF0A1020.toInt(), 0xFF17152F.toInt(), 0xFF101B2B.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), background)

        // Soft colour fields give the artwork depth without relying on album art.
        val glowTop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                820f, 380f, 520f,
                intArrayOf(0x553D66B7, 0x001A2340),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        val glowBottom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                230f, 1120f, 500f,
                intArrayOf(0x444A347D, 0x001A2340),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(820f, 380f, 520f, glowTop)
        canvas.drawCircle(230f, 1120f, 500f, glowBottom)

        fun textPaint(size: Float, color: Int, weight: Int = Typeface.NORMAL) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = Typeface.create("sans-serif", weight)
            }

        fun fit(value: String, paint: TextPaint, maxWidth: Float): String {
            var result = value.ifBlank { "—" }
            while (result.length > 2 && paint.measureText(result) > maxWidth) {
                result = result.dropLast(2).trimEnd() + "…"
            }
            return result
        }

        fun drawLabel(value: String, x: Float, y: Float, color: Int = muted) {
            canvas.drawText(value.uppercase(Locale.ROOT), x, y, textPaint(22f, color, Typeface.BOLD))
        }

        fun drawValue(value: String, x: Float, y: Float, size: Float, color: Int = ink, maxWidth: Float = 850f) {
            val paint = textPaint(size, color, Typeface.BOLD)
            canvas.drawText(fit(value, paint, maxWidth), x, y, paint)
        }

        // Small header and headline.
        drawLabel("KATMUSIC  /  LISTENING IDENTITY", 84f, 104f, cyan)
        drawValue("Your music.", 80f, 235f, 96f, maxWidth = 900f)
        drawValue("Your own orbit.", 80f, 340f, 88f, violet, maxWidth = 920f)
        canvas.drawText(
            "A soundprint shaped by the music you return to.",
            88f, 398f, textPaint(27f, muted),
        )

        // The radial waveform is a recognisable visual signature, seeded by
        // the user's actual listening totals so each export has its own shape.
        val cx = WIDTH / 2f
        val cy = 735f
        val totalSeed =
            insights.completedPlays.coerceAtLeast(0).toLong() * 13L +
            insights.uniqueTracks.coerceAtLeast(0).toLong() * 31L +
            insights.skips.coerceAtLeast(0).toLong() * 7L +
            (insights.totalListeningMs.coerceAtLeast(0L) / 60_000L)

        val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        listOf(172f, 205f, 270f).forEachIndexed { index, radius ->
            orbitPaint.color = if (index == 1) 0x55FFFFFF else 0x2EFFFFFF
            orbitPaint.strokeWidth = if (index == 1) 2.5f else 1.5f
            canvas.drawCircle(cx, cy, radius, orbitPaint)
        }

        val rays = 112
        for (i in 0 until rays) {
            val mix = ((i.toLong() * 37L + totalSeed * (i % 11 + 1)) % 997L).toFloat() / 997f
            val angle = Math.toRadians(i * 360.0 / rays - 90.0)
            val inner = 188f
            val outer = inner + 14f + mix * 72f
            val x1 = cx + cos(angle).toFloat() * inner
            val y1 = cy + sin(angle).toFloat() * inner
            val x2 = cx + cos(angle).toFloat() * outer
            val y2 = cy + sin(angle).toFloat() * outer
            orbitPaint.color = palette[i % palette.size]
            orbitPaint.alpha = 150 + (mix * 105f).toInt()
            orbitPaint.strokeWidth = 3.5f + mix * 4f
            canvas.drawLine(x1, y1, x2, y2, orbitPaint)
        }

        // Bright nodes are accents only; they do not encode hidden statistics.
        val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 9) {
            val angle = Math.toRadians(i * 40.0 - 90.0)
            val radius = if (i % 2 == 0) 280f else 245f
            nodePaint.color = palette[i % palette.size]
            nodePaint.alpha = 220
            canvas.drawCircle(
                cx + cos(angle).toFloat() * radius,
                cy + sin(angle).toFloat() * radius,
                if (i % 3 == 0) 7f else 4.5f,
                nodePaint,
            )
        }

        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - 35f, cy - 55f, 260f,
                intArrayOf(0xFF29385C.toInt(), 0xFF111A2D.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, 164f, centerPaint)
        val centerTitle = textPaint(32f, ink, Typeface.BOLD)
        centerTitle.textAlign = Paint.Align.CENTER
        canvas.drawText("SOUNDPRINT", cx, cy - 4f, centerTitle)
        val centerSub = textPaint(19f, muted, Typeface.NORMAL)
        centerSub.textAlign = Paint.Align.CENTER
        canvas.drawText("MADE OF YOUR LISTENS", cx, cy + 34f, centerSub)

        // Measured totals live in a clean lower panel.
        val panel = RectF(72f, 1055f, 1008f, 1238f)
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x332F3D5C
        }
        canvas.drawRoundRect(panel, 34f, 34f, panelPaint)

        val totalMs = insights.totalListeningMs.coerceAtLeast(0L)
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L
        val metrics = listOf(
            Triple("LISTENING TIME", hours.toString() + "h " + minutes + "m", 104f),
            Triple("COMPLETED PLAYS", String.format(Locale.ROOT, "%,d", insights.completedPlays.coerceAtLeast(0)), 390f),
            Triple("UNIQUE TRACKS", String.format(Locale.ROOT, "%,d", insights.uniqueTracks.coerceAtLeast(0)), 704f),
        )
        metrics.forEach { (label, value, x) ->
            drawLabel(label, x, 1110f)
            drawValue(value, x, 1178f, 44f, maxWidth = 270f)
        }

        // The personal highlight uses only fields supplied by ListeningInsights.
        drawLabel("ON HEAVY ROTATION", 88f, 1320f, cyan)
        drawValue(insights.topTrackTitle?.takeIf(String::isNotBlank) ?: "The next favourite",
            84f, 1385f, 43f, maxWidth = 900f)
        canvas.drawText(
            insights.topTrackArtist?.takeIf(String::isNotBlank) ?: "Keep listening",
            88f, 1430f, textPaint(27f, muted),
        )
        val topArtist = insights.topArtist?.takeIf(String::isNotBlank)
        if (topArtist != null) {
            drawLabel("YOUR TOP ARTIST  /  $topArtist", 88f, 1500f, violet)
        }
        canvas.drawText(
            "A snapshot of your listening.  ·  KatMusic",
            88f, 1552f, textPaint(20f, muted),
        )

        return bitmap
    }
}
