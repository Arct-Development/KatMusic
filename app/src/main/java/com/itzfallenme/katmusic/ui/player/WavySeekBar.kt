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
            val trackThickness = 4.5.dp.toPx()
            val thumbX = (shown * size.width).coerceIn(0f, size.width)
            val half = trackThickness / 2f

            // Rest of the track.
            drawLine(
                color = inactiveColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = trackThickness,
                cap = StrokeCap.Round
            )

            if (thumbX <= 0f) return@Canvas

            clip.reset()
            clip.addRoundRect(
                RoundRect(
                    rect = Rect(-half, 0f, thumbX + half, size.height),
                    topLeft = CornerRadius(half, half),
                    bottomLeft = CornerRadius(half, half),
                    topRight = CornerRadius(half, half),
                    bottomRight = CornerRadius(half, half)
                )
            )

            fun wave(
                path: Path,
                wavelength: Float,
                amp: Float,
                phase: Float,
                fill: Boolean
            ) {
                path.reset()
                path.moveTo(0f, centerY)
                var x = 0f
                val step = 1.5.dp.toPx()
                val k = (2 * PI / wavelength).toFloat()
                while (x <= thumbX) {
                    val angle = x * k - phase
                    path.lineTo(x, centerY - (0.5f + 0.5f * sin(angle)) * amp)
                    x += step
                }
                path.lineTo(thumbX, centerY)
                if (fill) path.close()
            }

            clipPath(clip) {
                val p1 = if (wavesOn) phase1 else 0f
                val p2 = if (wavesOn) phase2 else 0f
                val p3 = if (wavesOn) phase3 else 0f

                // Broad, quiet body.
                wave(path1, 150.dp.toPx(), liveAmp * 2.1f, p1, fill = true)
                drawPath(
                    path = path1,
                    brush = Brush.verticalGradient(
                        listOf(activeColor.copy(alpha = 0.10f), activeColor.copy(alpha = 0.24f)),
                        startY = centerY - liveAmp * 2f,
                        endY = centerY
                    )
                )

                // Middle layer.
                wave(path2, 110.dp.toPx(), liveAmp * 1.5f, p2, fill = true)
                drawPath(
                    path = path2,
                    brush = Brush.verticalGradient(
                        listOf(activeColor.copy(alpha = 0.30f), activeColor.copy(alpha = 0.52f)),
                        startY = centerY - liveAmp * 1.5f,
                        endY = centerY
                    )
                )

                // Luminous crest.
                wave(path3, 78.dp.toPx(), liveAmp, p3, fill = false)
                drawPath(
                    path = path3,
                    brush = Brush.horizontalGradient(
                        listOf(activeColor.copy(alpha = 0.85f), activeColor)
                    ),
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Thumb: only while scrubbing, so the resting bar stays a pure waveform.
            if (isDragging > 0f) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.22f),
                    radius = 13.dp.toPx(),
                    center = Offset(thumbX, centerY)
                )
                drawCircle(
                    color = activeColor,
                    radius = 7.dp.toPx(),
                    center = Offset(thumbX, centerY)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.92f),
                    radius = 2.6.dp.toPx(),
                    center = Offset(thumbX, centerY)
                )
            } else {
                // A small travelling dot marks the playhead.
                drawCircle(
                    color = activeColor,
                    radius = 3.dp.toPx(),
                    center = Offset(thumbX, centerY - liveAmp * 0.3f)
                )
            }
        }
    }
}
