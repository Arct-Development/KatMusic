package com.itzfallenme.katmusic

import com.itzfallenme.katmusic.data.Track
import com.itzfallenme.katmusic.data.presence.DiscordPresence
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordPresenceTest {

    private val track = Track(
        id = "dQw4w9WgXcQ",
        title = "Never Gonna Give You Up",
        artist = "Rick Astley",
        album = "Whenever You Need Somebody",
        artwork = "https://img.example/cover=w544-h544",
        durationMs = 213_000
    )

    private fun JsonObject.field(key: String) = this[key]?.jsonPrimitive?.content

    @Test
    fun `activity carries title artist artwork and progress window`() {
        val activity = DiscordPresence.buildActivity(
            track = track,
            playing = true,
            positionMs = 64_000,
            durationMs = 213_000,
            nowMs = 1_000_000_000L
        )

        assertEquals("KatMusic", activity.field("name"))
        assertEquals("2", activity["type"]?.jsonPrimitive?.content)
        assertEquals("Never Gonna Give You Up", activity.field("details"))
        assertEquals("Rick Astley\n · Whenever You Need Somebody", activity.field("state"))

        val assets = activity["assets"]!!.jsonObject
        assertEquals("https://img.example/cover=w544-h544", assets.field("large_image"))

        val timestamps = activity["timestamps"]!!.jsonObject
        val end = timestamps["end"]!!.jsonPrimitive.long
        val start = timestamps["start"]!!.jsonPrimitive.long
        // 213s long, 64s in, so 149s remain and the window is exactly the track length.
        assertEquals(end - start, 213L)
        assertEquals(1_000_000_000L / 1000 + 149L, end)
    }

    @Test
    fun `paused playback has no timestamps`() {
        val activity = DiscordPresence.buildActivity(track, false, 64_000, 213_000, 1L)
        assertTrue("paused cards show no progress", activity["timestamps"] == null)
    }

    @Test
    fun `no artwork falls back to the video thumbnail`() {
        val activity = DiscordPresence.buildActivity(
            track.copy(artwork = ""),
            true,
            0,
            213_000,
            1L
        )
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            activity["assets"]!!.jsonObject.field("large_image")
        )
    }

    @Test
    fun `local files get a project button instead of a video link`() {
        val activity = DiscordPresence.buildActivity(
            track.copy(localPath = "/sdcard/song.mp3", artwork = ""),
            true,
            0,
            0,
            1L
        )
        val buttons = activity["buttons"]!!.jsonArray
        assertEquals(DiscordPresence.PROJECT_URL, buttons[0].jsonObject.field("url"))
        assertFalse(activity["assets"] != null && activity["assets"]!!.jsonObject.field("large_image")!!.contains("ytimg"))
    }

    @Test
    fun `long fields are clipped with a marker`() {
        val long = "a".repeat(200)
        val clipped = DiscordPresence.clip(long)
        assertEquals(DiscordPresence.MAX_FIELD - 1 + 1, clipped.length)
        assertTrue(clipped.endsWith("…"))
    }

    @Test
    fun `signature changes when playback state changes`() {
        val playing = DiscordPresence.signature(track, true, 213_000)
        val paused = DiscordPresence.signature(track, false, 213_000)
        assertTrue(playing != paused)
        assertEquals("none", DiscordPresence.signature(null, true, 0))
    }

    @Test
    fun `set and clear frames use the activity command`() {
        val activity = DiscordPresence.buildActivity(track, true, 0, 213_000, 1L)
        val set = DiscordPresence.setActivityFrame(activity, 42, "nonce")
        assertTrue(set.contains("\"cmd\":\"SET_ACTIVITY\""))
        assertTrue(set.contains("\"pid\":42"))
        assertTrue(set.contains("dQw4w9WgXcQ"))

        val clear = DiscordPresence.clearActivityFrame(42, "nonce")
        assertTrue(clear.contains("\"activity\":null"))
    }
}
