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
        original.beginNewSession(listOf("content://ren/doc"))
        original.updatePlanTitle("HCI final")
        original.selectDeadline(StudyDeadline.InOneWeek)
        original.selectDailyTime(DailyStudyTime.FiveHours)
        original.toggleStudyDay(StudyDay.Monday)
        original.toggleStudyDay(StudyDay.Friday)
        original.goNext() // Goal -> Deadline

        // Simulate process death: create new ViewModel with same handle contents
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it)!! })
        val restored = PlanSetupViewModel(restoredHandle)
        val state = restored.uiState.value

        assertEquals(listOf("content://ren/doc"), state.documentUris)
        assertEquals(PlanSetupStep.Deadline, state.currentStep)
        assertEquals("HCI final", state.planTitle)
        assertEquals(StudyDeadline.InOneWeek, state.selectedDeadline)
        assertEquals(DailyStudyTime.FiveHours, state.selectedDailyTime)
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
                nowMillis = 1_772_496_000_000L,
            ) // 2026-03-04T00:00:00Z

            assertEquals("2026-03-04", viewModel.uiState.value.customDeadlineDate)
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun beginNewSessionClearsAnswersEvenForSameDocument() {
        val viewModel = PlanSetupViewModel(SavedStateHandle())
        viewModel.setDocuments(listOf("content://ren/document"))
        viewModel.updatePlanTitle("HCI final")

        viewModel.beginNewSession(listOf("content://ren/document"))

        assertEquals(listOf("content://ren/document"), viewModel.uiState.value.documentUris)
        assertEquals("", viewModel.uiState.value.planTitle)
    }
    @Test
    fun nextRequiresValidSelectionForEachStep() {
        assertFalse(PlanSetupUiState(currentStep = PlanSetupStep.PlanTitle).canContinue)
        assertFalse(PlanSetupUiState(currentStep = PlanSetupStep.Deadline).canContinue)
        assertFalse(PlanSetupUiState(currentStep = PlanSetupStep.DailyTime).canContinue)
        assertFalse(PlanSetupUiState(currentStep = PlanSetupStep.StudyDays).canContinue)

        assertTrue(
            PlanSetupUiState(
                currentStep = PlanSetupStep.PlanTitle,
                planTitle = "HCI final",
            ).canContinue,
        )
        assertTrue(
            PlanSetupUiState(
                currentStep = PlanSetupStep.Deadline,
                selectedDeadline = StudyDeadline.InOneWeek,
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
                planTitle = "HCI final",
                selectedDeadline = StudyDeadline.InOneWeek,
                selectedDailyTime = DailyStudyTime.OneHour,
                selectedDays = setOf(StudyDay.Monday),
            ).canContinue,
        )
    }

    @Test
    fun customTimeRequiresPositiveTotalMinutes() {
        val state = PlanSetupUiState(
            currentStep = PlanSetupStep.DailyTime,
            selectedDailyTime = DailyStudyTime.Custom,
        )

        assertFalse(state.canContinue)
        assertFalse(state.copy(customMinutesText = "0").canContinue)
        assertFalse(state.copy(customMinutesText = "abc").canContinue)
        assertFalse(state.copy(customHoursText = "25").canContinue)
        assertFalse(state.copy(customMinutesText = "60").canContinue)
        assertTrue(state.copy(customHoursText = "1").canContinue)
        assertTrue(state.copy(customMinutesText = "45").canContinue)
        assertTrue(state.copy(customHoursText = "2", customMinutesText = "30").canContinue)
        assertEquals("Enter hours, minutes, or both.", state.customTimeError)
        assertEquals("Minutes should be 0-59.", state.copy(customMinutesText = "60").customTimeError)
        assertEquals("Needs at least 1 minute. Tiny, but still something.", state.copy(customHoursText = "0").customTimeError)
    }

    @Test
    fun studyDaysMustFitBeforeDeadline() {
        val now = 1_772_625_600_000L // 2026-03-04T12:00:00Z, Wednesday
        val state = PlanSetupUiState(
            currentStep = PlanSetupStep.StudyDays,
            planTitle = "HCI final",
            selectedDeadline = StudyDeadline.Tomorrow,
            selectedDailyTime = DailyStudyTime.OneHour,
            selectedDays = setOf(StudyDay.Sunday),
        )

        assertFalse(state.canContinueAt(nowMillis = now, localTimeZone = TimeZone.getTimeZone("UTC")))
        assertEquals(
            "No picked day lands before the deadline. Tiny issue.",
            state.studyDaysDeadlineError(nowMillis = now, localTimeZone = TimeZone.getTimeZone("UTC")),
        )
        assertFalse(
            state.copy(selectedDays = setOf(StudyDay.Thursday))
                .canContinueAt(nowMillis = now, localTimeZone = TimeZone.getTimeZone("UTC")),
        )
        assertTrue(
            state.copy(selectedDays = setOf(StudyDay.Wednesday))
                .canContinueAt(nowMillis = now, localTimeZone = TimeZone.getTimeZone("UTC")),
        )
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
            documentUris = listOf("content://ren/document"),
            currentStep = PlanSetupStep.StudyDays,
            planTitle = "HCI final",
            selectedDeadline = StudyDeadline.ChooseDate,
            customDeadlineDate = "2099-06-21",
            selectedDailyTime = DailyStudyTime.Custom,
            customHoursText = "1",
            customMinutesText = "15",
            selectedDays = setOf(StudyDay.Monday, StudyDay.Wednesday),
        )

        val submission = state.toSubmission()

        assertEquals("content://ren/document", submission?.documentUris?.first())
        assertEquals(StudyGoal.PrepareForExam, submission?.goal)
        assertEquals("HCI final", submission?.planTitle)
        assertEquals(StudyDeadline.ChooseDate, submission?.deadline)
        assertEquals("2099-06-21", submission?.deadlineDate)
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
    fun currentLocalDayIsNotSelectable() {
        val selectedDay = 1_772_582_400_000L // 2026-03-04T00:00:00Z
        val now = 1_772_625_600_000L // 2026-03-04T12:00:00Z

        assertFalse(isSelectableDeadlineUtc(selectedDay, now))
    }

    @Test
    fun localTodayWestOfUtcIsNotSelectable() {
        val selectedDay = 1_772_496_000_000L // 2026-03-03T00:00:00Z
        val now = 1_772_604_000_000L // 2026-03-04T06:00:00Z, still Mar 3 in Los Angeles

        assertFalse(
            isSelectableDeadlineUtc(
                selectedMillis = selectedDay,
                nowMillis = now,
                localTimeZone = TimeZone.getTimeZone("America/Los_Angeles"),
            ),
        )
    }
}
