package com.itzfallenme.katmusic.data

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Small JSON file store with coalesced background writes.
 *
 * The library and settings used to serialize and write their whole file on the
 * calling thread on every change - a slider drag meant a dozen synchronous JSON
 * encodes and disk writes on the main thread. This keeps the in-memory value
 * synchronous (the UI still sees the change immediately) but moves encoding and
 * writing to one daemon thread and merges bursts into a single write after a
 * short quiet period. [flush] forces the latest value out when the app goes to
 * the background.
 */
class JsonStore<T>(
    context: Context,
    fileName: String,
    private val serializer: KSerializer<T>,
    private val defaultValue: () -> T,
    private val debounceMs: Long = 220L
) {

    private val file = File(context.filesDir, fileName)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "katmusic-store").apply { isDaemon = true }
    }

    private val lock = Any()
    private var pending: ScheduledFuture<*>? = null
    @Volatile
    private var latest: T? = null

    fun load(): T = runCatching {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else defaultValue()
    }.getOrDefault(defaultValue())

    /** Queues [value]; a burst of calls collapses into one write. */
    fun save(value: T) {
        latest = value
        synchronized(lock) {
            pending?.cancel(false)
            pending = executor.schedule({ writeLatest() }, debounceMs, TimeUnit.MILLISECONDS)
        }
    }

    /** Writes the latest value immediately, used when the app leaves the foreground. */
    fun flush() {
        synchronized(lock) {
            pending?.cancel(false)
            pending = null
        }
        executor.execute { writeLatest() }
    }

    private fun writeLatest() {
        val value = latest ?: return
        synchronized(lock) { pending = null }
        val text = runCatching { json.encodeToString(serializer, value) }.getOrNull() ?: return
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            tmp.renameTo(file)
        }
    }

}
