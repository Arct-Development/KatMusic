package com.itzfallenme.katmusic.desktop

import com.itzfallenme.katmusic.shared.lyrics.Lyrics
import com.itzfallenme.katmusic.shared.models.Track
import java.awt.Color
import java.awt.FileDialog
import java.awt.Font
import java.awt.Frame
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Desktop lyric-card export. Android renders a Compose/Android Bitmap card;
 * the desktop equivalent draws the same information with Java2D and writes a
 * PNG through the native save dialog, so no platform UI code is duplicated.
 */
object DesktopLyricsShare {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350
    private const val MARGIN = 96

    /** Returns the written file, or null when the user cancels. */
    fun export(parent: Frame?, track: Track, lyrics: Lyrics): File? {
        val dialog = FileDialog(parent, "Save lyrics card", FileDialog.SAVE).apply {
            file = suggestedName(track)
            isVisible = true
        }
        val directory = dialog.directory ?: return null
        val chosen = dialog.file ?: return null
        val target = File(directory, if (chosen.endsWith(".png", ignoreCase = true)) chosen else "$chosen.png")
        render(track, lyrics, target)
        return target
    }

    private fun suggestedName(track: Track): String {
        val base = listOf(track.artist, track.title)
            .filter(String::isNotBlank)
            .joinToString(" - ")
            .ifBlank { "KatMusic lyrics" }
        return base.replace(Regex("[\\\\/:*?\"<>|]"), " ").take(80) + ".png"
    }

    private fun render(track: Track, lyrics: Lyrics, target: File) {
        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            graphics.paint = GradientPaint(
                0f, 0f, Color(4, 11, 24),
                WIDTH.toFloat(), HEIGHT.toFloat(), Color(10, 40, 78),
            )
            graphics.fillRect(0, 0, WIDTH, HEIGHT)

            // Soft cyan glow behind the card, matching the KatMusic palette.
            graphics.paint = java.awt.RadialGradientPaint(
                WIDTH * 0.82f, 140f, 520f,
                floatArrayOf(0f, 1f),
                arrayOf(Color(42, 226, 239, 62), Color(42, 226, 239, 0)),
            )
            graphics.fillRect(0, 0, WIDTH, HEIGHT)

            // Card surface.
            graphics.color = Color(255, 255, 255, 16)
            graphics.fillRoundRect(MARGIN - 32, 64, WIDTH - (MARGIN - 32) * 2, HEIGHT - 128, 52, 52)
            graphics.color = Color(120, 220, 255, 46)
            graphics.drawRoundRect(MARGIN - 32, 64, WIDTH - (MARGIN - 32) * 2, HEIGHT - 128, 52, 52)

            graphics.color = Color(42, 226, 239)
            graphics.font = Font("SansSerif", Font.BOLD, 26)
            graphics.drawString("KATMUSIC", MARGIN, 142)
            graphics.fillRoundRect(MARGIN, 164, 72, 5, 5, 5)

            graphics.color = Color.WHITE
            graphics.font = Font("SansSerif", Font.BOLD, 58)
            val titleLines = wrap(track.title, graphics, WIDTH - MARGIN * 2).take(3)
            var y = 252
            titleLines.forEach { line ->
                graphics.drawString(line, MARGIN, y)
                y += 70
            }

            graphics.color = Color(137, 220, 255)
            graphics.font = Font("SansSerif", Font.PLAIN, 36)
            val artistLines = wrap(track.artist, graphics, WIDTH - MARGIN * 2).take(2)
            artistLines.forEach { line ->
                graphics.drawString(line, MARGIN, y)
                y += 46
            }
            if (track.album.isNotBlank()) {
                graphics.color = Color(190, 203, 224)
                graphics.font = Font("SansSerif", Font.PLAIN, 24)
                graphics.drawString(track.album, MARGIN, y + 6)
                y += 38
            }

            y += 44
            graphics.color = Color(255, 255, 255, 38)
            graphics.fillRect(MARGIN, y, WIDTH - MARGIN * 2, 1)
            y += 58

            // Lyrics block. Blank lines are skipped; long lines wrap and the
            // block stops cleanly above the footer instead of clipping.
            val lyricLines = lyrics.lines
                .map { it.text.trim() }
                .filter(String::isNotBlank)
                .take(80)
            graphics.font = Font("SansSerif", Font.PLAIN, 36)
            val metrics = graphics.fontMetrics
            val lineHeight = 54
            val footerTop = HEIGHT - 170
            var drawn = 0
            var truncated = false
            outer@ for (raw in lyricLines) {
                for (line in wrap(raw, graphics, WIDTH - MARGIN * 2)) {
                    if (y + metrics.ascent + metrics.descent > footerTop) {
                        truncated = true
                        break@outer
                    }
                    graphics.color = if (drawn == 0) Color.WHITE else Color(215, 228, 244)
                    graphics.drawString(line, MARGIN, y + metrics.ascent)
                    y += lineHeight
                    drawn++
                }
                y += 8
            }
            if (truncated) {
                graphics.color = Color(160, 190, 220)
                graphics.font = Font("SansSerif", Font.ITALIC, 24)
                graphics.drawString("…", MARGIN, y + 20)
            }
            if (drawn == 0) {
                graphics.color = Color(190, 203, 224)
                graphics.font = Font("SansSerif", Font.ITALIC, 32)
                graphics.drawString("Instrumental", MARGIN, y + 32)
            }

            graphics.color = Color(42, 226, 239)
            graphics.fillRoundRect(MARGIN, HEIGHT - 132, 44, 4, 4, 4)
            graphics.color = Color(190, 203, 224)
            graphics.font = Font("SansSerif", Font.PLAIN, 24)
            graphics.drawString("Shared from KatMusic", MARGIN, HEIGHT - 88)
            graphics.color = Color(137, 220, 255)
            graphics.font = Font("SansSerif", Font.BOLD, 24)
            val site = "katmusic.pages.dev"
            graphics.drawString(site, WIDTH - MARGIN - graphics.fontMetrics.stringWidth(site), HEIGHT - 88)
        } finally {
            graphics.dispose()
        }
        ImageIO.write(image, "png", target)
    }

    private fun wrap(text: String, graphics: Graphics2D, maxWidth: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val metrics = graphics.fontMetrics
        if (metrics.stringWidth(text) <= maxWidth) return listOf(text)
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (metrics.stringWidth(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(word)
                // A single unbreakable word wider than the card is split.
                while (metrics.stringWidth(current.toString()) > maxWidth && current.length > 1) {
                    var cut = current.length - 1
                    while (cut > 1 && metrics.stringWidth(current.substring(0, cut)) > maxWidth) cut--
                    lines += current.substring(0, cut)
                    current = StringBuilder(current.substring(cut))
                }
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines.ifEmpty { listOf(text) }
    }
}
