package com.hci.ren.feature.progress.presentation

import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.studymap.FocusSessionOutcome
import com.hci.ren.feature.studymap.FocusSessionRecord
import com.hci.ren.feature.studymap.StudyProject

internal object ProgressScreenFixtures {
    const val ScreenshotToday = "2026-07-12"

    fun screenshotProject() = project(
        dailyStudyMinutes = 90,
        focusHistory = mapOf(
            "2026-06-16" to listOf(focusRecord(plannedFocusMinutes = 15, focusSeconds = 900)),
            "2026-06-18" to listOf(focusRecord(plannedFocusMinutes = 25, focusSeconds = 1_500)),
            "2026-06-20" to listOf(
                focusRecord(plannedFocusMinutes = 25, focusSeconds = 1_500),
                focusRecord(plannedFocusMinutes = 15, focusSeconds = 900),
            ),
            "2026-06-23" to listOf(focusRecord(plannedFocusMinutes = 15, focusSeconds = 900)),
            "2026-06-24" to listOf(focusRecord(plannedFocusMinutes = 30, focusSeconds = 1_800)),
            "2026-06-26" to listOf(
                focusRecord(plannedFocusMinutes = 30, focusSeconds = 1_800),
                focusRecord(plannedFocusMinutes = 25, focusSeconds = 1_500),
            ),
            "2026-06-28" to listOf(
                focusRecord(plannedFocusMinutes = 30, focusSeconds = 1_800),
                focusRecord(plannedFocusMinutes = 30, focusSeconds = 1_800),
                focusRecord(plannedFocusMinutes = 15, focusSeconds = 900, interruptionCount = 1),
            ),
            "2026-06-30" to listOf(focusRecord(plannedFocusMinutes = 15, focusSeconds = 900)),
            "2026-07-01" to listOf(
                focusRecord(plannedFocusMinutes = 25, focusSeconds = 1_500),
                focusRecord(plannedFocusMinutes = 15, focusSeconds = 900),
            ),
            "2026-07-02" to listOf(
                focusRecord(plannedFocusMinutes = 25, focusSeconds = 1_500),
                focusRecord(plannedFocusMinutes = 25, focusSeconds = 1_500),
                focusRecord(plannedFocusMinutes = 15, focusSeconds = 900),
            ),
            "2026-07-04" to listOf(focusRecord(plannedFocusMinutes = 15, focusSeconds = 900)),
            "2026-07-05" to listOf(
                focusRecord(plannedFocusMinutes = 30, focusSeconds = 1_800),
                focusRecord(plannedFocusMinutes = 30, focusSeconds = 1_800),
                focusRecord(plannedFocusMinutes = 30, focusSeconds = 1_800),
            ),
            "2026-07-07" to listOf(
                focusRecord(plannedFocusMinutes = 15, focusSeconds = 900),
                focusRecord(plannedFocusMinutes = 25, focusSeconds = 300),
            ),
            "2026-07-08" to listOf(
                focusRecord(plannedFocusMinutes = 25, focusSeconds = 1_500),
                focusRecord(plannedFocusMinutes = 15, focusSeconds = 900),
            ),
            "2026-07-09" to listOf(
                focusRecord(plannedFocusMinutes = 25, focusSeconds = 1_500),
                focusRecord(plannedFocusMinutes = 25, focusSeconds = 1_500),
                focusRecord(
                    plannedFocusMinutes = 25,
                    focusSeconds = 1_020,
                    interruptionCount = 2,
                    outcome = FocusSessionOutcome.FocusStopped,
                ),
            ),
            "2026-07-10" to listOf(
                focusRecord(plannedFocusMinutes = 30, focusSeconds = 1_800),
                focusRecord(plannedFocusMinutes = 30, focusSeconds = 1_800),
            ),
            "2026-07-11" to listOf(
                focusRecord(plannedFocusMinutes = 25, focusSeconds = 1_500),
                focusRecord(plannedFocusMinutes = 25, focusSeconds = 1_500),
                focusRecord(plannedFocusMinutes = 15, focusSeconds = 900),
            ),
            "2026-07-12" to listOf(
                focusRecord(plannedFocusMinutes = 30, focusSeconds = 1_800),
                focusRecord(plannedFocusMinutes = 30, focusSeconds = 1_800),
                focusRecord(plannedFocusMinutes = 30, focusSeconds = 1_800),
            ),
        ),
    )

    fun focusRecord(
        focusSeconds: Int,
        plannedFocusMinutes: Int = 60,
        interruptionCount: Int = 0,
        outcome: FocusSessionOutcome = FocusSessionOutcome.FocusRoundEnded,
    ) = FocusSessionRecord(
        taskId = "task",
        plannedFocusMinutes = plannedFocusMinutes,
        plannedFocusSeconds = plannedFocusMinutes * 60,
        plannedBreakMinutes = 10,
        focusSeconds = focusSeconds,
        breakSeconds = 0,
        awaySeconds = 0,
        interruptionCount = interruptionCount,
        outcome = outcome,
        endedAtMillis = 1L,
    )

    fun project(
        focusHistory: Map<String, List<FocusSessionRecord>>,
        dailyStudyMinutes: Int = 120,
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
            dailyStudyMinutes = dailyStudyMinutes,
            studyDays = StudyDay.entries.toSet(),
        ),
        focusSessionHistoryByDate = focusHistory,
    )
}
