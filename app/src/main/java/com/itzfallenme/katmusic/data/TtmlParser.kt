package com.itzfallenme.katmusic.data

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses Apple-Music style TTML into word-timed lyric lines.
 *
 * The word providers used by the app serve TTML with `<span begin end>` timing per
 * syllable, background vocals marked `ttm:role="x-bg"`, translation tracks marked
 * `x-translation`/`x-roman`, and a global `lyricOffset` in the head. All of that is
 * handled here, together with the clock formats found in the wild (plain seconds,
 * `mm:ss.mmm`, `hh:mm:ss.mmm`, `ms`/`s` suffixes).
 *
 * Adapted from the LastWave lyrics pipeline (GPL-3.0).
 */
object TtmlParser {

    private val SKIPPED_ROLES = setOf("x-translation", "x-roman")
    private const val BACKGROUND_ROLE = "x-bg"

    fun parse(ttml: String): List<LyricLine> {
        if (ttml.isBlank() || "<p" !in ttml.lowercase()) return emptyList()
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { isExpandEntityReferences = false }
            }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(ttml)))
            val offsetMs = readLyricOffset(document)
            val paragraphs = document.getElementsByTagName("p")
            val lines = ArrayList<LyricLine>(paragraphs.length)
            for (index in 0 until paragraphs.length) {
                val paragraph = paragraphs.item(index) as? Element ?: continue
                lineFrom(paragraph)?.let { lines += it.shiftTimes(offsetMs) }
            }
            lines.sortedBy { it.timeMs }
        }.getOrDefault(emptyList())
    }

    fun parseTime(value: String?): Long? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.endsWith("ms", ignoreCase = true)) {
            return raw.dropLast(2).toDoubleOrNull()?.toLong()
        }
        val stripped = if (raw.endsWith("s", ignoreCase = true) && ":" !in raw) {
            raw.dropLast(1)
        } else raw
        val parts = stripped.split(':')
        val seconds = when (parts.size) {
            1 -> parts[0].toDoubleOrNull()
            2 -> parts[0].toDoubleOrNull()?.let { minutes ->
                parts[1].toDoubleOrNull()?.let { minutes * 60 + it }
            }
            3 -> parts[0].toDoubleOrNull()?.let { hours ->
                parts[1].toDoubleOrNull()?.let { minutes ->
                    parts[2].toDoubleOrNull()?.let { hours * 3600 + minutes * 60 + it }
                }
            }
            else -> null
        } ?: return null
        return (seconds * 1000).toLong()
    }

    private fun readLyricOffset(document: org.w3c.dom.Document): Long = runCatching {
        val candidates = ArrayList<Element>()
        for (tag in listOf("audio", "*")) {
            val nodes = document.getElementsByTagName(tag)
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                if (tag == "*" && element.tagName.substringAfter(':') != "audio") continue
                candidates += element
            }
            if (candidates.isNotEmpty()) break
        }
        for (element in candidates) {
            val attributes = element.attributes ?: continue
            for (index in 0 until attributes.length) {
                val attribute = attributes.item(index) ?: continue
                if (attribute.nodeName.substringAfter(':') == "lyricOffset") {
                    val seconds = attribute.nodeValue?.toDoubleOrNull()
                    if (seconds != null && seconds.isFinite()) return@runCatching (seconds * 1000).toLong()
                }
            }
        }
        0L
    }.getOrDefault(0L)

    private fun LyricLine.shiftTimes(offsetMs: Long): LyricLine {
        if (offsetMs == 0L) return this
        return copy(
            timeMs = (timeMs + offsetMs).coerceAtLeast(0L),
            syllables = syllables.map { it.copy(timeMs = (it.timeMs + offsetMs).coerceAtLeast(0L)) }
        )
    }

    private fun lineFrom(paragraph: Element): LyricLine? {
        val pieces = mutableListOf<Piece>()
        val backingPieces = mutableListOf<Piece>()
        collect(paragraph, pieces, backingPieces)
        val words = mergeIntoWords(pieces)
        val backingWords = mergeIntoWords(backingPieces)
        if (words.isEmpty()) {
            val text = paragraph.textContent?.trim().orEmpty()
            val begin = attrTime(paragraph, "begin") ?: return null
            if (text.isEmpty()) return null
            val end = attrTime(paragraph, "end")?.takeIf { it > begin }
                ?: attrDuration(paragraph, begin)
            val duration = if (end != null && end > begin) end - begin else 0L
            return LyricLine(timeMs = begin, text = text, durationMs = duration)
        }
        val begin = attrTime(paragraph, "begin") ?: words.first().timeMs
        val paragraphEnd = attrTime(paragraph, "end")
        val lineDuration = if (paragraphEnd != null && paragraphEnd > begin) {
            paragraphEnd - begin
        } else {
            val lastEnd = words.maxOfOrNull { it.timeMs + it.durationMs } ?: begin
            (lastEnd - begin).coerceAtLeast(0L)
        }
        val backingLine = backingWords.takeIf { it.isNotEmpty() }?.let { backing ->
            LyricLine(
                timeMs = backing.first().timeMs,
                text = backing.joinToString(" ") { it.text },
                syllables = backing.map { it.copy(isBackground = true) }
            )
        }
        val syllables = buildList {
            addAll(words)
            backingLine?.syllables?.let { addAll(it) }
        }.sortedBy { it.timeMs }
        return LyricLine(
            timeMs = minOf(begin, words.first().timeMs),
            text = words.joinToString(" ") { it.text },
            durationMs = lineDuration,
            syllables = syllables
        )
    }

    private fun collect(node: Node, out: MutableList<Piece>, backing: MutableList<Piece>) {
        val children = node.childNodes
        for (index in 0 until children.length) {
            when (val child = children.item(index)) {
                is Element -> {
                    val role = qualified(child, "ttm:role")
                    if (role in SKIPPED_ROLES) continue
                    val sink = if (role == BACKGROUND_ROLE) backing else out
                    val begin = attrTime(child, "begin")
                    val end = attrTime(child, "end") ?: (begin?.let { attrDuration(child, it) })
                    if (begin != null && end != null && !hasTimedChild(child)) {
                        sink += Piece.Timed(child.textContent.orEmpty(), begin, end)
                    } else {
                        collect(child, sink, backing)
                    }
                }
                else -> if (child.nodeType == Node.TEXT_NODE) {
                    val text = child.textContent.orEmpty()
                    if (text.isNotEmpty()) out += Piece.Text(text)
                }
            }
        }
    }

    private fun hasTimedChild(element: Element): Boolean {
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (child.getAttribute("begin")?.isNotEmpty() == true || hasTimedChild(child)) return true
        }
        return false
    }

    private fun mergeIntoWords(pieces: List<Piece>): List<LyricSyllable> {
        val words = mutableListOf<LyricSyllable>()
        val current = StringBuilder()
        var start = 0L
        var end = 0L
        var timed = false
        fun flush() {
            val text = current.toString().trim()
            current.setLength(0)
            if (text.isNotEmpty() && timed) {
                words += LyricSyllable(
                    timeMs = start,
                    durationMs = (end - start).coerceAtLeast(0L),
                    text = text
                )
            }
            timed = false
        }
        pieces.forEach { piece ->
            when (piece) {
                is Piece.Text -> when {
                    piece.text.isBlank() -> flush()
                    timed -> current.append(piece.text)
                    else -> Unit
                }
                is Piece.Timed -> {
                    if (piece.text.isBlank()) return@forEach
                    if (piece.text.first().isWhitespace()) flush()
                    if (current.isEmpty()) start = piece.start
                    current.append(piece.text.trim())
                    end = piece.end
                    timed = true
                    if (piece.text.last().isWhitespace()) flush()
                }
            }
        }
        flush()
        return words
    }

    private fun attrTime(element: Element, name: String): Long? {
        parseTime(element.getAttribute(name))?.let { return it }
        val local = name.substringAfter(':')
        val attributes = element.attributes ?: return null
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index) ?: continue
            if (attribute.nodeName == name || attribute.nodeName == local || attribute.localName == local) {
                parseTime(attribute.nodeValue)?.let { return it }
            }
        }
        return null
    }

    private fun attrDuration(element: Element, begin: Long): Long? {
        val raw = element.getAttribute("dur")?.takeIf { it.isNotBlank() }
            ?: run {
                val attributes = element.attributes ?: return null
                var found: String? = null
                for (index in 0 until attributes.length) {
                    val attribute = attributes.item(index) ?: continue
                    if (attribute.nodeName == "dur" || attribute.localName == "dur") {
                        found = attribute.nodeValue
                        break
                    }
                }
                found?.takeIf { it.isNotBlank() } ?: return null
            }
        val durationMs = parseTime(raw) ?: raw.trim().toDoubleOrNull()?.let { (it * 1000).toLong() }
        return durationMs?.let { begin + it }
    }

    private fun qualified(element: Element, name: String): String {
        element.getAttribute(name)?.takeIf { it.isNotEmpty() }?.let { return it }
        val local = name.substringAfter(':')
        val attributes = element.attributes ?: return ""
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index) ?: continue
            if (attribute.nodeName == name || attribute.nodeName == local || attribute.localName == local) {
                return attribute.nodeValue.orEmpty()
            }
        }
        return ""
    }

    private sealed interface Piece {
        data class Text(val text: String) : Piece
        data class Timed(val text: String, val start: Long, val end: Long) : Piece
    }
}
