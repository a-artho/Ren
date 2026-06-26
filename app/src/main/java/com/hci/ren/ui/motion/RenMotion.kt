package com.hci.ren.ui.motion

import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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
const val RenFadeThroughDurationMillis = 300
const val RenScreenTransitionDurationMillis = 420

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

fun renFadeThroughTransform(reducedMotion: Boolean): ContentTransform {
    val fadeIn = fadeIn(
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 240,
            easing = RenEmphasizedDecelerateEasing,
        ),
    )
    val fadeOut = fadeOut(
        animationSpec = tween(
            durationMillis = 0,
            easing = RenEmphasizedAccelerateEasing,
        ),
    )
    val enter = if (reducedMotion) {
        fadeIn
    } else {
        fadeIn + scaleIn(
            animationSpec = tween(
                durationMillis = RenFadeThroughDurationMillis,
                easing = RenEmphasizedDecelerateEasing,
            ),
            initialScale = 0.96f,
        )
    }
    return (enter togetherWith fadeOut).apply {
        targetContentZIndex = 1f
    }
}

fun renTabSwitchTransform(reducedMotion: Boolean): ContentTransform {
    val fadeIn = fadeIn(
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 120,
            easing = RenEmphasizedDecelerateEasing,
        ),
    )
    val fadeOut = fadeOut(
        animationSpec = tween(
            durationMillis = 0,
            easing = RenEmphasizedAccelerateEasing,
        ),
    )
    return (fadeIn togetherWith fadeOut).apply {
        targetContentZIndex = 1f
    }
}

fun renScreenTransform(forward: Boolean, reducedMotion: Boolean): ContentTransform {
    val enterDuration = RenScreenTransitionDurationMillis
    val exitDuration = 280
    val fadeIn = fadeIn(tween(enterDuration, easing = RenEmphasizedDecelerateEasing))
    val fadeOut = fadeOut(tween(exitDuration, easing = RenEmphasizedAccelerateEasing))
    if (reducedMotion) {
        return (fadeIn togetherWith fadeOut).apply {
            targetContentZIndex = 1f
        }
    }
    val direction = if (forward) 1 else -1
    return ((fadeIn + slideInHorizontally(
        animationSpec = tween(enterDuration, easing = RenEmphasizedDecelerateEasing),
        initialOffsetX = { direction * minOf(it / 18, 36) },
    )) togetherWith (fadeOut + slideOutHorizontally(
        animationSpec = tween(exitDuration, easing = RenEmphasizedAccelerateEasing),
        targetOffsetX = { -direction * minOf(it / 26, 28) },
    ))).apply {
        targetContentZIndex = 1f
    }
}

// endregion
