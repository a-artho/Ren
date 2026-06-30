package com.hci.ren.feature.plangeneration

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.studymap.availableStudyDates
import com.hci.ren.feature.studymap.currentStudyCalendar
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class FeasibilityStatus { Realistic, Intensive, Unrealistic }
data class FeasibilityResult(
    val status: FeasibilityStatus,
    val totalRequiredMinutes: Int,
    val availableMinutes: Int,
    val shortageMinutes: Int,
    val workloadRatio: Double,
    val estimatedCoveragePercent: Int,
    val recommendedDaysBalanced: Int,
    val recommendedDaysIntensive: Int,
    val availableMinutesPerStudyDay: Int,
    val hasDeadline: Boolean,
    val totalLikelyMinutes: Int = totalRequiredMinutes,
    val totalReservedMinutes: Int = totalRequiredMinutes,
    val likelyShortageMinutes: Int = shortageMinutes,
    val reservedShortageMinutes: Int = shortageMinutes,
)

class StudyPlanFeasibilityChecker {
    fun check(
        tasks: List<GeneratedStudyBlock>,
        preferences: PlanSetupSubmission,
        today: Calendar = currentStudyCalendar(preferences),
        dailyMinutesOverride: Int? = null,
    ): FeasibilityResult {
        val likely = requiredLikelyStudyMinutes(tasks)
        val reserved = requiredStudyMinutes(tasks)
        val availableStudyDays = availableStudyDates(preferences, today).size
        val dailyMinutes = (dailyMinutesOverride ?: preferences.dailyStudyMinutes).coerceAtLeast(0)
        val available = availableStudyDays * dailyMinutes
        val availableMinutesPerStudyDay = if (availableStudyDays > 0) {
            available / availableStudyDays
        } else {
            dailyMinutes
        }
        val ratio = if (available == 0) Double.POSITIVE_INFINITY else reserved.toDouble() / available
        val coverage = when {
            likely == 0 -> 100
            available == 0 -> 0
            else -> ((available.toDouble() / likely) * 100).roundToInt().coerceAtMost(100)
        }
        val status = when {
            reserved <= available * RealisticWorkloadRatio -> FeasibilityStatus.Realistic
            likely <= available -> FeasibilityStatus.Intensive
            else -> FeasibilityStatus.Unrealistic
        }
        val likelyShortage = (likely - available).coerceAtLeast(0)
        val reservedShortage = (reserved - available).coerceAtLeast(0)
        return FeasibilityResult(
            status = status,
            totalRequiredMinutes = reserved,
            availableMinutes = available,
            shortageMinutes = maxOf(likelyShortage, reservedShortage),
            workloadRatio = ratio,
            estimatedCoveragePercent = coverage,
            recommendedDaysBalanced = daysNeeded(reserved, availableMinutesPerStudyDay),
            recommendedDaysIntensive = daysNeeded(likely, availableMinutesPerStudyDay),
            availableMinutesPerStudyDay = availableMinutesPerStudyDay,
            hasDeadline = true,
            totalLikelyMinutes = likely,
            totalReservedMinutes = reserved,
            likelyShortageMinutes = likelyShortage,
            reservedShortageMinutes = reservedShortage,
        )
    }

    private fun daysNeeded(minutes: Int, dailyMinutes: Int) = if (minutes == 0) 0 else ceil(minutes.toDouble() / dailyMinutes.coerceAtLeast(1)).toInt()

}
