package com.hci.ren.feature.studymap

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDay
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.StudyGoal
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudySourceDocument
import com.hci.ren.feature.plangeneration.StudySourceRef
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.StudyTaskType
import com.hci.ren.feature.plangeneration.StudyTopic
import com.hci.ren.ui.theme.RenTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class StudyMapScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun generatedPlanShowsSummaryTreeAndMaterialView() {
        setScreen(scheduledPlan(), submission(StudyDeadline.InOneWeek, 60))

        composeRule.onNodeWithText("Calculus", substring = true, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Tree").assertIsDisplayed()
        composeRule.onAllNodesWithText("1 leaf", substring = true).assertCountEquals(2)
        composeRule.onNodeWithText("Read limits", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Material").performClick()
        composeRule.onNodeWithText("Calculus notes").assertIsDisplayed()
        composeRule.onNode(hasText("Calculus notes") and hasClickAction()).performClick()
        composeRule.onAllNodesWithText("Functions and limits").assertCountEquals(0)
        composeRule.onNodeWithText("Practice limits", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("page 2", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("45 min", substring = true).assertCountEquals(2)
    }

    @Test fun overloadedPlanShowsEditPlanSheet() {
        setScreen(plan(), submission(StudyDeadline.Tomorrow, 15))

        composeRule.onNodeWithText("This plan is overloaded").assertIsDisplayed()
        composeRule.onNodeWithText("Some leaves are still outside the scheduled days.").assertIsDisplayed()
        composeRule.onNodeWithText("Edit plan").performClick()
        composeRule.onNodeWithText("Change deadline").assertIsDisplayed()
        composeRule.onNodeWithText("Available study time").assertIsDisplayed()
        composeRule.onNodeWithText("Choose material").assertIsDisplayed()
    }

    @Test fun expandedGroupedLeavesCollapseWhenChildLeafIsTapped() {
        setScreen(groupedFuturePlan(), submission(StudyDeadline.InOneWeek, 90))

        composeRule.onNode(hasText("Functions and limits") and hasClickAction()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Review grouped leaf").assertDoesNotExist()

        composeRule.onNode(hasText("Functions and limits") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Practice grouped leaf").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Review grouped leaf").performScrollTo().assertIsDisplayed()

        composeRule.onNode(hasText("Practice grouped leaf") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Review grouped leaf").assertDoesNotExist()
        composeRule.onNodeWithText("Functions and limits").assertIsDisplayed()
    }

    @Test fun emptyStudyMapShowsCreateProjectAction() {
        setScreen(null, null)

        composeRule.onNodeWithText("New exam? Deep breaths", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Start planning").assertIsDisplayed()
    }

    @Test fun studyPlanMenuShowsEditAndDeleteActions() {
        setScreen(scheduledPlan(), submission(StudyDeadline.InOneWeek, 60))

        composeRule.onNodeWithContentDescription("Study plan options").performClick()
        composeRule.onNodeWithText("Edit plan").performClick()
        composeRule.onNodeWithText("Change deadline").assertIsDisplayed()
        composeRule.onNodeWithText("Available study time").assertIsDisplayed()
        composeRule.onNodeWithText("Choose material").assertIsDisplayed()
        composeRule.onNodeWithText("Delete plan").assertIsDisplayed()
    }

    private fun setScreen(
        plan: GeneratedStudyPlan?,
        submission: PlanSetupSubmission?,
    ) {
        composeRule.setContent {
            RenTheme {
                StudyMapScreen(
                    plan = plan,
                    preferences = submission,
                    onBack = {},
                    onCreateProject = {},
                    onOpenToday = {},
                    onRenamePlan = {},
                    onDeletePlan = {},
                    onConsumeMessage = {},
                    onApplyDeadline = {},
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
                effortMinMinutes = 45,
                effortLikelyMinutes = 45,
                effortMaxMinutes = 45,
                instructions = "Read the core rules.",
                topicIds = listOf("limits"),
                taskType = StudyTaskType.Concept,
            ),
            GeneratedStudyBlock(
                id = "practice",
                title = "Practice limits",
                order = 2,
                effortMinMinutes = 45,
                effortLikelyMinutes = 45,
                effortMaxMinutes = 45,
                instructions = "Solve five exercises.",
                topicIds = listOf("limits"),
                taskType = StudyTaskType.Practice,
            ),
        ),
        projectName = "Calculus",
    )

    private fun scheduledPlan() = plan().copy(
        sourceDocuments = listOf(StudySourceDocument("doc", "Calculus notes.pdf", order = 1, pageCount = 12)),
        blocks = listOf(
            GeneratedStudyBlock(
                id = "read",
                title = "Read limits",
                order = 1,
                effortMinMinutes = 45,
                effortLikelyMinutes = 45,
                effortMaxMinutes = 45,
                instructions = "Read the core rules.",
                topicIds = listOf("limits"),
                taskType = StudyTaskType.Concept,
                sourceRefs = listOf(StudySourceRef("doc", startPage = 2, endPage = 2, sectionTitle = "Limits", materialGroupTitle = "Functions and limits")),
            ),
            GeneratedStudyBlock(
                id = "practice",
                title = "Practice limits",
                order = 2,
                effortMinMinutes = 45,
                effortLikelyMinutes = 45,
                effortMaxMinutes = 45,
                instructions = "Solve five exercises.",
                topicIds = listOf("limits"),
                taskType = StudyTaskType.Practice,
                sourceRefs = listOf(StudySourceRef("doc", startPage = 3, endPage = 5, sectionTitle = "Limits", materialGroupTitle = "Functions and limits")),
            ),
        ),
    )

    private fun groupedFuturePlan() = plan().copy(
        sourceDocuments = listOf(StudySourceDocument("doc", "Calculus notes.pdf", order = 1, pageCount = 12)),
        blocks = listOf(
            GeneratedStudyBlock(
                id = "practice-grouped",
                title = "Practice grouped leaf",
                order = 1,
                effortMinMinutes = 45,
                effortLikelyMinutes = 45,
                effortMaxMinutes = 45,
                instructions = "Practice the first grouped leaf.",
                topicIds = listOf("limits"),
                taskType = StudyTaskType.Practice,
                status = StudyTaskStatus.Locked,
                scheduledDate = LocalDate.now().plusDays(1).toString(),
                sourceRefs = listOf(StudySourceRef("doc", startPage = 2, endPage = 3, sectionTitle = "Limits", materialGroupTitle = "Functions and limits")),
            ),
            GeneratedStudyBlock(
                id = "review-grouped",
                title = "Review grouped leaf",
                order = 2,
                effortMinMinutes = 45,
                effortLikelyMinutes = 45,
                effortMaxMinutes = 45,
                instructions = "Review the second grouped leaf.",
                topicIds = listOf("limits"),
                taskType = StudyTaskType.Review,
                status = StudyTaskStatus.Locked,
                scheduledDate = LocalDate.now().plusDays(1).toString(),
                sourceRefs = listOf(StudySourceRef("doc", startPage = 4, endPage = 5, sectionTitle = "Limits", materialGroupTitle = "Functions and limits")),
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
