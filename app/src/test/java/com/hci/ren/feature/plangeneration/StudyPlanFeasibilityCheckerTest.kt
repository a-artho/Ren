package com.hci.ren.feature.plangeneration

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class StudyPlanFeasibilityCheckerTest {
    private val monday = GregorianCalendar(2026, Calendar.JUNE, 22)

    @Test fun realisticPlanIncludesGoalBufferAndSelectedDays() {
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

        assertEquals(92, result.totalRequiredMinutes)
        assertEquals(180, result.availableMinutes)
        assertEquals(FeasibilityStatus.Realistic, result.status)
    }

    @Test fun tightPlanIsIntensive() {
        val result = StudyPlanFeasibilityChecker().check(
            listOf(block(minutes = 50)),
            submission(StudyGoal.ReviseKnown, StudyDeadline.Today, 60, setOf(StudyDay.Monday)),
            monday,
        )
        assertEquals(55, result.totalRequiredMinutes)
        assertEquals(FeasibilityStatus.Intensive, result.status)
    }

    @Test fun oversizedPlanIsUnrealisticAndReportsCoverage() {
        val result = StudyPlanFeasibilityChecker().check(
            listOf(block(minutes = 200), block(minutes = 200)),
            submission(StudyGoal.LearnThoroughly, StudyDeadline.Today, 60, setOf(StudyDay.Monday)),
            monday,
        )
        assertEquals(480, result.totalRequiredMinutes)
        assertEquals(420, result.shortageMinutes)
        assertEquals(13, result.estimatedCoveragePercent)
        assertEquals(FeasibilityStatus.Unrealistic, result.status)
    }

    @Test fun unavailableWeekdayProducesZeroAvailability() {
        val result = StudyPlanFeasibilityChecker().check(
            listOf(block(minutes = 30)),
            submission(StudyGoal.FinishQuickly, StudyDeadline.Today, 60, setOf(StudyDay.Tuesday)),
            monday,
        )
        assertEquals(0, result.availableMinutes)
        assertEquals(FeasibilityStatus.Unrealistic, result.status)
    }

    @Test fun emergencyPlanNeverCompressesBelowMinimumAndPostponesOverflow() {
        val tasks = listOf(
            block("high", 40, 20, TaskPriority.High),
            block("low", 40, 20, TaskPriority.Low),
        )
        val adapted = StudyPlanAdapter().fit(tasks, availableMinutes = 20, prioritised = true)

        assertEquals(TaskDisposition.MustComplete, adapted[0].disposition)
        assertTrue(adapted[0].durationMinutes >= adapted[0].minimumUsefulMinutes)
        assertEquals(TaskDisposition.IfTimeRemains, adapted[1].disposition)
    }

    @Test fun emergencyPlanUsesSkimWhenOnlyShortUsefulWindowRemains() {
        val adapted = StudyPlanAdapter().fit(
            listOf(block("high", 20, 20, TaskPriority.High), block("low", 30, 10, TaskPriority.Low)),
            availableMinutes = 25,
            prioritised = true,
        )
        assertEquals(TaskDisposition.IfTimeRemains, adapted[1].disposition)
        assertEquals(20, adapted.filter { it.disposition == TaskDisposition.MustComplete }.sumOf { it.durationMinutes })
    }

    @Test fun activeScheduleFitsBudgetAndOptionalTimeIsNotCounted() {
        val adapted = StudyPlanAdapter().fit(
            (1..15).map { block("b$it", 45, 20, TaskPriority.Medium).copy(order = it) },
            availableMinutes = 90,
            prioritised = false,
        )
        val active = adapted.filter { it.disposition == TaskDisposition.MustComplete }
        assertEquals(2, active.size)
        assertEquals(90, active.sumOf { it.durationMinutes })
        assertEquals(1, adapted.count { it.disposition == TaskDisposition.IfTimeRemains })
        assertEquals(12, adapted.count { it.disposition == TaskDisposition.Postponed })
    }

    @Test fun deadlineRecommendationsUseDailyAvailabilityNotBlockDuration() {
        val result = StudyPlanFeasibilityChecker().check(
            listOf(block(minutes = 675)),
            submission(StudyGoal.LearnThoroughly, StudyDeadline.Today, 90, setOf(StudyDay.Monday)),
            monday,
        )
        assertEquals(810, result.totalRequiredMinutes)
        assertEquals(90, result.availableMinutesPerStudyDay)
        assertEquals(9, result.recommendedDaysBalanced)
        assertEquals(6, result.recommendedDaysIntensive)
    }

    @Test fun everyScopeStrategyActuallyChangesTasks() {
        val tasks = listOf(
            block("foundation", 45, 20, TaskPriority.High).copy(taskType = StudyTaskType.Learn, isSkippable = false),
            block("practice", 45, 15, TaskPriority.Medium).copy(taskType = StudyTaskType.Practice),
            block("advanced", 45, 20, TaskPriority.Low).copy(taskType = StudyTaskType.Learn, topicIds = listOf("t2")),
        )
        val adjuster = StudyPlanScopeAdjuster()
        assertTrue(adjuster.applyGoalStrategy(tasks, StudyScopeGoal.PassExam).any { it.taskType == StudyTaskType.Practice })
        assertTrue(adjuster.applyGoalStrategy(tasks, StudyScopeGoal.ReviseOnly).all { it.taskType != StudyTaskType.Learn })
        assertFalse(adjuster.applyGoalStrategy(tasks, StudyScopeGoal.Fundamentals).any { it.id == "advanced" })
        assertTrue(adjuster.applyGoalStrategy(tasks, StudyScopeGoal.SkimEverything).all { it.taskType == StudyTaskType.Skim })
        assertEquals(listOf("advanced"), adjuster.applyGoalStrategy(tasks, StudyScopeGoal.SelectedTopics, setOf("t2")).map { it.id })
        assertEquals(tasks.size, adjuster.applyGoalStrategy(tasks, StudyScopeGoal.CompleteEverything).size)
    }

    private fun block(
        id: String = "b1",
        minutes: Int,
        minimum: Int = 10,
        priority: TaskPriority = TaskPriority.Medium,
    ) = GeneratedStudyBlock(
        id = id, title = id, order = 1, durationMinutes = minutes,
        instructions = "Review", topicIds = listOf("t1"), minimumUsefulMinutes = minimum,
        priority = priority, taskType = StudyTaskType.Review, priorityReason = "Reason", isSkippable = true,
    )

    private fun submission(
        goal: StudyGoal,
        deadline: StudyDeadline,
        dailyMinutes: Int,
        days: Set<StudyDay>,
    ) = PlanSetupSubmission(listOf("content://pdf"), goal, deadline, null, dailyMinutes, days)
}
