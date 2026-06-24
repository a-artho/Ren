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

const val RenMotionDurationMillis = 250
val RenMotionEasing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

@Composable
fun isReducedMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}

fun renScreenTransform(forward: Boolean, reducedMotion: Boolean): ContentTransform {
    val fadeIn = fadeIn(tween(RenMotionDurationMillis, easing = RenMotionEasing))
    val fadeOut = fadeOut(tween(RenMotionDurationMillis, easing = RenMotionEasing))
    if (reducedMotion) return fadeIn togetherWith fadeOut
    val direction = if (forward) 1 else -1
    return (fadeIn + slideInHorizontally(
        animationSpec = tween(RenMotionDurationMillis, easing = RenMotionEasing),
        initialOffsetX = { direction * minOf(it / 24, 48) },
    )) togetherWith (fadeOut + slideOutHorizontally(
        animationSpec = tween(RenMotionDurationMillis, easing = RenMotionEasing),
        targetOffsetX = { -direction * minOf(it / 24, 48) },
    ))
}
