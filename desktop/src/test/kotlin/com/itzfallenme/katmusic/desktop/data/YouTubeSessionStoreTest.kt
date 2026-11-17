package com.itzfallenme.katmusic.desktop.data

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YouTubeSessionStoreTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `session round-trips applies cookies and clears`() {
        var applied: String? = "unset"
        val store = YouTubeSessionStore(directory) { cookies -> applied = cookies }
        assertNull(store.session.value)
        assertNull(applied)

        val session = YouTubeSession(
            cookies = "SAPISID=abc; __Secure-3PAPISID=abc; SID=xyz",
            accountName = "Test Account",
            channelHandle = "@test",
            photoUrl = "https://example.com/avatar.jpg",
            pageId = "page-1",
        )
        store.save(session)

        assertEquals(session.cookies, store.cookieHeader())
        assertEquals("Test Account", store.session.value?.accountName)
        assertTrue(store.session.value!!.savedAtMillis > 0L)
        assertEquals(session.cookies, applied)

        val file = directory.resolve("youtube-session.json")
        assertTrue(Files.isRegularFile(file))

        // A fresh store (app restart) reloads the same session.
        var reloadedCookies: String? = null
        val reopened = YouTubeSessionStore(directory) { cookies -> reloadedCookies = cookies }
        assertEquals("Test Account", reopened.session.value?.accountName)
        assertEquals(session.cookies, reopened.cookieHeader())
        assertEquals(session.cookies, reloadedCookies)

        store.clear()
        assertNull(store.session.value)
        assertNull(store.cookieHeader())
        assertFalse(Files.exists(file))
    }

    @Test
    fun `session without a SAPISID cookie is refused`() {
        val store = YouTubeSessionStore(directory) { }
        assertFailsWith<IllegalArgumentException> {
            store.save(YouTubeSession(cookies = "SID=only-a-session-id"))
        }
        assertNull(store.session.value)
        assertFalse(Files.exists(directory.resolve("youtube-session.json")))
    }

    @Test
    fun `corrupt session file is ignored instead of crashing`() {
        Files.writeString(directory.resolve("youtube-session.json"), "{not json")
        val store = YouTubeSessionStore(directory) { }
        assertNull(store.session.value)
    }
}
