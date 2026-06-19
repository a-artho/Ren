package com.hci.ren.feature.pdfupload.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlanSetupViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PlanSetupUiState())
    val uiState: StateFlow<PlanSetupUiState> = _uiState.asStateFlow()

    fun setDocument(documentUri: String) {
        _uiState.update { state ->
            if (state.documentUri == documentUri) {
                state
            } else {
                PlanSetupUiState(documentUri = documentUri)
            }
        }
    }

    fun selectGoal(goal: StudyGoal) {
        _uiState.update { it.copy(selectedGoal = goal) }
    }

    fun selectDeadline(deadline: StudyDeadline) {
        _uiState.update { state ->
            state.copy(
                selectedDeadline = deadline,
                customDeadlineDate = if (deadline == StudyDeadline.ChooseDate) {
                    state.customDeadlineDate
                } else {
                    null
                },
                customDeadlineLabel = if (deadline == StudyDeadline.ChooseDate) {
                    state.customDeadlineLabel
                } else {
                    null
                },
            )
        }
    }

    fun selectCustomDate(epochMillis: Long) {
        _uiState.update { state ->
            state.copy(
                selectedDeadline = StudyDeadline.ChooseDate,
                customDeadlineDate = isoDateFormat.format(Date(epochMillis)),
                customDeadlineLabel = displayDateFormat.format(Date(epochMillis)),
            )
        }
    }

    fun selectDailyTime(time: DailyStudyTime) {
        _uiState.update { it.copy(selectedDailyTime = time) }
    }

    fun updateCustomMinutes(value: String) {
        _uiState.update {
            it.copy(customMinutesText = value.filter(Char::isDigit).take(3))
        }
    }

    fun toggleStudyDay(day: StudyDay) {
        _uiState.update { state ->
            val days = if (day in state.selectedDays) {
                state.selectedDays - day
            } else {
                state.selectedDays + day
            }
            state.copy(selectedDays = days)
        }
    }

    fun selectShortcut(shortcut: StudyDayShortcut) {
        _uiState.update { it.copy(selectedDays = daysForShortcut(shortcut)) }
    }

    fun showAdvancedMessage() {
        _uiState.update { it.copy(isAdvancedMessageVisible = true) }
    }

    fun goBack(): Boolean {
        val step = _uiState.value.currentStep
        if (step == PlanSetupStep.Goal) return false

        _uiState.update {
            it.copy(currentStep = PlanSetupStep.entries[step.ordinal - 1])
        }
        return true
    }

    fun goNext() {
        val state = _uiState.value
        if (!state.canContinue || state.currentStep == PlanSetupStep.StudyDays) return

        _uiState.update {
            it.copy(currentStep = PlanSetupStep.entries[state.currentStep.ordinal + 1])
        }
    }

    fun generatePlan(): PlanSetupSubmission? {
        val submission = _uiState.value.toSubmission() ?: return null
        _uiState.update { it.copy(generatedSubmission = submission) }
        return submission
    }

    private companion object {
        val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayDateFormat = SimpleDateFormat("d MMM", Locale.US)
    }
}

