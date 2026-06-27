package com.hci.ren.feature.studymap

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.IntensiveWorkloadRatio
import com.hci.ren.feature.plangeneration.RealisticWorkloadRatio
import com.hci.ren.feature.plangeneration.requiredStudyMinutes
import java.util.Calendar

enum class PlanRealismStatus { OnTrack, Tight, Unrealistic }

data class PlanRealism(
    val status: PlanRealismStatus,
    val remainingMinutes: Int,
    val availableMinutes: Int,
    val shortageMinutes: Int,
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
        val remaining = requiredStudyMinutes(tasks)
        val dailyMinutes = (dailyMinutesOverride ?: preferences.dailyStudyMinutes).coerceAtLeast(0)
        val available = availableStudyDates(preferences, today)
            .map { it.toStudyDate() }
            .sumOf { date -> dailyAvailableMinutesByDate[date]?.coerceIn(0, 1_440) ?: dailyMinutes }
        val unplaced = requiredStudyMinutes(unscheduledTasks)
        val shortage = maxOf(unplaced, remaining - available, 0)
        val status = when {
            unplaced > 0 -> PlanRealismStatus.Unrealistic
            remaining <= available * RealisticWorkloadRatio -> PlanRealismStatus.OnTrack
            remaining <= available * IntensiveWorkloadRatio -> PlanRealismStatus.Tight
            else -> PlanRealismStatus.Unrealistic
        }
        return PlanRealism(status, remaining, available, shortage)
    }
}
