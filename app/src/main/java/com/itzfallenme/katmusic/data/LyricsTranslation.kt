package com.itzfallenme.katmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Romanisation and translation of a lyric sheet.
 *
 * Lines are sent in one batch separated by a visible pipe, because the service answers as
 * prose and drops newlines - and, with `dt=rm`, even private-use markers. A pipe survives
 * both modes and keeps the answer aligned with the sung lines: a batch whose part count
 * does not match its input is rejected outright, so the view falls back to the original
 * text instead of showing shifted sub-lines.
 */
object LyricsTranslation {

    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
    private const val MAX_BATCH_CHARS = 3_000
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val SEPARATOR = " | "

    data class Result(val texts: List<String>, val sourceLanguage: String = "")

    private fun payload(lines: List<String>): String =
        lines.joinToString(SEPARATOR) { it.replace('|', '／') }

    private fun request(lines: List<String>, target: String, mode: String): Result? {
        if (lines.isEmpty()) return null
        val body = FormBody.Builder()
            .add("client", "dict-chrome-ex")
            .add("sl", "auto")
            .add("tl", target)
            .add("dt", mode)
            .add("q", payload(lines))
            .build()
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(body)
            .build()
        val response = runCatching {
            http.newCall(request).execute().use { call ->
                if (call.isSuccessful) call.body?.string() else null
            }
        }.getOrNull() ?: return null

        return runCatching {
            val root = json.parseToJsonElement(response).jsonArray
            val segments = root[0].jsonArray
            // dt=t puts the translation in slot 0, dt=rm puts the romanisation in slot 3.
            val slot = if (mode == "rm") 3 else 0
            val text = segments.joinToString("") { segment ->
                segment.jsonArray.getOrNull(slot)?.jsonPrimitive?.content.orEmpty()
            }
            val source = root.getOrNull(2)?.jsonPrimitive?.content.orEmpty()
            val parts = text.split('|').map { it.trim() }
            if (parts.size != lines.size || parts.any { it.isBlank() }) return@runCatching null
            Result(parts, source)
        }.getOrNull()
    }

    private suspend fun run(lines: List<String>, target: String, mode: String): Result? =
        withContext(Dispatchers.IO) {
            if (lines.sumOf { it.length } <= MAX_BATCH_CHARS) return@withContext request(lines, target, mode)

            // Long sheets: split on line boundaries so the markers stay 1:1 per chunk.
            val chunks = ArrayList<List<String>>()
            var current = ArrayList<String>()
            var length = 0
            lines.forEach { line ->
                if (current.isNotEmpty() && length + line.length > MAX_BATCH_CHARS) {
                    chunks += current
                    current = ArrayList()
                    length = 0
                }
                current += line
                length += line.length + 8
            }
            if (current.isNotEmpty()) chunks += current

            val merged = ArrayList<String>()
            var source = ""
            for (chunk in chunks) {
                val result = request(chunk, target, mode) ?: return@withContext null
                if (source.isBlank()) source = result.sourceLanguage
                merged += result.texts
            }
            Result(merged, source)
        }

    /** Romanised text for every line, or null when the sheet is already Latin script. */
    suspend fun romanize(synced: List<LyricLine>): Result? {
        val lines = synced.map { it.text }
        if (lines.isEmpty() || lines.all { it.isBlank() }) return null
        val result = run(lines, "en", "rm") ?: return null
        // Nothing was transliterated: these lyrics are already in the target script.
        val changed = result.texts.zip(lines).count { (romanised, original) ->
            !romanised.equals(original, ignoreCase = true)
        }
        return if (changed >= (lines.size + 2) / 3) result else null
    }

    /** Machine translation of every line into [targetLanguage]. */
    suspend fun translate(synced: List<LyricLine>, targetLanguage: String): Result? {
        val target = targetLanguage.trim().ifBlank { return null }
        val lines = synced.map { it.text }
        if (lines.isEmpty() || lines.all { it.isBlank() }) return null
        return run(lines, target, "t")
    }
}
