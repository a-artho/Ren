package com.hci.ren.feature.plangeneration

import kotlin.math.ceil
import kotlin.math.roundToInt

data class StudyWorkload(
    val robustMinutes: Int,
    val cognitivePoints: Int,
)

fun GeneratedStudyBlock.localWorkload(): StudyWorkload {
    val likely = effortLikelyMinutes.coerceAtLeast(durationMinutes).coerceAtLeast(1)
    val maxMinutes = effortMaxMinutes.coerceAtLeast(likely)
    val confidenceBuffer = when (estimateConfidence) {
        EstimateConfidence.High -> 1.05
        EstimateConfidence.Medium -> 1.12
        EstimateConfidence.Low -> 1.25
    }
    val robust = ceil(likely * confidenceBuffer)
        .toInt()
        .coerceAtLeast(likely)
        .coerceAtMost(maxMinutes)

    val difficulty = normalizedScore(difficultyScore ?: difficulty.defaultScore)
    val density = normalizedScore(densityScore ?: taskType.defaultDensityScore)
    val production = normalizedScore(productionDemandScore ?: taskType.defaultProductionDemandScore)
    val intensity = (taskType.baseIntensity + 0.20 * difficulty + 0.15 * density + 0.20 * production)
        .coerceIn(0.65, 1.55)
    val cognitive = (robust * intensity).roundToInt().coerceAtLeast(1)
    return StudyWorkload(robust, cognitive)
}

fun GeneratedStudyBlock.effectiveRobustMinutes(): Int =
    localWorkload().robustMinutes

fun GeneratedStudyBlock.effectiveCognitivePoints(): Int =
    localWorkload().cognitivePoints

private fun normalizedScore(value: Int): Double = (value.coerceIn(1, 5) - 1) / 4.0

private val StudyBlockDifficulty.defaultScore: Int
    get() = when (this) {
        StudyBlockDifficulty.Light -> 2
        StudyBlockDifficulty.Standard -> 3
        StudyBlockDifficulty.Heavy -> 4
    }

private val StudyTaskType.defaultDensityScore: Int
    get() = when (this) {
        StudyTaskType.Concept -> 3
        StudyTaskType.Practice -> 3
        StudyTaskType.Review -> 2
        StudyTaskType.MockTest -> 4
        StudyTaskType.Memorization -> 3
        StudyTaskType.Reading -> 2
        StudyTaskType.Summary -> 3
        StudyTaskType.MistakeReview -> 3
        StudyTaskType.Custom -> 3
        StudyTaskType.Quiz -> 3
    }

private val StudyTaskType.defaultProductionDemandScore: Int
    get() = when (this) {
        StudyTaskType.Concept -> 3
        StudyTaskType.Practice -> 4
        StudyTaskType.Review -> 2
        StudyTaskType.MockTest -> 5
        StudyTaskType.Memorization -> 3
        StudyTaskType.Reading -> 2
        StudyTaskType.Summary -> 3
        StudyTaskType.MistakeReview -> 3
        StudyTaskType.Custom -> 3
        StudyTaskType.Quiz -> 4
    }

private val StudyTaskType.baseIntensity: Double
    get() = when (this) {
        StudyTaskType.Reading -> 0.75
        StudyTaskType.Review -> 0.75
        StudyTaskType.Memorization -> 0.85
        StudyTaskType.Summary -> 0.90
        StudyTaskType.Concept -> 0.95
        StudyTaskType.MistakeReview -> 0.95
        StudyTaskType.Custom -> 0.95
        StudyTaskType.Quiz -> 1.05
        StudyTaskType.Practice -> 1.15
        StudyTaskType.MockTest -> 1.20
    }
