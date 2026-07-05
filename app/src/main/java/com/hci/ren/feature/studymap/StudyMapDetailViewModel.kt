package com.hci.ren.feature.studymap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hci.ren.R
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class StudyMapDetailUiState(
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val project: StudyProject? = null,
    val todaySession: TodaySessionState? = null,
    val todayWrapUpMessage: String? = null,
    val errorMessage: String? = null,
    val userMessage: String? = null,
)

class StudyMapDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudyProjectRepository.create(application)
    private val todayWrapUpService = TodayWrapUpService()
    private val writeMutex = Mutex()
    private val _uiState = MutableStateFlow(StudyMapDetailUiState())
    val uiState = _uiState.asStateFlow()

    fun loadActiveProject(force: Boolean = false) {
        viewModelScope.launch {
            val current = _uiState.value
            if (current.isLoading) return@launch
            if (!force && current.hasLoaded) return@launch
            if (!current.hasLoaded && current.project == null) {
                _uiState.value = current.copy(isLoading = true)
            }
            try {
                val project = repository.getActive()
                if (project == null) {
                    _uiState.value = StudyMapDetailUiState(hasLoaded = true)
                } else {
                    publish(project)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = StudyMapDetailUiState(
                    hasLoaded = true,
                    errorMessage = getApplication<Application>().getString(R.string.study_map_open_error),
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }

    fun consumeTodayWrapUpMessage() {
        _uiState.value = _uiState.value.copy(todayWrapUpMessage = null)
    }

    fun applyDeadline(date: String) {
        val millis = date.toStudyCalendar()?.timeInMillis ?: return
        mutate("Deadline updated.") { project ->
            project.copy(
                deadlineAtMillis = millis,
                preferences = project.preferences.copy(deadline = StudyDeadline.ChooseDate, deadlineDate = date),
            )
        }
    }

    fun increaseDailyTime(minutes: Int) = mutate("Daily study time updated.") {
        it.copy(dailyMinutesOverride = minutes.coerceIn(1, 1_440))
    }

    fun updateTodayAvailableTime(date: String, minutes: Int?) {
        if (date.toStudyCalendar() == null) return
        val normalized = minutes?.coerceIn(0, MaxTodaySessionMinutes)
        val current = _uiState.value
        val session = current.todaySession
            ?.takeIf { it.date == date }
            ?: TodaySessionState(date = date)
        val updatedSession = session.copy(availableMinutes = normalized)
        _uiState.value = current.copy(
            todaySession = updatedSession.takeUnless { it.isEmpty },
        )
    }

    fun updateTodayTaskAction(
        date: String,
        taskId: String,
        action: TodaySessionTaskAction,
    ) {
        if (date.toStudyCalendar() == null || taskId.isBlank()) return
        val current = _uiState.value
        val session = current.todaySession
            ?.takeIf { it.date == date }
            ?: TodaySessionState(date = date)
        val updatedSession = session.applyTaskAction(taskId, action)
        _uiState.value = current.copy(
            todaySession = updatedSession.takeUnless { it.isEmpty },
        )
    }

    fun wrapUpToday(date: String) {
        val before = _uiState.value.project ?: return
        val result = todayWrapUpService.wrapUp(
            project = before,
            date = date,
            session = _uiState.value.todaySession,
        ) ?: return
        val updated = result.project.copy(updatedAtMillis = System.currentTimeMillis())
        publish(
            project = updated,
            todaySession = null,
            todayWrapUpMessage = todayWrapUpMessage(result.summary),
        )
        viewModelScope.launch {
            writeMutex.withLock {
                try {
                    repository.upsert(updated)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    publish(before, getApplication<Application>().getString(R.string.study_map_save_error))
                }
            }
        }
    }

    fun renamePlan(name: String) {
        val title = name.safeStudyProjectTitle()
        if (title.isBlank()) return
        mutate("Plan name updated.") {
            it.copy(
                title = title,
                plan = it.plan.copy(projectName = title),
                preferences = it.preferences.copy(planTitle = title),
            )
        }
    }

    fun deletePlan() {
        val before = _uiState.value.project ?: return
        _uiState.value = StudyMapDetailUiState(hasLoaded = true)
        viewModelScope.launch {
            writeMutex.withLock {
                try {
                    repository.deleteActive()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    publish(before, getApplication<Application>().getString(R.string.study_map_save_error))
                }
            }
        }
    }

    fun reduceScope(strategy: ScopeReduction, selectedTopicIds: Set<String>) = mutate("Study map updated.") { project ->
        val excludedIds = when (strategy) {
            ScopeReduction.ChooseTopics -> project.plan.blocks
                .filter { task -> selectedTopicIds.isNotEmpty() && task.topicIds.none(selectedTopicIds::contains) }
                .mapTo(mutableSetOf()) { it.id }
        }
        val state = project.taskStateById.toMutableMap()
        excludedIds.forEach { taskId ->
            state[taskId] = StudyTaskState(status = StudyTaskStatus.ExcludedByUser)
        }
        project.copy(taskStateById = state)
    }

    fun continueAnyway() = mutate("Plan kept as requested.") { it.copy(acceptedTightPlan = true) }

    fun updateTaskStatus(taskId: String, status: StudyTaskStatus) = mutate("Study map updated.") { project ->
        val task = project.plan.blocks.firstOrNull { it.id == taskId } ?: return@mutate project
        val unresolved = task.dependencies.any { dependency ->
            project.taskStateById[dependency]?.status != StudyTaskStatus.Completed
        }
        if (status == StudyTaskStatus.InProgress && unresolved) {
            project
        } else {
            project.withTaskState(taskId, StudyTaskState(status = status))
        }
    }

    fun excludeTask(taskId: String) = mutate("Study map updated.") {
        it.withTaskState(taskId, StudyTaskState(status = StudyTaskStatus.ExcludedByUser))
    }

    fun restoreTask(taskId: String) = mutate("Study map updated.") {
        it.withTaskState(taskId, StudyTaskState())
    }

    private fun mutate(message: String, transform: (StudyProject) -> StudyProject) {
        val before = _uiState.value.project ?: return
        val updated = transform(before).copy(updatedAtMillis = System.currentTimeMillis())
        publish(updated, message)
        viewModelScope.launch {
            writeMutex.withLock {
                try {
                    repository.upsert(updated)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    publish(before, getApplication<Application>().getString(R.string.study_map_save_error))
                }
            }
        }
    }

    private fun publish(
        project: StudyProject,
        message: String? = null,
        todaySession: TodaySessionState? = _uiState.value.todaySession,
        todayWrapUpMessage: String? = null,
    ) {
        _uiState.value = StudyMapDetailUiState(
            hasLoaded = true,
            project = project,
            todaySession = todaySession,
            todayWrapUpMessage = todayWrapUpMessage,
            userMessage = message,
        )
    }

    private fun todayWrapUpMessage(summary: TodayWrapUpSummary): String {
        val savedChanges = summary.completedTasks > 0 || summary.removedTasks > 0
        val movedForward = summary.movedForwardTasks > 0 || summary.movedForwardMinutes > 0
        val app = getApplication<Application>()
        return when {
            savedChanges && movedForward -> app.getString(R.string.today_wrapped_up_saved_and_moved_message)
            movedForward -> app.getString(R.string.today_wrapped_up_moved_message)
            savedChanges -> app.getString(R.string.today_wrapped_up_saved_message)
            else -> app.getString(R.string.today_wrapped_up_message)
        }
    }
}

private fun StudyProject.withTaskState(taskId: String, state: StudyTaskState): StudyProject {
    if (taskId.isBlank() || plan.blocks.none { it.id == taskId }) return this
    val updated = taskStateById.toMutableMap()
    if (state.isDefault) {
        updated.remove(taskId)
    } else {
        updated[taskId] = state
    }
    return copy(taskStateById = updated)
}
