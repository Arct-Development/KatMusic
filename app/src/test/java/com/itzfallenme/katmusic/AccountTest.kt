package com.itzfallenme.katmusic

import com.itzfallenme.katmusic.data.AccountStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTest {

    private val cookieA =
        "SID=abc.def; HSID=one; SSID=two; APISID=three; SAPISID=sapisid-a; __Secure-3PAPISID=secure-a"

    @Test
    fun `same session yields the same account id`() {
        assertEquals(AccountStore.idFor(cookieA), AccountStore.idFor(cookieA))
    }

    @Test
    fun `different sessions yield different ids`() {
        val cookieB = cookieA.replace("sapisid-a", "sapisid-b")
        assertNotEquals(AccountStore.idFor(cookieA), AccountStore.idFor(cookieB))
    }

    @Test
    fun `ids are stable across unrelated cookie changes`() {
        val refreshed = "$cookieA; PREF=changed"
        assertEquals(AccountStore.idFor(cookieA), AccountStore.idFor(refreshed))
    }

    @Test
    fun `headers carry cookie and a sapisid hash`() {
        val headers = AccountStore.buildHeaders(cookieA)
        assertEquals(cookieA, headers["Cookie"])
        assertEquals("https://music.youtube.com", headers["X-Origin"])
        val authorization = headers["Authorization"].orEmpty()
        assertTrue(authorization.startsWith("SAPISIDHASH "))
        assertTrue(authorization.split("_").size == 2)
    }

    @Test
    fun `empty cookie produces no headers`() {
        assertTrue(AccountStore.buildHeaders("").isEmpty())
    }

    @Test
    fun `session without sapisid still identifies the account`() {
        val noSapisid = "SID=abc.def; HSID=one"
        assertTrue(AccountStore.idFor(noSapisid).isNotBlank())
        val headers = AccountStore.buildHeaders(noSapisid)
        assertEquals(noSapisid, headers["Cookie"])
        assertTrue(headers["Authorization"] == null)
    }
}
