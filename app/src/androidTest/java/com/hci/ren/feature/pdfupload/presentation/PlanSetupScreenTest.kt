package com.hci.ren.feature.pdfupload.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.hci.ren.ui.theme.RenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlanSetupScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun goalStepRequiresSelectionBeforeNext() {
        var selectedGoal: StudyGoal? = null
        composeRule.setContent {
            RenTheme {
                PlanSetupScreen(
                    state = PlanSetupUiState(currentStep = PlanSetupStep.Goal),
                    onBack = {},
                    onGoalSelected = { selectedGoal = it },
                    onDeadlineSelected = {},
                    onDateSelected = {},
                    onDailyTimeSelected = {},
                    onCustomMinutesChanged = {},
                    onDayToggled = {},
                    onShortcutSelected = {},
                    onAdvancedControls = {},
                    onNext = {},
                    onGeneratePlan = {},
                )
            }
        }

        composeRule.onNodeWithText("1 OF 4").assertIsDisplayed()
        composeRule.onNodeWithTag("plan-next").assertIsNotEnabled()
        composeRule.onNodeWithText("Prepare for an exam").performClick()

        assertEquals(StudyGoal.PrepareForExam, selectedGoal)
    }

    @Test
    fun dailyTimeStepShowsCustomInput() {
        var customMinutes = ""
        composeRule.setContent {
            RenTheme {
                PlanSetupScreen(
                    state = PlanSetupUiState(
                        currentStep = PlanSetupStep.DailyTime,
                        selectedDailyTime = DailyStudyTime.Custom,
                    ),
                    onBack = {},
                    onGoalSelected = {},
                    onDeadlineSelected = {},
                    onDateSelected = {},
                    onDailyTimeSelected = {},
                    onCustomMinutesChanged = { customMinutes = it },
                    onDayToggled = {},
                    onShortcutSelected = {},
                    onAdvancedControls = {},
                    onNext = {},
                    onGeneratePlan = {},
                )
            }
        }

        composeRule.onNodeWithText("3 OF 4").assertIsDisplayed()
        composeRule.onNodeWithTag("custom-minutes").assertIsDisplayed()
        composeRule.onNodeWithTag("plan-next").assertIsNotEnabled()
        composeRule.onNodeWithTag("custom-minutes").performTextInput("90")

        assertEquals("90", customMinutes)
    }

    @Test
    fun studyDaysStepCanGenerateWhenDaysSelected() {
        var generated = false
        var shortcut: StudyDayShortcut? = null
        composeRule.setContent {
            RenTheme {
                PlanSetupScreen(
                    state = PlanSetupUiState(
                        currentStep = PlanSetupStep.StudyDays,
                        selectedDays = setOf(
                            StudyDay.Monday,
                            StudyDay.Tuesday,
                            StudyDay.Wednesday,
                            StudyDay.Thursday,
                            StudyDay.Friday,
                        ),
                    ),
                    onBack = {},
                    onGoalSelected = {},
                    onDeadlineSelected = {},
                    onDateSelected = {},
                    onDailyTimeSelected = {},
                    onCustomMinutesChanged = {},
                    onDayToggled = {},
                    onShortcutSelected = { shortcut = it },
                    onAdvancedControls = {},
                    onNext = {},
                    onGeneratePlan = { generated = true },
                )
            }
        }

        composeRule.onNodeWithText("4 OF 4").assertIsDisplayed()
        composeRule.onNodeWithText("Advanced controls").assertIsDisplayed()
        composeRule.onNodeWithText("Weekdays").performClick()
        composeRule.onNodeWithTag("generate-plan").assertIsEnabled().performClick()

        assertEquals(StudyDayShortcut.Weekdays, shortcut)
        assertEquals(true, generated)
    }

    @Test
    fun selectedOptionsExposeAccessibilityState() {
        composeRule.setContent {
            RenTheme {
                PlanSetupScreen(
                    state = PlanSetupUiState(
                        currentStep = PlanSetupStep.StudyDays,
                        selectedDays = StudyDay.entries.toSet(),
                    ),
                    onBack = {},
                    onGoalSelected = {},
                    onDeadlineSelected = {},
                    onDateSelected = {},
                    onDailyTimeSelected = {},
                    onCustomMinutesChanged = {},
                    onDayToggled = {},
                    onShortcutSelected = {},
                    onAdvancedControls = {},
                    onNext = {},
                    onGeneratePlan = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Monday").assertIsOn()
        composeRule.onNodeWithText("Every day").assertIsSelected()
    }
}
