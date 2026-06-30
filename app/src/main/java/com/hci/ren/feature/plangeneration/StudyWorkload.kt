package com.hci.ren.feature.plangeneration

import kotlin.math.ceil
import kotlin.math.roundToInt

data class StudyWorkload(
    val minMinutes: Int,
    val likelyMinutes: Int,
    val maxMinutes: Int,
    val reservedMinutes: Int,
    val cognitivePoints: Int,
)

object WorkloadEngine {
    fun estimate(block: GeneratedStudyBlock): StudyWorkload {
        val minMinutes = block.effortMinMinutes.coerceAtLeast(1)
        val likely = block.effortLikelyMinutes.coerceAtLeast(minMinutes)
        val maxMinutes = block.effortMaxMinutes.coerceAtLeast(likely)
        val riskWeight = when (block.estimateConfidence) {
            EstimateConfidence.High -> 0.25
            EstimateConfidence.Medium -> 0.50
            EstimateConfidence.Low -> 0.75
        }
        val reserved = ceil(likely + riskWeight * (maxMinutes - likely))
            .toInt()
            .coerceIn(likely, maxMinutes)

        val difficulty = normalizedScore(block.difficultyScore ?: 3)
        val density = normalizedScore(block.densityScore ?: block.taskType.defaultDensityScore)
        val production = normalizedScore(block.productionDemandScore ?: block.taskType.defaultProductionDemandScore)
        val intensity = (block.taskType.baseIntensity + 0.20 * difficulty + 0.15 * density + 0.20 * production)
            .coerceIn(0.65, 1.55)
        val cognitive = (reserved * intensity).roundToInt().coerceAtLeast(1)
        return StudyWorkload(minMinutes, likely, maxMinutes, reserved, cognitive)
    }
}

fun GeneratedStudyBlock.localWorkload(): StudyWorkload = WorkloadEngine.estimate(this)

val GeneratedStudyBlock.likelyStudyMinutes: Int
    get() = localWorkload().likelyMinutes

val GeneratedStudyBlock.reservedStudyMinutes: Int
    get() = localWorkload().reservedMinutes

fun GeneratedStudyBlock.effectiveReservedMinutes(): Int =
    localWorkload().reservedMinutes

fun GeneratedStudyBlock.effectiveCognitivePoints(): Int =
    localWorkload().cognitivePoints

private fun normalizedScore(value: Int): Double = (value.coerceIn(1, 5) - 1) / 4.0

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
