package com.hci.ren.feature.plangeneration

import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

internal data class PlanGenerationBreathValues(
    val scale: Float,
    val auraAlphaMultiplier: Float,
    val coreAlphaMultiplier: Float,
)

internal fun planGenerationBreathMotion(phase: Float): PlanGenerationBreathValues {
    val wave = breathWave(phase)
    return PlanGenerationBreathValues(
        scale = 1f + wave * 0.065f,
        auraAlphaMultiplier = 0.9f - wave * 0.08f,
        coreAlphaMultiplier = 0.97f + wave * 0.05f,
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

private fun breathWave(phase: Float): Float {
    return sin(phase.coerceIn(0f, 1f) * TWO_PI).toFloat()
}

private const val TWO_PI = 6.2831855f
