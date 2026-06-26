package com.hci.ren.feature.plangeneration

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.studymap.availableStudyDates
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
)

class StudyPlanFeasibilityChecker {
    fun check(
        tasks: List<GeneratedStudyBlock>,
        preferences: PlanSetupSubmission,
        today: Calendar = Calendar.getInstance(),
        dailyMinutesOverride: Int? = null,
    ): FeasibilityResult {
        val required = requiredStudyMinutes(tasks)
        val availableStudyDays = availableStudyDates(preferences, today).size
        val dailyMinutes = (dailyMinutesOverride ?: preferences.dailyStudyMinutes).coerceAtLeast(0)
        val available = availableStudyDays * dailyMinutes
        val availableMinutesPerStudyDay = if (availableStudyDays > 0) {
            available / availableStudyDays
        } else {
            dailyMinutes
        }
        val ratio = if (available == 0) Double.POSITIVE_INFINITY else required.toDouble() / available
        val coverage = when {
            required == 0 -> 100
            available == 0 -> 0
            else -> ((available.toDouble() / required) * 100).roundToInt().coerceAtMost(100)
        }
        val status = when {
            required <= available * 0.9 -> FeasibilityStatus.Realistic
            required <= available * 1.15 -> FeasibilityStatus.Intensive
            else -> FeasibilityStatus.Unrealistic
        }
        return FeasibilityResult(status, required, available, (required - available).coerceAtLeast(0), ratio, coverage,
            daysNeeded(required, availableMinutesPerStudyDay),
            daysNeeded(required, (availableMinutesPerStudyDay * 1.5).roundToInt()),
            availableMinutesPerStudyDay,
            hasDeadline = true)
    }

    private fun daysNeeded(minutes: Int, dailyMinutes: Int) = if (minutes == 0) 0 else ceil(minutes.toDouble() / dailyMinutes.coerceAtLeast(1)).toInt()

}
