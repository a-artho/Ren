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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
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
    private var preparationJob: Job? = null
    private val preparedDocumentsLock = Any()
    private var preparedDocuments: List<PreparedDocumentUpload> = restorePreparedDocuments()
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
        preparationJob?.cancel()
        preparationJob = null
        val preparedToDelete = clearPreparedDocuments()
        polling = false
        preferences.edit {
            remove(KEY_PLAN_ID)
            remove(KEY_SUBMISSION)
            remove(KEY_REQUEST_ID)
            remove(KEY_PREPARED_DOCUMENTS)
        }
        backendStatus.value = PlanStatus.Uploading
        fetchedPlan = null
        _uiState.value = PlanGenerationUiState(status = PlanStatus.Uploading)
        submission = null
        requestId = null
        if (cancelPlanId == null) {
            deletePreparedDocuments(preparedToDelete)
        }
        if (cancelPlanId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repository.cancelPlan(cancelPlanId) }
                    .onFailure { Log.w("PlanGeneration", "Failed to cancel plan $cancelPlanId", it) }
            }
        } else if (recoveryRequest != null && preparedToDelete.isEmpty()) {
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

    fun prepareDocuments(documentUris: List<String>) {
        val orderedUris = documentUris.distinct()
        val removed: List<PreparedDocumentUpload>
        val records: List<PreparedDocumentUpload>
        synchronized(preparedDocumentsLock) {
            val existingByUri = preparedDocuments.associateBy { it.uri }
            removed = preparedDocuments.filterNot { it.uri in orderedUris }
            records = orderedUris.map { uri ->
                existingByUri[uri] ?: PreparedDocumentUpload(
                    uri = uri,
                    uploadRequestId = UUID.randomUUID().toString(),
                )
            }
            preparedDocuments = records
            persistPreparedDocumentsLocked()
        }
        deletePreparedDocuments(removed)
        startPreparationJobIfNeeded()
    }

    private fun startPreparationJobIfNeeded() {
        if (preparationJob?.isActive == true) return
        preparationJob = viewModelScope.launch(Dispatchers.IO) {
            var stoppedAfterFailure = false
            try {
                val pending = synchronized(preparedDocumentsLock) {
                    preparedDocuments.filter { it.documentId == null }
                }
                if (pending.isEmpty()) return@launch
                val results = uploadPreparedDocuments(pending)
                stoppedAfterFailure = results.any { it == PreparedDocumentUploadResult.Failed }
            } finally {
                preparationJob = null
                if (!stoppedAfterFailure && synchronized(preparedDocumentsLock) { preparedDocuments.any { it.documentId == null } }) {
                    startPreparationJobIfNeeded()
                }
            }
        }
    }

    private suspend fun uploadPreparedDocuments(
        records: List<PreparedDocumentUpload>,
        onFinished: () -> Unit = {},
    ): List<PreparedDocumentUploadResult> = coroutineScope {
        val semaphore = Semaphore(MAX_DOCUMENT_PREPARATION_UPLOADS)
        records.map { record ->
            async {
                semaphore.withPermit {
                    uploadPreparedDocument(record).also { onFinished() }
                }
            }
        }.awaitAll()
    }

    private fun uploadPreparedDocument(record: PreparedDocumentUpload): PreparedDocumentUploadResult {
        val uploadedDocumentId = runCatching {
            repository.uploadDocument(record.uri.toUri(), record.uploadRequestId)
        }.onFailure {
            Log.w("PlanGeneration", "Failed preparing document upload", it)
        }.getOrNull() ?: return PreparedDocumentUploadResult.Failed

        val shouldKeep = synchronized(preparedDocumentsLock) {
            preparedDocuments.any { it.uri == record.uri && it.uploadRequestId == record.uploadRequestId }
        }
        if (!shouldKeep) {
            runCatching { repository.deleteDocument(uploadedDocumentId) }
                .onFailure { Log.w("PlanGeneration", "Failed deleting stale prepared document", it) }
            return PreparedDocumentUploadResult.Stale
        }
        synchronized(preparedDocumentsLock) {
            preparedDocuments = preparedDocuments.map {
                if (it.uri == record.uri && it.uploadRequestId == record.uploadRequestId) {
                    it.copy(documentId = uploadedDocumentId)
                } else {
                    it
                }
            }
            persistPreparedDocumentsLocked()
        }
        return PreparedDocumentUploadResult.Uploaded
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
                    val documentIds = preparedDocumentIdsFor(value) { current, total ->
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
        val completedPlan = fetchedPlan
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
                        remove(KEY_PREPARED_DOCUMENTS)
                    }
                    discardPreparedDocuments()
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
        if (failurePhase == GenerationFailurePhase.BackendTerminal) {
            clearPreparedDocumentIds()
        }
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
                    val plan = fetchedPlan
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
                            remove(KEY_PREPARED_DOCUMENTS)
                        }
                        discardPreparedDocuments()
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
        val preparedToDelete = if (failurePhase == GenerationFailurePhase.UploadOrCreate) {
            clearPreparedDocuments()
        } else {
            discardPreparedDocuments()
            emptyList()
        }
        deletePreparedDocuments(preparedToDelete)
        preferences.edit {
            remove(KEY_PLAN_ID)
            remove(KEY_SUBMISSION)
            remove(KEY_REQUEST_ID)
            remove(KEY_PREPARED_DOCUMENTS)
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
            .put("studyDayResetOffsetHours", value.studyDayResetOffsetHours)
        preferences.edit {
            putString(KEY_SUBMISSION, json.toString())
            putString(KEY_REQUEST_ID, id)
        }
    }

    private suspend fun preparedDocumentIdsFor(
        value: PlanSetupSubmission,
        onProgress: (current: Int, total: Int) -> Unit,
    ): List<String> {
        val currentUris = synchronized(preparedDocumentsLock) { preparedDocuments.map { it.uri } }
        if (currentUris != value.documentUris) {
            prepareDocuments(value.documentUris)
        }
        preparationJob?.join()
        val total = value.documentUris.size
        val orderedRecords = value.documentUris.map { uri ->
            synchronized(preparedDocumentsLock) {
                preparedDocuments.firstOrNull { it.uri == uri }
                    ?: PreparedDocumentUpload(uri = uri, uploadRequestId = UUID.randomUUID().toString()).also { created ->
                        preparedDocuments = preparedDocuments + created
                        persistPreparedDocumentsLocked()
                    }
            }
        }
        val missing = orderedRecords.filter { it.documentId == null }
        if (missing.isNotEmpty()) {
            val completed = AtomicInteger(total - missing.size)
            val results = uploadPreparedDocuments(missing) {
                onProgress(completed.incrementAndGet(), total)
            }
            if (results.any { it == PreparedDocumentUploadResult.Failed }) {
                error("The selected PDF could not be uploaded")
            }
        } else {
            repeat(total) { index -> onProgress(index + 1, total) }
        }
        return value.documentUris.map { uri ->
            synchronized(preparedDocumentsLock) {
                preparedDocuments.firstOrNull { it.uri == uri }?.documentId
            } ?: error("The selected PDF could not be uploaded")
        }
    }

    private fun clearPreparedDocuments(): List<PreparedDocumentUpload> {
        preparationJob?.cancel()
        preparationJob = null
        return synchronized(preparedDocumentsLock) {
            val current = preparedDocuments
            preparedDocuments = emptyList()
            persistPreparedDocumentsLocked()
            current
        }
    }

    private fun discardPreparedDocuments() {
        preparationJob?.cancel()
        preparationJob = null
        synchronized(preparedDocumentsLock) {
            preparedDocuments = emptyList()
            persistPreparedDocumentsLocked()
        }
    }

    private fun clearPreparedDocumentIds() {
        synchronized(preparedDocumentsLock) {
            preparedDocuments = preparedDocuments.map { it.copy(documentId = null) }
            persistPreparedDocumentsLocked()
        }
    }

    private fun deletePreparedDocuments(records: List<PreparedDocumentUpload>) {
        if (records.none { it.documentId != null }) return
        viewModelScope.launch(Dispatchers.IO) {
            deletePreparedDocumentsBlocking(records)
        }
    }

    private fun deletePreparedDocumentsBlocking(records: List<PreparedDocumentUpload>) {
        records.mapNotNull { it.documentId }.distinct().forEach { documentId ->
            runCatching { repository.deleteDocument(documentId) }
                .onFailure { Log.w("PlanGeneration", "Failed deleting prepared document $documentId", it) }
        }
    }

    private fun persistPreparedDocumentsLocked() {
        val array = JSONArray()
        preparedDocuments.forEach { record ->
            array.put(
                JSONObject()
                    .put("uri", record.uri)
                    .put("uploadRequestId", record.uploadRequestId)
                    .put("documentId", record.documentId),
            )
        }
        preferences.edit { putString(KEY_PREPARED_DOCUMENTS, array.toString()) }
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
        val documentUris = json.getJSONArray("documentUris").let { arr ->
            buildList { repeat(arr.length()) { add(arr.getString(it)) } }
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
            studyDayResetOffsetHours = json.optInt("studyDayResetOffsetHours", 4).coerceIn(0, 23),
            planTitle = json.optString("planTitle").takeUnless { it.isBlank() || it == "null" }.orEmpty(),
        )
    }.onFailure {
        Log.w("PlanGeneration", "Discarding invalid saved generation request", it)
        preferences.edit { clear() }
    }.getOrNull()

    private fun restorePreparedDocuments(): List<PreparedDocumentUpload> = runCatching {
        val raw = preferences.getString(KEY_PREPARED_DOCUMENTS, null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val uri = item.optString("uri").takeIf { it.isNotBlank() } ?: return@repeat
                val uploadRequestId = item.optString("uploadRequestId").takeIf { it.isNotBlank() }
                    ?: UUID.randomUUID().toString()
                val documentId = item.optString("documentId").takeUnless { it.isBlank() || it == "null" }
                add(PreparedDocumentUpload(uri = uri, uploadRequestId = uploadRequestId, documentId = documentId))
            }
        }
    }.onFailure {
        Log.w("PlanGeneration", "Discarding invalid prepared document state", it)
        preferences.edit { remove(KEY_PREPARED_DOCUMENTS) }
    }.getOrDefault(emptyList())

    companion object {
        const val PREFERENCES = "plan_generation"
        const val KEY_PLAN_ID = "pending_plan_id"
        const val KEY_SUBMISSION = "pending_submission"
        const val KEY_REQUEST_ID = "pending_request_id"
        const val KEY_PREPARED_DOCUMENTS = "prepared_documents"
        const val MAX_TRANSIENT_FAILURE_MILLIS = 5 * 60 * 1000L
        private const val MAX_DOCUMENT_PREPARATION_UPLOADS = 5
        private val VISUAL_STEP_MINIMUM_DURATION = 2.seconds
        private val COMPLETION_SETTLE_DURATION = 650.milliseconds
    }
}

private enum class PreparedDocumentUploadResult {
    Uploaded,
    Stale,
    Failed,
}

private data class PreparedDocumentUpload(
    val uri: String,
    val uploadRequestId: String,
    val documentId: String? = null,
)
