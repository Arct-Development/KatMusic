package com.itzfallenme.katmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * AI playlist suggestions built from the listener's own history.
 *
 * Works with any OpenAI-compatible chat-completions endpoint, and the key belongs to the
 * user: it is stored in settings on the device and sent only to the base URL they chose.
 * The model is asked for a strict JSON list of `title`/`artist` pairs, which are then
 * resolved against the YouTube Music catalogue - so the answer never contains a track the
 * app cannot play, and a hallucinated song simply drops out of the result.
 */
object AiSuggest {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    data class Suggestion(val title: String, val artist: String)

    data class Result(
        val tracks: List<Track> = emptyList(),
        val answer: String = "",
        val missing: Int = 0
    )

    fun isConfigured(settings: AppSettings): Boolean =
        settings.aiApiKey.isNotBlank() && settings.aiModel.isNotBlank()

    private fun systemPrompt(): String = buildString {
        append("You are a music curator. ")
        append("Suggest songs the listener will probably love, based on their listening. ")
        append("Reply with JSON only, no prose, in this exact shape: ")
        append("""{"intro":"one short sentence","songs":[{"title":"...","artist":"..."}]}""")
        append(". Use real, well-known releases; never invent titles. ")
        append("Vary genres and eras, and avoid repeating artists already listed unless asked.")
    }

    private fun userPrompt(
        topArtists: List<String>,
        topTracks: List<String>,
        request: String,
        count: Int
    ): String = buildString {
        append("Listener's top artists: ")
        append(topArtists.take(12).joinToString(", ").ifBlank { "unknown" })
        append(".\nRecent tracks: ")
        append(topTracks.take(12).joinToString("; ").ifBlank { "unknown" })
        append(".\nRequest: ")
        append(request.ifBlank { "a varied mix in the same spirit" })
        append(".\nGive exactly $count songs.")
    }

    /** Parses the model answer; tolerant of code fences and stray prose. */
    fun parseSongs(answer: String): Pair<String, List<Suggestion>> {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return "" to emptyList()
        val body = answer.substring(start, end + 1)
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val intro = root["intro"]?.jsonPrimitive?.content.orEmpty()
            val songs = (root["songs"] as? JsonArray ?: JsonArray(emptyList()))
                .mapNotNull { entry ->
                    val obj = entry as? JsonObject ?: return@mapNotNull null
                    val title = obj["title"]?.jsonPrimitive?.content.orEmpty().trim()
                    val artist = obj["artist"]?.jsonPrimitive?.content.orEmpty().trim()
                    if (title.isBlank()) null else Suggestion(title, artist)
                }
            intro to songs
        }.getOrDefault("" to emptyList())
    }

    /**
     * Asks the configured model for songs, then resolves each one against the catalogue.
     * Suggestions that cannot be matched are counted and skipped.
     */
    suspend fun suggest(
        settings: AppSettings,
        topArtists: List<String>,
        topTracks: List<String>,
        request: String,
        count: Int = 20
    ): Result = withContext(Dispatchers.IO) {
        if (!isConfigured(settings)) return@withContext Result()

        val base = settings.aiBaseUrl.trim().ifBlank { "https://api.openai.com/v1" }
            .trimEnd('/')
        val payload = buildJsonObject {
            put("model", settings.aiModel.trim())
            put("temperature", 0.8)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt())
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userPrompt(topArtists, topTracks, request, count))
                })
            })
        }
        val responseBody = runCatching {
            val requestWithAuth = Request.Builder()
                .url("$base/chat/completions")
                .header("Authorization", "Bearer ${settings.aiApiKey.trim()}")
                .header("Accept", "application/json")
                .header("User-Agent", "KatMusic/1.3 (https://github.com/ItzFallenMe/KatMusic)")
                .post(payload.toString().toRequestBody(mediaType))
                .build()
            http.newCall(requestWithAuth).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull() ?: return@withContext Result(answer = "The AI service did not answer.")

        val answer = runCatching {
            val root = json.parseToJsonElement(responseBody).jsonObject
            root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content.orEmpty()
        }.getOrDefault("")

        val (intro, songs) = parseSongs(answer)
        if (songs.isEmpty()) {
            return@withContext Result(answer = answer.ifBlank { "The AI reply was not usable." })
        }

        val resolved = ArrayList<Track>(songs.size)
        var missing = 0
        songs.forEach { suggestion ->
            val tracks = runCatching {
                InnerTube.search(
                    listOf(suggestion.title, suggestion.artist).filter { it.isNotBlank() }.joinToString(" ")
                )
            }.getOrDefault(emptyList()).flatMap { it.tracks }
            val best = tracks.firstOrNull { track ->
                track.title.contains(suggestion.title, ignoreCase = true) ||
                    suggestion.title.contains(track.title, ignoreCase = true)
            } ?: tracks.firstOrNull { track ->
                track.artist.contains(suggestion.artist, ignoreCase = true)
            }
            if (best != null) {
                if (resolved.none { it.id == best.id }) resolved += best
            } else {
                missing++
            }
        }
        Result(tracks = resolved, answer = intro, missing = missing)
    }
}
