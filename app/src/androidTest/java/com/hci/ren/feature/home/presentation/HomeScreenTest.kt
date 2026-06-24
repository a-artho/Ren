package com.hci.ren.feature.home.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hci.ren.ui.theme.RenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyHomeShowsUploadAndSampleActions() {
        composeRule.setContent {
            RenTheme {
                HomeScreen(
                    state = HomePreviewData.empty,
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Upload your first study material").assertIsDisplayed()
        composeRule.onNodeWithText("Upload PDF").assertIsDisplayed()
        composeRule.onNodeWithText("Try sample material").assertIsDisplayed()
    }

    @Test
    fun addButtonDispatchesAddMaterialAction() {
        var receivedAction: HomeAction? = null
        composeRule.setContent {
            RenTheme {
                HomeScreen(
                    state = HomePreviewData.empty,
                    onAction = { receivedAction = it },
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Add study material")
            .performClick()

        assertEquals(HomeAction.AddMaterialClicked, receivedAction)
    }

    @Test
    fun activeHomeShowsStudySessionProgressAndMaterials() {
        composeRule.setContent {
            RenTheme {
                HomeScreen(
                    state = HomePreviewData.active,
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Continue studying").assertIsDisplayed()
        composeRule.onNodeWithText("Selection widgets").assertIsDisplayed()
        composeRule.onNodeWithText("Today's progress").assertIsDisplayed()
        composeRule.onNodeWithText("02 - Widgets").assertIsDisplayed()
        composeRule.onNodeWithText("Calculus Chapter 1").assertIsDisplayed()
    }

    @Test
    fun startFocusDispatchesStartFocusAction() {
        var receivedAction: HomeAction? = null
        composeRule.setContent {
            RenTheme {
                HomeScreen(
                    state = HomePreviewData.active,
                    onAction = { receivedAction = it },
                )
            }
        }

        composeRule.onNodeWithTag("start-focus").performClick()

        assertEquals(HomeAction.StartFocusClicked, receivedAction)
    }

    @Test
    fun materialClickDispatchesMaterialAction() {
        var receivedAction: HomeAction? = null
        composeRule.setContent {
            RenTheme {
                HomeScreen(
                    state = HomePreviewData.active,
                    onAction = { receivedAction = it },
                )
            }
        }

        composeRule.onNodeWithTag("material-widgets").performClick()

        assertEquals(HomeAction.MaterialClicked("widgets"), receivedAction)
    }

    @Test
    fun visibleSheetUploadDispatchesUploadPdfAction() {
        var receivedAction: HomeAction? = null
        composeRule.setContent {
            RenTheme {
                HomeScreen(
                    state = HomePreviewData.active.copy(
                        isAddMaterialSheetVisible = true,
                    ),
                    onAction = { receivedAction = it },
                )
            }
        }

        composeRule.onNodeWithText("Add study material").assertIsDisplayed()
        composeRule.onNodeWithTag("sheet-upload-pdf").performClick()

        assertEquals(HomeAction.UploadPdfClicked, receivedAction)
    }

    @Test
    fun unavailableHomeActionShowsFeedbackInsteadOfFailingSilently() {
        composeRule.setContent {
            RenTheme {
                HomeRoute(onUploadPdf = {})
            }
        }

        composeRule.onNodeWithTag("start-focus").performClick()

        composeRule.onNodeWithText("This feature isn’t available yet.").assertIsDisplayed()
    }
}
