package com.itzfallenme.katmusic

import com.itzfallenme.katmusic.data.InnerTube
import com.itzfallenme.katmusic.data.LyricsApi
import com.itzfallenme.katmusic.data.YtJson
import com.itzfallenme.katmusic.data.YtJson.json
import com.itzfallenme.katmusic.data.str
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTest {

    @Test
    fun `lrc parser handles multiple stamps per line and fractions`() {
        val lrc = """
            [ar:KatMusic]
            [00:12.50]First line
            [00:15.123][00:45.1]Shared chorus
            [01:02]No fraction
            []empty stamp
        """.trimIndent()

        val lines = LyricsApi.parseLrc(lrc)

        assertEquals(4, lines.size)
        assertEquals(12_500, lines[0].timeMs)
        assertEquals("First line", lines[0].text)
        assertEquals(15_123, lines[1].timeMs)
        assertEquals(45_100, lines[2].timeMs)
        assertEquals("Shared chorus", lines[2].text)
        assertEquals(62_000, lines[3].timeMs)
        assertTrue(lines.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs })
    }

    @Test
    fun `lrc parser ignores non timestamped header lines`() {
        val lines = LyricsApi.parseLrc("plain text\n[ti:Title]\n[00:01.00]Hello")
        assertEquals(1, lines.size)
        assertEquals("Hello", lines[0].text)
    }

    @Test
    fun `duration parsing handles minutes hours and junk`() {
        assertEquals(222_000, YtJson.parseDurationMs("3:42"))
        assertEquals(3_730_000, YtJson.parseDurationMs("1:02:10"))
        assertEquals(0, YtJson.parseDurationMs("") )
        assertEquals(0, YtJson.parseDurationMs("live"))
    }

    @Test
    fun `artwork url is upgraded from thumbnail size`() {
        val upgraded = YtJson.upgradeArtwork(
            "https://lh3.googleusercontent.com/abc=w60-h60-l90-rj"
        )
        assertTrue(upgraded.contains("=w544-h544"))
    }

    @Test
    fun `text extraction joins runs and falls back to simpleText`() {
        val root = json.parseToJsonElement(
            """{"title":{"runs":[{"text":"Never "},{"text":"Gonna"}]},"label":{"simpleText":"Mix"}}"""
        ).jsonObject
        assertEquals("Never Gonna", YtJson.text(root["title"]))
        assertEquals("Mix", YtJson.text(root["label"]))
    }

    @Test
    fun `shelf parsing extracts tracks with artists album and duration`() {
        val root = json.parseToJsonElement(SEARCH_FIXTURE).jsonObject
        val shelves = InnerTube.parseShelves(root)

        assertEquals(1, shelves.size)
        val shelf = shelves.first()
        assertEquals("Songs", shelf.title)
        assertEquals(2, shelf.tracks.size)

        val first = shelf.tracks[0]
        assertEquals("video_one", first.id)
        assertEquals("Midnight Drive", first.title)
        assertEquals("Neon Kite", first.artist)
        assertEquals("UCneonkite", first.artistId)
        assertEquals("Night Roads", first.album)
        assertEquals("MPREb_nightroads", first.albumId)
        assertEquals(215_000, first.durationMs)
        assertTrue(first.artwork.contains("=w544-h544"))

        val second = shelf.tracks[1]
        assertEquals("video_two", second.id)
        assertEquals(184_000, second.durationMs)
        assertEquals("Sable", second.artist)
    }

    @Test
    fun `shelf parsing reads cards and detects kinds`() {
        val root = json.parseToJsonElement(CARD_FIXTURE).jsonObject
        val shelf = InnerTube.parseShelves(root).first()
        assertEquals(2, shelf.cards.size)
        assertEquals("MPREb_album", shelf.cards[0].browseId)
        assertEquals(com.itzfallenme.katmusic.data.CardKind.ALBUM, shelf.cards[0].kind)
        assertEquals("UCartist", shelf.cards[1].browseId)
        assertEquals(com.itzfallenme.katmusic.data.CardKind.ARTIST, shelf.cards[1].kind)
    }

    @Test
    fun `playlist importer extracts ids from common link shapes`() {
        assertEquals(
            "PLabc123456789",
            com.itzfallenme.katmusic.data.PlaylistImporter.extractPlaylistId(
                "https://music.youtube.com/playlist?list=PLabc123456789&si=xyz"
            )
        )
        assertEquals(
            "RDCLAK5uy_k",
            com.itzfallenme.katmusic.data.PlaylistImporter.extractPlaylistId("VL RDCLAK5uy_k".replace(" ", ""))
        )
        assertEquals(
            null,
            com.itzfallenme.katmusic.data.PlaylistImporter.extractPlaylistId("just some words")
        )
        assertTrue(
            com.itzfallenme.katmusic.data.PlaylistImporter.isUnsupportedSource(
                "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
            )
        )
    }

    @Test
    fun `ttml parser extracts word timing background vocals and offset`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <head><metadata><audio lyricOffset="-0.5"/></metadata></head>
              <body><div>
                <p begin="1.0" end="3.0">
                  <span begin="1.0" end="1.5">Hel</span><span begin="1.5" end="2.0">lo </span><span begin="2.0" end="2.6">world</span>
                </p>
                <p begin="4.0" end="5.0">
                  <span begin="4.0" end="4.5">Main</span>
                  <span ttm:role="x-bg" begin="4.5" end="5.0">echo</span>
                </p>
              </div></body>
            </tt>
        """.trimIndent()

        val lines = com.itzfallenme.katmusic.data.TtmlParser.parse(ttml)

        assertEquals(2, lines.size)
        assertEquals(500L, lines[0].timeMs)
        assertEquals("Hello world", lines[0].text)
        val words = lines[0].syllables.filter { !it.isBackground }
        assertEquals(2, words.size)
        // Spans without an interior space are one word, so "Hel"+"lo " merge.
        assertEquals("Hello", words[0].text)
        assertEquals(500L, words[0].timeMs)
        assertEquals(1_000L, words[0].durationMs)
        assertEquals("world", words[1].text)
        assertEquals(1_500L, words[1].timeMs)

        assertEquals(3_500L, lines[1].timeMs)
        assertTrue(lines[1].syllables.any { it.isBackground && it.text == "echo" })
    }

    @Test
    fun `ttml parser reads clock formats`() {
        assertEquals(1_500L, com.itzfallenme.katmusic.data.TtmlParser.parseTime("1.5s"))
        assertEquals(1_500L, com.itzfallenme.katmusic.data.TtmlParser.parseTime("1500ms"))
        assertEquals(61_250L, com.itzfallenme.katmusic.data.TtmlParser.parseTime("01:01.250"))
        assertEquals(3_725_000L, com.itzfallenme.katmusic.data.TtmlParser.parseTime("01:02:05"))
        assertEquals(null, com.itzfallenme.katmusic.data.TtmlParser.parseTime(""))
    }

    private fun JsonObject.get(key: String) = this[key]

    companion object {
        private val SEARCH_FIXTURE = """
        {
          "contents": {
            "tabbedSearchResultsRenderer": {
              "tabs": [
                {
                  "tabRenderer": {
                    "title": "YT Music",
                    "content": {
                      "sectionListRenderer": {
                        "contents": [
                          {
                            "itemSectionRenderer": {
                              "contents": [
                                {
                                  "musicShelfRenderer": {
                                    "title": { "runs": [ { "text": "Songs" } ] },
                                    "contents": [
                                      {
                                        "musicResponsiveListItemRenderer": {
                                          "playlistItemData": { "videoId": "video_one" },
                                          "flexColumns": [
                                            {
                                              "musicResponsiveListItemFlexColumnRenderer": {
                                                "text": { "runs": [ { "text": "Midnight Drive" } ] }
                                              }
                                            },
                                            {
                                              "musicResponsiveListItemFlexColumnRenderer": {
                                                "text": {
                                                  "runs": [
                                                    {
                                                      "text": "Neon Kite",
                                                      "navigationEndpoint": {
                                                        "browseEndpoint": {
                                                          "browseId": "UCneonkite",
                                                          "browseEndpointContextSupportedConfigs": {}
                                                        }
                                                      }
                                                    },
                                                    { "text": " • " },
                                                    {
                                                      "text": "Night Roads",
                                                      "navigationEndpoint": {
                                                        "browseEndpoint": { "browseId": "MPREb_nightroads" }
                                                      }
                                                    },
                                                    { "text": " • " },
                                                    { "text": "3:35" }
                                                  ]
                                                }
                                              }
                                            }
                                          ],
                                          "thumbnail": {
                                            "musicThumbnailRenderer": {
                                              "thumbnail": {
                                                "thumbnails": [
                                                  { "url": "https://img.example/one=w60-h60-l90-rj" }
                                                ]
                                              }
                                            }
                                          }
                                        }
                                      },
                                      {
                                        "musicResponsiveListItemRenderer": {
                                          "playlistItemData": { "videoId": "video_two" },
                                          "flexColumns": [
                                            {
                                              "musicResponsiveListItemFlexColumnRenderer": {
                                                "text": { "runs": [ { "text": "Paper Planes" } ] }
                                              }
                                            },
                                            {
                                              "musicResponsiveListItemFlexColumnRenderer": {
                                                "text": {
                                                  "runs": [
                                                    { "text": "Sable" },
                                                    { "text": " • " },
                                                    { "text": "3:04" }
                                                  ]
                                                }
                                              }
                                            }
                                          ],
                                          "fixedColumns": [
                                            {
                                              "musicResponsiveListItemFixedColumnRenderer": {
                                                "text": { "runs": [ { "text": "3:04" } ] }
                                              }
                                            }
                                          ]
                                        }
                                      }
                                    ]
                                  }
                                }
                              ]
                            }
                          }
                        ]
                      }
                    }
                  }
                }
              ]
            }
          }
        }
        """.trimIndent()

        private val CARD_FIXTURE = """
        {
          "contents": {
            "singleColumnBrowseResultsRenderer": {
              "tabs": [
                {
                  "tabRenderer": {
                    "content": {
                      "sectionListRenderer": {
                        "contents": [
                          {
                            "musicCarouselShelfRenderer": {
                              "header": {
                                "musicCarouselShelfBasicHeaderRenderer": {
                                  "title": { "runs": [ { "text": "You might like" } ] }
                                }
                              },
                              "contents": [
                                {
                                  "musicTwoRowItemRenderer": {
                                    "title": { "runs": [ { "text": "Night Roads" } ] },
                                    "subtitle": { "runs": [ { "text": "Album • Neon Kite" } ] },
                                    "thumbnailRenderer": {
                                      "musicThumbnailRenderer": {
                                        "thumbnail": {
                                          "thumbnails": [
                                            { "url": "https://img.example/album=w120-h120" }
                                          ]
                                        }
                                      }
                                    },
                                    "navigationEndpoint": {
                                      "browseEndpoint": { "browseId": "MPREb_album" }
                                    }
                                  }
                                },
                                {
                                  "musicTwoRowItemRenderer": {
                                    "title": { "runs": [ { "text": "Neon Kite" } ] },
                                    "subtitle": { "runs": [ { "text": "Artist" } ] },
                                    "navigationEndpoint": {
                                      "browseEndpoint": { "browseId": "UCartist" }
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        ]
                      }
                    }
                  }
                }
              ]
            }
          }
        }
        """.trimIndent()
    }
}
