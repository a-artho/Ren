package com.hci.ren.feature.pdfupload.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.hci.ren.ui.theme.RenTheme
import org.junit.Rule
import org.junit.Test

class PdfUploadScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun continueIsDisabledUntilPdfLoads() {
        composeRule.setContent {
            RenTheme {
                PdfUploadScreen(
                    state = PdfUploadUiState(),
                    onBack = {},
                    onPickPdf = {},
                    onAddMorePdf = {},
                    onContinue = {},
                    onSelectPdf = {},
                    onRemovePdf = {},
                    onPageSelected = {},
                    onPageRequested = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("pdf-continue").assertIsNotEnabled()
    }

    @Test
    fun validPdfShowsFileCardAndEnablesContinue() {
        composeRule.setContent {
            RenTheme {
                PdfUploadScreen(
                    state = readyState(),
                    onBack = {},
                    onPickPdf = {},
                    onAddMorePdf = {},
                    onContinue = {},
                    onSelectPdf = {},
                    onRemovePdf = {},
                    onPageSelected = {},
                    onPageRequested = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("pdf-file-card").assertIsDisplayed()
        composeRule.onNodeWithText("Lecture notes.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("4 pages • 1.5 MB").assertIsDisplayed()
        composeRule.onNodeWithTag("pdf-continue").assertIsEnabled()
        composeRule.onNodeWithText("Next we'll customize how you want to study.").assertIsDisplayed()
    }

    @Test
    fun thumbnailsIncludeEveryPage() {
        composeRule.setContent {
            RenTheme {
                PdfUploadScreen(
                    state = readyState(selectedPageIndex = 1),
                    onBack = {},
                    onPickPdf = {},
                    onAddMorePdf = {},
                    onContinue = {},
                    onSelectPdf = {},
                    onRemovePdf = {},
                    onPageSelected = {},
                    onPageRequested = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("pdf-thumbnail-0").assertIsDisplayed()
        composeRule.onNodeWithTag("pdf-thumbnail-1").assertIsDisplayed()
        composeRule.onNodeWithTag("pdf-thumbnail-2").assertIsDisplayed()
    }

    private fun readyState(selectedPageIndex: Int = 0) = PdfUploadUiState(
        documentGroup = DocumentGroup(
            documents = listOf(PdfDocumentUiModel(
                uri = "content://ren/document",
                fileName = "Lecture notes.pdf",
                sizeBytes = 1_572_864,
                pageCount = 4,
            )),
        ),
        selectedPageIndex = selectedPageIndex,
        loadStatus = PdfLoadStatus.Ready,
    )
}
