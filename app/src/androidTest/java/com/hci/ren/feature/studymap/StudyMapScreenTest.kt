package com.hci.ren.feature.studymap

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudyTopic
import com.hci.ren.ui.theme.RenTheme
import org.junit.Rule
import org.junit.Test

class StudyMapScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun generatedPlanShowsSummaryScheduleAndTopicView() {
        setScreen(plan(), submission(StudyDeadline.NoFixedDeadline, 60))

        composeRule.onNodeWithText("Study Map").assertIsDisplayed()
        composeRule.onNodeWithText("Calculus").assertIsDisplayed()
        composeRule.onNodeWithText("Next up").assertIsDisplayed()
        composeRule.onNodeWithText("Topics").performClick()
        composeRule.onNodeWithText("Limits").assertIsDisplayed()
    }

    @Test fun unrealisticPlanShowsInPlaceAdjustmentSheet() {
        setScreen(plan(), submission(StudyDeadline.Today, 15), suggestedDeadline = "2099-06-30", balancedDays = 5, intensiveDays = 3)

        composeRule.onNodeWithText("This plan may need changes").assertIsDisplayed()
        composeRule.onNodeWithText("Extend deadline").performClick()
        composeRule.onNodeWithText("Choose a better deadline").assertIsDisplayed()
        composeRule.onNodeWithText("Balanced").assertIsDisplayed()
        composeRule.onNodeWithText("Intensive").assertIsDisplayed()
        composeRule.onNodeWithText("Custom deadline").assertIsDisplayed()
    }

    @Test fun emptyStudyMapShowsCreateProjectAction() {
        setScreen(null, null)

        composeRule.onNodeWithText("No study project selected").assertIsDisplayed()
        composeRule.onNodeWithText("Create project").assertIsDisplayed()
    }

    private fun setScreen(
        plan: GeneratedStudyPlan?,
        submission: PlanSetupSubmission?,
        suggestedDeadline: String? = null,
        balancedDays: Int = 0,
        intensiveDays: Int = 0,
    ) {
        composeRule.setContent {
            RenTheme {
                StudyMapScreen(
                    plan = plan,
                    preferences = submission,
                    suggestedDeadline = suggestedDeadline,
                    recommendedDaysBalanced = balancedDays,
                    recommendedDaysIntensive = intensiveDays,
                    onHome = {},
                    onCreateProject = {},
                    onInsights = {},
                    onConsumeMessage = {},
                    onApplyDeadline = {},
                    onExtendDeadline = { _, _ -> },
                    onIncreaseDailyTime = {},
                    onReduceScope = { _, _ -> },
                    onContinueAnyway = {},
                    onTaskStatusChange = { _, _ -> },
                    onTaskDurationChange = { _, _ -> },
                    onExcludeTask = {},
                    onRestoreTask = {},
                )
            }
        }
    }

    private fun plan() = GeneratedStudyPlan(
        id = "plan",
        topics = listOf(StudyTopic("limits", "Limits", 1)),
        blocks = listOf(
            GeneratedStudyBlock("read", "Read limits", 1, 45, "Read the core rules.", listOf("limits")),
            GeneratedStudyBlock("practice", "Practice limits", 2, 45, "Solve five exercises.", listOf("limits")),
        ),
        totalEstimatedMinutes = 90,
        projectName = "Calculus",
    )

    private fun submission(deadline: StudyDeadline, dailyMinutes: Int) = PlanSetupSubmission(
        documentUris = listOf("content://test"),
        goal = StudyGoal.PrepareForExam,
        deadline = deadline,
        deadlineDate = null,
        dailyStudyMinutes = dailyMinutes,
        studyDays = StudyDay.entries.toSet(),
    )
}

