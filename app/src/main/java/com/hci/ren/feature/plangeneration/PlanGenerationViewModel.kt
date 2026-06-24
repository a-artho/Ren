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
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.studymap.PlanAdjustmentService
import com.hci.ren.feature.studymap.ScopeReduction
import com.hci.ren.feature.studymap.StudyProjectRepository
import com.hci.ren.feature.studymap.dayOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
    private val feasibilityChecker = StudyPlanFeasibilityChecker()
    private val planAdapter = StudyPlanAdapter()
    private val scopeAdjuster = StudyPlanScopeAdjuster()
    private val adjustmentService = PlanAdjustmentService()
    private var sourcePlan: GeneratedStudyPlan? = null
    private var scheduleDailyMinutesOverride: Int? = null

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
            remove(KEY_STUDY_MAP_STATE)
        }
        backendStatus.value = PlanStatus.Uploading
        fetchedPlan = null
        sourcePlan = null
        scheduleDailyMinutesOverride = null
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
                    val documentId = repository.uploadDocument(
                        recoverySubmission.documentUri.toUri(),
                        recoveryRequestId,
                    )
                    val recoveredPlanId = repository.createPlan(
                        documentId,
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
                _uiState.value = PlanGenerationUiState(status = PlanStatus.Uploading)
                startVisualProgress()
                val planId = withContext(Dispatchers.IO) {
                    val documentId = repository.uploadDocument(value.documentUri.toUri(), persistedRequestId)
                    repository.createPlan(documentId, value, persistedRequestId)
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
                        remove(KEY_STUDY_MAP_STATE)
                    }
                    requestId = null
                    _uiState.value = _uiState.value.copy(
                        status = PlanStatus.Completed,
                        plan = completedPlan,
                        feasibility = feasibilityChecker.check(completedPlan.blocks, completedSubmission),
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
                    delay(1500.milliseconds) // Completion animation time
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
                        sourcePlan = plan
                        preferences.edit {
                            remove(KEY_PLAN_ID)
                            remove(KEY_SUBMISSION)
                            remove(KEY_REQUEST_ID)
                            remove(KEY_STUDY_MAP_STATE)
                        }
                        requestId = null
                        _uiState.value = _uiState.value.copy(
                            plan = plan,
                            feasibility = feasibilityChecker.check(plan.blocks, currentSubmission),
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
                    delay(2000.milliseconds) // At least 2.0 seconds per step
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
                                val fetched = repository.plan(planId).copy(projectName = resolveProjectName())
                                restoreStudyMapPlan(fetched) ?: fetched
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

    fun prioritiseMostImportant() = adaptCompletedPlan(prioritised = true)

    fun continueAnyway() {
        val plan = _uiState.value.plan ?: return
        _uiState.value = _uiState.value.copy(
            plan = plan,
            acceptedTightPlan = true,
            originalGoalDoesNotFit = true,
            changeMessage = getApplication<Application>().getString(R.string.plan_kept_message),
        )
        persistStudyMapState()
    }

    fun currentSubmission(): PlanSetupSubmission? = submission

    fun suggestedDeadline(): String? {
        val plan = _uiState.value.plan ?: return null
        val current = submission ?: return null
        return adjustmentService.suggestedDeadline(
            plan.blocks,
            current,
            dailyMinutesOverride = scheduleDailyMinutesOverride,
        )
    }

    fun applyDeadline(date: String) {
        val parsed = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(date)
        }.getOrNull() ?: return
        extendDeadlineTo(parsed.time)
        _uiState.value = _uiState.value.copy(changeMessage = getApplication<Application>().getString(R.string.deadline_updated_message))
        persistStudyMapState()
    }

    fun increaseDailyTime(minutes: Int) {
        val plan = _uiState.value.plan ?: return
        val current = submission ?: return
        val updatedMinutes = minutes.coerceIn(1, 1_440)
        scheduleDailyMinutesOverride = updatedMinutes
        val result = feasibilityChecker.check(plan.blocks.filter(::isRequiredStudyMapTask), current, dailyMinutesOverride = updatedMinutes)
        _uiState.value = _uiState.value.copy(
            feasibility = result,
            dailyStudyMinutesOverride = updatedMinutes,
            changeMessage = getApplication<Application>().getString(R.string.daily_time_updated_message),
        )
        persistStudyMapState()
    }

    fun reduceScope(strategy: ScopeReduction, selectedTopicIds: Set<String> = emptySet()) {
        val plan = _uiState.value.plan ?: return
        val current = submission ?: return
        val adjusted = adjustmentService.applyScope(plan.blocks, strategy, selectedTopicIds)
        val changed = adjusted.zip(plan.blocks).count { (after, before) -> after != before }
        val updatedPlan = plan.copy(
            blocks = adjusted,
            totalEstimatedMinutes = adjusted.filter(::isRequiredStudyMapTask).sumOf { it.durationMinutes },
        )
        val result = feasibilityChecker.check(
            adjusted.filter(::isRequiredStudyMapTask),
            current,
            dailyMinutesOverride = scheduleDailyMinutesOverride,
        )
        _uiState.value = _uiState.value.copy(
            plan = updatedPlan,
            feasibility = result,
            originalGoalDoesNotFit = false,
            changeMessage = when (strategy) {
                ScopeReduction.ReducePractice -> getApplication<Application>().resources.getQuantityString(R.plurals.practice_tasks_shortened, changed, changed)
                else -> getApplication<Application>().resources.getQuantityString(R.plurals.tasks_moved_optional, changed, changed)
            },
        )
        persistStudyMapState()
    }

    fun updateTaskStatus(taskId: String, status: StudyTaskStatus) {
        updatePlanTask(taskId) { task ->
            val unresolvedDependency = task.dependencies.any { dependency ->
                _uiState.value.plan?.blocks?.firstOrNull { it.id == dependency }?.status != StudyTaskStatus.Completed
            }
            if (
                status == StudyTaskStatus.InProgress &&
                (unresolvedDependency || task.status == StudyTaskStatus.Locked && task.dependencies.isEmpty())
            ) task else task.copy(status = status)
        }
    }

    fun updateTaskDuration(taskId: String, minutes: Int) {
        updatePlanTask(taskId) { it.copy(durationMinutes = minutes.coerceIn(1, 1_440)) }
    }

    fun excludeTask(taskId: String) {
        updatePlanTask(taskId) {
            it.copy(
                isOptional = true,
                isExcluded = false,
                disposition = TaskDisposition.IfTimeRemains,
                status = StudyTaskStatus.Optional,
                scheduledDate = null,
            )
        }
    }

    fun restoreTask(taskId: String) {
        updatePlanTask(taskId) {
            it.copy(
                isOptional = false,
                isExcluded = false,
                disposition = TaskDisposition.MustComplete,
                status = StudyTaskStatus.NotStarted,
            )
        }
    }

    fun consumeChangeMessage() {
        if (_uiState.value.changeMessage != null) _uiState.value = _uiState.value.copy(changeMessage = null)
    }

    private fun updatePlanTask(taskId: String, transform: (GeneratedStudyBlock) -> GeneratedStudyBlock) {
        val plan = _uiState.value.plan ?: return
        val updated = plan.copy(blocks = plan.blocks.map { if (it.id == taskId) transform(it) else it })
        _uiState.value = _uiState.value.copy(
            plan = updated.copy(totalEstimatedMinutes = updated.blocks.filter(::isRequiredStudyMapTask).sumOf { it.durationMinutes }),
            changeMessage = getApplication<Application>().getString(R.string.study_map_updated_message),
        )
        persistStudyMapState()
    }

    fun reduceGoal(goal: StudyScopeGoal): FeasibilityStatus? = applyScope(goal)

    fun focusOnTopics(topicIds: Set<String>): FeasibilityStatus? =
        if (topicIds.isEmpty()) null else applyScope(StudyScopeGoal.SelectedTopics, topicIds)

    fun extendDeadline(studyDays: Int, intensive: Boolean = false): FeasibilityStatus? {
        val current = submission ?: return null
        val dailyMinutes = if (intensive) (current.dailyStudyMinutes * 1.5).toInt() else current.dailyStudyMinutes
        val result = updateDeadline(deadlineAfterStudyDays(studyDays, current.studyDays), dailyMinutes.takeIf { intensive })
        _uiState.value = _uiState.value.copy(changeMessage = getApplication<Application>().getString(R.string.deadline_updated_message))
        return result
    }

    fun extendDeadlineTo(epochMillis: Long): FeasibilityStatus? {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMillis))
        return updateDeadline(date, null)
    }

    private fun updateDeadline(date: String, effectiveDailyMinutes: Int?): FeasibilityStatus? {
        val plan = _uiState.value.plan ?: return null
        val updated = submission?.copy(
            deadline = com.hci.ren.feature.pdfupload.presentation.StudyDeadline.ChooseDate,
            deadlineDate = date,
        ) ?: return null
        submission = updated
        scheduleDailyMinutesOverride = effectiveDailyMinutes
        requestId?.let { persistRequest(updated, it) }
        val result = feasibilityChecker.check(plan.blocks.filter(::isRequiredStudyMapTask), updated, dailyMinutesOverride = effectiveDailyMinutes)
        _uiState.value = _uiState.value.copy(feasibility = result, originalGoalDoesNotFit = false)
        _uiState.value = _uiState.value.copy(dailyStudyMinutesOverride = scheduleDailyMinutesOverride)
        persistStudyMapState()
        return result.status
    }

    private fun applyScope(goal: StudyScopeGoal, selectedTopics: Set<String> = emptySet()): FeasibilityStatus? {
        val base = sourcePlan ?: _uiState.value.plan ?: return null
        val currentSubmission = submission ?: return null
        val blocks = scopeAdjuster.applyGoalStrategy(base.blocks, goal, selectedTopics)
        if (blocks.isEmpty()) return null
        val usedTopics = blocks.flatMapTo(mutableSetOf()) { it.topicIds }
        val adjustedPlan = base.copy(
            topics = base.topics.filter { it.id in usedTopics },
            blocks = blocks,
            totalEstimatedMinutes = blocks.sumOf { it.durationMinutes },
        )
        val result = feasibilityChecker.check(
            blocks,
            currentSubmission,
            dailyMinutesOverride = scheduleDailyMinutesOverride,
        )
        _uiState.value = _uiState.value.copy(
            plan = adjustedPlan,
            feasibility = result,
            originalGoalDoesNotFit = false,
        )
        return result.status
    }

    private fun adaptCompletedPlan(prioritised: Boolean) {
        val state = _uiState.value
        val plan = state.plan ?: return
        val feasibility = state.feasibility ?: return
        val adaptedBlocks = planAdapter.fit(
            plan.blocks,
            feasibility.availableMinutes,
            prioritised,
            preservePractice = submission?.goal == com.hci.ren.feature.pdfupload.presentation.StudyGoal.PrepareForExam,
        )
        val adaptedPlan = plan.copy(
            blocks = adaptedBlocks,
            totalEstimatedMinutes = adaptedBlocks.filter { it.disposition == TaskDisposition.MustComplete }.sumOf { it.durationMinutes },
        )
        _uiState.value = state.copy(plan = adaptedPlan, originalGoalDoesNotFit = true)
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

    private fun resolveProjectName(): String {
        val fallback = getApplication<Application>().getString(R.string.study_plan_default)
        val uri = submission?.documentUri?.toUri() ?: return fallback
        return runCatching {
            getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).substringBeforeLast('.').ifBlank { fallback } else fallback
            } ?: fallback
        }.getOrDefault(fallback)
    }

    private fun persistStudyMapState() {
        val plan = _uiState.value.plan ?: return
        val json = JSONObject()
            .put("planId", plan.id)
            .put("projectName", plan.projectName)
            .put("dailyMinutesOverride", scheduleDailyMinutesOverride)
            .put("acceptedTightPlan", _uiState.value.acceptedTightPlan)
            .put("blocks", JSONArray(plan.blocks.map { block ->
                JSONObject()
                    .put("id", block.id)
                    .put("durationMinutes", block.durationMinutes)
                    .put("status", block.status.name)
                    .put("scheduledDate", block.scheduledDate)
                    .put("isOptional", block.isOptional)
                    .put("isExcluded", block.isExcluded)
                    .put("disposition", block.disposition.name)
            }))
        preferences.edit { putString(KEY_STUDY_MAP_STATE, json.toString()) }
    }

    private fun restoreStudyMapPlan(fetched: GeneratedStudyPlan): GeneratedStudyPlan? = runCatching {
        val json = JSONObject(preferences.getString(KEY_STUDY_MAP_STATE, null) ?: return null)
        if (json.optString("planId") != fetched.id) return null
        scheduleDailyMinutesOverride = json.optInt("dailyMinutesOverride").takeIf { it > 0 }
        val savedBlocks = json.getJSONArray("blocks").objectsById()
        val restoredBlocks = fetched.blocks.map { block ->
            val saved = savedBlocks[block.id] ?: return@map block
            block.copy(
                durationMinutes = saved.optInt("durationMinutes", block.durationMinutes).coerceAtLeast(1),
                status = saved.optString("status").toTaskStatus(block.status),
                scheduledDate = saved.optString("scheduledDate").takeUnless { it.isBlank() || it == "null" },
                isOptional = saved.optBoolean("isOptional", block.isOptional),
                isExcluded = saved.optBoolean("isExcluded", block.isExcluded),
                disposition = saved.optString("disposition").toDisposition(block.disposition),
            )
        }
        fetched.copy(
            blocks = restoredBlocks,
            totalEstimatedMinutes = restoredBlocks.filter(::isRequiredStudyMapTask).sumOf { it.durationMinutes },
            projectName = json.optString("projectName", fetched.projectName),
        ).also {
            _uiState.value = _uiState.value.copy(
                dailyStudyMinutesOverride = scheduleDailyMinutesOverride,
                acceptedTightPlan = json.optBoolean("acceptedTightPlan"),
            )
        }
    }.onFailure { Log.w("PlanGeneration", "Discarding invalid Study Map state", it) }.getOrNull()

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
        const val KEY_STUDY_MAP_STATE = "study_map_state"
        const val MAX_TRANSIENT_FAILURE_MILLIS = 5 * 60 * 1000L
    }
}

private fun GeneratedStudyPlan.withScheduledTotal() = copy(
    totalEstimatedMinutes = blocks.filter { it.disposition == TaskDisposition.MustComplete }.sumOf { it.durationMinutes },
)

private fun deadlineAfterStudyDays(days: Int, selectedDays: Set<com.hci.ren.feature.pdfupload.presentation.StudyDay>): String {
    val cursor = dayOnly(Calendar.getInstance())
    var counted = 0
    while (counted < days.coerceAtLeast(1)) {
        val day = com.hci.ren.feature.pdfupload.presentation.StudyDay.entries[(cursor.get(Calendar.DAY_OF_WEEK) + 5) % 7]
        if (day in selectedDays) counted++
        if (counted < days) cursor.add(Calendar.DAY_OF_MONTH, 1)
    }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cursor.time)
}

private fun JSONArray.objectsById(): Map<String, JSONObject> = buildMap {
    repeat(length()) {
        val value = getJSONObject(it)
        put(value.getString("id"), value)
    }
}

private fun String.toTaskStatus(default: StudyTaskStatus): StudyTaskStatus =
    runCatching { StudyTaskStatus.valueOf(this) }.getOrDefault(default)

private fun String.toDisposition(default: TaskDisposition): TaskDisposition =
    runCatching { TaskDisposition.valueOf(this) }.getOrDefault(default)

private fun isRequiredStudyMapTask(task: GeneratedStudyBlock): Boolean =
    !task.isExcluded && !task.isOptional && task.disposition == TaskDisposition.MustComplete
