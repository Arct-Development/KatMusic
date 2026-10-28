package com.itzfallenme.katmusic.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Small, deliberately generic helpers over the (very) deeply nested InnerTube JSON.
 * Renderer shapes change constantly, so instead of modelling each one we scan for the
 * handful of node types that matter and pull text out defensively.
 */
internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

internal fun JsonObject.obj(key: String): JsonObject? =
    runCatching { this[key]?.jsonObject }.getOrNull()

internal fun JsonObject.arr(key: String): JsonArray? =
    runCatching { this[key]?.jsonArray }.getOrNull()

internal fun JsonObject.str(key: String): String? =
    runCatching { (this[key] as? JsonPrimitive)?.contentOrNull }.getOrNull()

internal fun JsonObject.bool(key: String): Boolean? =
    runCatching { (this[key] as? JsonPrimitive)?.booleanOrNull }.getOrNull()

internal fun JsonObject.long(key: String): Long? =
    runCatching { (this[key] as? JsonPrimitive)?.longOrNull }.getOrNull()

internal fun JsonObject.browseIdOf(): String? =
    obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")

internal fun JsonObject.browseParamsOf(): String? =
    obj("navigationEndpoint")?.obj("browseEndpoint")?.str("params")

internal fun JsonObject.watchIdOf(): String? =
    obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId")

internal object YtJson {

    val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    /** Joins every `runs[].text` (or `simpleText`) inside a text renderer. */
    fun text(node: JsonElement?): String {
        val o = node.asObject() ?: return ""
        o.str("simpleText")?.let { if (it.isNotBlank()) return it }
        val runs = o.arr("runs") ?: o.obj("text")?.arr("runs")
        if (runs != null) {
            return runs.joinToString("") { run ->
                run.asObject()?.str("text").orEmpty()
            }.trim()
        }
        o.arr("text")?.let { return text(JsonObject(mapOf("runs" to it))) }
        return ""
    }

    /** Any thumbnails array, normalised to a large https url. */
    fun artwork(node: JsonElement?): String {
        val urls = ArrayList<String>()
        collectThumbUrls(node, urls, 0)
        val best = urls.maxByOrNull { it.length } ?: return ""
        return upgradeArtwork(best)
    }

    private fun collectThumbUrls(node: JsonElement?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 12) return
        when (node) {
            is JsonObject -> node.forEach { (k, v) ->
                if (k == "url" && v is JsonPrimitive && v.isString) {
                    val s = v.content
                    if (s.startsWith("http")) out.add(s)
                } else collectThumbUrls(v, out, depth + 1)
            }
            is JsonArray -> node.forEach { collectThumbUrls(it, out, depth + 1) }
            else -> Unit
        }
    }

    /** YouTube serves 60x60 by default; rewrite the size suffix to something usable. */
    fun upgradeArtwork(url: String): String {
        if (url.isBlank()) return url
        val hosted = if (url.startsWith("//")) "https:$url" else url
        return hosted
            .replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
            .replace(Regex("=s\\d+(-[a-z0-9-]+)?$"), "=s544")
    }

    /** Depth-first search for the first object stored under [key]. */
    fun find(node: JsonElement?, key: String): JsonObject? {
        if (node == null) return null
        when (node) {
            is JsonObject -> {
                (node[key] as? JsonObject)?.let { return it }
                node.values.forEach { child -> find(child, key)?.let { return it } }
            }
            is JsonArray -> node.forEach { child -> find(child, key)?.let { return it } }
            else -> Unit
        }
        return null
    }

    fun findAll(
        node: JsonElement?,
        key: String,
        out: MutableList<JsonObject> = ArrayList()
    ): List<JsonObject> {
        when (node) {
            is JsonObject -> node.forEach { (k, v) ->
                if (k == key && v is JsonObject) out.add(v) else findAll(v, key, out)
            }
            is JsonArray -> node.forEach { findAll(it, key, out) }
            else -> Unit
        }
        return out
    }

    fun idFromEndpoint(endpoint: JsonElement?): String? {
        val o = endpoint.asObject() ?: return null
        o.obj("watchEndpoint")?.str("videoId")?.let { return it }
        o.obj("browseEndpoint")?.str("browseId")?.let { return it }
        o.obj("watchPlaylistEndpoint")?.str("playlistId")?.let { return it }
        o.obj("playlistEndpoint")?.str("playlistId")?.let { return it }
        return null
    }

    /** "3:42" / "1:02:11" to milliseconds. */
    fun parseDurationMs(text: String): Long {
        val parts = text.trim().split(":").mapNotNull { it.toLongOrNull() }
        if (parts.isEmpty()) return 0L
        var total = 0L
        parts.forEach { total = total * 60 + it }
        return total * 1000
    }
}
