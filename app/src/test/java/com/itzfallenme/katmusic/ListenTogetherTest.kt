package com.itzfallenme.katmusic

import com.itzfallenme.katmusic.playback.RoomMessage
import com.itzfallenme.katmusic.playback.RoomProtocol
import com.itzfallenme.katmusic.playback.RoomTrack
import com.itzfallenme.katmusic.playback.needsResync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.random.Random

class ListenTogetherTest {

    @Test
    fun `messages survive a round trip`() {
        val message = RoomMessage(
            type = "state",
            roomCode = "AB12",
            hostName = "Pixel",
            track = RoomTrack(
                id = "abc",
                title = "Song",
                artist = "Artist",
                album = "Album",
                artwork = "https://img",
                durationMs = 200_000
            ),
            queue = listOf(RoomTrack(id = "abc", title = "Song")),
            index = 0,
            positionMs = 42_000,
            playing = true,
            sentAt = 1_700_000_000_000
        )
        val decoded = RoomProtocol.decode(RoomProtocol.encode(message))
        assertNotNull(decoded)
        assertEquals("state", decoded!!.type)
        assertEquals("AB12", decoded.roomCode)
        assertEquals("Song", decoded.track?.title)
        assertEquals(42_000, decoded.positionMs)
        assertTrue(decoded.playing)
        assertEquals(1, decoded.queue.size)
    }

    @Test
    fun `garbage lines are ignored instead of crashing the read loop`() {
        assertNull(RoomProtocol.decode("not json"))
        assertNull(RoomProtocol.decode(""))
        assertNull(RoomProtocol.decode(null))
    }

    @Test
    fun `room codes are four unambiguous characters`() {
        val random = Random(7)
        val code = RoomProtocol.newCode(random)
        assertEquals(4, code.length)
        assertTrue(code.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" })
        assertEquals(4, RoomProtocol.newCode(Random(8)).length)
    }

    @Test
    fun `discovery packets match only their own code`() {
        val packet = RoomProtocol.discoveryPacket("AB12", "Pixel")
        assertTrue(RoomProtocol.matches(packet, "AB12"))
        assertTrue(RoomProtocol.matches(packet, "ab12"))
        assertFalse(RoomProtocol.matches(packet, "ZZ99"))
        assertFalse(RoomProtocol.matches("hello", "AB12"))
        assertFalse(RoomProtocol.matches("KATROOM|AB12", "AB12"))
    }

    @Test
    fun `guest only re-seeks once drift passes the tolerance`() {
        assertFalse(needsResync(10_000, 10_400))
        assertFalse(needsResync(10_000, 12_000))
        assertTrue(needsResync(10_000, 13_000))
        assertTrue(needsResync(13_000, 10_000))
    }

    @Test
    fun `a guest and host exchange newline framed messages over a real socket`() {
        val server = ServerSocket(0)
        val received = AtomicReference<RoomMessage?>()
        val serverThread = thread {
            server.accept().use { socket ->
                val line = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                received.set(RoomProtocol.decode(line))
                BufferedWriter(OutputStreamWriter(socket.getOutputStream())).apply {
                    write(
                        RoomProtocol.encode(
                            RoomMessage(type = "welcome", roomCode = "AB12", hostName = "Host")
                        )
                    )
                    flush()
                }
            }
        }
        Socket("127.0.0.1", server.localPort).use { socket ->
            BufferedWriter(OutputStreamWriter(socket.getOutputStream())).apply {
                write(
                    RoomProtocol.encode(
                        RoomMessage(
                            type = "join",
                            roomCode = "AB12",
                            guestName = "Guest",
                            track = RoomTrack(id = "x", title = "Song")
                        )
                    )
                )
                flush()
            }
            val reply = RoomProtocol.decode(
                BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
            )
            assertEquals("welcome", reply?.type)
            assertEquals("Host", reply?.hostName)
        }
        serverThread.join(3_000)
        server.close()
        assertEquals("join", received.get()?.type)
        assertEquals("Song", received.get()?.track?.title)
    }

    @Test
    fun `room tracks convert back to playable tracks`() {
        val track = RoomTrack(
            id = "x",
            title = "Title",
            artist = "Artist",
            album = "Album",
            artwork = "art",
            durationMs = 1234
        ).toTrack()
        assertEquals("x", track.id)
        assertEquals("Album", track.album)
        assertEquals(1234, track.durationMs)
        assertTrue(track.isPlayable)
    }
}
