package com.itzfallenme.katmusic.ui.screens.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint

/**
 * Original KatMusic "Listening Receipt" artwork.
 * All numbers come from local playback telemetry; no invented listening data.
 */
object ListeningReceiptRenderer {
    private const val WIDTH = 1080
    private const val HEIGHT = 1600

    fun render(insights: ListeningInsights): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF100D1C.toInt())

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                intArrayOf(0xFF25164A.toInt(), 0xFF11101E.toInt(), 0xFF321C3F.toInt()),
                null, android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bg)

        val card = RectF(56f, 56f, 1024f, 1544f)
        canvas.drawRoundRect(card, 42f, 42f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7F0E7.toInt() })

        fun text(value: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = Typeface.create(if (bold) "sans-serif-black" else "sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            canvas.drawText(value.take(48), x, y, paint)
        }
        fun line(y: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB8AFA7.toInt(); strokeWidth = 2f }
            canvas.drawLine(112f, y, 968f, y, p)
        }
        val ink = 0xFF201A2B.toInt()
        val muted = 0xFF746A78.toInt()
        text("KATMUSIC  /  LISTENING RECEIPT", 112f, 150f, 30f, muted, true)
        text("YOUR SOUND,", 112f, 280f, 82f, ink, true)
        text("YOUR STORY.", 112f, 370f, 82f, 0xFF7449B8.toInt(), true)
        text("An all-time snapshot from this device", 112f, 430f, 30f, muted)
        line(490f)

        val totalMs = insights.totalListeningMs.coerceAtLeast(0L)
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L
        text("TOTAL LISTENING TIME", 112f, 570f, 26f, muted, true)
        text(String.format(java.util.Locale.ROOT, "%d h %02d m", hours, minutes), 112f, 670f, 76f, ink, true)
        line(730f)

        val metrics = listOf(
            "COMPLETED PLAYS" to insights.completedPlays.coerceAtLeast(0).toString(),
            "UNIQUE TRACKS" to insights.uniqueTracks.coerceAtLeast(0).toString(),
            "SKIPS RECORDED" to insights.skips.coerceAtLeast(0).toString()
        )
        metrics.forEachIndexed { index, metric ->
            val y = 835f + index * 135f
            text(metric.first, 112f, y, 25f, muted, true)
            text(metric.second, 790f, y, 48f, ink, true)
            line(y + 42f)
        }

        text("YOUR MOST-PLAYED TRACK", 112f, 1280f, 25f, muted, true)
        text(insights.topTrackTitle?.takeIf { it.isNotBlank() } ?: "The next favorite is waiting", 112f, 1345f, 38f, ink, true)
        text(insights.topTrackArtist?.takeIf { it.isNotBlank() } ?: "Keep listening", 112f, 1395f, 28f, muted)
        text(insights.topArtist?.let { "TOP ARTIST  /  $it" } ?: "MADE WITH KATMUSIC", 112f, 1480f, 25f, 0xFF7449B8.toInt(), true)

        val holes = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF100D1C.toInt() }
        var x = 86f
        while (x < 1010f) {
            canvas.drawCircle(x, 56f, 8f, holes)
            canvas.drawCircle(x, 1544f, 8f, holes)
            x += 28f
        }
        return bitmap
    }
}
