package com.itzfallenme.katmusic.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * A small rubber-band squeeze for content dragged past the end of a list, ported
 * from LastWave.
 *
 * The platform's own stretch effect must be turned off while this is used
 * ([WithoutPlatformOverscroll]), otherwise two overscroll systems react to the
 * same boundary and the list appears to move, stop, then move again. While the
 * finger is down the translation follows the drag synchronously; the spring only
 * takes over once the drag ends, so there is always exactly one source of truth.
 */
@Composable
fun Modifier.wobbleOverscroll(enabled: Boolean = true): Modifier {
    if (!enabled || Motion.reduced) return this
    var raw by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y != 0f) {
                    raw = (raw + available.y * 0.30f).coerceIn(-34f, 34f)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (raw != 0f) {
                    settle.snapTo(raw)
                    raw = 0f
                    settle.animateTo(
                        0f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                return Velocity.Zero
            }
        }
    }
    return this
        .nestedScroll(connection)
        .graphicsLayer {
            val offset = if (raw != 0f) raw else settle.value
            translationY = offset
            clip = offset != 0f
        }
}

/** Turns off Android's built-in stretch overscroll for the wrapped content. */
@Suppress("DEPRECATION")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WithoutPlatformOverscroll(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOverscrollConfiguration provides null, content = content)
}
