package com.hci.ren.ui.motion

import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset

// region — MD3 Easing Tokens

/** MD3 Emphasized easing — for elements that stay on screen. */
val RenEmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** MD3 Emphasized Decelerate — for elements entering the screen. */
val RenEmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** MD3 Emphasized Accelerate — for elements exiting the screen. */
val RenEmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Backward-compatible alias for the original custom easing. */
val RenMotionEasing = RenEmphasizedEasing

// endregion

// region — MD3 Duration Tokens

const val RenMotionDurationMillis = 250

// endregion

// region — Reusable Animation Specs

/** Content transition spec: fade in/out with MD3 emphasized easing. */
fun <T> renContentSpec() = tween<T>(RenMotionDurationMillis, easing = RenEmphasizedEasing)

/** Enter transition spec: fade/slide in with decelerate easing. */
fun <T> renEnterSpec() = tween<T>(RenMotionDurationMillis, easing = RenEmphasizedDecelerateEasing)

/** Exit transition spec: fade/slide out with accelerate easing. */
fun <T> renExitSpec() = tween<T>(RenMotionDurationMillis, easing = RenEmphasizedAccelerateEasing)

// endregion

// region — Reduced Motion

@Composable
fun isReducedMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}

// endregion

// region — Screen Transform

fun renScreenTransform(forward: Boolean, reducedMotion: Boolean): ContentTransform {
    val enterDuration = 400
    val exitDuration = 200
    val enterDelay = 100
    val fadeInSpec = tween<Float>(enterDuration, delayMillis = enterDelay, easing = RenEmphasizedDecelerateEasing)
    val slideInSpec = tween<IntOffset>(enterDuration, delayMillis = enterDelay, easing = RenEmphasizedDecelerateEasing)
    val fadeOutSpec = tween<Float>(exitDuration, easing = RenEmphasizedAccelerateEasing)
    val slideOutSpec = tween<IntOffset>(exitDuration, easing = RenEmphasizedAccelerateEasing)
    val reducedFadeInSpec = tween<Float>(enterDuration, easing = RenEmphasizedDecelerateEasing)
    if (reducedMotion) {
        return fadeIn(reducedFadeInSpec) togetherWith fadeOut(fadeOutSpec)
    }
    val direction = if (forward) 1 else -1
    return (fadeIn(fadeInSpec) + slideInHorizontally(
        animationSpec = slideInSpec,
        initialOffsetX = { direction * minOf(it / 24, 48) },
    )) togetherWith (fadeOut(fadeOutSpec) + slideOutHorizontally(
        animationSpec = slideOutSpec,
        targetOffsetX = { -direction * minOf(it / 24, 48) },
    ))
}

// endregion
