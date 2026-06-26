package com.hci.ren.feature.studymap

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudySourceDocument
import com.hci.ren.feature.plangeneration.StudySourceRef
import com.hci.ren.feature.plangeneration.StudyTaskType
import com.hci.ren.feature.plangeneration.StudyTopic
import com.hci.ren.ui.theme.RenTheme
import org.junit.Rule
import org.junit.Test

class StudyMapScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun generatedPlanShowsSummaryMapAndTopicView() {
        setScreen(scheduledPlan(), submission(StudyDeadline.InOneWeek, 60))

        composeRule.onNodeWithText("Calculus").assertIsDisplayed()
        composeRule.onNodeWithText("Map").assertIsDisplayed()
        composeRule.onNodeWithText("Read limits").assertIsDisplayed()
        composeRule.onNodeWithText("Topics").performClick()
        composeRule.onNodeWithText("Limits").assertIsDisplayed()
    }

    @Test fun unrealisticPlanShowsInPlaceAdjustmentSheet() {
        setScreen(plan(), submission(StudyDeadline.Tomorrow, 15), suggestedDeadline = "2099-06-30", balancedDays = 5, intensiveDays = 3)

        composeRule.onNodeWithText("This plan may need changes").assertIsDisplayed()
        composeRule.onNodeWithText("Extend deadline").performClick()
        composeRule.onNodeWithText("Choose a better deadline").assertIsDisplayed()
        composeRule.onNodeWithText("Balanced").assertIsDisplayed()
        composeRule.onNodeWithText("Intensive").assertIsDisplayed()
        composeRule.onNodeWithText("Custom deadline").assertIsDisplayed()
    }

    @Test fun emptyStudyMapShowsCreateProjectAction() {
        setScreen(null, null)

        composeRule.onNodeWithText("No plan yet. Tragic, but fixable.").assertIsDisplayed()
        composeRule.onNodeWithText("Create study plan").assertIsDisplayed()
    }

    @Test fun studyPlanMenuShowsEditAndDeleteActions() {
        setScreen(scheduledPlan(), submission(StudyDeadline.InOneWeek, 60))

        composeRule.onNodeWithContentDescription("Study plan options").performClick()
        composeRule.onNodeWithText("Edit plan").assertIsDisplayed()
        composeRule.onNodeWithText("Delete plan").assertIsDisplayed()

        composeRule.onNodeWithText("Edit plan").performClick()
        composeRule.onNodeWithText("Change deadline").assertIsDisplayed()
        composeRule.onNodeWithText("Available study time").assertIsDisplayed()
        composeRule.onNodeWithText("Reduce scope").assertIsDisplayed()
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
                    onBack = {},
                    onCreateProject = {},
                    onOpenToday = {},
                    onDeletePlan = {},
                    onConsumeMessage = {},
                    onApplyDeadline = {},
                    onExtendDeadline = { _, _ -> },
                    onIncreaseDailyTime = {},
                    onReduceScope = { _, _ -> },
                    onContinueAnyway = {},
                )
            }
        }
    }

    private fun plan() = GeneratedStudyPlan(
        id = "plan",
        topics = listOf(StudyTopic("limits", "Limits", 1)),
        blocks = listOf(
            GeneratedStudyBlock(
                id = "read",
                title = "Read limits",
                order = 1,
                durationMinutes = 45,
                instructions = "Read the core rules.",
                topicIds = listOf("limits"),
                taskType = StudyTaskType.Concept,
            ),
            GeneratedStudyBlock(
                id = "practice",
                title = "Practice limits",
                order = 2,
                durationMinutes = 45,
                instructions = "Solve five exercises.",
                topicIds = listOf("limits"),
                taskType = StudyTaskType.Practice,
            ),
        ),
        totalEstimatedMinutes = 90,
        projectName = "Calculus",
    )

    private fun scheduledPlan() = plan().copy(
        sourceDocuments = listOf(StudySourceDocument("doc", "Calculus notes.pdf", order = 1, pageCount = 12)),
        blocks = listOf(
            GeneratedStudyBlock(
                id = "read",
                title = "Read limits",
                order = 1,
                durationMinutes = 45,
                instructions = "Read the core rules.",
                topicIds = listOf("limits"),
                taskType = StudyTaskType.Concept,
                sourceRefs = listOf(StudySourceRef("doc", startPage = 2, endPage = 2)),
            ),
            GeneratedStudyBlock(
                id = "practice",
                title = "Practice limits",
                order = 2,
                durationMinutes = 45,
                instructions = "Solve five exercises.",
                topicIds = listOf("limits"),
                taskType = StudyTaskType.Practice,
                sourceRefs = listOf(StudySourceRef("doc", startPage = 3, endPage = 5)),
            ),
        ),
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
