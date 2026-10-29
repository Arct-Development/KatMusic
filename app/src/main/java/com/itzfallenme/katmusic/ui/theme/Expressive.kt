package com.itzfallenme.katmusic.ui.theme

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Continuous-curvature corner, the silhouette M3 Expressive uses for cards and
 * buttons. Android's [androidx.compose.foundation.shape.RoundedCornerShape]
 * draws a quarter circle per corner, which leaves a visible seam where the
 * straight edge meets the arc; this path blends the two with a cubic shoulder.
 */
class SmoothCornerShape(
    private val cornerRadius: Dp = 20.dp,
    private val smoothing: Float = 0.6f
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val smooth = smoothing.coerceIn(0f, 1f)
        // A corner can never be larger than half the shortest edge, otherwise the
        // cubic control points cross over each other and the path renders as a
        // rectangle. Clamping first keeps every size valid.
        val radius = with(density) { cornerRadius.toPx() }
            .coerceIn(0f, min(size.width, size.height) / 2f)
        if (radius <= 0.5f) {
            return Outline.Rectangle(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
        }
        // Pulling the control point in from the corner gives the continuous
        // shoulder; 0 smoothing behaves like a circular arc.
        val control = radius * (1f - smooth * 0.55f)
        val path = Path().apply {
            moveTo(radius, 0f)
            lineTo(size.width - radius, 0f)
            cubicTo(
                size.width - control, 0f,
                size.width, control,
                size.width, radius
            )
            lineTo(size.width, size.height - radius)
            cubicTo(
                size.width, size.height - control,
                size.width - control, size.height,
                size.width - radius, size.height
            )
            lineTo(radius, size.height)
            cubicTo(
                control, size.height,
                0f, size.height - control,
                0f, size.height - radius
            )
            lineTo(0f, radius)
            cubicTo(
                0f, control,
                control, 0f,
                radius, 0f
            )
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * One-shot "pop" whenever [key] changes: scales up and settles back. Used for
 * likes and other toggles, where a plain colour swap is easy to miss.
 */
@Composable
fun Modifier.popWhen(key: Any?): Modifier {
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (Motion.reduced) return@LaunchedEffect
        scale.animateTo(1.28f, tween(120))
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Counts up to [value] instead of swapping the digits instantly. */
@Composable
fun RollingNumber(
    value: Int,
    style: androidx.compose.ui.text.TextStyle,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val animated by androidx.compose.animation.core.animateIntAsState(
        targetValue = value,
        animationSpec = if (Motion.reduced) androidx.compose.animation.core.snap()
        else tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "rollingNumber"
    )
    Text(text = animated.toString(), style = style, color = color, modifier = modifier)
}

/** Corner tokens for the expressive surfaces used across the app. */
object ExpressiveShapes {
    val card = SmoothCornerShape(26.dp, 0.55f)
    val small = SmoothCornerShape(16.dp, 0.5f)
    val sheet = SmoothCornerShape(32.dp, 0.6f)
    val button = SmoothCornerShape(20.dp, 0.65f)
}

/**
 * Segmented control with the expressive "push" physics: the tapped segment
 * briefly grows while its neighbours give way, and the active segment morphs
 * from pill to squircle instead of just changing colour.
 */
@Composable
fun ExpressiveSegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    var pressedIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(pressedIndex) {
        if (pressedIndex >= 0) {
            delay(260)
            pressedIndex = -1
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(containerColor)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, label ->
            val weight by animateFloatAsState(
                targetValue = when {
                    pressedIndex == index -> 1.22f
                    pressedIndex >= 0 -> 0.88f
                    index == selectedIndex -> 1.1f
                    else -> 1f
                },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "segmentWeight"
            )
            val active = index == selectedIndex
            val background by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(200),
                label = "segmentColor"
            )
            val radius by animateDpAsState(
                targetValue = if (active) 16.dp else 26.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "segmentCorner"
            )
            Box(
                modifier = Modifier
                    .weight(weight)
                    .height(40.dp)
                    .graphicsLayer { shape = SmoothCornerShape(radius, 0.6f); clip = true }
                    .background(background)
                    .clickable {
                        pressedIndex = index
                        onSelect(index)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Previous / play-pause / next as one elastic row: pressing a control expands
 * it while its neighbours compress, and the play button's corners morph with
 * playback state.
 */
@Composable
fun ExpressiveTransportRow(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    buffering: Boolean = false,
    height: Dp = 78.dp
) {
    var pressed by remember { mutableStateOf(0) }
    LaunchedEffect(pressed) {
        if (pressed != 0) {
            delay(240)
            pressed = 0
        }
    }
    val base = MaterialTheme.colorScheme.secondaryContainer
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        fun weightFor(slot: Int): Float = when {
            pressed == slot -> 1.25f
            pressed != 0 -> 0.72f
            else -> 1f
        }
        val prevWeight by animateFloatAsState(weightFor(1), label = "prevWeight")
        val playWeight by animateFloatAsState(weightFor(2), label = "playWeight")
        val nextWeight by animateFloatAsState(weightFor(3), label = "nextWeight")
        val playRadius by animateDpAsState(
            targetValue = if (isPlaying) 22.dp else 50.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "playCorner"
        )

        TransportButton(
            modifier = Modifier.weight(prevWeight),
            container = base,
            onClick = { pressed = 1; onPrevious() }
        ) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(30.dp)
            )
        }
        TransportButton(
            modifier = Modifier.weight(playWeight),
            container = accent,
            shape = SmoothCornerShape(playRadius, 0.65f),
            onClick = { pressed = 2; onPlayPause() }
        ) {
            if (buffering) {
                MorphingShapeLoader(size = 26.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Crossfade(targetState = isPlaying, animationSpec = tween(160), label = "playIcon") { playing ->
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        TransportButton(
            modifier = Modifier.weight(nextWeight),
            container = base,
            onClick = { pressed = 3; onNext() }
        ) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun TransportButton(
    modifier: Modifier,
    container: Color,
    shape: Shape = CircleShape,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "transportScale"
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.shape = shape
                clip = true
            }
            .background(container)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Shape-morph loading indicator: a rounded square that rocks into a circle and
 * back. Used in place of a spinner where a track is resolving.
 */
@Composable
fun MorphingShapeLoader(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "morph")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "phase"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200)),
        label = "rotation"
    )
    Canvas(modifier.size(size)) {
        val inset = this.size.minDimension * 0.18f
        val side = this.size.minDimension - inset * 2
        val radius = inset + side * 0.5f * phase
        val path = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = inset,
                    top = inset,
                    right = inset + side,
                    bottom = inset + side,
                    cornerRadius = CornerRadius(radius, radius)
                )
            )
        }
        rotate(rotation) {
            drawPath(path, color)
        }
    }
}

/** Thin sine-wave progress line, the expressive replacement for a flat bar. */
@Composable
fun WavyProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 14.dp
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1500, easing = androidx.compose.animation.core.LinearEasing)),
        label = "shift"
    )
    Canvas(modifier.fillMaxWidth().height(height)) {
        val midY = size.height / 2f
        val amplitude = size.height * 0.22f
        val stroke = size.height * 0.28f
        val endX = size.width * progress.coerceIn(0f, 1f)
        drawLine(
            color = track,
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        if (endX <= 0f) return@Canvas
        val path = Path()
        var x = 0f
        path.moveTo(0f, midY)
        while (x <= endX) {
            val y = midY + sin((x / 22f + shift).toDouble()).toFloat() * amplitude
            path.lineTo(x, y)
            x += 4f
        }
        drawPath(
            path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }
}
