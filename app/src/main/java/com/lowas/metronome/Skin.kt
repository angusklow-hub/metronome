package com.lowas.metronome

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Everything that differs between the three interface styles: corner shapes, spacing, how titles
 * are laid out, and how animations move. Read it with [LocalLook] from any composable.
 */
class Look(val skin: Skin) {
    val m3 = skin == Skin.M3
    val oneUi = skin == Skin.ONEUI
    val ios = skin == Skin.IOS

    /** Radius of the big tempo panel. */
    val stage: Dp = when (skin) { Skin.M3 -> 40.dp; Skin.ONEUI -> 30.dp; Skin.IOS -> 26.dp }
    /** Radius of the ordinary cards. */
    val card: Dp = when (skin) { Skin.M3 -> 28.dp; Skin.ONEUI -> 26.dp; Skin.IOS -> 20.dp }
    val cardPad: Dp = when (skin) { Skin.M3 -> 20.dp; Skin.ONEUI -> 22.dp; Skin.IOS -> 16.dp }
    val gap: Dp = when (skin) { Skin.M3 -> 14.dp; Skin.ONEUI -> 12.dp; Skin.IOS -> 10.dp }
    /** Radius of buttons and chips; One UI and iOS both favour full capsules. */
    val chip: Dp = when (skin) { Skin.M3 -> 10.dp; Skin.ONEUI -> 22.dp; Skin.IOS -> 18.dp }
    /** Corner radius of the beat bars. */
    val bar: Dp = when (skin) { Skin.M3 -> 8.dp; Skin.ONEUI -> 50.dp; Skin.IOS -> 50.dp }
    val button: Dp = when (skin) { Skin.M3 -> 20.dp; Skin.ONEUI -> 28.dp; Skin.IOS -> 16.dp }
    /** Play button: a rounded square on Material, a circle on One UI, a squircle on iOS. */
    val playRadiusFraction: Float = when (skin) { Skin.M3 -> 0.3f; Skin.ONEUI -> 0.5f; Skin.IOS -> 0.34f }
    /** One UI puts a large title at the top left; iOS uses a small centred one. */
    val bigTitle = skin == Skin.ONEUI
    /** iOS draws a hairline under the title bar. */
    val hairline = skin == Skin.IOS
    val navLabels = skin != Skin.IOS
    /** iOS is built around translucency, so the glass look is always on there. */
    val forceGlass = skin == Skin.IOS
    /** Material You bounces; One UI eases; iOS settles quickly with little overshoot. */
    val bouncy = skin == Skin.M3

    fun <T> motion(): AnimationSpec<T> = when (skin) {
        Skin.M3 -> spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
        Skin.ONEUI -> tween(300, easing = FastOutSlowInEasing)
        Skin.IOS -> spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
    }

    /** How a beat swell springs back. */
    fun <T> beat(): AnimationSpec<T> = when (skin) {
        Skin.M3 -> spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMedium)
        Skin.ONEUI -> spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
        Skin.IOS -> spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessHigh)
    }

    /** How far a beat pushes the display out. */
    val swell: Float = when (skin) { Skin.M3 -> 0.30f; Skin.ONEUI -> 0.20f; Skin.IOS -> 0.14f }

    val shapes: Shapes = Shapes(
        extraSmall = RoundedCornerShape(chip * 0.5f),
        small = RoundedCornerShape(chip * 0.8f),
        medium = RoundedCornerShape(card * 0.6f),
        large = RoundedCornerShape(card * 0.8f),
        extraLarge = RoundedCornerShape(card),
    )
}

fun lookOf(skin: Int) = Look(Skin.values().getOrElse(skin) { Skin.M3 })

val LocalLook = staticCompositionLocalOf { Look(Skin.M3) }

/**
 * Type scale per style: Material 3 as shipped, One UI a little larger and lighter, iOS tighter
 * with heavier titles.
 */
fun typographyFor(skin: Skin): Typography {
    val base = Typography()
    return when (skin) {
        Skin.M3 -> base
        Skin.ONEUI -> base.copy(
            displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light, fontSize = 82.sp),
            displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Light),
            headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(fontSize = 17.sp),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
        )
        Skin.IOS -> base.copy(
            displayLarge = TextStyle(
                fontSize = 76.sp, fontWeight = FontWeight.Medium, letterSpacing = (-2.5).sp,
                fontFeatureSettings = "tnum"
            ),
            displayMedium = TextStyle(
                fontSize = 44.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1.5).sp,
                fontFeatureSettings = "tnum"
            ),
            headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, letterSpacing = (-0.2).sp),
            bodySmall = base.bodySmall.copy(fontSize = 13.sp),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/** Shrinks slightly while held, with the current style's physics. */
@Composable
fun Modifier.pressSpring(source: MutableInteractionSource, amount: Float = 0.94f): Modifier {
    val look = LocalLook.current
    val pressed by source.collectIsPressedAsState()
    val sc = androidx.compose.animation.core.animateFloatAsState(
        if (pressed) amount else 1f,
        animationSpec = if (look.bouncy) {
            spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessHigh)
        },
        label = "press"
    )
    return this.scale(sc.value)
}

/** The app's rounded shape for the current style. */
@Composable
fun cardShape(): RoundedCornerShape = RoundedCornerShape(LocalLook.current.card)

@Composable
fun appShapes(): Shapes = MaterialTheme.shapes
