package com.itzfallenme.katmusic

import com.itzfallenme.katmusic.data.AiSuggest
import com.itzfallenme.katmusic.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSuggestTest {

    @Test
    fun `parses the documented answer shape`() {
        val answer = """
            {"intro":"A slow, warm set.","songs":[
              {"title":"Nightcall","artist":"Kavinsky"},
              {"title":"Teardrop","artist":"Massive Attack"}
            ]}
        """.trimIndent()

        val (intro, songs) = AiSuggest.parseSongs(answer)

        assertEquals("A slow, warm set.", intro)
        assertEquals(2, songs.size)
        assertEquals("Nightcall", songs[0].title)
        assertEquals("Kavinsky", songs[0].artist)
    }

    @Test
    fun `tolerates code fences and surrounding prose`() {
        val answer = """
            Sure! Here you go:
            ```json
            {"intro":"Upbeat.","songs":[{"title":"Levitating","artist":"Dua Lipa"}]}
            ```
            Enjoy.
        """.trimIndent()

        val (intro, songs) = AiSuggest.parseSongs(answer)
        assertEquals("Upbeat.", intro)
        assertEquals(1, songs.size)
        assertEquals("Levitating", songs[0].title)
    }

    @Test
    fun `drops entries without a title`() {
        val answer = """{"songs":[{"title":"","artist":"X"},{"title":"Real","artist":"Y"}]}"""
        val (_, songs) = AiSuggest.parseSongs(answer)
        assertEquals(1, songs.size)
        assertEquals("Real", songs[0].title)
    }

    @Test
    fun `returns nothing for a non json answer`() {
        val (intro, songs) = AiSuggest.parseSongs("I could not think of anything.")
        assertTrue(intro.isBlank())
        assertTrue(songs.isEmpty())
    }

    @Test
    fun `configuration needs both a key and a model`() {
        assertFalse(AiSuggest.isConfigured(AppSettings()))
        assertTrue(AiSuggest.isConfigured(AppSettings(aiApiKey = "sk-test")))
        assertFalse(AiSuggest.isConfigured(AppSettings(aiApiKey = "sk-test", aiModel = " ")))
    }
}
