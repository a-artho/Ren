package com.hci.ren.feature.studymap

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hci.ren.ui.theme.RenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StudyMapLibraryScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun populatedLibraryShowsControlsAndOpensCorrectProject() {
        var opened = ""
        setScreen(state(projects = listOf(summary()))) { opened = it }

        composeRule.onNodeWithText("Study Map").assertIsDisplayed()
        composeRule.onNodeWithText("Search study maps").assertIsDisplayed()
        composeRule.onNodeWithText("All").assertIsDisplayed()
        composeRule.onNodeWithText("Recently updated").assertIsDisplayed()
        composeRule.onNodeWithText("Computer Architecture CA1").performClick()

        assertEquals("project", opened)
    }

    @Test fun emptyLibraryReusesCreateProjectAction() {
        var created = false
        setScreen(StudyMapLibraryUiState(isLoading = false)) { }
        composeRule.onNodeWithText("No study maps yet").assertIsDisplayed()
        composeRule.onNodeWithText("Create project").assertIsDisplayed()
    }

    @Test fun deleteRequiresConfirmationAndReturnsProjectId() {
        var deleted = ""
        composeRule.setContent {
            RenTheme { TestScreen(state(projects = listOf(summary())), onOpen = {}, onDelete = { deleted = it }) }
        }

        composeRule.onNodeWithContentDescription("Study map actions").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Delete study map?").assertIsDisplayed()
        assertTrue(deleted.isEmpty())
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals("project", deleted)
    }

    private fun setScreen(state: StudyMapLibraryUiState, onOpen: (String) -> Unit) {
        composeRule.setContent { RenTheme { TestScreen(state, onOpen) } }
    }

    @androidx.compose.runtime.Composable
    private fun TestScreen(
        state: StudyMapLibraryUiState,
        onOpen: (String) -> Unit,
        onDelete: (String) -> Unit = {},
    ) = StudyMapLibraryScreen(
        state = state,
        onQueryChange = {},
        onFilterChange = {},
        onSortChange = {},
        onClearSearchAndFilter = {},
        onOpenProject = onOpen,
        onDeleteProject = onDelete,
        onRetry = {},
        onConsumeMessage = {},
        onHome = {},
        onCreateProject = {},
        onInsights = {},
    )

    private fun state(projects: List<StudyProjectSummary>) = StudyMapLibraryUiState(
        isLoading = false,
        allProjectsCount = projects.size,
        projects = projects,
    )

    private fun summary() = StudyProjectSummary(
        id = "project",
        title = "Computer Architecture CA1",
        createdAtMillis = 1,
        updatedAtMillis = 2,
        deadlineAtMillis = null,
        completedTasks = 2,
        totalTasks = 5,
        progress = .4f,
        status = StudyProjectStatus.Active,
    )
}
