package com.hci.ren.feature.plangeneration

import android.app.Application
import android.provider.OpenableColumns
import android.util.Log
import android.os.SystemClock
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hci.ren.R
import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.studymap.StudyProjectRepository
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
    private val studyProjectRepository = StudyProjectRepository.create(application)
    private val preferences = application.getSharedPreferences(PREFERENCES, 0)
    private val _uiState = MutableStateFlow(PlanGenerationUiState())
    val uiState = _uiState.asStateFlow()
    private var submission: PlanSetupSubmission? = restoreSubmission()
    private var requestId: String? = preferences.getString(KEY_REQUEST_ID, null)
    private var polling = false
    private var activeJob: Job? = null
    private var failurePhase = GenerationFailurePhase.UploadOrCreate

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
        val cancelPlanId = planId?.takeIf {
            _uiState.value.status !in setOf(PlanStatus.Completed, PlanStatus.Failed)
        }
        val recoveryRequest = if (
            planId == null && pendingSubmission != null && pendingRequestId != null
        ) {
            pendingSubmission to pendingRequestId
        } else {
            null
        }
        activeJob?.cancel()
        activeJob = null
        visualProgressJob?.cancel()
        visualProgressJob = null
        polling = false
        preferences.edit {
            remove(KEY_PLAN_ID)
            remove(KEY_SUBMISSION)
            remove(KEY_REQUEST_ID)
        }
        backendStatus.value = PlanStatus.Uploading
        fetchedPlan = null
        _uiState.value = PlanGenerationUiState(status = PlanStatus.Uploading)
        submission = null
        requestId = null
        if (cancelPlanId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repository.cancelPlan(cancelPlanId) }
                    .onFailure { Log.w("PlanGeneration", "Failed to cancel plan $cancelPlanId", it) }
            }
        } else if (recoveryRequest != null) {
            val (recoverySubmission, recoveryRequestId) = recoveryRequest
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val documentIds = repository.uploadDocuments(
                        recoverySubmission.documentUris.map { it.toUri() },
                        recoveryRequestId,
                    )
                    val recoveredPlanId = repository.createPlan(
                        documentIds,
                        recoverySubmission,
                        recoveryRequestId,
                    )
                    repository.cancelPlan(recoveredPlanId)
                }.onFailure { Log.w("PlanGeneration", "Failed to cancel pending request", it) }
            }
        }
    }

    /**
     * Removes the persisted plan ID so process death does not auto-resume
     * this plan. Keeps study-map state in memory for the current session.
     */
    fun clearPendingPlanId() {
        preferences.edit { remove(KEY_PLAN_ID) }
    }

    fun start(value: PlanSetupSubmission) {
        if (_uiState.value.planId != null || polling) return
        submission = value
        val newRequestId = UUID.randomUUID().toString()
        requestId = newRequestId
        persistRequest(value, newRequestId)
        startPersistedRequest()
    }

    private fun startPersistedRequest() {
        val value = submission ?: return
        val persistedRequestId = requestId ?: return
        activeJob?.cancel()
        visualProgressJob?.cancel()
        backendStatus.value = PlanStatus.Uploading
        failurePhase = GenerationFailurePhase.UploadOrCreate
        fetchedPlan = null
        activeJob = viewModelScope.launch {
            try {
                _uiState.value = PlanGenerationUiState(
                    status = PlanStatus.Uploading,
                    uploadingDocumentTotal = value.documentUris.size,
                )
                startVisualProgress()
                val planId = withContext(Dispatchers.IO) {
                    val documentIds = repository.uploadDocuments(
                        value.documentUris.map { it.toUri() },
                        persistedRequestId,
                    ) { current, total ->
                        _uiState.value = _uiState.value.copy(
                            uploadingDocumentIndex = current,
                            uploadingDocumentTotal = total,
                        )
                    }
                    repository.createPlan(documentIds, value, persistedRequestId)
                }
                preferences.edit { putString(KEY_PLAN_ID, planId) }
                _uiState.value = _uiState.value.copy(planId = planId)
                backendStatus.value = PlanStatus.Analyzing
                failurePhase = GenerationFailurePhase.Polling
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
        failurePhase = GenerationFailurePhase.Polling
        backendStatus.value = PlanStatus.Analyzing
        fetchedPlan = null
        _uiState.value = PlanGenerationUiState(PlanStatus.Analyzing, planId)
        activeJob?.cancel()
        visualProgressJob?.cancel()
        startVisualProgress()
        activeJob = viewModelScope.launch { poll(planId) }
    }

    fun retry() {
        val completedPlan = fetchedPlan?.withScheduledTotal()
        val completedSubmission = submission
        if (completedPlan != null && completedSubmission != null && _uiState.value.status == PlanStatus.Failed) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(status = PlanStatus.Finalizing, errorMessage = null)
                try {
                    withContext(Dispatchers.IO) {
                        studyProjectRepository.saveGeneratedPlan(completedPlan, completedSubmission)
                    }
                    preferences.edit {
                        remove(KEY_PLAN_ID)
                        remove(KEY_SUBMISSION)
                        remove(KEY_REQUEST_ID)
                    }
                    requestId = null
                    _uiState.value = _uiState.value.copy(
                        status = PlanStatus.Completed,
                        plan = completedPlan,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e("PlanGeneration", "Failed retrying completed project save", error)
                    _uiState.value = _uiState.value.copy(
                        status = PlanStatus.Failed,
                        errorMessage = "Your plan was created, but we couldn’t save it. Try again.",
                    )
                }
            }
            return
        }
        val value = submission ?: run { fail(); return }
        val retryRequestId = requestId?.let { existing ->
            requestIdForRetry(existing, failurePhase) { UUID.randomUUID().toString() }
        } ?: UUID.randomUUID().toString()
        requestId = retryRequestId
        persistRequest(value, retryRequestId)
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
                    delay(COMPLETION_SETTLE_DURATION)
                    val plan = fetchedPlan?.withScheduledTotal()
                    val currentSubmission = submission
                    if (plan == null || currentSubmission == null) {
                        fail()
                        return@launch
                    }
                    try {
                        withContext(Dispatchers.IO) {
                            studyProjectRepository.saveGeneratedPlan(plan, currentSubmission)
                        }
                        preferences.edit {
                            remove(KEY_PLAN_ID)
                            remove(KEY_SUBMISSION)
                            remove(KEY_REQUEST_ID)
                        }
                        requestId = null
                        _uiState.value = _uiState.value.copy(
                            plan = plan,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("PlanGeneration", "Failed saving completed study project", e)
                        _uiState.value = _uiState.value.copy(
                            status = PlanStatus.Failed,
                            errorMessage = "Your plan was created, but we couldn’t save it. Try again.",
                        )
                    }
                } else {
                    delay(VISUAL_STEP_MINIMUM_DURATION)
                }
            }
        }
    }

    private suspend fun poll(planId: String) {
        polling = true
        var retryDelay = 1.seconds
        var firstTransientFailureAt: Long? = null
        try {
            while (true) {
                try {
                    val status = withContext(Dispatchers.IO) { repository.status(planId) }
                    retryDelay = 1.seconds
                    firstTransientFailureAt = null
                    when (status) {
                        PlanStatus.Completed -> {
                            fetchedPlan = withContext(Dispatchers.IO) {
                                val plan = repository.plan(planId)
                                val named = plan.withPreferredProjectName()
                                named
                            }
                            backendStatus.value = PlanStatus.Completed
                            return
                        }
                        PlanStatus.Failed -> {
                            failurePhase = GenerationFailurePhase.BackendTerminal
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
                } catch (e: PlanApiException) {
                    if (!isRetryableStatusCode(e.statusCode)) {
                        Log.e("PlanGeneration", "Permanent plan request failure (${e.statusCode})", e)
                        fail()
                        return
                    }
                    firstTransientFailureAt = firstTransientFailureAt ?: SystemClock.elapsedRealtime()
                    if (SystemClock.elapsedRealtime() - firstTransientFailureAt >= MAX_TRANSIENT_FAILURE_MILLIS) {
                        fail()
                        return
                    }
                    Log.w("PlanGeneration", "Transient plan request failure", e)
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(30.seconds)
                } catch (e: Exception) {
                    firstTransientFailureAt = firstTransientFailureAt ?: SystemClock.elapsedRealtime()
                    if (SystemClock.elapsedRealtime() - firstTransientFailureAt >= MAX_TRANSIENT_FAILURE_MILLIS) {
                        fail()
                        return
                    }
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
        preferences.edit {
            remove(KEY_PLAN_ID)
            remove(KEY_SUBMISSION)
            remove(KEY_REQUEST_ID)
        }
    }

    fun currentSubmission(): PlanSetupSubmission? = submission

    private fun persistRequest(value: PlanSetupSubmission, id: String) {
        val json = JSONObject()
            .put("documentUris", JSONArray(value.documentUris))
            .put("goal", value.goal.name)
            .put("planTitle", value.planTitle)
            .put("deadline", value.deadline.name)
            .put("deadlineDate", value.deadlineDate)
            .put("dailyStudyMinutes", value.dailyStudyMinutes)
            .put("studyDays", JSONArray(value.studyDays.map { it.name }))
        preferences.edit {
            putString(KEY_SUBMISSION, json.toString())
            putString(KEY_REQUEST_ID, id)
        }
    }

    private fun resolveProjectName(): String {
        submission?.planTitle?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val fallback = getApplication<Application>().getString(R.string.study_plan_default)
        val uris = submission?.documentUris ?: return fallback
        val names = uris.mapNotNull { uri ->
            runCatching {
                getApplication<Application>().contentResolver.query(uri.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0).substringBeforeLast('.').ifBlank { null } else cursor.getString(0).substringBeforeLast('.')
                } ?: null
            }.getOrNull()
        }.filter { it.isNotBlank() }
        return when {
            names.isEmpty() -> fallback
            names.size == 1 -> names[0]
            names.size == 2 -> "${names[0]} + ${names[1]}"
            else -> "${names[0]}, ${names[1]} + ${names.size - 2} more"
        }
    }

    private fun GeneratedStudyPlan.withPreferredProjectName(): GeneratedStudyPlan {
        val userTitle = submission?.planTitle?.trim().orEmpty()
        return when {
            userTitle.isNotBlank() -> copy(projectName = userTitle)
            projectName == DEFAULT_PROJECT_NAME -> copy(projectName = resolveProjectName())
            else -> this
        }
    }

    private fun restoreSubmission(): PlanSetupSubmission? = runCatching {
        val json = JSONObject(preferences.getString(KEY_SUBMISSION, null) ?: return null)
        val documentUris = if (json.has("documentUris")) {
            val arr = json.getJSONArray("documentUris")
            buildList { repeat(arr.length()) { add(arr.getString(it)) } }
        } else {
            val single = if (json.has("documentUri") && !json.isNull("documentUri")) {
                json.getString("documentUri")
            } else {
                return null
            }
            json.put("documentUris", JSONArray(listOf(single)))
            json.remove("documentUri")
            preferences.edit { putString(KEY_SUBMISSION, json.toString()) }
            listOf(single)
        }
        PlanSetupSubmission(
            documentUris = documentUris,
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
            planTitle = json.optString("planTitle").takeUnless { it.isBlank() || it == "null" }.orEmpty(),
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
        const val MAX_TRANSIENT_FAILURE_MILLIS = 5 * 60 * 1000L
        private val VISUAL_STEP_MINIMUM_DURATION = 2.seconds
        private val COMPLETION_SETTLE_DURATION = 650.milliseconds
    }
}

private fun GeneratedStudyPlan.withScheduledTotal() = copy(
    totalEstimatedMinutes = blocks.filterNot { it.status == StudyTaskStatus.ExcludedByUser }.sumOf { it.durationMinutes },
)
