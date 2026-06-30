package com.hci.ren.feature.studymap

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.IntensiveWorkloadRatio
import com.hci.ren.feature.plangeneration.RealisticWorkloadRatio
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.requiredStudyMinutes
import java.util.Calendar

enum class PlanRealismStatus { OnTrack, Tight, Unrealistic }

data class PlanRealism(
    val status: PlanRealismStatus,
    val remainingMinutes: Int,
    val availableMinutes: Int,
    val shortageMinutes: Int,
    val remainingLikelyMinutes: Int = remainingMinutes,
    val remainingReservedMinutes: Int = remainingMinutes,
    val likelyShortageMinutes: Int = shortageMinutes,
    val reservedShortageMinutes: Int = shortageMinutes,
)

class PlanRealismCalculator {
    fun calculate(
        tasks: List<GeneratedStudyBlock>,
        preferences: PlanSetupSubmission,
        today: Calendar = currentStudyCalendar(preferences),
        dailyMinutesOverride: Int? = null,
        unscheduledTasks: List<GeneratedStudyBlock> = emptyList(),
        dailyAvailableMinutesByDate: Map<String, Int> = emptyMap(),
    ): PlanRealism {
        val remainingLikely = requiredLikelyMinutes(tasks)
        val remainingReserved = requiredStudyMinutes(tasks)
        val dailyMinutes = (dailyMinutesOverride ?: preferences.dailyStudyMinutes).coerceAtLeast(0)
        val available = availableStudyDates(preferences, today)
            .map { it.toStudyDate() }
            .sumOf { date -> dailyAvailableMinutesByDate[date]?.coerceIn(0, 1_440) ?: dailyMinutes }
        val unplacedLikely = requiredLikelyMinutes(unscheduledTasks)
        val unplacedReserved = requiredStudyMinutes(unscheduledTasks)
        val likelyShortage = maxOf(unplacedLikely, remainingLikely - available, 0)
        val reservedShortage = maxOf(unplacedReserved, remainingReserved - available, 0)
        val status = when {
            unplacedLikely > 0 -> PlanRealismStatus.Unrealistic
            remainingReserved <= available * RealisticWorkloadRatio -> PlanRealismStatus.OnTrack
            remainingLikely <= available -> PlanRealismStatus.Tight
            remainingReserved <= available * IntensiveWorkloadRatio -> PlanRealismStatus.Tight
            else -> PlanRealismStatus.Unrealistic
        }
        return PlanRealism(
            status = status,
            remainingMinutes = remainingReserved,
            availableMinutes = available,
            shortageMinutes = maxOf(likelyShortage, reservedShortage),
            remainingLikelyMinutes = remainingLikely,
            remainingReservedMinutes = remainingReserved,
            likelyShortageMinutes = likelyShortage,
            reservedShortageMinutes = reservedShortage,
        )
    }
}

private fun requiredLikelyMinutes(tasks: List<GeneratedStudyBlock>): Int =
    tasks.asSequence()
        .filter { it.status !in setOf(StudyTaskStatus.ExcludedByUser, StudyTaskStatus.DeferredByUser) }
        .filter { it.status != StudyTaskStatus.Completed }
        .sumOf { it.effortLikelyMinutes.coerceAtLeast(1) }
