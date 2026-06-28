package com.hci.ren.feature.pdfupload.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
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
    fun planTitleStepRequiresTextBeforeNext() {
        var title = ""
        composeRule.setContent {
            RenTheme {
                PlanSetupScreen(
                    state = PlanSetupUiState(currentStep = PlanSetupStep.PlanTitle),
                    onBack = {},
                    onPlanTitleChanged = { title = it },
                    onDeadlineSelected = {},
                    onDateSelected = {},
                    onDailyTimeSelected = {},
                    onCustomHoursChanged = {},
                    onCustomMinutesChanged = {},
                    onDayToggled = {},
                    onShortcutSelected = {},
                    onStudyDayResetOffsetSelected = {},
                    onNext = {},
                    onGeneratePlan = {},
                )
            }
        }

        composeRule.onNodeWithText("2 OF 5").assertIsDisplayed()
        composeRule.onNodeWithTag("plan-next").assertIsNotEnabled()
        composeRule.onNodeWithTag("plan-title").performTextInput("HCI final")

        assertEquals("HCI final", title)
    }

    @Test
    fun deadlineStepShowsOnlyActionableDeadlines() {
        composeRule.setContent {
            RenTheme {
                PlanSetupScreen(
                    state = PlanSetupUiState(currentStep = PlanSetupStep.Deadline),
                    onBack = {},
                    onPlanTitleChanged = {},
                    onDeadlineSelected = {},
                    onDateSelected = {},
                    onDailyTimeSelected = {},
                    onCustomHoursChanged = {},
                    onCustomMinutesChanged = {},
                    onDayToggled = {},
                    onShortcutSelected = {},
                    onStudyDayResetOffsetSelected = {},
                    onNext = {},
                    onGeneratePlan = {},
                )
            }
        }

        composeRule.onNodeWithText("When is it due?").assertIsDisplayed()
        composeRule.onAllNodesWithText("No fixed deadline").assertCountEquals(0)
    }

    @Test
    fun dailyTimeStepShowsCustomInput() {
        var setupState by mutableStateOf(
            PlanSetupUiState(
                currentStep = PlanSetupStep.DailyTime,
                selectedDailyTime = DailyStudyTime.Custom,
            ),
        )
        composeRule.setContent {
            RenTheme {
                PlanSetupScreen(
                    state = setupState,
                    onBack = {},
                    onPlanTitleChanged = {},
                    onDeadlineSelected = {},
                    onDateSelected = {},
                    onDailyTimeSelected = {},
                    onCustomHoursChanged = { setupState = setupState.copy(customHoursText = it) },
                    onCustomMinutesChanged = { setupState = setupState.copy(customMinutesText = it) },
                    onDayToggled = {},
                    onShortcutSelected = {},
                    onStudyDayResetOffsetSelected = {},
                    onNext = {},
                    onGeneratePlan = {},
                )
            }
        }

        composeRule.onNodeWithText("4 OF 5").assertIsDisplayed()
        composeRule.onNodeWithTag("custom-hours").assertIsDisplayed()
        composeRule.onNodeWithTag("custom-minutes").assertIsDisplayed()
        composeRule.onNodeWithTag("plan-next").assertIsNotEnabled()
        composeRule.onNodeWithTag("custom-hours").performTextInput("1")
        composeRule.onNodeWithTag("custom-minutes").performTextInput("30")

        composeRule.runOnIdle {
            assertEquals("1", setupState.customHoursText)
            assertEquals("30", setupState.customMinutesText)
        }
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
                        planTitle = "HCI final",
                        selectedDeadline = StudyDeadline.InOneWeek,
                        selectedDailyTime = DailyStudyTime.OneHour,
                        selectedDays = setOf(
                            StudyDay.Monday,
                            StudyDay.Tuesday,
                            StudyDay.Wednesday,
                            StudyDay.Thursday,
                            StudyDay.Friday,
                        ),
                    ),
                    onBack = {},
                    onPlanTitleChanged = {},
                    onDeadlineSelected = {},
                    onDateSelected = {},
                    onDailyTimeSelected = {},
                    onCustomHoursChanged = {},
                    onCustomMinutesChanged = {},
                    onDayToggled = {},
                    onShortcutSelected = { shortcut = it },
                    onStudyDayResetOffsetSelected = {},
                    onNext = {},
                    onGeneratePlan = { generated = true },
                )
            }
        }

        composeRule.onNodeWithText("5 OF 5").assertIsDisplayed()
        composeRule.onNodeWithText("5 days selected").assertIsDisplayed()
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
                    onPlanTitleChanged = {},
                    onDeadlineSelected = {},
                    onDateSelected = {},
                    onDailyTimeSelected = {},
                    onCustomHoursChanged = {},
                    onCustomMinutesChanged = {},
                    onDayToggled = {},
                    onShortcutSelected = {},
                    onStudyDayResetOffsetSelected = {},
                    onNext = {},
                    onGeneratePlan = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Monday").assertIsOn()
        composeRule.onNodeWithText("Every day").assertIsSelected()
    }
}
