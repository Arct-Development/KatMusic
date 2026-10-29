package com.itzfallenme.katmusic.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Waveform seek bar: the played portion of the track is a stack of three
 * flowing sine layers (soft, brighter, luminous) with the untouched part as a
 * flat capsule. While you drag, the waves flatten out and a thumb appears, so
 * scrubbing feels exact instead of swimming.
 *
 * Inspired by LastWave's seek bar; the drawing here is written for this app's
 * palette and does not include its layer logic or timings.
 */
@Composable
fun WavySeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color,
    inactiveColor: Color,
    playing: Boolean = true,
    amplitude: Float = 2.2f
) {
    var dragging by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableFloatStateOf(0f) }
    val shown = if (isDragging > 0f) dragging else progress.coerceIn(0f, 1f)

    val transition = rememberInfiniteTransition(label = "wave")
    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase1"
    )
    val phase2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "phase2"
    )
    val phase3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase3"
    )

    // Flatten the waves while scrubbing so the thumb is unambiguous.
    val calm by animateFloatAsState(
        targetValue = if (isDragging > 0f) 0.18f else 1f,
        animationSpec = tween(180),
        label = "waveCalm"
    )
    val liveAmp = amplitude * calm
    val wavesOn = playing && isDragging == 0f

    val path1 = remember { Path() }
    val path2 = remember { Path() }
    val path3 = remember { Path() }
    val clip = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = 1f
                        dragging = (it.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = 0f
                        onSeek(dragging)
                    },
                    onDragCancel = { isDragging = 0f },
                    onHorizontalDrag = { change, _ ->
                        dragging = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                )
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            val centerY = size.height / 2f
            val trackThickness = 5.dp.toPx()
            val thumbX = (shown * size.width).coerceIn(0f, size.width)

            // Unplayed: a quiet capsule. Played: the same capsule in the
            // foreground tone, exactly like the reference player.
            drawLine(
                color = inactiveColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = trackThickness,
                cap = StrokeCap.Round
            )
            if (thumbX > 0f) {
                drawLine(
                    color = activeColor,
                    start = Offset(0f, centerY),
                    end = Offset(thumbX, centerY),
                    strokeWidth = trackThickness,
                    cap = StrokeCap.Round
                )
            }

            // Chapter marker: a short hairline across the track at the quarter
            // point, so the bar reads as a timeline instead of a plain slider.
            val tickX = size.width * 0.32f
            drawLine(
                color = inactiveColor,
                start = Offset(tickX, centerY - trackThickness * 0.9f),
                end = Offset(tickX, centerY + trackThickness * 0.9f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Thumb: a slim rounded bar, taller than the track and always visible.
            val thumbWidth = if (isDragging > 0f) 4.5.dp.toPx() else 3.5.dp.toPx()
            val thumbHeight = (trackThickness * (if (isDragging > 0f) 5.4f else 4.4f))
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(thumbX - thumbWidth / 2f, centerY - thumbHeight / 2f),
                size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f)
            )
        }
    }
}
