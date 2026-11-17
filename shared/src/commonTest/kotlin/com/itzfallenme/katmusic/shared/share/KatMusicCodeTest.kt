package com.itzfallenme.katmusic.shared.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KatMusicCodeTest {

    @Test
    fun `round trips video ids`() {
        for (id in listOf("dQw4w9WgXcQ", "9bZkp7q19f0", "M7lc1UVf-VE", "a_B-c3D4e5F")) {
            val code = assertNotNull(KatMusicCode.encode(id))
            assertEquals(id, KatMusicCode.decode(code))
            assertEquals(code, KatMusicCode.normalize(code.lowercase().replace("-", "")))
        }
    }

    @Test
    fun `rejects bad ids and damaged checksums`() {
        assertNull(KatMusicCode.encode("short"))
        assertNull(KatMusicCode.encode("dQw4w9WgXc!"))
        val code = assertNotNull(KatMusicCode.encode("dQw4w9WgXcQ"))
        val damaged = code.dropLast(1) + if (code.last() == '0') '1' else '0'
        assertNull(KatMusicCode.decode(damaged))
        assertNull(KatMusicCode.decode("KM-00000-00000-00000-00000"))
    }
}
