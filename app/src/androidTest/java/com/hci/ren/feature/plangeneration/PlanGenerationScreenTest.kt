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

    @Test fun uploadSubtitleIsActiveDuringUpload() {
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

        composeRule.onNodeWithText(uploadingDocStr).assertDoesNotExist()
        composeRule.onNodeWithText(sendingFileStr).assertIsDisplayed()
        composeRule.onNodeWithText(readingMaterialStr).assertDoesNotExist()
        composeRule.onNodeWithText(analyzingContentStr).assertDoesNotExist()
    }

    @Test fun uploadSubtitleDoesNotShowPdfCountProgress() {
        composeRule.setContent {
            RenTheme {
                PlanGenerationScreen(
                    PlanGenerationUiState(
                        status = PlanStatus.Uploading,
                        uploadingDocumentIndex = 2,
                        uploadingDocumentTotal = 5,
                    ),
                    onBack = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.step_subtitle_uploading)).assertIsDisplayed()
        composeRule.onNodeWithText("Uploading PDF 2 of 5").assertDoesNotExist()
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

        composeRule.onNodeWithText(uploadingDocStr).assertDoesNotExist()
        composeRule.onNodeWithText(sendingFileStr).assertDoesNotExist()
        composeRule.onNodeWithText(identifyingTopicsStr).assertDoesNotExist()
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
