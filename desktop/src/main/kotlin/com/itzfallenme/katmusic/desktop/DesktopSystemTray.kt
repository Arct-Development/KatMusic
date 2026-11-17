package com.itzfallenme.katmusic.desktop

import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.player.PlayerController
import java.awt.Color
import java.awt.EventQueue
import java.awt.Image
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Optional OS tray media controls and track-change notifications.
 * Unsupported desktop environments simply keep the in-window player controls.
 */
class DesktopSystemTray(
    private val player: PlayerController,
) : AutoCloseable {
    private val icon: TrayIcon?
    private val playPauseItem = java.awt.MenuItem("Play / Pause")

    init {
        icon = runCatching {
            if (!SystemTray.isSupported()) return@runCatching null
            val menu = PopupMenu().apply {
                add(playPauseItem)
                add(java.awt.MenuItem("Next track").apply {
                    addActionListener { player.next() }
                })
                add(java.awt.MenuItem("Previous track").apply {
                    addActionListener { player.previous() }
                })
            }
            playPauseItem.addActionListener { player.togglePlayPause() }
            TrayIcon(createTrayImage(), "KatMusic", menu).apply {
                isImageAutoSize = true
                addActionListener { player.togglePlayPause() }
                SystemTray.getSystemTray().add(this)
            }
        }.getOrNull()
    }

    fun update(track: Track?, isPlaying: Boolean, notifyTrackChange: Boolean) {
        val tray = icon ?: return
        EventQueue.invokeLater {
            playPauseItem.label = if (isPlaying) "Pause playback" else "Resume playback"
            tray.toolTip = track?.let {
                "KatMusic · ${it.title} — ${it.artist}".take(63)
            } ?: "KatMusic"
            if (notifyTrackChange && isPlaying && track != null) {
                runCatching {
                    tray.displayMessage(
                        "KatMusic",
                        "${track.title} — ${track.artist}",
                        TrayIcon.MessageType.INFO,
                    )
                }
            }
        }
    }

    override fun close() {
        icon?.let { tray -> runCatching { SystemTray.getSystemTray().remove(tray) } }
    }

    private fun createTrayImage(): Image =
        runCatching {
            DesktopSystemTray::class.java.getResourceAsStream("/katmusic_logo.png")?.use { ImageIO.read(it) }
        }.getOrNull() ?: createFallbackTrayImage()

    private fun createFallbackTrayImage(): BufferedImage =
        BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB).also { image ->
            val graphics = image.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.color = Color(22, 139, 255)
            graphics.fillRoundRect(1, 1, 30, 30, 10, 10)
            graphics.color = Color(255, 255, 255)
            graphics.font = java.awt.Font("Dialog", java.awt.Font.BOLD, 23)
            graphics.drawString("K", 7, 24)
            graphics.color = Color(42, 226, 239)
            graphics.fillOval(24, 4, 5, 5)
            graphics.dispose()
        }
}
