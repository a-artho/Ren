package com.hci.ren.feature.plangeneration

import android.app.Application
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PlanGenerationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlanApiRepository(application.contentResolver)
    private val preferences = application.getSharedPreferences(PREFERENCES, 0)
    private val _uiState = MutableStateFlow(PlanGenerationUiState())
    val uiState = _uiState.asStateFlow()
    private var submission: PlanSetupSubmission? = restoreSubmission()
    private var requestId: String? = preferences.getString(KEY_REQUEST_ID, null)
    private var polling = false
    private var activeJob: Job? = null

    // Visual progression tracking
    private val backendStatus = MutableStateFlow(PlanStatus.Uploading)
    private var fetchedPlan: GeneratedStudyPlan? = null
    private var visualProgressJob: Job? = null

    init {
        val savedPlanId = preferences.getString(KEY_PLAN_ID, null)
        when {
            savedPlanId != null -> resume(savedPlanId)
            submission != null && requestId != null -> startPersistedRequest()
        }
    }

    fun reset() {
        val planId = _uiState.value.planId ?: preferences.getString(KEY_PLAN_ID, null)
        val pendingSubmission = submission
        val pendingRequestId = requestId
        val shouldCancelRemotely = planId != null &&
            _uiState.value.status !in setOf(PlanStatus.Completed, PlanStatus.Failed)
        val shouldRecoverAndCancel = planId == null && pendingSubmission != null &&
            pendingRequestId != null && _uiState.value.status != PlanStatus.Failed
        activeJob?.cancel()
        activeJob = null
        visualProgressJob?.cancel()
        visualProgressJob = null
        polling = false
        preferences.edit { clear() }
        backendStatus.value = PlanStatus.Uploading
        fetchedPlan = null
        _uiState.value = PlanGenerationUiState(status = PlanStatus.Uploading)
        submission = null
        requestId = null
        if (shouldCancelRemotely && planId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repository.cancelPlan(planId) }
                    .onFailure { Log.w("PlanGeneration", "Failed to cancel plan $planId", it) }
            }
        } else if (shouldRecoverAndCancel && pendingSubmission != null && pendingRequestId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val documentId = repository.uploadDocument(pendingSubmission.documentUri.toUri())
                    val recoveredPlanId = repository.createPlan(documentId, pendingSubmission, pendingRequestId)
                    repository.cancelPlan(recoveredPlanId)
                }.onFailure { Log.w("PlanGeneration", "Failed to cancel pending request", it) }
            }
        }
    }

    fun start(value: PlanSetupSubmission) {
        if (_uiState.value.planId != null || polling) return
        submission = value
        requestId = UUID.randomUUID().toString()
        persistRequest(value, requestId!!)
        startPersistedRequest()
    }

    private fun startPersistedRequest() {
        val value = submission ?: return
        val persistedRequestId = requestId ?: return
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
                    repository.createPlan(documentId, value, persistedRequestId)
                }
                preferences.edit { putString(KEY_PLAN_ID, planId) }
                _uiState.value = _uiState.value.copy(planId = planId)
                backendStatus.value = PlanStatus.Analyzing
                poll(planId)
            } catch (e: CancellationException) {
                throw e
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
        val newRequestId = UUID.randomUUID().toString()
        requestId = newRequestId
        persistRequest(value, newRequestId)
        preferences.edit { remove(KEY_PLAN_ID) }
        activeJob?.cancel()
        visualProgressJob?.cancel()
        backendStatus.value = PlanStatus.Uploading
        fetchedPlan = null
        _uiState.value = PlanGenerationUiState()
        startPersistedRequest()
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
        var retryDelay = 1.seconds
        try {
            while (true) {
                try {
                    val status = withContext(Dispatchers.IO) { repository.status(planId) }
                    retryDelay = 1.seconds
                    when (status) {
                        PlanStatus.Completed -> {
                            fetchedPlan = withContext(Dispatchers.IO) { repository.plan(planId) }
                            backendStatus.value = PlanStatus.Completed
                            return
                        }
                        PlanStatus.Failed -> {
                            backendStatus.value = PlanStatus.Failed
                            Log.e("PlanGeneration", "Plan generation failed on backend side")
                            fail()
                            return
                        }
                        else -> {
                            backendStatus.value = status
                            delay(2.seconds)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("PlanGeneration", "Transient plan request failure", e)
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(30.seconds)
                }
            }
        } finally { polling = false }
    }

    private fun fail() {
        backendStatus.value = PlanStatus.Failed
        _uiState.value = _uiState.value.copy(status = PlanStatus.Failed, errorMessage = "We couldn’t create the plan. Try again.")
    }

    private fun persistRequest(value: PlanSetupSubmission, id: String) {
        val json = JSONObject()
            .put("documentUri", value.documentUri)
            .put("goal", value.goal.name)
            .put("deadline", value.deadline.name)
            .put("deadlineDate", value.deadlineDate)
            .put("dailyStudyMinutes", value.dailyStudyMinutes)
            .put("studyDays", JSONArray(value.studyDays.map { it.name }))
        preferences.edit {
            putString(KEY_SUBMISSION, json.toString())
            putString(KEY_REQUEST_ID, id)
        }
    }

    private fun restoreSubmission(): PlanSetupSubmission? = runCatching {
        val json = JSONObject(preferences.getString(KEY_SUBMISSION, null) ?: return null)
        PlanSetupSubmission(
            documentUri = json.getString("documentUri"),
            goal = com.hci.ren.feature.pdfupload.presentation.StudyGoal.valueOf(json.getString("goal")),
            deadline = com.hci.ren.feature.pdfupload.presentation.StudyDeadline.valueOf(json.getString("deadline")),
            deadlineDate = json.optString("deadlineDate").takeUnless { it.isBlank() || it == "null" },
            dailyStudyMinutes = json.getInt("dailyStudyMinutes"),
            studyDays = buildSet {
                val days = json.getJSONArray("studyDays")
                repeat(days.length()) {
                    add(com.hci.ren.feature.pdfupload.presentation.StudyDay.valueOf(days.getString(it)))
                }
            },
        )
    }.onFailure {
        Log.w("PlanGeneration", "Discarding invalid saved generation request", it)
        preferences.edit { clear() }
    }.getOrNull()

    companion object {
        const val PREFERENCES = "plan_generation"
        const val KEY_PLAN_ID = "pending_plan_id"
        const val KEY_SUBMISSION = "pending_submission"
        const val KEY_REQUEST_ID = "pending_request_id"
    }
}
