package com.itzfallenme.katmusic.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** app.css: --radius: 20px; --radius-sm: 12px; --radius-xs: 8px */
val KatMusicShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Beyond Material3's standard 5-step Shapes() scale — for hero-style
 *  containers (the Home stats card) that should read as a single, large,
 *  continuous surface rather than a boxy web-style card. */
val ExpressiveHeroShape = RoundedCornerShape(26.dp)
val ExpressivePillShape = RoundedCornerShape(percent = 50)
val HeroInnerShape = RoundedCornerShape(18.dp)
val StatPillShape = RoundedCornerShape(16.dp)
val ListContainerShape = RoundedCornerShape(20.dp)
val BadgePillShape = RoundedCornerShape(percent = 50)
val NowPlayingCardShape = RoundedCornerShape(18.dp)
val TrackRowShape = RoundedCornerShape(12.dp)
val ArtworkShape = RoundedCornerShape(12.dp)

/**
 * Material 3 Expressive 10-step corner scale primitives.
 */
object M3ExpressiveShape {
    val None = RoundedCornerShape(0.dp)
    val ExtraSmall = RoundedCornerShape(4.dp)
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(16.dp)
    val LargeIncreased = RoundedCornerShape(20.dp)
    val ExtraLarge = RoundedCornerShape(28.dp)
    val ExtraLargeIncreased = RoundedCornerShape(32.dp)
    val ExtraExtraLarge = RoundedCornerShape(48.dp)
    val Full = RoundedCornerShape(percent = 50)

    // Asymmetric shapes for connected components (e.g. ConnectedButtonGroup)
    val GroupLeft = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 4.dp, bottomEnd = 4.dp)
    val GroupMiddle = RoundedCornerShape(4.dp)
    val GroupRight = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp)
    val GroupSingle = RoundedCornerShape(20.dp)
}
