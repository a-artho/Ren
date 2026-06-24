package com.hci.ren.feature.studymap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hci.ren.R
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.StudyPlanFeasibilityChecker
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.TaskDisposition
import java.text.SimpleDateFormat
import java.util.Calendar
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
    val project: StudyProject? = null,
    val errorMessage: String? = null,
    val userMessage: String? = null,
    val suggestedDeadline: String? = null,
    val recommendedDaysBalanced: Int = 0,
    val recommendedDaysIntensive: Int = 0,
)

class StudyMapDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudyProjectRepository.create(application)
    private val adjustmentService = PlanAdjustmentService()
    private val feasibilityChecker = StudyPlanFeasibilityChecker()
    private val writeMutex = Mutex()
    private val _uiState = MutableStateFlow(StudyMapDetailUiState())
    val uiState = _uiState.asStateFlow()
    private var loadedProjectId: String? = null

    fun loadProject(id: String) {
        if (id.isBlank()) {
            _uiState.value = StudyMapDetailUiState(errorMessage = getApplication<Application>().getString(R.string.study_map_unavailable))
            return
        }
        if (loadedProjectId == id && _uiState.value.project != null) return
        loadedProjectId = id
        viewModelScope.launch {
            _uiState.value = StudyMapDetailUiState(isLoading = true)
            try {
                val project = repository.getById(id)
                if (project == null) {
                    _uiState.value = StudyMapDetailUiState(errorMessage = getApplication<Application>().getString(R.string.study_map_missing))
                } else {
                    publish(project)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = StudyMapDetailUiState(errorMessage = getApplication<Application>().getString(R.string.study_map_open_error))
            }
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
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

    fun extendDeadline(studyDays: Int, intensive: Boolean) {
        val project = _uiState.value.project ?: return
        val date = deadlineAfterSelectedStudyDays(studyDays, project.preferences.studyDays)
        val effectiveMinutes = if (intensive) (project.preferences.dailyStudyMinutes * 1.5).toInt() else null
        mutate("Deadline updated.") { current ->
            current.copy(
                deadlineAtMillis = date.toStudyCalendar()?.timeInMillis,
                preferences = current.preferences.copy(deadline = StudyDeadline.ChooseDate, deadlineDate = date),
                dailyMinutesOverride = effectiveMinutes,
            )
        }
    }

    fun increaseDailyTime(minutes: Int) = mutate("Daily study time updated.") {
        it.copy(dailyMinutesOverride = minutes.coerceIn(1, 1_440))
    }

    fun reduceScope(strategy: ScopeReduction, selectedTopicIds: Set<String>) = mutate("Study map updated.") { project ->
        val blocks = adjustmentService.applyScope(project.plan.blocks, strategy, selectedTopicIds)
        project.copy(plan = project.plan.copy(
            blocks = blocks,
            totalEstimatedMinutes = blocks.filter(::isRequiredTask).sumOf { it.durationMinutes },
        ))
    }

    fun continueAnyway() = mutate("Plan kept as requested.") { it.copy(acceptedTightPlan = true) }

    fun updateTaskStatus(taskId: String, status: StudyTaskStatus) = updateTask(taskId) { task, plan ->
        val unresolved = task.dependencies.any { dependency ->
            plan.blocks.firstOrNull { it.id == dependency }?.status != StudyTaskStatus.Completed
        }
        if (status == StudyTaskStatus.InProgress && unresolved) task else task.copy(status = status)
    }

    fun updateTaskDuration(taskId: String, minutes: Int) = updateTask(taskId) { task, _ ->
        task.copy(durationMinutes = minutes.coerceIn(1, 1_440))
    }

    fun excludeTask(taskId: String) = updateTask(taskId) { task, _ ->
        task.copy(
            isOptional = true,
            isExcluded = false,
            disposition = TaskDisposition.IfTimeRemains,
            status = StudyTaskStatus.Optional,
            scheduledDate = null,
        )
    }

    fun restoreTask(taskId: String) = updateTask(taskId) { task, _ ->
        task.copy(
            isOptional = false,
            isExcluded = false,
            disposition = TaskDisposition.MustComplete,
            status = StudyTaskStatus.NotStarted,
        )
    }

    private fun updateTask(
        taskId: String,
        transform: (GeneratedStudyBlock, com.hci.ren.feature.plangeneration.GeneratedStudyPlan) -> GeneratedStudyBlock,
    ) = mutate("Study map updated.") { project ->
        val plan = project.plan
        val blocks = plan.blocks.map { task -> if (task.id == taskId) transform(task, plan) else task }
        project.copy(plan = plan.copy(
            blocks = blocks,
            totalEstimatedMinutes = blocks.filter(::isRequiredTask).sumOf { it.durationMinutes },
        ))
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

    private fun publish(project: StudyProject, message: String? = null) {
        val required = project.plan.blocks.filter(::isRequiredTask)
        val feasibility = feasibilityChecker.check(
            required,
            project.preferences,
            dailyMinutesOverride = project.dailyMinutesOverride,
        )
        _uiState.value = StudyMapDetailUiState(
            project = project,
            userMessage = message,
            suggestedDeadline = adjustmentService.suggestedDeadline(
                project.plan.blocks,
                project.preferences,
                dailyMinutesOverride = project.dailyMinutesOverride,
            ),
            recommendedDaysBalanced = feasibility.recommendedDaysBalanced,
            recommendedDaysIntensive = feasibility.recommendedDaysIntensive,
        )
    }
}

private fun isRequiredTask(task: GeneratedStudyBlock) =
    !task.isExcluded && task.status != StudyTaskStatus.Excluded && !task.isOptional && task.disposition == TaskDisposition.MustComplete

private fun deadlineAfterSelectedStudyDays(days: Int, selectedDays: Set<com.hci.ren.feature.pdfupload.presentation.StudyDay>): String {
    val cursor = dayOnly(Calendar.getInstance())
    var counted = 0
    while (counted < days.coerceAtLeast(1)) {
        if (cursor.studyDay in selectedDays) counted++
        if (counted < days.coerceAtLeast(1)) cursor.add(Calendar.DAY_OF_MONTH, 1)
    }
    return cursor.toStudyDate()
}
