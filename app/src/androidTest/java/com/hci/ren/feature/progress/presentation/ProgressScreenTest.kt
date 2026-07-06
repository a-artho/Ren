package com.hci.ren.feature.progress.presentation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.hci.ren.MainActivity
import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.studymap.FocusSessionOutcome
import com.hci.ren.feature.studymap.FocusSessionRecord
import com.hci.ren.feature.studymap.StudyProject
import com.hci.ren.ui.theme.RenTheme
import org.junit.Rule
import org.junit.Test

class ProgressScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun progressScreenShowsStudyConsistencyFromFocusHistory() {
        composeRule.activity.setContent {
            RenTheme {
                ProgressScreen(
                    project = project(
                        focusHistory = mapOf(
                            "2026-07-07" to listOf(focusRecord(focusSeconds = 3_600)),
                            "2026-07-08" to listOf(focusRecord(focusSeconds = 3_600)),
                            "2026-07-09" to listOf(focusRecord(focusSeconds = 7_200)),
                            "2026-06-30" to listOf(focusRecord(focusSeconds = 1_800)),
                            "2026-07-01" to listOf(focusRecord(focusSeconds = 1_800)),
                            "2026-07-03" to listOf(focusRecord(focusSeconds = 1_800)),
                            "2026-07-05" to listOf(focusRecord(focusSeconds = 1_800)),
                        ),
                    ),
                    today = "2026-07-09",
                )
            }
        }

        composeRule.onNodeWithTag("study-consistency-card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Study consistency").assertIsDisplayed()
        composeRule.onNodeWithText("Most consistent last week").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "3 day streak. Most consistent last week. 7 study days across the last 4 weeks.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("consistency-cell-0-3").assertIsDisplayed()
        composeRule.onNodeWithTag("consistency-cell-1-1").assertIsDisplayed()
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
            dailyStudyMinutes = 120,
            studyDays = StudyDay.entries.toSet(),
        ),
        focusSessionHistoryByDate = focusHistory,
    )
}
