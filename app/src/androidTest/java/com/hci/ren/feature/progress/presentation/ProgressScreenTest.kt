package com.hci.ren.feature.progress.presentation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.hci.ren.MainActivity
import com.hci.ren.feature.studymap.FocusSessionOutcome
import com.hci.ren.ui.theme.RenTheme
import org.junit.Rule
import org.junit.Test

class ProgressScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun progressScreenShowsDynamicWeeklyFocusAxis() {
        composeRule.activity.setContent {
            RenTheme {
                ProgressScreen(
                    project = ProgressScreenFixtures.project(
                        dailyStudyMinutes = 45,
                        focusHistory = mapOf(
                            "2026-07-06" to listOf(ProgressScreenFixtures.focusRecord(focusSeconds = 1_800)),
                        ),
                    ),
                    today = "2026-07-06",
                )
            }
        }

        composeRule.onNodeWithText("Weekly focus").assertIsDisplayed()
        composeRule.onNodeWithText("15m").assertIsDisplayed()
        composeRule.onNodeWithText("1h").assertIsDisplayed()
    }

    @Test
    fun progressScreenShowsStudyConsistencyFromFocusHistory() {
        composeRule.activity.setContent {
            RenTheme {
                ProgressScreen(
                    project = ProgressScreenFixtures.project(
                        focusHistory = mapOf(
                            "2026-07-07" to listOf(ProgressScreenFixtures.focusRecord(focusSeconds = 3_600)),
                            "2026-07-08" to listOf(ProgressScreenFixtures.focusRecord(focusSeconds = 3_600)),
                            "2026-07-09" to listOf(ProgressScreenFixtures.focusRecord(focusSeconds = 7_200)),
                            "2026-06-30" to listOf(ProgressScreenFixtures.focusRecord(focusSeconds = 1_800)),
                            "2026-07-01" to listOf(ProgressScreenFixtures.focusRecord(focusSeconds = 1_800)),
                            "2026-07-03" to listOf(ProgressScreenFixtures.focusRecord(focusSeconds = 1_800)),
                            "2026-07-05" to listOf(ProgressScreenFixtures.focusRecord(focusSeconds = 1_800)),
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

    @Test
    fun progressScreenShowsBestRhythmFromAdaptiveFocusHistory() {
        composeRule.activity.setContent {
            RenTheme {
                ProgressScreen(
                    project = ProgressScreenFixtures.project(
                        focusHistory = mapOf(
                            "2026-07-07" to listOf(
                                ProgressScreenFixtures.focusRecord(plannedFocusMinutes = 10, focusSeconds = 600),
                                ProgressScreenFixtures.focusRecord(
                                    plannedFocusMinutes = 10,
                                    focusSeconds = 360,
                                    outcome = FocusSessionOutcome.FocusStopped,
                                ),
                            ),
                            "2026-07-08" to listOf(
                                ProgressScreenFixtures.focusRecord(plannedFocusMinutes = 15, focusSeconds = 900),
                                ProgressScreenFixtures.focusRecord(plannedFocusMinutes = 15, focusSeconds = 960),
                                ProgressScreenFixtures.focusRecord(
                                    plannedFocusMinutes = 15,
                                    focusSeconds = 900,
                                    interruptionCount = 1,
                                ),
                            ),
                        ),
                    ),
                    today = "2026-07-09",
                )
            }
        }

        composeRule.onNodeWithTag("best-rhythm-card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Best rhythm").assertIsDisplayed()
        composeRule.onNodeWithText("Clean round success rate").assertIsDisplayed()
        composeRule.onNodeWithText("67%").assertIsDisplayed()
        composeRule.onNodeWithText("3 rounds").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "15m is your best rhythm at 67% clean success.",
        ).assertIsDisplayed()
    }
}
