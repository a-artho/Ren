package com.hci.ren.feature.plangeneration

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyPlanFeasibilityCheckerTest {
    private val monday = GregorianCalendar(2026, Calendar.JUNE, 22)

    @Test fun realisticPlanUsesSelectedDays() {
        val result = StudyPlanFeasibilityChecker().check(
            tasks = listOf(block(minutes = 40), block(minutes = 40)),
            preferences = submission(
                goal = StudyGoal.PrepareForExam,
                deadline = StudyDeadline.InThreeDays,
                dailyMinutes = 60,
                days = setOf(StudyDay.Monday, StudyDay.Tuesday, StudyDay.Wednesday),
            ),
            today = monday,
        )

        assertEquals(80, result.totalRequiredMinutes)
        assertEquals(180, result.availableMinutes)
        assertEquals(FeasibilityStatus.Realistic, result.status)
    }

    @Test fun tightPlanIsIntensive() {
        val result = StudyPlanFeasibilityChecker().check(
            listOf(block(minutes = 55)),
            submission(StudyGoal.PrepareForExam, StudyDeadline.ChooseDate, 60, setOf(StudyDay.Monday), "2026-06-23"),
            monday,
        )
        assertEquals(55, result.totalRequiredMinutes)
        assertEquals(FeasibilityStatus.Intensive, result.status)
    }

    @Test fun oversizedPlanIsUnrealisticAndReportsCoverage() {
        val result = StudyPlanFeasibilityChecker().check(
            listOf(block(minutes = 200), block(minutes = 200)),
            submission(StudyGoal.PrepareForExam, StudyDeadline.ChooseDate, 60, setOf(StudyDay.Monday), "2026-06-23"),
            monday,
        )
        assertEquals(400, result.totalRequiredMinutes)
        assertEquals(340, result.shortageMinutes)
        assertEquals(15, result.estimatedCoveragePercent)
        assertEquals(FeasibilityStatus.Unrealistic, result.status)
    }

    @Test fun unavailableWeekdayProducesZeroAvailability() {
        val result = StudyPlanFeasibilityChecker().check(
            listOf(block(minutes = 30)),
            submission(StudyGoal.PrepareForExam, StudyDeadline.ChooseDate, 60, setOf(StudyDay.Tuesday), "2026-06-23"),
            monday,
        )
        assertEquals(0, result.availableMinutes)
        assertEquals(FeasibilityStatus.Unrealistic, result.status)
    }

    @Test fun negativeDailyOverrideProducesZeroAvailability() {
        val result = StudyPlanFeasibilityChecker().check(
            listOf(block(minutes = 30)),
            submission(StudyGoal.PrepareForExam, StudyDeadline.InThreeDays, 60, setOf(StudyDay.Monday)),
            monday,
            dailyMinutesOverride = -10,
        )

        assertEquals(0, result.availableMinutes)
        assertEquals(FeasibilityStatus.Unrealistic, result.status)
    }

    @Test fun deadlineRecommendationsUseDailyAvailabilityNotBlockDuration() {
        val result = StudyPlanFeasibilityChecker().check(
            listOf(block(minutes = 675)),
            submission(StudyGoal.PrepareForExam, StudyDeadline.ChooseDate, 90, setOf(StudyDay.Monday), "2026-06-23"),
            monday,
        )
        assertEquals(675, result.totalRequiredMinutes)
        assertEquals(90, result.availableMinutesPerStudyDay)
        assertEquals(8, result.recommendedDaysBalanced)
        assertEquals(5, result.recommendedDaysIntensive)
    }

    private fun block(
        id: String = "b1",
        minutes: Int,
    ) = GeneratedStudyBlock(
        id = id,
        title = id,
        order = 1,
        effortMinMinutes = minutes,
        effortLikelyMinutes = minutes,
        effortMaxMinutes = minutes,
        instructions = "Review",
        topicIds = listOf("t1"),
        taskType = StudyTaskType.Review,
    )

    private fun submission(
        goal: StudyGoal,
        deadline: StudyDeadline,
        dailyMinutes: Int,
        days: Set<StudyDay>,
        deadlineDate: String? = null,
    ) = PlanSetupSubmission(listOf("content://pdf"), goal, deadline, deadlineDate, dailyMinutes, days)
}
