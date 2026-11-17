package com.itzfallenme.katmusic.desktop

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.itzfallenme.katmusic.shared.player.PlayerController
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Desktop media-key support.
 *
 * Uses JNativeHook for OS-level media keys when the platform allows a native
 * hook (Windows/macOS/X11). On Wayland, headless or sandboxed desktops the
 * native hook is unavailable; in that case the same keys work while the
 * KatMusic window has focus through an AWT dispatcher. Either way the tray
 * menu remains the always-available OS control.
 */
class DesktopMediaKeys(
    private val player: PlayerController,
) : AutoCloseable {

    private val pressed = HashSet<Int>()
    private var nativeListener: NativeKeyListener? = null
    private var awtDispatcher: KeyEventDispatcher? = null

    /** True when media keys are captured even while another app is focused. */
    val globalHookActive: Boolean

    init {
        // JNativeHook is chatty at INFO; keep the app console readable.
        runCatching {
            Logger.getLogger("com.github.kwhat.jnativehook").apply {
                level = Level.WARNING
                handlers?.forEach { it.level = Level.WARNING }
            }
        }

        val listener = object : NativeKeyListener {
            override fun nativeKeyPressed(event: NativeKeyEvent) {
                if (!pressed.add(event.keyCode)) return
                handle(event.keyCode)
            }

            override fun nativeKeyReleased(event: NativeKeyEvent) {
                pressed.remove(event.keyCode)
            }
        }

        val registered = runCatching {
            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(listener)
        }.isSuccess

        if (registered) {
            nativeListener = listener
            globalHookActive = true
        } else {
            // A failed listener registration must not leave the native hook
            // installed after this object is discarded.
            runCatching { GlobalScreen.unregisterNativeHook() }

            val dispatcher = KeyEventDispatcher { event ->
                if (event.id == KeyEvent.KEY_PRESSED && handleAwt(event.keyCode)) {
                    true
                } else {
                    false
                }
            }
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
            awtDispatcher = dispatcher
            globalHookActive = false
        }
    }

    private fun handle(keyCode: Int) {
        when (keyCode) {
            NativeKeyEvent.VC_MEDIA_PLAY -> player.togglePlayPause()
            NativeKeyEvent.VC_MEDIA_STOP -> player.stop()
            NativeKeyEvent.VC_MEDIA_NEXT -> player.next()
            NativeKeyEvent.VC_MEDIA_PREVIOUS -> player.previous()
        }
    }

    private fun handleAwt(keyCode: Int): Boolean = when (keyCode) {
        AWT_MEDIA_PLAY_PAUSE -> {
            player.togglePlayPause()
            true
        }

        AWT_MEDIA_NEXT -> {
            player.next()
            true
        }

        AWT_MEDIA_PREVIOUS -> {
            player.previous()
            true
        }

        AWT_MEDIA_STOP -> {
            player.stop()
            true
        }

        else -> false
    }

    private companion object {
        // AWT does not expose media-key constants; these are the standard
        // Linux/Windows media key codes AWT passes through when focused.
        const val AWT_MEDIA_NEXT = 176
        const val AWT_MEDIA_PREVIOUS = 177
        const val AWT_MEDIA_STOP = 178
        const val AWT_MEDIA_PLAY_PAUSE = 179
    }

    override fun close() {
        nativeListener?.let { listener ->
            runCatching { GlobalScreen.removeNativeKeyListener(listener) }
            runCatching { GlobalScreen.unregisterNativeHook() }
            nativeListener = null
        }
        awtDispatcher?.let { dispatcher ->
            runCatching {
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(dispatcher)
            }
            awtDispatcher = null
        }
    }
}
