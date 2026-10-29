package com.itzfallenme.katmusic.data

/**
 * How much bandwidth a stream may use. YouTube Music offers the same audio
 * renditions to every client; the tiers are a bitrate ceiling over those
 * renditions, not a lossless mode.
 */
enum class StreamQuality(
    val id: String,
    val label: String,
    val description: String,
    private val maxBitrateKbps: Int
) {
    MAX("max", "Max", "Best available stream, usually Opus at 256 kbps", 0),
    HIGH("high", "High", "Capped at 256 kbps", 256),
    SAVER("saver", "Data saver", "Capped at 128 kbps", 128);

    val maxBitrateBps: Int get() = maxBitrateKbps * 1000

    /** True when [bitrate] fits under this tier; unknown (0) bitrates always fit. */
    fun allows(bitrate: Int): Boolean =
        maxBitrateKbps == 0 || bitrate <= 0 || bitrate <= maxBitrateBps

    companion object {
        fun fromId(id: String?): StreamQuality = entries.firstOrNull { it.id == id } ?: HIGH
    }
}
