package com.itzfallenme.katmusic.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.sign

/**
 * Master switch for the iOS-style translucent materials. Settings → Appearance
 * exposes it; every glass surface falls back to an opaque tonal fill when it is
 * off, or on a device that cannot render a backdrop.
 */
val LocalLiquidGlass = staticCompositionLocalOf { true }

/** Background source that glass surfaces inside the captured content refract. */
val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Factory matching kyant0 backdrop's API. The Compose compiler forbids try/catch
 * around composable calls, so this stays a direct passthrough; first-frame safety
 * comes from [isDeviceGlassCapable] at every call site.
 */
@Composable
fun rememberLayerBackdrop(
    onDraw: androidx.compose.ui.graphics.drawscope.ContentDrawScope.() -> Unit = { drawContent() }
): LayerBackdrop? = rememberLayerBackdrop(onDraw = onDraw)

/** Marks a composable as the source layer that sibling glass surfaces refract. */
fun Modifier.layerBackdropCompat(backdrop: LayerBackdrop): Modifier = this.layerBackdrop(backdrop)

/** Remember a backdrop that paints a flat colour behind the captured content. */
@Composable
fun rememberBackdrop(color: Color): LayerBackdrop? =
    rememberLayerBackdrop {
        drawRect(color)
        drawContent()
    }

@Composable
fun isDeviceGlassCapable(): Boolean {
    val view = LocalView.current
    if (view.isInEditMode) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    if (!view.isHardwareAccelerated) return false
    val context = LocalContext.current
    val am = remember(context) {
        runCatching { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }.getOrNull()
    }
    if (am?.isLowRamDevice == true) return false
    return true
}

@Composable
fun isLiquidGlassBackdropSupported(): Boolean = LocalLiquidGlass.current && isDeviceGlassCapable()

@Composable
fun isLiquidGlassEnabled(): Boolean = LocalLiquidGlass.current

@Composable
fun Modifier.liquidGlassSource(backdrop: LayerBackdrop?): Modifier {
    if (backdrop == null) return this
    if (!isLiquidGlassBackdropSupported()) return this
    return this.layerBackdrop(backdrop)
}

/** Press/hold state for a single glass surface: observe-only, never consumes taps. */
class GlassInteraction(private val animationScope: CoroutineScope) {
    private val pressSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
    private val pressAnimation = Animatable(0f, 0.001f)

    /** 0f at rest, animating to 1f while pressed. Read inside draw/effect blocks. */
    val pressProgress: Float get() = pressAnimation.value

    /** Local-space touch point used as the centre of the press glow. */
    var touchPosition by mutableStateOf(Offset.Zero)
        private set

    suspend fun detectPress(pointer: PointerInputScope) = with(pointer) {
        inspectDragGestures(
            onDragStart = { down ->
                touchPosition = down.position
                animationScope.launch { pressAnimation.animateTo(1f, pressSpec) }
            },
            onDragEnd = { animationScope.launch { pressAnimation.animateTo(0f, pressSpec) } },
            onDragCancel = { animationScope.launch { pressAnimation.animateTo(0f, pressSpec) } }
        ) { change, _ ->
            touchPosition = change.position
        }
    }
}

@Composable
fun rememberGlassInteraction(): GlassInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassInteraction(scope) }
}

/**
 * Single source of truth for every glass surface's frost level, so the nav bar,
 * dock, mini player and player controls cannot drift apart.
 */
private object GlassRecipe {
    const val BLUR_BASE_DP = 12f
    const val BLUR_BRIGHT_DP = 20f
    const val BLUR_DARK_DP = 6f

    const val BRIGHTNESS = 0.05f
    const val CONTRAST = 1f
    const val SATURATION = 1.1f

    const val LENS_DIVISOR = 6f

    const val MIN_SCRIM = 0.18f
    const val MAX_SCRIM = 0.56f

    const val PRESS_BLUR_DP = 2f
}

/**
 * Interactive liquid glass drawing modifier.
 * The element must be a sibling of the backdrop source, never inside it.
 */
fun Modifier.drawInteractiveGlass(
    isDark: Boolean,
    backdrop: Backdrop,
    layer: GraphicsLayer? = null,
    luminanceAnimation: Float,
    shape: Shape,
    interaction: GlassInteraction?,
    pressedScale: Float = 1.12f,
    highlight: Highlight = Highlight.Default,
    blurScale: Float = 1f,
    minScrim: Float = GlassRecipe.MIN_SCRIM,
    maxScrim: Float = GlassRecipe.MAX_SCRIM
): Modifier = this
    .drawBackdrop(
        backdrop = backdrop,
        shape = { lensSafeGlassShape(shape) },
        highlight = { highlight },
        effects = {
            val l = (luminanceAnimation * 2f - 1f).let { sign(it) * it * it }
            val press = interaction?.pressProgress ?: 0f
            colorControls(
                brightness = GlassRecipe.BRIGHTNESS,
                contrast = GlassRecipe.CONTRAST,
                saturation = GlassRecipe.SATURATION
            )
            blur(
                (
                    if (l > 0f) {
                        lerp(GlassRecipe.BLUR_BASE_DP.dp.toPx(), GlassRecipe.BLUR_BRIGHT_DP.dp.toPx(), l)
                    } else {
                        lerp(GlassRecipe.BLUR_BASE_DP.dp.toPx(), GlassRecipe.BLUR_DARK_DP.dp.toPx(), -l)
                    }
                ) * blurScale + GlassRecipe.PRESS_BLUR_DP.dp.toPx() * press
            )
            lens(
                size.minDimension / GlassRecipe.LENS_DIVISOR + GlassRecipe.PRESS_BLUR_DP.dp.toPx() * press,
                size.minDimension / 2f,
                false
            )
        },
        onDrawBackdrop = { drawBackdrop ->
            drawBackdrop()
        },
        onDrawSurface = {
            val darken = lerp(minScrim, maxScrim, ((luminanceAnimation - 0.3f) / 0.5f).coerceIn(0f, 1f))
            drawRect((if (isDark) Color.Black else Color.White).copy(alpha = darken))
            val press = interaction?.pressProgress ?: 0f
            if (press > 0f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.18f * press), Color.Transparent),
                        center = interaction?.touchPosition ?: Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension * 1.2f
                    ),
                    blendMode = BlendMode.Plus
                )
            }
        },
        layerBlock = if (interaction != null) {
            {
                val scale = lerp(1f, pressedScale, interaction.pressProgress)
                scaleX = scale
                scaleY = scale
            }
        } else {
            null
        }
    ).then(
        if (interaction != null) {
            Modifier.pointerInput(interaction) { interaction.detectPress(this) }
        } else {
            Modifier
        }
    )

/**
 * kyant's lens effect only accepts corner-based shapes and throws for a plain
 * [RectangleShape], which would take the whole render pass down. A zero-radius
 * rounded rectangle is visually identical and valid lens input.
 */
internal fun lensSafeGlassShape(shape: Shape): Shape =
    if (shape == RectangleShape) RoundedCornerShape(0.dp) else shape

/** Opaque fallback used whenever glass is off or unsupported. */
@Composable
private fun Modifier.opaqueGlassFallback(shape: Shape): Modifier = this
    .clip(shape)
    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))

/** Static glass surface: fixed mid luminance, no scrolling-content sampling. */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    highlight: Highlight = Highlight.Default,
    forceDark: Boolean = false
): Modifier {
    if (!isLiquidGlassBackdropSupported()) return opaqueGlassFallback(shape)
    val isDark = forceDark || LocalIsDarkTheme.current
    val layer = rememberGraphicsLayer()
    val interaction = rememberGlassInteraction()
    return this.drawInteractiveGlass(
        isDark = isDark,
        backdrop = backdrop,
        layer = layer,
        luminanceAnimation = 0.5f,
        shape = shape,
        interaction = if (interactive) interaction else null,
        highlight = highlight
    )
}

/** Glass surface that samples the luminance of what scrolls beneath it. */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    luminanceAnimation: Float,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    blurScale: Float = 1f,
    minScrim: Float = GlassRecipe.MIN_SCRIM,
    maxScrim: Float = GlassRecipe.MAX_SCRIM,
    forceDark: Boolean = false
): Modifier {
    if (!isLiquidGlassBackdropSupported()) return opaqueGlassFallback(shape)
    val isDark = forceDark || LocalIsDarkTheme.current
    val interaction = rememberGlassInteraction()
    return this.drawInteractiveGlass(
        isDark = isDark,
        backdrop = backdrop,
        luminanceAnimation = luminanceAnimation,
        shape = shape,
        interaction = if (interactive) interaction else null,
        pressedScale = 1.04f,
        blurScale = blurScale,
        minScrim = minScrim,
        maxScrim = maxScrim
    )
}

@Composable
fun LiquidGlassContainer(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    highlight: Highlight = Highlight.Default,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    if (backdrop == null || !isLiquidGlassBackdropSupported()) {
        Box(
            modifier = modifier.opaqueGlassFallback(shape),
            contentAlignment = contentAlignment,
            content = content
        )
        return
    }
    Box(
        modifier = modifier.liquidGlass(backdrop, shape, interactive, highlight),
        contentAlignment = contentAlignment,
        content = content
    )
}

/** Floating pill hosting icon buttons in a glass shell. */
@Composable
fun LiquidGlassActionPill(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable RowScope.() -> Unit
) {
    if (backdrop == null || !isLiquidGlassBackdropSupported()) {
        Row(
            modifier = modifier.opaqueGlassFallback(shape),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
        return
    }
    Row(
        modifier = modifier.liquidGlass(backdrop, shape, true, Highlight.Default),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun LiquidGlassIconButton(
    backdrop: Backdrop?,
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    shape: Shape = CircleShape,
    tint: Color = Color.White,
    interactive: Boolean = true,
    highlight: Highlight = Highlight(width = 1.dp)
) {
    LiquidGlassContainer(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        interactive = interactive,
        highlight = highlight
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onClick)
        )
    }
}

/**
 * Observe-only drag/press recogniser: reports press and drag without consuming
 * events, so glass surfaces react while the buttons they wrap keep their taps.
 */
internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        onDrag(down, Offset.Zero)
        val upEvent = drag(
            pointerId = down.id,
            onDrag = { onDrag(it, it.positionChange()) }
        )
        if (upEvent == null) onDragCancel() else onDragEnd(upEvent)
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) return dragEvent else pointer = otherDown.id
        } else {
            if (dragEvent.previousPosition != dragEvent.position) return dragEvent
        }
    }
}
