package com.hci.ren.feature.plangeneration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.assertTrue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hci.ren.R
import com.hci.ren.ui.theme.RenTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanGenerationScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun uploadRowIsActiveDuringUpload() {
        composeRule.setContent {
            RenTheme {
                PlanGenerationScreen(
                    PlanGenerationUiState(status = PlanStatus.Uploading),
                    onBack = {},
                    onRetry = {}
                )
            }
        }
        val uploadingDocStr = context.getString(R.string.uploading_document)
        val sendingFileStr = context.getString(R.string.step_subtitle_uploading)
        val readingMaterialStr = context.getString(R.string.reading_material)
        val analyzingContentStr = context.getString(R.string.step_subtitle_analyzing)

        // Uploading document should be displayed and show its active subtitle
        composeRule.onNodeWithText(uploadingDocStr).assertIsDisplayed()
        composeRule.onNodeWithText(sendingFileStr).assertIsDisplayed()

        // Subsequent steps should be displayed but NOT their active subtitles
        composeRule.onNodeWithText(readingMaterialStr).assertIsDisplayed()
        composeRule.onNodeWithText(analyzingContentStr).assertDoesNotExist()
    }

    @Test fun backendStageIsActiveAfterUploadCompletes() {
        composeRule.setContent {
            RenTheme {
                PlanGenerationScreen(
                    PlanGenerationUiState(status = PlanStatus.IdentifyingTopics),
                    onBack = {},
                    onRetry = {}
                )
            }
        }
        val uploadingDocStr = context.getString(R.string.uploading_document)
        val sendingFileStr = context.getString(R.string.step_subtitle_uploading)
        val identifyingTopicsStr = context.getString(R.string.identifying_topics)
        val analyzingContentStr = context.getString(R.string.step_subtitle_identifying)

        // Uploading step is complete, so its label is visible but its active subtitle is hidden
        composeRule.onNodeWithText(uploadingDocStr).assertIsDisplayed()
        composeRule.onNodeWithText(sendingFileStr).assertDoesNotExist()

        // IdentifyingTopics is active, so its label and active subtitle are visible
        composeRule.onNodeWithText(identifyingTopicsStr).assertIsDisplayed()
        composeRule.onNodeWithText(analyzingContentStr).assertIsDisplayed()
    }

    @Test fun failureShowsWorkingRetryAndBackActions() {
        var retried = false
        var backed = false
        val customError = "Custom backend failure message."
        composeRule.setContent {
            RenTheme {
                PlanGenerationScreen(
                    PlanGenerationUiState(status = PlanStatus.Failed, errorMessage = customError),
                    onBack = { backed = true },
                    onRetry = { retried = true },
                )
            }
        }
        composeRule.onNodeWithText(customError).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.retry)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        assertTrue(retried)
        assertTrue(backed)
    }

    @Test fun completedPlanRendersOrderedContentAndDurations() {
        val plan = GeneratedStudyPlan(
            id = "plan", totalEstimatedMinutes = 45,
            topics = listOf(StudyTopic("t1", "Foundations", 1)),
            blocks = listOf(GeneratedStudyBlock("b1", "Review", 1, 45, "Summarize the chapter.", listOf("t1"))),
        )
        composeRule.setContent { RenTheme { PlanDetailsScreen(plan, {}) } }
        composeRule.onNodeWithText("1. Foundations").assertIsDisplayed()
        composeRule.onNodeWithText("Review").assertIsDisplayed()
        composeRule.onNodeWithText("Block 1").assertIsDisplayed()
        
        val totalMinutesStr = context.resources.getQuantityString(R.plurals.scheduled_minutes, 45, 45)
        val blockMinutesStr = context.resources.getQuantityString(R.plurals.block_minutes, 45, 45)
        composeRule.onNodeWithText(totalMinutesStr).assertIsDisplayed()
        composeRule.onNodeWithText(blockMinutesStr).assertIsDisplayed()
        composeRule.onNodeWithText("Summarize the chapter.").assertIsDisplayed()
    }

    @Test fun postponedTasksAreSeparatedAndOnlyActiveTasksAreNumbered() {
        val plan = GeneratedStudyPlan(
            id = "plan",
            topics = emptyList(),
            blocks = listOf(
                GeneratedStudyBlock("active", "Active task", 8, 45, "Study", emptyList()),
                GeneratedStudyBlock("later", "Later task", 15, 45, "Study later", emptyList(), disposition = TaskDisposition.Postponed),
            ),
            totalEstimatedMinutes = 45,
        )
        composeRule.setContent { RenTheme { PlanDetailsScreen(plan) {} } }

        composeRule.onNodeWithText(context.getString(R.string.scheduled_now)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.postponed)).assertIsDisplayed()
        composeRule.onNodeWithText("Block 1").assertIsDisplayed()
        composeRule.onNodeWithText("Block 8").assertDoesNotExist()
        composeRule.onNodeWithText("Block 15").assertDoesNotExist()
    }

    @Test fun systemBackLeavesFailedScreen() {
        var backed = false
        composeRule.setContent {
            RenTheme {
                PlanGenerationScreen(
                    PlanGenerationUiState(status = PlanStatus.Failed),
                    onBack = { backed = true },
                    onRetry = {},
                )
            }
        }

        pressBack()
        composeRule.runOnIdle { assertTrue(backed) }
    }

    @Test fun systemBackLeavesPlanDetails() {
        var backed = false
        val plan = GeneratedStudyPlan("plan", emptyList(), emptyList(), 0)
        composeRule.setContent { RenTheme { PlanDetailsScreen(plan) { backed = true } } }

        pressBack()
        composeRule.runOnIdle { assertTrue(backed) }
    }

    @Test fun backButtonShowsConfirmationDialogDuringProcessing() {
        var backed = false
        composeRule.setContent {
            RenTheme {
                PlanGenerationScreen(
                    PlanGenerationUiState(status = PlanStatus.Analyzing),
                    onBack = { backed = true },
                    onRetry = {}
                )
            }
        }
        
        // Click back button
        composeRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        
        // Back should NOT have been called yet
        org.junit.Assert.assertFalse(backed)
        
        // Confirmation dialog should be displayed
        val dialogTitle = context.getString(R.string.cancel_generation_title)
        val dialogMessage = context.getString(R.string.cancel_generation_message)
        val confirmBtnStr = context.getString(R.string.cancel_generation_confirm)
        val dismissBtnStr = context.getString(R.string.cancel_generation_dismiss)
        
        composeRule.onNodeWithText(dialogTitle).assertIsDisplayed()
        composeRule.onNodeWithText(dialogMessage).assertIsDisplayed()
        
        // If we click dismiss, dialog disappears and back is not called
        composeRule.onNodeWithText(dismissBtnStr).performClick()
        composeRule.onNodeWithText(dialogTitle).assertDoesNotExist()
        org.junit.Assert.assertFalse(backed)
        
        // Click back button again
        composeRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        
        // If we click confirm, dialog disappears and back IS called
        composeRule.onNodeWithText(confirmBtnStr).performClick()
        composeRule.onNodeWithText(dialogTitle).assertDoesNotExist()
        assertTrue(backed)
    }
}
