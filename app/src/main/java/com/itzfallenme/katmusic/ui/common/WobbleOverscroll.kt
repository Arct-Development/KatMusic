package com.itzfallenme.katmusic.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
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
 * Rubber-band "squeeze" for scrollable content dragged past its bounds, taken
 * from LastWave (Clash-Projects/LastWave-native, GPL-3.0 — same licence as this
 * app) where it was tuned across three real-device iterations.
 *
 * Pair it with [WithoutPlatformOverscroll]: the platform's own stretch effect
 * must be off, otherwise two overscroll systems fight at the boundary and it
 * reads as a phantom second movement after the scroll has settled.
 */
@Composable
fun Modifier.wobbleOverscroll(): Modifier {
    var raw by remember { mutableFloatStateOf(0f) }
    val settleAnim = remember { Animatable(0f) }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y != 0f) {
                    raw = (raw + available.y * 0.30f).coerceIn(-32f, 32f)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (raw != 0f) {
                    settleAnim.snapTo(raw)
                    raw = 0f
                    settleAnim.animateTo(
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
            // Exactly one source of truth for translationY: the live drag while
            // the finger is down, the spring afterwards.
            val offset = if (raw != 0f) raw else settleAnim.value
            translationY = offset
            clip = offset != 0f
        }
}

/** Turns off Android's built-in stretch overscroll for the wrapped content. */
@Suppress("DEPRECATION")
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WithoutPlatformOverscroll(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.LocalOverscrollConfiguration provides null,
        content = content
    )
}
