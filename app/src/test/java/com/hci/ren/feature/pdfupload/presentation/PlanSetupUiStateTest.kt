package com.hci.ren.feature.pdfupload.presentation

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class PlanSetupUiStateTest {
    @Test
    fun setupSelectionsAreRestoredFromSavedStateHandle() {
        val handle = SavedStateHandle()
        val original = PlanSetupViewModel(handle)
        original.beginNewSession("content://ren/doc")
        original.selectGoal(StudyGoal.PrepareForExam)
        original.selectDeadline(StudyDeadline.InOneWeek)
        original.selectDailyTime(DailyStudyTime.FortyFiveMinutes)
        original.toggleStudyDay(StudyDay.Monday)
        original.toggleStudyDay(StudyDay.Friday)
        original.goNext() // Goal -> Deadline

        // Simulate process death: create new ViewModel with same handle contents
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it)!! })
        val restored = PlanSetupViewModel(restoredHandle)
        val state = restored.uiState.value

        assertEquals("content://ren/doc", state.documentUri)
        assertEquals(PlanSetupStep.Deadline, state.currentStep)
        assertEquals(StudyGoal.PrepareForExam, state.selectedGoal)
        assertEquals(StudyDeadline.InOneWeek, state.selectedDeadline)
        assertEquals(DailyStudyTime.FortyFiveMinutes, state.selectedDailyTime)
        assertEquals(setOf(StudyDay.Monday, StudyDay.Friday), state.selectedDays)
    }

    @Test
    fun datePickerUtcMillisAreNotShiftedByLocalTimeZone() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val viewModel = PlanSetupViewModel(SavedStateHandle())

            viewModel.selectCustomDate(
                epochMillis = 1_772_582_400_000L,
                nowMillis = 1_772_582_400_000L,
            ) // 2026-03-04T00:00:00Z

            assertEquals("2026-03-04", viewModel.uiState.value.customDeadlineDate)
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun beginNewSessionClearsAnswersEvenForSameDocument() {
        val viewModel = PlanSetupViewModel(SavedStateHandle())
        viewModel.setDocument("content://ren/document")
        viewModel.selectGoal(StudyGoal.PrepareForExam)

        viewModel.beginNewSession("content://ren/document")

        assertEquals("content://ren/document", viewModel.uiState.value.documentUri)
        assertEquals(null, viewModel.uiState.value.selectedGoal)
    }
    @Test
    fun nextRequiresValidSelectionForEachStep() {
        assertFalse(PlanSetupUiState(currentStep = PlanSetupStep.Goal).canContinue)
        assertFalse(PlanSetupUiState(currentStep = PlanSetupStep.Deadline).canContinue)
        assertFalse(PlanSetupUiState(currentStep = PlanSetupStep.DailyTime).canContinue)
        assertFalse(PlanSetupUiState(currentStep = PlanSetupStep.StudyDays).canContinue)

        assertTrue(
            PlanSetupUiState(
                currentStep = PlanSetupStep.Goal,
                selectedGoal = StudyGoal.PrepareForExam,
            ).canContinue,
        )
        assertTrue(
            PlanSetupUiState(
                currentStep = PlanSetupStep.Deadline,
                selectedDeadline = StudyDeadline.NoFixedDeadline,
            ).canContinue,
        )
        assertTrue(
            PlanSetupUiState(
                currentStep = PlanSetupStep.DailyTime,
                selectedDailyTime = DailyStudyTime.OneHour,
            ).canContinue,
        )
        assertTrue(
            PlanSetupUiState(
                currentStep = PlanSetupStep.StudyDays,
                selectedDays = setOf(StudyDay.Monday),
            ).canContinue,
        )
    }

    @Test
    fun customTimeRequiresPositiveMinutes() {
        val state = PlanSetupUiState(
            currentStep = PlanSetupStep.DailyTime,
            selectedDailyTime = DailyStudyTime.Custom,
        )

        assertFalse(state.canContinue)
        assertFalse(state.copy(customMinutesText = "0").canContinue)
        assertFalse(state.copy(customMinutesText = "abc").canContinue)
        assertTrue(state.copy(customMinutesText = "90").canContinue)
    }

    @Test
    fun shortcutSelectionsReplaceStudyDays() {
        assertEquals(
            StudyDay.entries.toSet(),
            daysForShortcut(StudyDayShortcut.EveryDay),
        )
        assertEquals(
            setOf(
                StudyDay.Monday,
                StudyDay.Tuesday,
                StudyDay.Wednesday,
                StudyDay.Thursday,
                StudyDay.Friday,
            ),
            daysForShortcut(StudyDayShortcut.Weekdays),
        )
        assertEquals(
            setOf(StudyDay.Saturday, StudyDay.Sunday),
            daysForShortcut(StudyDayShortcut.Weekends),
        )
    }

    @Test
    fun completedSubmissionIncludesPdfReferenceAndSetupData() {
        val state = PlanSetupUiState(
            documentUri = "content://ren/document",
            currentStep = PlanSetupStep.StudyDays,
            selectedGoal = StudyGoal.PrepareForExam,
            selectedDeadline = StudyDeadline.ChooseDate,
            customDeadlineDate = "2026-06-21",
            selectedDailyTime = DailyStudyTime.Custom,
            customMinutesText = "75",
            selectedDays = setOf(StudyDay.Monday, StudyDay.Wednesday),
        )

        val submission = state.toSubmission()

        assertEquals("content://ren/document", submission?.documentUri)
        assertEquals(StudyGoal.PrepareForExam, submission?.goal)
        assertEquals(StudyDeadline.ChooseDate, submission?.deadline)
        assertEquals("2026-06-21", submission?.deadlineDate)
        assertEquals(75, submission?.dailyStudyMinutes)
        assertEquals(setOf(StudyDay.Monday, StudyDay.Wednesday), submission?.studyDays)
    }

    @Test
    fun pastCustomDeadlineIsRejectedAtViewModelBoundary() {
        val selectedDay = 1_772_496_000_000L // 2026-03-03T00:00:00Z
        val now = 1_772_625_600_000L // 2026-03-04T12:00:00Z
        val viewModel = PlanSetupViewModel(SavedStateHandle())

        viewModel.selectCustomDate(selectedDay, nowMillis = now)

        assertEquals(null, viewModel.uiState.value.customDeadlineDate)
    }

    @Test
    fun currentLocalDayIsSelectable() {
        val selectedDay = 1_772_582_400_000L // 2026-03-04T00:00:00Z
        val now = 1_772_625_600_000L // 2026-03-04T12:00:00Z

        assertTrue(isSelectableDeadlineUtc(selectedDay, now))
    }

    @Test
    fun localTodayWestOfUtcIsNotRejectedAsYesterday() {
        val selectedDay = 1_772_496_000_000L // 2026-03-03T00:00:00Z
        val now = 1_772_604_000_000L // 2026-03-04T06:00:00Z, still Mar 3 in Los Angeles

        assertTrue(
            isSelectableDeadlineUtc(
                selectedMillis = selectedDay,
                nowMillis = now,
                localTimeZone = TimeZone.getTimeZone("America/Los_Angeles"),
            ),
        )
    }
}
