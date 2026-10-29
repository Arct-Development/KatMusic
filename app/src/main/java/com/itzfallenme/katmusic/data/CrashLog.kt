package com.itzfallenme.katmusic.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last uncaught exception so the app can show it on the next launch
 * instead of the user just seeing "KatMusic keeps stopping".
 *
 * The handler writes the trace and hands control back to the platform's own
 * handler, so crash reporting and the system dialog behave exactly as before;
 * this only adds a copy the user can send.
 */
object CrashLog {

    private const val FILE_NAME = "last-crash.txt"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** Installs the writer once; safe to call from Application.onCreate. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(app, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
        com.itzfallenme.katmusic.util.AppLog.e("Crash", "uncaught in ${thread.name}", throwable)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        file(context).writeText(
            buildString {
                appendLine("KatMusic crash")
                appendLine("time: $stamp")
                appendLine("thread: ${thread.name}")
                appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine("version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
                appendLine()
                append(stack.toString())
                // The last few log lines usually say what the app was doing when it
                // died; a bare stack trace alone rarely explains a user report.
                val trail = runCatching {
                    com.itzfallenme.katmusic.util.AppLog.tail(40)
                }.getOrNull()
                if (!trail.isNullOrBlank()) {
                    appendLine()
                    appendLine("recent log")
                    appendLine(trail)
                }
            }
        )
    }

    /** The stored trace, or null when the last session ended cleanly. */
    fun read(context: Context): String? {
        val stored = file(context)
        return if (stored.exists()) stored.readText().takeIf { it.isNotBlank() } else null
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
