package com.hci.ren.feature.studymap

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.RealisticWorkloadRatio
import com.hci.ren.feature.plangeneration.requiredLikelyStudyMinutes
import com.hci.ren.feature.plangeneration.requiredStudyMinutes
import java.util.Calendar

enum class PlanRealismStatus { OnTrack, Tight, Crammed, Overloaded }

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
        schedule: StudySchedule? = null,
        unscheduledTasks: List<GeneratedStudyBlock> = emptyList(),
        dailyAvailableMinutesByDate: Map<String, Int> = emptyMap(),
    ): PlanRealism {
        val remainingLikely = requiredLikelyStudyMinutes(tasks)
        val remainingReserved = requiredStudyMinutes(tasks)
        val dailyMinutes = (dailyMinutesOverride ?: preferences.dailyStudyMinutes).coerceAtLeast(0)
        val available = availableStudyDates(preferences, today)
            .map { it.toStudyDate() }
            .sumOf { date -> dailyAvailableMinutesByDate[date]?.coerceIn(0, 1_440) ?: dailyMinutes }
        val effectiveUnscheduledTasks = schedule?.unscheduledTasks ?: unscheduledTasks
        val likelyShortage = maxOf(remainingLikely - available, 0)
        val reservedShortage = maxOf(remainingReserved - available, 0)
        val status = when {
            effectiveUnscheduledTasks.isNotEmpty() -> PlanRealismStatus.Overloaded
            schedule?.days.orEmpty().any { it.isOverCapacity } -> PlanRealismStatus.Crammed
            schedule?.fitMode == ScheduleFitMode.Reserved && remainingReserved <= available * RealisticWorkloadRatio -> PlanRealismStatus.OnTrack
            schedule?.fitMode == ScheduleFitMode.Reserved -> PlanRealismStatus.Tight
            schedule?.fitMode == ScheduleFitMode.LikelyFallback -> PlanRealismStatus.Tight
            schedule == null && remainingReserved <= available * RealisticWorkloadRatio -> PlanRealismStatus.OnTrack
            schedule == null && remainingLikely <= available -> PlanRealismStatus.Tight
            else -> PlanRealismStatus.Overloaded
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
