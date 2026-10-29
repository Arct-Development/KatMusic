package com.itzfallenme.katmusic.util

import android.content.Context
import android.util.Log
import com.itzfallenme.katmusic.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-build file logger.
 *
 * The app has no server-side telemetry, and `adb logcat` is not always at hand
 * when a user reports "it broke". In debug builds every event written here is
 * appended to `filesDir/katmusic-debug.log`, readable from Settings → Debug log
 * and copyable to a bug report. Release builds log nothing to disk: the same
 * calls become plain Logcat no-ops, so shipping behaviour is unchanged.
 *
 * Writes are synchronous and small, and the file rotates at [MAX_BYTES], so a
 * crash trace written from the uncaught-exception handler always survives.
 */
object AppLog {

    private const val FILE_NAME = "katmusic-debug.log"
    private const val MAX_BYTES = 512 * 1024
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var file: File? = null

    fun init(context: Context) {
        if (!BuildConfig.DEBUG) return
        file = File(context.applicationContext.filesDir, FILE_NAME)
        i("AppLog", "log started, version ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_STAMP})")
    }

    val enabled: Boolean get() = BuildConfig.DEBUG && file != null

    fun d(tag: String, message: String) = write("D", tag, message, null)

    fun i(tag: String, message: String) = write("I", tag, message, null)

    fun w(tag: String, message: String, error: Throwable? = null) = write("W", tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        // Logcat in every build; the file only in debug.
        when (level) {
            "E" -> Log.e(tag, message, error)
            "W" -> Log.w(tag, message, error)
            "D" -> Log.d(tag, message, error)
            else -> Log.i(tag, message, error)
        }
        val target = file ?: return
        runCatching {
            if (target.exists() && target.length() > MAX_BYTES) {
                val kept = target.readLines().takeLast(600)
                target.writeText(kept.joinToString("\n", postfix = "\n"))
            }
            val line = buildString {
                append(stamp.format(Date()))
                append(" $level/")
                append(tag)
                append(": ")
                append(message)
                if (error != null) {
                    append(" — ")
                    append(error.javaClass.simpleName)
                    append(": ")
                    append(error.message)
                }
                append('\n')
            }
            target.appendText(line)
            if (error != null) {
                target.appendText(error.stackTraceToString() + "\n")
            }
        }
    }

    /** The last [lines] entries, for the in-app viewer. */
    fun tail(lines: Int = 400): String {
        val target = file ?: return "Debug logging is only available in debug builds."
        if (!target.exists()) return "No log entries yet."
        return runCatching {
            target.readLines().takeLast(lines).joinToString("\n")
        }.getOrDefault("Could not read the log.")
    }

    fun clear() {
        runCatching { file?.delete() }
    }

    fun sizeBytes(): Long = runCatching { file?.length() ?: 0L }.getOrDefault(0L)
}
