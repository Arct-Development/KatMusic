package com.itzfallenme.katmusic.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Seek bar whose progress line gently undulates while music plays, and grows into
 * a scrubber thumb while dragging.
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
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
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
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val centerY = size.height / 2f
            val shownProgress = if (isDragging > 0f) dragging else progress.coerceIn(0f, 1f)
            val directionStep = size.width / 120f

            fun wavePath(untilFraction: Float, wavesEnabled: Boolean): Path {
                val path = Path()
                val endX = size.width * untilFraction
                path.moveTo(0f, centerY)
                var x = 0f
                while (x < endX) {
                    val wave = if (wavesEnabled && playing) {
                        sin((x / directionStep) * 0.55f + phase) * amplitude
                    } else 0f
                    path.lineTo(x, centerY + wave)
                    x += 2f
                }
                path.lineTo(endX, centerY)
                return path
            }

            drawPath(
                path = wavePath(1f, false),
                color = inactiveColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = wavePath(shownProgress, true),
                brush = Brush.horizontalGradient(
                    listOf(activeColor.copy(alpha = 0.75f), activeColor)
                ),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )
            if (isDragging > 0f) {
                drawCircle(
                    color = activeColor,
                    radius = 7.dp.toPx(),
                    center = Offset(size.width * shownProgress, centerY)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = 2.5.dp.toPx(),
                    center = Offset(size.width * shownProgress, centerY)
                )
            }
        }
    }
}
