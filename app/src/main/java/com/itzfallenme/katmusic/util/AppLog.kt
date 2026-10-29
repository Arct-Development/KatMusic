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
 * when a user reports "it broke". Every event written here is appended to
 * `filesDir/katmusic-debug.log`, readable from Settings → Debug log and
 * copyable to a bug report.
 *
 * Debug builds keep everything. Release builds keep only warnings and errors,
 * capped much smaller, because that is the part a crash report needs - debug
 * logging being unavailable in the build users actually run used to leave
 * nothing to look at after a crash.
 *
 * Writes are synchronous and small, and the file rotates at [maxBytes], so a
 * crash trace written from the uncaught-exception handler always survives.
 */
object AppLog {

    private const val FILE_NAME = "katmusic-debug.log"
    private const val MAX_BYTES = 512 * 1024
    private const val RELEASE_MAX_BYTES = 128 * 1024
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var file: File? = null

    private val maxBytes: Long
        get() = if (BuildConfig.DEBUG) MAX_BYTES.toLong() else RELEASE_MAX_BYTES.toLong()

    fun init(context: Context) {
        file = File(context.applicationContext.filesDir, FILE_NAME)
        i("AppLog", "log started, version ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_STAMP})")
    }

    val enabled: Boolean get() = file != null

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
        // Release builds keep warnings and errors only: that is the part that
        // explains a crash, and the file stays small.
        if (!BuildConfig.DEBUG && (level == "D" || level == "I")) return
        runCatching {
            if (target.exists() && target.length() > maxBytes) {
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
        val target = file ?: return "The log is not available on this build."
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
