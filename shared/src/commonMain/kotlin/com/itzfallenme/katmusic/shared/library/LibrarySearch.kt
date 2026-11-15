package com.itzfallenme.katmusic.shared.library

import com.itzfallenme.katmusic.shared.models.Track

/**
 * Query matching for the shared search screen. Diacritic-insensitive and
 * token based, so "bjork hunter" finds "Björk — Hunter"; the same logic runs
 * on Android and desktop instead of each platform rolling its own filter.
 */
object LibrarySearch {

    fun filter(tracks: List<Track>, query: String): List<Track> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val terms = normalize(trimmed).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        return tracks.filter { track ->
            val haystack = normalize("${track.title} ${track.artist} ${track.album}")
            terms.all { haystack.contains(it) }
        }
    }

    fun normalize(value: String): String = buildString(value.length) {
        value.lowercase().forEach { char ->
            append(
                when (char) {
                    'à', 'á', 'â', 'ä', 'ã', 'å', 'ā' -> 'a'
                    'ç', 'č' -> 'c'
                    'è', 'é', 'ê', 'ë', 'ē', 'ě' -> 'e'
                    'ì', 'í', 'î', 'ï', 'ī' -> 'i'
                    'ñ', 'ň' -> 'n'
                    'ò', 'ó', 'ô', 'ö', 'õ', 'ø', 'ō' -> 'o'
                    'ř' -> 'r'
                    'š', 'ş' -> 's'
                    'ù', 'ú', 'û', 'ü', 'ū', 'ů' -> 'u'
                    'ý', 'ÿ' -> 'y'
                    'ž' -> 'z'
                    'ß' -> 's'
                    else -> char
                },
            )
        }
    }
}
