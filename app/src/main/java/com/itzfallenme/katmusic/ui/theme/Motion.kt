package com.itzfallenme.katmusic.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * One motion language for the whole app: springy spatial movement, non-bouncy fades.
 * Screens, docks, controls and list rows all pull their specs from here so the
 * rhythm stays consistent.
 */
object Motion {
    const val Quick = 160
    const val Standard = 300
    const val Emphasized = 440

    fun <T> spatialFast() = spring<T>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
    fun <T> spatialDefault() = spring<T>(dampingRatio = 0.8f, stiffness = 650f)
    fun <T> spatialSlow() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    fun <T> effectsFast() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> effectsDefault() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 700f
    )

    fun forwardEnter(): EnterTransition =
        fadeIn(effectsDefault()) +
            slideInHorizontally(spatialDefault()) { it / 10 } +
            scaleIn(spatialFast(), initialScale = 0.97f)

    fun forwardExit(): ExitTransition =
        fadeOut(effectsFast()) + scaleOut(effectsDefault(), targetScale = 0.985f)

    fun backEnter(): EnterTransition =
        fadeIn(effectsDefault()) +
            slideInHorizontally(spatialDefault()) { -it / 12 } +
            scaleIn(spatialFast(), initialScale = 0.97f)

    fun backExit(): ExitTransition =
        fadeOut(effectsFast()) +
            slideOutHorizontally(spatialDefault()) { it / 12 } +
            scaleOut(effectsDefault(), targetScale = 0.985f)
}

/** Springy press-down scale for cards, rows and buttons. */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.96f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) targetScale else 1f,
        animationSpec = Motion.spatialFast(),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Clickable with bounce feedback and a ripple, the default for every tappable row. */
@Composable
fun Modifier.bounceClickable(
    enabled: Boolean = true,
    targetScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .pressScale(interactionSource, targetScale)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClick = onClick
        )
}
