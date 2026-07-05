package com.hci.ren.feature.plangeneration

import kotlin.math.max
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

internal data class WaveMotionProfile(
    val waveReach: Float,
    val scaleXBase: Float,
    val scaleYBase: Float,
)

internal data class PlanGenerationBreathValues(
    val scale: Float,
    val auraAlphaMultiplier: Float,
    val coreAlphaMultiplier: Float,
)

internal data class WaveMotionValues(
    val progress: Float,
    val scaleX: Float,
    val scaleY: Float,
    val alphaMultiplier: Float,
    val speedTaming: Float,
)

internal fun planGenerationBreathMotion(phase: Float): PlanGenerationBreathValues {
    val wave = breathWave(phase)
    return PlanGenerationBreathValues(
        scale = 1f + wave * 0.065f,
        auraAlphaMultiplier = 0.9f - wave * 0.08f,
        coreAlphaMultiplier = 0.97f + wave * 0.05f,
    )
}

internal fun planGenerationRippleProgress(
    breathProgress: Float,
    index: Int,
    waveCount: Int,
): Float {
    val baseProgress = (index + 1f) / (waveCount + 1f)
    val centeredBreath = breathWave(breathProgress)
    val lastIndex = (waveCount - 1).coerceAtLeast(1).toFloat()
    val centeredIndex = index - lastIndex / 2f
    val direction = centeredIndex / (lastIndex / 2f).coerceAtLeast(1f)
    return (baseProgress + centeredBreath * direction * 0.045f).coerceIn(0.12f, 0.88f)
}

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
        scaleX = softenedFastWaveScale(profile.scaleXBase, rawScaleX, speedTaming).coerceAtMost(0.9f),
        scaleY = softenedFastWaveScale(profile.scaleYBase, rawScaleY, speedTaming).coerceAtMost(0.58f),
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
    val baseSpeed = visualWaveSpeed(profile.waveReach, baseScaleX, baseScaleY)
    val waveSpeed = visualWaveSpeed(
        waveReach = profile.waveReach,
        scaleX = scaleX,
        scaleY = scaleY,
    )
    return ((waveSpeed / baseSpeed) - 1.14f).coerceIn(0f, 0.34f) / 0.34f
}

private fun visualWaveSpeed(
    waveReach: Float,
    scaleX: Float,
    scaleY: Float,
): Float = waveReach * max(scaleX, scaleY)

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

private fun breathWave(phase: Float): Float {
    return sin(phase.coerceIn(0f, 1f) * TWO_PI).toFloat()
}

private fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)

private const val TWO_PI = 6.2831855f
