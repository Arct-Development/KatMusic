package com.itzfallenme.katmusic.data

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Importer for the headphone correction profiles published by the AutoEq project.
 *
 * Three text formats are understood:
 *  - parametric: `Filter 1: ON PK Fc 105 Hz Gain 5.5 dB Q 0.70`
 *  - graphic:    `GraphicEQ: 20 -0.5; 21 -0.4; 25 -0.3; ...`
 *  - csv:        `frequency,gain`
 *
 * A profile is evaluated at the centre frequency of each equalizer band, so a 10-band
 * system gets a sensible approximation of the published curve.
 */
object AutoEq {

    enum class FilterType { PEAKING, LOW_SHELF, HIGH_SHELF }

    data class Filter(
        val type: FilterType,
        val frequencyHz: Float,
        val gainDb: Float,
        val q: Float
    )

    data class Profile(
        val name: String = "",
        val preampDb: Float = 0f,
        val filters: List<Filter> = emptyList(),
        val curve: List<Pair<Float, Float>> = emptyList()
    ) {
        val isEmpty: Boolean get() = filters.isEmpty() && curve.isEmpty()
        val filterCount: Int get() = filters.size
    }

    private val preampRegex = Regex("Preamp:\\s*(-?[\\d.]+)\\s*dB", RegexOption.IGNORE_CASE)
    private val filterRegex = Regex(
        "Filter\\s*\\d+:\\s*ON\\s+([A-Za-z]{2,3})\\s+Fc\\s+([\\d.]+)\\s*Hz\\s+Gain\\s+(-?[\\d.]+)\\s*dB" +
            "(?:\\s+Q\\s+([\\d.]+))?",
        RegexOption.IGNORE_CASE
    )

    /** Reads a profile from pasted text. Returns an empty profile when nothing matched. */
    fun parse(text: String, name: String = ""): Profile {
        val preamp = preampRegex.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 0f
        val filters = filterRegex.findAll(text).mapNotNull { match ->
            val type = when (match.groupValues[1].uppercase()) {
                "PK", "PEQ", "P" -> FilterType.PEAKING
                "LSC", "LS", "LSH" -> FilterType.LOW_SHELF
                "HSC", "HS", "HSH" -> FilterType.HIGH_SHELF
                else -> null
            } ?: return@mapNotNull null
            val frequency = match.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
            val gain = match.groupValues[3].toFloatOrNull() ?: return@mapNotNull null
            val q = match.groupValues.getOrNull(4)?.toFloatOrNull() ?: 0.7f
            Filter(type, frequency, gain, q)
        }.toList()

        val curve = parseCurve(text)
        val detectedName = name.ifBlank {
            text.lineSequence()
                .firstOrNull { it.trim().startsWith("#") && it.length > 3 }
                ?.trim()?.removePrefix("#")?.trim()
                .orEmpty()
        }
        return Profile(detectedName, preamp, filters, curve)
    }

    private fun parseCurve(text: String): List<Pair<Float, Float>> {
        val graphic = Regex("GraphicEQ:\\s*(.+)", RegexOption.IGNORE_CASE).find(text)
        val body = graphic?.groupValues?.get(1)
        val points = ArrayList<Pair<Float, Float>>()
        if (body != null) {
            body.split(';').forEach { chunk ->
                val parts = chunk.trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val frequency = parts[0].toFloatOrNull()
                    val gain = parts[1].toFloatOrNull()
                    if (frequency != null && gain != null && frequency > 0f) {
                        points.add(frequency to gain)
                    }
                }
            }
        }
        if (points.size >= 5) return points.sortedBy { it.first }

        // Fall back to a two-column CSV / whitespace table.
        val table = ArrayList<Pair<Float, Float>>()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("Preamp")) {
                return@forEach
            }
            val cells = trimmed.split(',', '\t', ' ').filter { it.isNotBlank() }
            if (cells.size != 2) return@forEach
            val frequency = cells[0].trim().toFloatOrNull()
            val gain = cells[1].trim().removeSuffix("dB").trim().toFloatOrNull()
            if (frequency != null && gain != null && frequency > 0f) {
                table.add(frequency to gain)
            }
        }
        return if (table.size >= 5) table.sortedBy { it.first } else emptyList()
    }

    /** Curve value in dB at [frequencyHz], without preamp applied. */
    fun responseAt(profile: Profile, frequencyHz: Float): Float {
        if (frequencyHz <= 0f) return 0f
        var total = interpolate(profile.curve, frequencyHz)
        profile.filters.forEach { filter ->
            total += filterResponse(filter, frequencyHz)
        }
        return total
    }

    private fun filterResponse(filter: Filter, frequencyHz: Float): Float {
        val fc = filter.frequencyHz.takeIf { it > 0f } ?: return 0f
        val octaves = log2(frequencyHz / fc)
        return when (filter.type) {
            FilterType.PEAKING -> {
                val bandwidth = (2f / ln(2f)) * asinh(1f / (2f * filter.q.coerceAtLeast(0.1f)))
                val sigma = (bandwidth / 2.35f).coerceAtLeast(0.15f)
                filter.gainDb * exp(-0.5f * (octaves / sigma).pow(2))
            }
            // Shelves are modelled as a smooth step around the corner frequency.
            FilterType.LOW_SHELF -> filter.gainDb / (1f + 2f.pow(2f * octaves))
            FilterType.HIGH_SHELF -> filter.gainDb / (1f + 2f.pow(-2f * octaves))
        }
    }

    private fun asinh(value: Float): Float = ln(value + kotlin.math.sqrt(value * value + 1f))

    private fun log2(value: Float): Float = (ln(value) / ln(2f))

    private fun interpolate(points: List<Pair<Float, Float>>, frequencyHz: Float): Float {
        if (points.isEmpty()) return 0f
        if (points.size == 1) return points.first().second
        if (frequencyHz <= points.first().first) return points.first().second
        if (frequencyHz >= points.last().first) return points.last().second
        for (index in 0 until points.size - 1) {
            val (lowFreq, lowGain) = points[index]
            val (highFreq, highGain) = points[index + 1]
            if (frequencyHz in lowFreq..highFreq) {
                val span = log10(highFreq) - log10(lowFreq)
                if (span <= 0f) return lowGain
                val ratio = (log10(frequencyHz) - log10(lowFreq)) / span
                return lowGain + (highGain - lowGain) * ratio
            }
        }
        return points.last().second
    }

    /**
     * Converts a profile into one gain per equalizer band, clamped to the ±[limit] dB
     * that the platform equalizer accepts. Boosts are shifted down as a whole so the
     * loudest band sits at 0 dB, which keeps the correction from clipping.
     */
    fun gainsFor(profile: Profile, bandHz: List<Int>, limit: Float = 12f): List<Float> {
        if (profile.isEmpty || bandHz.isEmpty()) return List(bandHz.size) { 0f }
        val raw = bandHz.map { responseAt(profile, it.toFloat()) + profile.preampDb }
        val headroom = raw.maxOrNull()?.takeIf { it > 0f } ?: 0f
        return raw.map { gain ->
            val shifted = if (headroom > 0f) gain - headroom else gain
            ((shifted.coerceIn(-limit, limit)) * 10f).roundToInt() / 10f
        }
    }

    /** True when the text looks like an AutoEq export, used to validate pasted input. */
    fun looksLikeProfile(text: String): Boolean =
        text.contains("GraphicEQ", ignoreCase = true) ||
            filterRegex.containsMatchIn(text) ||
            text.lineSequence().count { it.trim().split(' ', ',').size == 2 } >= 5
}
