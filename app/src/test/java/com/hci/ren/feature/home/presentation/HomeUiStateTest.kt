package com.hci.ren.feature.home.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun activePreviewContainsNextSessionAndMaterials() {
        val expected = HomeUiState(
            userName = "Aryan",
            greeting = "Good afternoon",
            content = HomeContent.Active(
                nextSession = NextSessionUiModel(
                    title = "Selection widgets",
                    pageRange = "Pages 6-7",
                    duration = "20 min",
                ),
                progress = DailyProgressUiModel(
                    focusTime = "1h 10m",
                    completedSessions = "2 done",
                    reviewsAdded = "1 topic",
                ),
                materials = listOf(
                    MaterialUiModel(
                        id = "widgets",
                        title = "02 - Widgets",
                        subtitle = "Human-Computer Interaction",
                        supportingText = "2h 5m left",
                        progressPercent = 42,
                    ),
                    MaterialUiModel(
                        id = "calculus-1",
                        title = "Calculus Chapter 1",
                        subtitle = "17 pages",
                        supportingText = "Not started",
                        progressPercent = 0,
                    ),
                ),
            ),
            isAddMaterialSheetVisible = false,
        )

        assertEquals(expected, HomePreviewData.active)
    }

    @Test
    fun emptyPreviewUsesEmptyContent() {
        val expected = HomeUiState(
            userName = "Aryan",
            greeting = "Good morning",
            content = HomeContent.Empty,
            isAddMaterialSheetVisible = false,
        )

        assertEquals(expected, HomePreviewData.empty)
    }
}
