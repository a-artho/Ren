package com.hci.ren.feature.studymap

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import kotlin.math.ceil
import kotlin.math.roundToInt

internal fun GeneratedStudyPlan.prepareForLocalScheduling(
    preferences: PlanSetupSubmission,
): GeneratedStudyPlan {
    val capacityMinutes = preferences.dailyStudyMinutes.coerceAtLeast(1)
    val splitGroups = blocks
        .sortedBy { it.order }
        .map { block ->
            SplitGroup(
                originalId = block.id,
                blocks = if (countsTowardRequiredTime(block)) block.splitForCapacity(capacityMinutes) else listOf(block),
            )
        }
    val dependencyTargetByOriginalId = splitGroups.associate { group ->
        group.originalId to group.blocks.last().id
    }
    var nextOrder = 1
    val normalizedBlocks = splitGroups.flatMap { group ->
        group.blocks.mapIndexed { index, block ->
            val dependencies = when {
                index > 0 -> listOf(group.blocks[index - 1].id)
                else -> block.dependencies.map { dependency ->
                    dependencyTargetByOriginalId[dependency] ?: dependency
                }.filterNot { it == block.id }.distinct()
            }
            block.copy(order = nextOrder++, dependencies = dependencies)
        }
    }
    return copy(
        blocks = normalizedBlocks,
        totalEstimatedMinutes = normalizedBlocks
            .filter(::countsTowardRequiredTime)
            .sumOf { it.durationMinutes },
    )
}

private data class SplitGroup(
    val originalId: String,
    val blocks: List<GeneratedStudyBlock>,
)

private fun GeneratedStudyBlock.splitForCapacity(capacityMinutes: Int): List<GeneratedStudyBlock> {
    val totalMinutes = durationMinutes.coerceAtLeast(1)
    val minimum = minimumUsefulMinutes.coerceAtLeast(1)
    if (!splitAllowed || totalMinutes <= capacityMinutes || capacityMinutes < minimum) {
        return listOf(this)
    }

    val partCount = ceil(totalMinutes.toDouble() / capacityMinutes)
        .toInt()
        .coerceAtLeast(2)
    val durations = balancedDurations(totalMinutes, partCount)
    if (durations.any { it < minimum || it > capacityMinutes }) {
        return listOf(this)
    }

    val continuity = continuityGroup?.takeIf { it.isNotBlank() } ?: id
    return durations.mapIndexed { index, partMinutes ->
        val partNumber = index + 1
        val partId = "${id}_part$partNumber"
        val ratio = partMinutes.toDouble() / totalMinutes
        val partMinimum = minimum.coerceAtMost(partMinutes)
        copy(
            id = partId,
            title = "$title ($partNumber/${durations.size})",
            durationMinutes = partMinutes,
            estimatedMinutes = partMinutes,
            minimumUsefulMinutes = partMinimum,
            effortMinMinutes = scaledEffort(effortMinMinutes, ratio).coerceIn(partMinimum, partMinutes),
            effortLikelyMinutes = partMinutes,
            effortMaxMinutes = scaledEffort(effortMaxMinutes, ratio).coerceAtLeast(partMinutes),
            splitAllowed = false,
            continuityGroup = continuity,
            scheduledDate = null,
        )
    }
}

private fun balancedDurations(totalMinutes: Int, partCount: Int): List<Int> {
    val base = totalMinutes / partCount
    val remainder = totalMinutes % partCount
    return List(partCount) { index -> base + if (index < remainder) 1 else 0 }
}

private fun scaledEffort(minutes: Int, ratio: Double): Int =
    (minutes.coerceAtLeast(1) * ratio).roundToInt().coerceAtLeast(1)
