package com.itzfallenme.katmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itzfallenme.katmusic.data.LyricLine
import com.itzfallenme.katmusic.data.LyricSyllable
import kotlinx.coroutines.delay

/**
 * Karaoke lyric surface, ported from LastWave's `ModernLyricsView`
 * (Clash-Projects/LastWave-native, GPL-3.0). KatMusic is GPL-3.0 as well, so the
 * notice travels with the code.
 *
 * Everything here works off the provider's own timestamps:
 *
 *  - The dim line is drawn first, then a second, fully-lit copy of the *same*
 *    text is clipped to the sung extent with a feathered mask, so the light
 *    sweeps through each word exactly when it is sung (no per-word fades that
 *    look like the line is flickering on and off).
 *  - While a word is being sung its glyphs physically lift a couple of
 *    pixels and settle back, so a soft wave travels across the line.
 *  - A travelling radial halo and a blurred duplicate of the line provide the
 *    glow; both follow the wipe, which is what makes the active word bloom
 *    instead of the whole line turning white at once.
 *  - Neighbouring lines fade, soften and lag slightly behind the scroll, so
 *    focus reads as a spotlight moving rather than the list resizing.
 */
@Composable
fun KaraokeLyrics(
    lines: List<LyricLine>,
    positionMs: () -> Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    glow: Boolean = true,
    subLines: List<String?>? = null,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val listState = rememberLazyListState()
    var userScrolledAt by remember { mutableLongStateOf(0L) }
    val position by rememberUpdatedState(positionMs)

    val activeIndex by remember(lines) {
        derivedStateOf { activeLineIndex(lines, position()) }
    }

    LaunchedEffect(activeIndex, isPlaying) {
        if (activeIndex !in lines.indices) return@LaunchedEffect
        // Give a manual scroll a moment of ownership before the follow resumes.
        val grace = if (isPlaying) 700L else 5000L
        if (System.currentTimeMillis() - userScrolledAt < grace) return@LaunchedEffect
        runCatching {
            val info = listState.layoutInfo
            val viewport = info.viewportSize.height
            val item = info.visibleItemsInfo.find { it.index == activeIndex }
            if (item != null && viewport > 0) {
                val targetY = viewport / 3
                val delta = item.offset - targetY
                listState.animateScrollBy(delta.toFloat(), scrollDurationMs(delta.toFloat()))
            } else {
                listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .lyricsFadingEdge(topFadeDp = 32.dp, bottomFadeDp = 88.dp),
        contentPadding = PaddingValues(top = 36.dp, bottom = 120.dp, start = 18.dp, end = 18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        itemsIndexed(lines, key = { index, line -> "$index:${line.timeMs}" }) { index, line ->
            val isActive = index == activeIndex
            val distance = kotlin.math.abs(index - activeIndex)
            KaraokeLine(
                line = line,
                isActive = isActive,
                distance = distance,
                position = position,
                isPlaying = isPlaying,
                scale = scale,
                glow = glow,
                subLine = subLines?.getOrNull(index),
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onSeek = { onSeek(line.timeMs) },
                onUserScroll = { userScrolledAt = System.currentTimeMillis() },
            )
        }
    }
}

@Composable
private fun KaraokeLine(
    line: LyricLine,
    isActive: Boolean,
    distance: Int,
    position: () -> Long,
    isPlaying: Boolean,
    scale: Float,
    glow: Boolean,
    subLine: String?,
    activeColor: Color,
    inactiveColor: Color,
    onSeek: () -> Unit,
    onUserScroll: () -> Unit,
) {
    val density = LocalDensity.current

    val lineScale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.975f,
        animationSpec = tween(400, easing = LYRIC_EASE),
        label = "lyricScale",
    )
    val alpha by animateFloatAsState(
        targetValue = LYRIC_ALPHA[distance.coerceAtMost(LYRIC_ALPHA.lastIndex)],
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "lyricAlpha",
    )
    val blurDp by animateFloatAsState(
        targetValue = if (isActive) 0f else LYRIC_BLUR[distance.coerceAtMost(LYRIC_BLUR.lastIndex)],
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "lyricBlur",
    )
    val translationY by animateFloatAsState(
        targetValue = when {
            isActive -> -3f
            distance > 0 -> 3f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "lyricTranslate",
    )

    // Elastic trail: rows below the active one lag behind the scroll and spring
    // back one after another, so the list feels pulled by the music.
    val trail = remember { Animatable(0f) }
    LaunchedEffect(isActive) {
        if (isActive && isPlaying) {
            trail.snapTo(6f)
            trail.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 170f))
        } else if (trail.value != 0f) {
            trail.snapTo(0f)
        }
    }
    // A short swell when a line takes focus.
    val onset = remember { Animatable(0f) }
    LaunchedEffect(isActive) {
        if (isActive) {
            onset.snapTo(0f)
            onset.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
        } else {
            onset.snapTo(0f)
        }
    }
    val onsetWave =
        if (isActive && onset.value in 0.001f..0.999f) kotlin.math.sin(Math.PI.toFloat() * onset.value) else 0f
    val totalScale = lineScale * (1f + 0.018f * onsetWave)

    val fontStyle = remember(scale) {
        TextStyle(
            fontSize = (23f * scale).sp,
            lineHeight = (33f * scale).sp,
            fontWeight = if (isActive) FontWeight.Black else FontWeight.SemiBold,
            letterSpacing = 0.1.sp,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0.5f)
                scaleX = totalScale
                scaleY = totalScale
                this.translationY = (translationY + trail.value) * density.density
                this.alpha = alpha
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val r = blurDp * density.density
                    renderEffect = if (r > 0.3f) BlurEffect(r, r) else null
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onUserScroll()
                onSeek()
            }
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (line.hasSyllables) {
                KaraokeSyllableLine(
                    line = line,
                    isActive = isActive,
                    position = position,
                    fontStyle = fontStyle,
                    glow = glow,
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                )
            } else {
                KaraokePlainLine(
                    text = line.text.ifBlank { "♪" },
                    isActive = isActive,
                    fontStyle = fontStyle,
                    glow = glow,
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                )
            }
            if (!subLine.isNullOrBlank()) {
                Text(
                    text = subLine,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = (13f * scale).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = if (isActive) activeColor.copy(alpha = 0.75f) else inactiveColor.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp),
                )
            }
        }
    }
}

/** A line without word timings: colour and glow ease in when it becomes current. */
@Composable
private fun KaraokePlainLine(
    text: String,
    isActive: Boolean,
    fontStyle: TextStyle,
    glow: Boolean,
    activeColor: Color,
    inactiveColor: Color,
) {
    val color by androidx.compose.animation.animateColorAsState(
        targetValue = if (isActive) activeColor else inactiveColor.copy(alpha = 0.45f),
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "plainLineColor",
    )
    Box(Modifier.fillMaxWidth()) {
        if (isActive && glow) LyricGlowText(text = text, style = fontStyle)
        Text(
            text = text,
            style = fontStyle,
            color = color,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The karaoke line: dim text, then a lit copy clipped to the sung extent with a
 * feathered edge, plus a halo that rides the current word.
 */
@Composable
private fun KaraokeSyllableLine(
    line: LyricLine,
    isActive: Boolean,
    position: () -> Long,
    fontStyle: TextStyle,
    glow: Boolean,
    activeColor: Color,
    inactiveColor: Color,
) {
    val displayTexts = remember(line) { resolveSyllableDisplayTexts(line) }
    val fullText = remember(line, displayTexts) {
        line.syllables.indices.joinToString("") { displayTexts.getOrElse(it) { line.syllables[it].text } }
    }
    val charRanges = remember(line, displayTexts) {
        var offset = 0
        line.syllables.indices.map { i ->
            val start = offset
            offset += displayTexts.getOrElse(i) { line.syllables[i].text }.length
            start to offset
        }
    }
    var layout by remember(fullText) { androidx.compose.runtime.mutableStateOf<TextLayoutResult?>(null) }

    val positionSource = rememberUpdatedState(position)

    Box(Modifier.fillMaxWidth()) {
        if (isActive && glow) LyricGlowText(text = fullText, style = fontStyle)

        // Dim base text, one syllable at a time still visible as "next up".
        Text(
            text = fullText,
            style = fontStyle,
            color = inactiveColor.copy(alpha = 0.38f),
            textAlign = TextAlign.Start,
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    val l = layout
                    if (l == null || !isActive) drawContent()
                    else drawWithSyllableLift(l, line, charRanges, positionSource.value(), 3.dp.toPx())
                },
        )

        // Travelling halo behind the current word: this is the "glow follows the
        // word" part that a plain line-colour change cannot do.
        if (isActive && glow) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawWithContent {
                        val l = layout ?: return@drawWithContent
                        val sung = computeSung(l, line, charRanges, fullText, positionSource.value()) ?: return@drawWithContent
                        val centerY = (l.getLineTop(sung.lineIndex) + l.getLineBottom(sung.lineIndex)) / 2f
                        val radius = (l.getLineBottom(sung.lineIndex) - l.getLineTop(sung.lineIndex)) * 1.6f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    activeColor.copy(alpha = 0.30f),
                                    activeColor.copy(alpha = 0.10f),
                                    Color.Transparent,
                                ),
                                center = Offset(sung.x, centerY),
                                radius = radius.coerceAtLeast(1f),
                            ),
                            radius = radius.coerceAtLeast(1f),
                            center = Offset(sung.x, centerY),
                        )
                    }
            )
        }

        // Lit copy, masked to the sung extent. The mask is a soft gradient so the
        // boundary is a feather, not a hard cut.
        Text(
            text = fullText,
            style = fontStyle,
            color = activeColor,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    val l = layout
                    if (!isActive || l == null) return@drawWithContent
                    val textLength = fullText.length
                    if (textLength == 0) return@drawWithContent
                    val pos = positionSource.value()
                    val sung = computeSung(l, line, charRanges, fullText, pos)
                    // Before the first word there is nothing lit yet.
                    if (sung == null) return@drawWithContent

                    val liftPx = 3.dp.toPx()
                    if (sung.fill >= textLength) {
                        drawWithSyllableLift(l, line, charRanges, pos, liftPx)
                        return@drawWithContent
                    }
                    val top = l.getLineTop(sung.lineIndex) - liftPx * 1.5f
                    val bottom = l.getLineBottom(sung.lineIndex)
                    val feather = sung.feather
                    val xStart = (sung.x - feather).coerceAtLeast(0f)
                    val xEnd = (sung.x + feather).coerceAtMost(size.width)

                    val canvas = drawContext.canvas.nativeCanvas
                    val checkpoint = canvas.saveLayer(0f, 0f, size.width, size.height, null)
                    drawWithSyllableLift(l, line, charRanges, pos, liftPx)

                    if (xEnd < size.width) canvas.drawRect(xEnd, top, size.width, bottom, CLEAR_PAINT)
                    if (xEnd > xStart) {
                        MASK_PAINT.shader = android.graphics.LinearGradient(
                            xStart, 0f, xEnd, 0f,
                            android.graphics.Color.WHITE,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Shader.TileMode.CLAMP,
                        )
                        canvas.drawRect(xStart, top, xEnd, bottom, MASK_PAINT)
                    }
                    if (bottom < size.height) {
                        canvas.drawRect(0f, bottom, size.width, size.height, CLEAR_PAINT)
                    }
                    canvas.restoreToCount(checkpoint)
                },
        )
    }
}

// Lazily built: the class also holds pure timing helpers that unit tests load on
// a plain JVM, where instantiating android.graphics.Paint throws.
private val CLEAR_PAINT: android.graphics.Paint by lazy {
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
    }
}
private val MASK_PAINT: android.graphics.Paint by lazy {
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
    }
}

/** Where the karaoke wipe currently sits. */
private data class Sung(
    /** Lit character extent inside the full line. */
    val fill: Float,
    /** Pixel x of the wipe edge on [lineIndex]. */
    val x: Float,
    val lineIndex: Int,
    val feather: Float,
)

private fun computeSung(
    l: TextLayoutResult,
    line: LyricLine,
    charRanges: List<Pair<Int, Int>>,
    fullText: String,
    pos: Long,
): Sung? {
    val textLength = fullText.length
    if (textLength == 0) return null
    var fill = 0f
    var sylIdx = -1
    var sylP = 0f
    for (i in line.syllables.indices) {
        val syl = line.syllables[i]
        if (pos < syl.timeMs) break
        val (cs, ce) = charRanges.getOrNull(i) ?: continue
        val linear = if (syl.durationMs <= 0L) 1f
        else ((pos - syl.timeMs).toFloat() / syl.durationMs).coerceIn(0f, 1f)
        // A sung syllable attacks fast and then sustains; a linear wipe reads
        // as lagging behind the voice.
        val p = 1f - Math.pow((1f - linear).toDouble(), 1.4).toFloat()
        fill = cs + (ce - cs) * p
        sylIdx = i
        sylP = p
        if (p < 1f) break
    }
    if (sylIdx < 0 || fill <= 0f) return null

    val (sCs, sCe) = charRanges[sylIdx]
    val startIdx = sCs.coerceIn(0, textLength - 1)
    val lastIdx = (sCe - 1).coerceIn(startIdx, textLength - 1)
    val sameLine = l.getLineForOffset(startIdx) == l.getLineForOffset(lastIdx)
    val lineIdx: Int
    val x: Float
    var feather = 12f
    if (sameLine) {
        lineIdx = l.getLineForOffset(startIdx)
        val xs = l.getHorizontalPosition(startIdx, true)
        val xe = if (sCe < textLength && l.getLineForOffset(sCe) == lineIdx) {
            l.getHorizontalPosition(sCe, true)
        } else l.getLineRight(lineIdx)
        x = xs + (xe - xs) * sylP
        feather = (kotlin.math.abs(xe - xs) * 0.3f).coerceIn(5f, 18f)
    } else {
        // Wrapped syllable: interpolate across glyph offsets, not per char, so
        // complex scripts (matras, conjuncts) do not stall the wipe.
        val charIdx = fill.toInt().coerceIn(0, textLength - 1)
        val frac = fill - charIdx
        lineIdx = l.getLineForOffset(charIdx)
        val x0 = l.getHorizontalPosition(charIdx, true)
        val x1 = if (charIdx + 1 < textLength && l.getLineForOffset(charIdx + 1) == lineIdx) {
            l.getHorizontalPosition(charIdx + 1, true)
        } else l.getLineRight(lineIdx)
        x = x0 + (x1 - x0) * frac
    }
    return Sung(fill, x, lineIdx, feather)
}

/**
 * Lift envelope (0..1) for a syllable: rises as it starts being sung, holds,
 * then settles after it ends, so a wave travels across the line.
 */
private fun syllableLift(syl: LyricSyllable, pos: Long): Float {
    if (pos < syl.timeMs) return 0f
    val dur = syl.durationMs.coerceAtLeast(1L)
    val rise = ((pos - syl.timeMs).toFloat() / dur.coerceIn(140L, 420L)).coerceIn(0f, 1f)
    val riseE = 1f - (1f - rise) * (1f - rise) * (1f - rise)
    val after = pos - (syl.timeMs + dur)
    val settle = if (after <= 0L) 1f else (1f - after / 520f).coerceIn(0f, 1f)
    val settleE = settle * settle * (3f - 2f * settle)
    return riseE * settleE
}

/** Draws [drawContent] with each lifting syllable raised, without lifting the rest. */
private fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.drawWithSyllableLift(
    l: TextLayoutResult,
    line: LyricLine,
    charRanges: List<Pair<Int, Int>>,
    pos: Long,
    liftPx: Float,
) {
    val textLength = l.layoutInput.text.length
    var rects: ArrayList<FloatArray>? = null
    for (i in line.syllables.indices) {
        val lift = syllableLift(line.syllables[i], pos)
        if (lift <= 0.001f) continue
        val (cs, ce) = charRanges.getOrNull(i) ?: continue
        if (ce <= cs || cs >= textLength) continue
        val last = (ce - 1).coerceAtMost(textLength - 1)
        val li = l.getLineForOffset(cs)
        if (li != l.getLineForOffset(last)) continue
        val xs = l.getHorizontalPosition(cs, true)
        val xe = if (ce < textLength && l.getLineForOffset(ce) == li) {
            l.getHorizontalPosition(ce, true)
        } else l.getLineRight(li)
        if (rects == null) rects = ArrayList(4)
        rects += floatArrayOf(
            minOf(xs, xe), l.getLineTop(li), maxOf(xs, xe), l.getLineBottom(li), -lift * liftPx,
        )
    }
    if (rects == null) {
        drawContent()
        return
    }
    val canvas = drawContext.canvas.nativeCanvas
    canvas.save()
    for (r in rects) canvas.clipOutRect(r[0], r[1], r[2], r[3])
    drawContent()
    canvas.restore()
    for (r in rects) {
        canvas.save()
        canvas.translate(0f, r[4])
        canvas.clipRect(r[0], r[1], r[2], r[3])
        drawContent()
        canvas.restore()
    }
}

/**
 * Glow behind the active line: a blurred, layout-identical copy of the same
 * Text. A TextStyle shadow drifts off complex-script shaping, a duplicate
 * cannot — same text, style, width and direction means same bounds.
 */
@Composable
private fun LyricGlowText(text: String, style: TextStyle) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
        // Pre-31 Modifier.blur is a no-op; a soft shadow is the fallback.
        Text(
            text = text,
            style = style.copy(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.White.copy(alpha = 0.45f),
                    blurRadius = 20f,
                    offset = Offset.Zero,
                )
            ),
            color = Color.Transparent,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    Text(
        text = text,
        style = style,
        color = Color.White.copy(alpha = 0.42f),
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .blur(9.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
    )
}

/** Melts the top and bottom of the lyrics viewport instead of hard-clipping it. */
private fun Modifier.lyricsFadingEdge(topFadeDp: androidx.compose.ui.unit.Dp, bottomFadeDp: androidx.compose.ui.unit.Dp): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val top = topFadeDp.toPx()
            val bottom = bottomFadeDp.toPx()
            val h = size.height
            if (h > top + bottom) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        (top / h) to Color.Black,
                        ((h - bottom) / h) to Color.Black,
                        1f to Color.Transparent,
                        startY = 0f,
                        endY = h,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

internal fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (lines[middle].timeMs <= positionMs) low = middle + 1 else high = middle
    }
    return low - 1
}

/**
 * Provider tokens often arrive without their separator spaces. Restore them
 * from the plain line text so words do not run together after the wipe.
 */
internal fun resolveSyllableDisplayTexts(line: LyricLine): List<String> {
    val syllables = line.syllables
    if (syllables.isEmpty()) return emptyList()
    val fullText = line.text
    if (fullText.contains(' ') || fullText.contains('\u00A0')) {
        val result = mutableListOf<String>()
        var cursor = 0
        for (i in syllables.indices) {
            val syl = syllables[i]
            val clean = syl.text.trim()
            if (clean.isEmpty()) {
                result.add(syl.text)
                continue
            }
            val idx = fullText.indexOf(clean, startIndex = cursor, ignoreCase = true)
            if (idx >= 0) {
                val end = idx + clean.length
                val trailingSpace = end < fullText.length && (fullText[end] == ' ' || fullText[end] == '\u00A0')
                if (trailingSpace && i < syllables.size - 1) {
                    result.add("$clean ")
                    cursor = end + 1
                } else {
                    result.add(clean)
                    cursor = end
                }
            } else {
                result.add(if (i < syllables.size - 1) "$clean " else clean)
            }
        }
        return result
    }
    return syllables.map { it.text }
}

private val LYRIC_EASE = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)
private val LYRIC_ALPHA = floatArrayOf(1f, 0.80f, 0.66f, 0.52f, 0.40f, 0.28f)
private val LYRIC_BLUR = floatArrayOf(0f, 0.6f, 1f, 1.5f, 2f, 2.6f)

private const val SCROLL_MIN_MS = 320f
private const val SCROLL_MAX_MS = 520f

private fun scrollDurationMs(deltaPx: Float): Float =
    (SCROLL_MIN_MS + kotlin.math.abs(deltaPx) * 0.35f).coerceAtMost(SCROLL_MAX_MS)

/** Frame-driven pixel scroll for [LazyListState], which ships no animated scrollBy. */
private suspend fun LazyListState.animateScrollBy(pixels: Float, durationMs: Float) {
    if (pixels == 0f) return
    var consumed = 0f
    var startNanos = -1L
    while (true) {
        val target = withFrameNanos { now ->
            if (startNanos < 0L) startNanos = now
            val t = ((now - startNanos) / (durationMs * 1_000_000f)).coerceIn(0f, 1f)
            pixels * LYRIC_EASE.transform(t)
        }
        val delta = target - consumed
        val scrolled = scrollBy(delta)
        consumed += scrolled
        if (scrolled == 0f && delta != 0f) break
        if (kotlin.math.abs(target - pixels) < 0.5f) break
    }
}
