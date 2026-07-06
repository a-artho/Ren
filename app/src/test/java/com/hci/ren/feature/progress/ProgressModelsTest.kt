package com.hci.ren.feature.progress

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.studymap.FocusSessionOutcome
import com.hci.ren.feature.studymap.FocusSessionRecord
import com.hci.ren.feature.studymap.StudyProject
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressModelsTest {
    @Test fun weeklyFocusSummaryAggregatesCurrentStudyWeekOnly() {
        val project = project(
            dailyMinutes = 240,
            focusHistory = mapOf(
                "2026-07-05" to listOf(focusRecord(focusSeconds = 7_200)),
                "2026-07-06" to listOf(
                    focusRecord(focusSeconds = 3_600),
                    focusRecord(focusSeconds = 1_800),
                ),
                "2026-07-08" to listOf(focusRecord(focusSeconds = 7_200)),
                "2026-07-12" to listOf(focusRecord(focusSeconds = 14_400)),
                "2026-07-13" to listOf(focusRecord(focusSeconds = 3_600)),
            ),
        )

        val summary = buildWeeklyFocusSummary(project, today = "2026-07-09")

        assertEquals(
            listOf(
                WeeklyFocusDay("2026-07-06", 90),
                WeeklyFocusDay("2026-07-07", 0),
                WeeklyFocusDay("2026-07-08", 120),
                WeeklyFocusDay("2026-07-09", 0),
                WeeklyFocusDay("2026-07-10", 0),
                WeeklyFocusDay("2026-07-11", 0),
                WeeklyFocusDay("2026-07-12", 240),
            ),
            summary.days,
        )
        assertEquals(450, summary.totalFocusMinutes)
        assertEquals(3, summary.studyDays)
    }

    @Test fun weeklyFocusSummaryRoundsChartMaximumToTwoHourIntervalsWithGoalHeadroom() {
        val summary = buildWeeklyFocusSummary(
            project(
                dailyMinutes = 240,
                focusHistory = mapOf(
                    "2026-07-06" to listOf(focusRecord(focusSeconds = 30_300)),
                ),
            ),
            today = "2026-07-06",
        )

        assertEquals(600, summary.maxChartMinutes)
    }

    @Test fun weeklyFocusSummaryRoundsPartialSecondsUpToVisibleMinutes() {
        val summary = buildWeeklyFocusSummary(
            project(
                dailyMinutes = 120,
                focusHistory = mapOf(
                    "2026-07-06" to listOf(focusRecord(focusSeconds = 61)),
                ),
            ),
            today = "2026-07-06",
        )

        assertEquals(2, summary.days.first().focusMinutes)
        assertEquals(2, summary.totalFocusMinutes)
    }

    private fun focusRecord(focusSeconds: Int) = FocusSessionRecord(
        taskId = "task",
        plannedFocusMinutes = 60,
        plannedBreakMinutes = 10,
        focusSeconds = focusSeconds,
        breakSeconds = 0,
        awaySeconds = 0,
        interruptionCount = 0,
        outcome = FocusSessionOutcome.FocusRoundEnded,
        endedAtMillis = 1L,
    )

    private fun project(
        dailyMinutes: Int,
        focusHistory: Map<String, List<FocusSessionRecord>>,
    ) = StudyProject(
        id = "plan",
        title = "Plan",
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
        deadlineAtMillis = null,
        plan = GeneratedStudyPlan(
            id = "plan",
            topics = emptyList(),
            blocks = emptyList(),
            projectName = "Plan",
        ),
        preferences = PlanSetupSubmission(
            documentUris = emptyList(),
            goal = StudyGoal.PrepareForExam,
            deadline = StudyDeadline.ChooseDate,
            deadlineDate = "2026-07-30",
            dailyStudyMinutes = dailyMinutes,
            studyDays = StudyDay.entries.toSet(),
        ),
        focusSessionHistoryByDate = focusHistory,
    )
}
