package com.itzfallenme.katmusic.shared.share

/**
 * KatMusic song codes: a short, human-readable, reversible code for a
 * YouTube-backed track. No server lookup is needed, so a code keeps working
 * offline and in screenshots.
 *
 * Format: `KM-XXXXX-XXXXX-XXXXX-XXXXX`
 * - 18 Crockford-base32 characters carry the 11-byte video id.
 * - 2 characters are a checksum, so a mistyped code is rejected instead of
 *   opening the wrong song.
 */
object KatMusicCode {

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val PAYLOAD_CHARS = 18
    private const val CHECK_CHARS = 2
    private const val PREFIX = "KM"

    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

    /** Encodes an 11-character video id, or null when it is not a stable id. */
    fun encode(videoId: String?): String? {
        val id = videoId?.trim().orEmpty()
        if (!videoIdPattern.matches(id)) return null
        val payload = base32Encode(id.encodeToByteArray())
        val check = checksum(payload)
        val body = payload + check
        val groups = body.chunked(5)
        return (listOf(PREFIX) + groups).joinToString("-")
    }

    /**
     * Decodes a code with or without separators. Returns null for invalid
     * checksums, unknown characters and non-video payloads.
     */
    fun decode(raw: String?): String? {
        var normalized = raw.orEmpty().uppercase().filter { it.isLetterOrDigit() }
        if (normalized.startsWith(PREFIX) && normalized.length == PREFIX.length + PAYLOAD_CHARS + CHECK_CHARS) {
            normalized = normalized.removePrefix(PREFIX)
        }
        if (normalized.length != PAYLOAD_CHARS + CHECK_CHARS) return null
        val payload = normalized.take(PAYLOAD_CHARS)
        val check = normalized.takeLast(CHECK_CHARS)
        if (checksum(payload) != check) return null
        val bytes = base32Decode(payload) ?: return null
        if (bytes.size != 11) return null
        val id = bytes.decodeToString()
        return id.takeIf(videoIdPattern::matches)
    }

    /** Formats any accepted code consistently, or null when it is invalid. */
    fun normalize(raw: String?): String? {
        val id = decode(raw) ?: return null
        return encode(id)
    }

    private fun checksum(payload: String): String {
        var value = 0x1F
        for (ch in payload) {
            value = (value * 31 + ALPHABET.indexOf(ch).coerceAtLeast(0)) and 0x3FF
        }
        return buildString {
            append(ALPHABET[(value shr 5) and 31])
            append(ALPHABET[value and 31])
        }
    }

    private fun base32Encode(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                out.append(ALPHABET[(buffer shr bits) and 31])
            }
        }
        if (bits > 0) {
            out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        }
        return out.toString()
    }

    private fun base32Decode(value: String): ByteArray? {
        val out = ArrayList<Byte>(11)
        var buffer = 0
        var bits = 0
        for (ch in value) {
            val digit = ALPHABET.indexOf(ch)
            if (digit < 0) return null
            buffer = (buffer shl 5) or digit
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out += ((buffer shr bits) and 0xFF).toByte()
            }
        }
        return out.toByteArray()
    }
}
