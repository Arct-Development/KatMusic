package com.itzfallenme.katmusic.data.presence

import com.itzfallenme.katmusic.data.Track
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Discord Rich Presence payload.
 *
 * The card Discord renders for a running app:
 *
 * ```
 *   Listening to KatMusic
 *   ┌────┐  Song title
 *   │art │  Artist · Album
 *   └────┘  ▁▁▁▁▁▁ 1:04 / 3:47
 *   [ Listen on YouTube ]   [ Get KatMusic ]
 * ```
 *
 * Timestamps are pushed as a start/end pair so Discord runs the progress bar on its own
 * clock: one presence per track stays correct to the end, and position ticks never need
 * to reach the wire.
 *
 * Everything here is pure - no Android types, no Discord types - so the format is
 * unit-testable on the JVM.
 */
object DiscordPresence {

    /**
     * The KatMusic application registered on the Discord Developer Portal. Rich
     * Presence needs one so Discord can attribute the card, show the app name and
     * resolve its artwork; users can still override it in Settings.
     */
    const val DEFAULT_APP_ID = "1563081601254953002"

    const val PROJECT_URL = "https://github.com/ItzFallenMe/KatMusic"
    const val RELEASES_URL = "$PROJECT_URL/releases"
    const val BUTTON_LISTEN = "Listen on YouTube"
    const val BUTTON_GET_APP = "Get KatMusic"

    /** Discord drops anything longer, so fields are clipped with a visible marker. */
    const val MAX_FIELD = 120
    const val MAX_STATE = 125

    /** 2 = "Listening to". */
    private const val TYPE_LISTENING = 2

    /** The card only changes this often; position ticks are not a reason to republish. */
    const val PUSH_INTERVAL_MS = 15_000L

    /** A missing Discord app must never cost more than one cheap check per interval. */
    const val RETRY_INTERVAL_MS = 15_000L

    fun clip(value: String, limit: Int = MAX_FIELD): String {
        val trimmed = value.trim()
        return if (trimmed.length > limit) trimmed.take(limit - 1) + "…" else trimmed
    }

    private fun stateLine(artist: String, album: String?): String {
        val head = artist.trim().ifEmpty { album?.trim().orEmpty() }.ifEmpty { "KatMusic" }
        val line = if (album.isNullOrBlank()) clip(head) else clip(head) + "\n · " + clip(album)
        return if (line.length > MAX_STATE) line.take(MAX_STATE - 1) + "…" else line
    }

    /** http(s) artwork only: Discord cannot fetch a local file path. */
    fun largeImage(track: Track): String? {
        val artwork = track.artwork.trim()
        return when {
            artwork.startsWith("https://") || artwork.startsWith("http://") -> artwork
            track.id.isNotBlank() && !track.isLocal -> "https://i.ytimg.com/vi/${track.id}/hqdefault.jpg"
            else -> null
        }
    }

    /** Identity of everything the card shows, for throttling repushes. */
    fun signature(track: Track?, playing: Boolean, durationMs: Long): String {
        if (track == null) return "none"
        return listOf(
            track.id, track.title, track.artist, track.album.orEmpty(),
            track.artwork, playing.toString(), durationMs.toString()
        ).joinToString("|")
    }

    fun buildActivity(
        track: Track,
        playing: Boolean,
        positionMs: Long,
        durationMs: Long,
        nowMs: Long
    ): JsonObject {
        val title = clip(track.title)
        val duration = durationMs.takeIf { it > 0 } ?: track.durationMs
        val timestamps = if (playing && duration > 0) {
            val position = positionMs.coerceIn(0L, duration)
            val endMs = (nowMs + (duration - position)) / 1000
            buildJsonObject {
                put("start", (endMs - duration / 1000).coerceAtLeast(0))
                put("end", endMs)
            }
        } else null

        return buildJsonObject {
            put("type", TYPE_LISTENING)
            put("name", "KatMusic")
            put("details", title)
            put("state", stateLine(track.artist, track.album))
            timestamps?.let { put("timestamps", it) }
            largeImage(track)?.let { image ->
                put("assets", buildJsonObject {
                    put("large_image", image)
                    put("large_text", clip(track.album.orEmpty().ifBlank { title }))
                })
            }
            putJsonArray("buttons") {
                add(buildJsonObject {
                    put("label", BUTTON_LISTEN)
                    put(
                        "url",
                        if (track.isLocal) PROJECT_URL else "https://www.youtube.com/watch?v=${track.id}"
                    )
                })
                add(buildJsonObject {
                    put("label", BUTTON_GET_APP)
                    put("url", RELEASES_URL)
                })
            }
            put("instance", JsonPrimitive(true))
        }
    }

    /** `{"cmd":"SET_ACTIVITY", …}` - the frame the transport hands to Discord. */
    fun setActivityFrame(activity: JsonObject, pid: Int, nonce: String): String =
        buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            put("args", buildJsonObject {
                put("pid", pid)
                put("activity", activity)
            })
            put("nonce", nonce)
        }.toString()

    /** A null activity hides the card, so one command covers clearing too. */
    fun clearActivityFrame(pid: Int, nonce: String): String =
        buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            put("args", buildJsonObject {
                put("pid", pid)
                put("activity", JsonNull)
            })
            put("nonce", nonce)
        }.toString()
}
