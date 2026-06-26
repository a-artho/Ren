package com.hci.ren.feature.pdfupload.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class PlanSetupViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(restoreState())
    val uiState: StateFlow<PlanSetupUiState> = _uiState.asStateFlow()

    fun setDocuments(documentUris: List<String>) {
        _uiState.update { state ->
            if (state.documentUris == documentUris) {
                state
            } else {
                PlanSetupUiState(documentUris = documentUris).also(::persistState)
            }
        }
    }

    fun beginNewSession(documentUris: List<String>) {
        savedStateHandle.keys().forEach { savedStateHandle.remove<Any>(it) }
        val state = PlanSetupUiState(documentUris = documentUris)
        _uiState.value = state
        persistState(state)
    }

    fun updatePlanTitle(value: String) {
        updateAndPersist { it.copy(planTitle = value.take(80)) }
    }

    fun selectDeadline(deadline: StudyDeadline) {
        updateAndPersist { state ->
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

    fun selectCustomDate(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()) {
        if (!isSelectableDeadlineUtc(epochMillis, nowMillis)) return
        updateAndPersist { state ->
            state.copy(
                selectedDeadline = StudyDeadline.ChooseDate,
                customDeadlineDate = isoDateFormat.format(Date(epochMillis)),
                customDeadlineLabel = displayDateFormat.format(Date(epochMillis)),
            )
        }
    }

    fun selectDailyTime(time: DailyStudyTime) {
        updateAndPersist { it.copy(selectedDailyTime = time) }
    }

    fun updateCustomMinutes(value: String) {
        updateAndPersist {
            it.copy(customMinutesText = value.filter(Char::isDigit).take(2))
        }
    }

    fun updateCustomHours(value: String) {
        updateAndPersist {
            it.copy(customHoursText = value.filter(Char::isDigit).take(2))
        }
    }

    fun toggleStudyDay(day: StudyDay) {
        updateAndPersist { state ->
            val days = if (day in state.selectedDays) {
                state.selectedDays - day
            } else {
                state.selectedDays + day
            }
            state.copy(selectedDays = days)
        }
    }

    fun selectShortcut(shortcut: StudyDayShortcut) {
        updateAndPersist { it.copy(selectedDays = daysForShortcut(shortcut)) }
    }

    fun goBack(): Boolean {
        val step = _uiState.value.currentStep
        if (step == PlanSetupStep.PlanTitle) return false

        updateAndPersist {
            it.copy(currentStep = PlanSetupStep.entries[step.ordinal - 1])
        }
        return true
    }

    fun goNext() {
        val state = _uiState.value
        if (!state.canContinue || state.currentStep == PlanSetupStep.StudyDays) return

        updateAndPersist {
            it.copy(currentStep = PlanSetupStep.entries[state.currentStep.ordinal + 1])
        }
    }

    fun generatePlan(): PlanSetupSubmission? {
        val submission = _uiState.value.toSubmission() ?: return null
        _uiState.update { it.copy(generatedSubmission = submission) }
        return submission
    }

    fun editDeadline() {
        updateAndPersist { it.copy(currentStep = PlanSetupStep.Deadline, generatedSubmission = null) }
    }

    private inline fun updateAndPersist(crossinline transform: (PlanSetupUiState) -> PlanSetupUiState) {
        _uiState.update { state ->
            transform(state).also(::persistState)
        }
    }

    private fun persistState(state: PlanSetupUiState) {
        savedStateHandle[KEY_DOCUMENT_URI_LIST] = state.documentUris.joinToString("|")
        savedStateHandle[KEY_STEP] = state.currentStep.name
        setOrRemove(KEY_PLAN_TITLE, state.planTitle)
        setOrRemove(KEY_DEADLINE, state.selectedDeadline?.name)
        setOrRemove(KEY_CUSTOM_DEADLINE_DATE, state.customDeadlineDate)
        setOrRemove(KEY_CUSTOM_DEADLINE_LABEL, state.customDeadlineLabel)
        setOrRemove(KEY_DAILY_TIME, state.selectedDailyTime?.name)
        savedStateHandle[KEY_CUSTOM_HOURS] = state.customHoursText
        savedStateHandle[KEY_CUSTOM_MINUTES] = state.customMinutesText
        savedStateHandle[KEY_DAYS] = state.selectedDays.joinToString(",") { it.name }
    }

    private fun setOrRemove(key: String, value: String?) {
        if (value != null) savedStateHandle[key] = value
        else savedStateHandle.remove<String>(key)
    }

    private fun restoreState(): PlanSetupUiState {
        val documentUris = savedStateHandle.get<String>(KEY_DOCUMENT_URI_LIST)
            ?.split("|")
            ?.filter { it.isNotEmpty() }
            ?: return PlanSetupUiState()
        return PlanSetupUiState(
            documentUris = documentUris,
            currentStep = savedStateHandle.get<String>(KEY_STEP)
                ?.let { runCatching { PlanSetupStep.valueOf(it) }.getOrNull() }
                ?: PlanSetupStep.PlanTitle,
            planTitle = savedStateHandle.get<String>(KEY_PLAN_TITLE) ?: "",
            selectedDeadline = savedStateHandle.get<String>(KEY_DEADLINE)
                ?.let { runCatching { StudyDeadline.valueOf(it) }.getOrNull() },
            customDeadlineDate = savedStateHandle[KEY_CUSTOM_DEADLINE_DATE],
            customDeadlineLabel = savedStateHandle[KEY_CUSTOM_DEADLINE_LABEL],
            selectedDailyTime = savedStateHandle.get<String>(KEY_DAILY_TIME)
                ?.let { runCatching { DailyStudyTime.valueOf(it) }.getOrNull() },
            customHoursText = savedStateHandle.get<String>(KEY_CUSTOM_HOURS) ?: "",
            customMinutesText = savedStateHandle.get<String>(KEY_CUSTOM_MINUTES) ?: "",
            selectedDays = savedStateHandle.get<String>(KEY_DAYS)
                ?.split(",")
                ?.filter { it.isNotEmpty() }
                ?.mapNotNull { runCatching { StudyDay.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?: emptySet(),
        )
    }

    private companion object {
        const val KEY_DOCUMENT_URI_LIST = "setup_document_uri_list"
        const val KEY_STEP = "setup_step"
        const val KEY_PLAN_TITLE = "setup_plan_title"
        const val KEY_DEADLINE = "setup_deadline"
        const val KEY_CUSTOM_DEADLINE_DATE = "setup_custom_date"
        const val KEY_CUSTOM_DEADLINE_LABEL = "setup_custom_label"
        const val KEY_DAILY_TIME = "setup_daily_time"
        const val KEY_CUSTOM_HOURS = "setup_custom_hours"
        const val KEY_CUSTOM_MINUTES = "setup_custom_minutes"
        const val KEY_DAYS = "setup_days"

        val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayDateFormat = SimpleDateFormat("d MMM", Locale.US)

        init {
            isoDateFormat.timeZone = TimeZone.getTimeZone("UTC")
            displayDateFormat.timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

internal fun isSelectableDeadlineUtc(
    selectedMillis: Long,
    nowMillis: Long,
    localTimeZone: TimeZone = TimeZone.getDefault(),
): Boolean {
    val utc = TimeZone.getTimeZone("UTC")
    val selectedUtcDay = Calendar.getInstance(utc).run {
        timeInMillis = selectedMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
    val localToday = Calendar.getInstance(localTimeZone).apply {
        timeInMillis = nowMillis
    }
    val localTodayAsUtcDay = Calendar.getInstance(utc).run {
        clear()
        set(
            localToday.get(Calendar.YEAR),
            localToday.get(Calendar.MONTH),
            localToday.get(Calendar.DAY_OF_MONTH),
        )
        timeInMillis
    }
    return selectedUtcDay > localTodayAsUtcDay
}
