package com.itzfallenme.katmusic.data.sponsorblock

// SPDX-License-Identifier: GPL-3.0-or-later

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class SponsorSegment(
    val startMs: Long,
    val endMs: Long,
    val category: String,
    val uuid: String,
)

/**
 * SponsorBlock's public skip-segment API (https://sponsor.ajay.app).
 *
 * Only `skip` action types are returned: the player seeks past them, it never
 * mutes or labels them. Results are cached per video in memory because the
 * progress ticker must not wait on the network.
 */
@Singleton
class SponsorBlockRepository @Inject constructor(
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<SponsorSegment>>()

    suspend fun segments(videoId: String): List<SponsorSegment> {
        cache[videoId]?.let { return it }
        val fetched = withContext(Dispatchers.IO) { fetch(videoId) }
        cache[videoId] = fetched
        return fetched
    }

    private fun fetch(videoId: String): List<SponsorSegment> {
        val url = "https://sponsor.ajay.app/api/skipSegments".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("videoID", videoId)
            ?.addQueryParameter(
                "categories",
                """["sponsor","selfpromo","interaction","intro","outro","music_offtopic"]""",
            )
            ?.build()
            ?: return emptyList()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()
                json.parseToJsonElement(body).jsonArray.flatMap { entry ->
                    val segments = entry.jsonObject["segments"]?.jsonArray ?: return@flatMap emptyList()
                    segments.mapNotNull { element ->
                        val segment = element.jsonObject
                        if (segment["actionType"]?.jsonPrimitive?.contentOrNull != "skip") return@mapNotNull null
                        val range = segment["segment"]?.jsonArray ?: return@mapNotNull null
                        val startSeconds = range.getOrNull(0)?.jsonPrimitive?.content?.toDoubleOrNull()
                            ?: return@mapNotNull null
                        val endSeconds = range.getOrNull(1)?.jsonPrimitive?.content?.toDoubleOrNull()
                            ?: return@mapNotNull null
                        if (endSeconds <= startSeconds) return@mapNotNull null
                        SponsorSegment(
                            startMs = (startSeconds * 1_000).toLong(),
                            endMs = (endSeconds * 1_000).toLong(),
                            category = segment["category"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            uuid = segment["UUID"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
