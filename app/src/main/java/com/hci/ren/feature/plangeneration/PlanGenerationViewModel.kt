package com.hci.ren.feature.plangeneration

import android.app.Application
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PlanGenerationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlanApiRepository(application.contentResolver)
    private val preferences = application.getSharedPreferences(PREFERENCES, 0)
    private val _uiState = MutableStateFlow(PlanGenerationUiState())
    val uiState = _uiState.asStateFlow()
    private var submission: PlanSetupSubmission? = null
    private var polling = false
    private var activeJob: Job? = null

    // Visual progression tracking
    private val backendStatus = MutableStateFlow(PlanStatus.Uploading)
    private var fetchedPlan: GeneratedStudyPlan? = null
    private var visualProgressJob: Job? = null

    init { preferences.getString(KEY_PLAN_ID, null)?.let(::resume) }

    fun reset() {
        activeJob?.cancel()
        activeJob = null
        visualProgressJob?.cancel()
        visualProgressJob = null
        polling = false
        preferences.edit { remove(KEY_PLAN_ID) }
        backendStatus.value = PlanStatus.Uploading
        fetchedPlan = null
        _uiState.value = PlanGenerationUiState(status = PlanStatus.Uploading)
    }

    fun start(value: PlanSetupSubmission) {
        if (_uiState.value.planId != null || polling) return
        submission = value
        activeJob?.cancel()
        visualProgressJob?.cancel()
        backendStatus.value = PlanStatus.Uploading
        fetchedPlan = null
        activeJob = viewModelScope.launch {
            try {
                _uiState.value = PlanGenerationUiState(status = PlanStatus.Uploading)
                startVisualProgress()
                val planId = withContext(Dispatchers.IO) {
                    val documentId = repository.uploadDocument(value.documentUri.toUri())
                    repository.createPlan(documentId, value, UUID.randomUUID().toString())
                }
                preferences.edit { putString(KEY_PLAN_ID, planId) }
                _uiState.value = _uiState.value.copy(planId = planId)
                backendStatus.value = PlanStatus.Analyzing
                poll(planId)
            } catch (e: Exception) {
                Log.e("PlanGeneration", "Failed starting plan generation", e)
                fail()
            }
        }
    }

    fun resume(planId: String) {
        if (polling) return
        backendStatus.value = PlanStatus.Analyzing
        fetchedPlan = null
        _uiState.value = PlanGenerationUiState(PlanStatus.Analyzing, planId)
        activeJob?.cancel()
        visualProgressJob?.cancel()
        startVisualProgress()
        activeJob = viewModelScope.launch { poll(planId) }
    }

    fun retry() {
        val value = submission ?: run { fail(); return }
        preferences.edit { remove(KEY_PLAN_ID) }
        activeJob?.cancel()
        visualProgressJob?.cancel()
        backendStatus.value = PlanStatus.Uploading
        fetchedPlan = null
        _uiState.value = PlanGenerationUiState()
        start(value)
    }

    private fun startVisualProgress() {
        visualProgressJob?.cancel()
        visualProgressJob = viewModelScope.launch {
            val steps = listOf(
                PlanStatus.Uploading,
                PlanStatus.Analyzing,
                PlanStatus.IdentifyingTopics,
                PlanStatus.CreatingBlocks,
                PlanStatus.Finalizing,
                PlanStatus.Completed
            )
            for (step in steps) {
                while (true) {
                    val currentBackendStatus = backendStatus.value
                    if (currentBackendStatus == PlanStatus.Failed) {
                        _uiState.value = _uiState.value.copy(status = PlanStatus.Failed)
                        return@launch
                    }
                    if (steps.indexOf(currentBackendStatus) >= steps.indexOf(step)) {
                        break
                    }
                    delay(100.milliseconds)
                }
                
                _uiState.value = _uiState.value.copy(status = step, planId = _uiState.value.planId)
                
                if (step == PlanStatus.Completed) {
                    delay(1500.milliseconds) // Completion animation time
                    _uiState.value = _uiState.value.copy(plan = fetchedPlan)
                } else {
                    delay(2000.milliseconds) // At least 2.0 seconds per step
                }
            }
        }
    }

    private suspend fun poll(planId: String) {
        polling = true
        try {
            while (true) {
                val status = withContext(Dispatchers.IO) { repository.status(planId) }
                backendStatus.value = status
                when (status) {
                    PlanStatus.Completed -> {
                        val plan = withContext(Dispatchers.IO) { repository.plan(planId) }
                        fetchedPlan = plan
                        preferences.edit { remove(KEY_PLAN_ID) }
                        return
                    }
                    PlanStatus.Failed -> {
                        Log.e("PlanGeneration", "Plan generation failed on backend side")
                        fail()
                        return
                    }
                    else -> delay(2.seconds)
                }
            }
        } catch (e: Exception) {
            Log.e("PlanGeneration", "Error polling plan status", e)
            fail()
        } finally { polling = false }
    }

    private fun fail() {
        backendStatus.value = PlanStatus.Failed
        _uiState.value = _uiState.value.copy(status = PlanStatus.Failed, errorMessage = "We couldn’t create the plan. Try again.")
    }

    companion object {
        const val PREFERENCES = "plan_generation"
        const val KEY_PLAN_ID = "pending_plan_id"
    }
}
