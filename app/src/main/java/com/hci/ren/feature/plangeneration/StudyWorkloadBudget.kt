package com.hci.ren.feature.plangeneration

internal const val RealisticWorkloadRatio = 0.90
internal const val IntensiveWorkloadRatio = 1.15

internal fun requiredStudyMinutes(
    tasks: List<GeneratedStudyBlock>,
    includeCompleted: Boolean = false,
): Int {
    return tasks.asSequence()
        .filter { it.status !in setOf(StudyTaskStatus.ExcludedByUser, StudyTaskStatus.DeferredByUser) }
        .filter { includeCompleted || it.status != StudyTaskStatus.Completed }
        .sumOf { it.requiredBlockMinutes() }
}

internal fun GeneratedStudyBlock.requiredBlockMinutes(): Int =
    maxOf(effortLikelyMinutes, effectiveReservedMinutes(), 1)
