package com.hci.ren.feature.plangeneration

import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

internal data class WaveMotionProfile(
    val waveReach: Float,
    val drift: Float,
    val scaleXBase: Float,
    val scaleYBase: Float,
)

internal data class WaveMotionValues(
    val progress: Float,
    val scaleX: Float,
    val scaleY: Float,
    val alphaMultiplier: Float,
    val speedTaming: Float,
)

internal fun planGenerationWaveMotion(
    profile: WaveMotionProfile,
    index: Int,
    progress: Float,
): WaveMotionValues {
    val rawScaleX = profile.scaleXBase + (index % 4) * 0.11f
    val rawScaleY = profile.scaleYBase + ((index + 2) % 4) * 0.09f
    val speedTaming = fastWaveTaming(profile, index, rawScaleX, rawScaleY)
    return WaveMotionValues(
        progress = easedFastWaveProgress(progress, speedTaming),
        scaleX = softenedFastWaveScale(profile.scaleXBase, rawScaleX, speedTaming),
        scaleY = softenedFastWaveScale(profile.scaleYBase, rawScaleY, speedTaming),
        alphaMultiplier = fastWaveAlpha(progress, speedTaming),
        speedTaming = speedTaming,
    )
}

internal fun planGenerationVisualStepMinimumDuration(step: PlanStatus) = when (step) {
    PlanStatus.Uploading -> 2.seconds
    PlanStatus.Analyzing -> 3.seconds
    PlanStatus.IdentifyingTopics -> 2.seconds
    PlanStatus.CreatingBlocks -> 4.seconds
    PlanStatus.Finalizing -> 2.seconds
    PlanStatus.Completed,
    PlanStatus.Failed,
    -> 2.seconds
}

private fun fastWaveTaming(
    profile: WaveMotionProfile,
    index: Int,
    scaleX: Float,
    scaleY: Float,
): Float {
    val baseScaleX = profile.scaleXBase
    val baseScaleY = profile.scaleYBase + 0.18f
    val baseSpeed = visualWaveSpeed(profile.waveReach, baseScaleX, baseScaleY, profile.drift)
    val waveSpeed = visualWaveSpeed(
        waveReach = profile.waveReach,
        scaleX = scaleX,
        scaleY = scaleY,
        drift = profile.drift + index * 0.004f,
    )
    return ((waveSpeed / baseSpeed) - 1.14f).coerceIn(0f, 0.34f) / 0.34f
}

private fun visualWaveSpeed(
    waveReach: Float,
    scaleX: Float,
    scaleY: Float,
    drift: Float,
): Float = waveReach * max(scaleX, scaleY) + drift * 0.35f

private fun easedFastWaveProgress(progress: Float, speedTaming: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    val eased = t * t * (3f - 2f * t)
    return t + (eased - t) * speedTaming
}

private fun softenedFastWaveScale(
    baseScale: Float,
    rawScale: Float,
    speedTaming: Float,
): Float = baseScale + (rawScale - baseScale) * (1f - speedTaming * 0.42f)

private fun fastWaveAlpha(progress: Float, speedTaming: Float): Float {
    val baseFade = (1f - progress) * (1f - progress)
    val fadeIn = (progress / 0.18f).coerceIn(0f, 1f)
    return baseFade * (1f - speedTaming * (1f - fadeIn) * 0.36f)
}
