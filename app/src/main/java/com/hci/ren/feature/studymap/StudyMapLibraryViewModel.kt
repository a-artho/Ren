package com.hci.ren.feature.studymap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hci.ren.R
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StudyMapLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudyProjectRepository.create(application)
    private val _uiState = MutableStateFlow(StudyMapLibraryUiState())
    val uiState = _uiState.asStateFlow()
    private var sourceProjects: List<StudyProjectSummary> = emptyList()

    init {
        observeProjects()
    }

    fun retry() = observeProjects()

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        publish()
    }

    fun updateFilter(filter: StudyMapFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        publish()
    }

    fun updateSort(sort: StudyMapSort) {
        _uiState.value = _uiState.value.copy(sort = sort)
        publish()
    }

    fun clearSearchAndFilter() {
        _uiState.value = _uiState.value.copy(query = "", filter = StudyMapFilter.All)
        publish()
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = _uiState.value.copy(userMessage = getApplication<Application>().getString(R.string.study_map_delete_error))
                }
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }

    private fun observeProjects() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                repository.observeAll().collect { projects ->
                    sourceProjects = projects.map(StudyProject::toSummary)
                    publish(isLoading = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = getApplication<Application>().getString(R.string.study_maps_load_error),
                )
            }
        }
    }

    private fun publish(isLoading: Boolean = _uiState.value.isLoading) {
        val current = _uiState.value
        _uiState.value = current.copy(
            isLoading = isLoading,
            allProjectsCount = sourceProjects.size,
            projects = filterAndSortStudyProjects(
                sourceProjects,
                current.query,
                current.filter,
                current.sort,
                Locale.getDefault(),
            ),
        )
    }
}
