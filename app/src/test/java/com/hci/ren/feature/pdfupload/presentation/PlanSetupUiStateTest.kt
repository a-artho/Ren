package com.hci.ren.feature.pdfupload.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanSetupUiStateTest {
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
}
