package com.hci.ren.feature.pdfupload.presentation

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.hci.ren.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class PdfUploadViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val repository = PdfDocumentRepository(application.contentResolver)
    private val pageCache = BoundedPageCache<PdfRenderKey, android.graphics.Bitmap>(
        maxWeight = MaxBitmapCacheBytes,
        weightOf = { it.allocationByteCount.toLong() },
    )
    private val renderSemaphore = Semaphore(2)
    private val loadingPages = mutableSetOf<PdfRenderKey>()
    private var documentLoadGeneration = 0L
    private var sessionId = savedStateHandle[KEY_SESSION_ID] ?: 0L

    private val _uiState = MutableStateFlow(PdfUploadUiState(sessionId = sessionId))
    val uiState: StateFlow<PdfUploadUiState> = _uiState.asStateFlow()

    fun restoreDocumentIfNeeded() {
        if (_uiState.value.documentGroup != null || _uiState.value.loadStatus != PdfLoadStatus.Idle) return
        val uriList = restoreUriList() ?: return
        val savedPdfIndex = savedStateHandle.get<Int>(KEY_SELECTED_PDF_INDEX) ?: 0
        if (savedPdfIndex > 0 && savedPdfIndex < uriList.size) {
            val loadGeneration = ++documentLoadGeneration
            saveUriList(uriList)
            savedStateHandle[KEY_SELECTED_PDF_INDEX] = savedPdfIndex
            pageCache.clear()
            loadingPages.clear()
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) { repository.loadDocuments(uriList) }
                if (loadGeneration != documentLoadGeneration) return@launch
                result.onSuccess { documents ->
                    val group = DocumentGroup(documents = documents, selectedPdfIndex = savedPdfIndex)
                    _uiState.value = PdfUploadUiState(sessionId = sessionId, documentGroup = group, loadStatus = PdfLoadStatus.Ready)
                    requestPage(PdfRenderKey(savedPdfIndex, 0, PdfRenderKind.Preview), previewWidthPx)
                }
            }
        } else {
            selectDocuments(uriList)
        }
    }

    fun selectDocuments(uris: List<Uri>) {
        val uriFilter = filterDuplicateDocuments(uris, keyOf = { it.toString() })
        val uniqueUris = uriFilter.uniqueItems
        if (uniqueUris.size > MaxPdfDocumentCount) {
            _uiState.value = PdfUploadUiState(
                sessionId = sessionId,
                loadStatus = PdfLoadStatus.Error(
                    getApplication<Application>().getString(R.string.too_many_pdfs, MaxPdfDocumentCount)
                ),
            )
            return
        }
        val loadGeneration = ++documentLoadGeneration
        pageCache.clear()
        loadingPages.clear()
        _uiState.value = PdfUploadUiState(sessionId = sessionId, loadStatus = PdfLoadStatus.Loading)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.loadDocuments(uniqueUris) }
            if (loadGeneration != documentLoadGeneration) return@launch
            result
                .onSuccess { documents ->
                    val documentFilter = filterDuplicateDocuments(documents, keyOf = PdfDocumentUiModel::identityKey)
                    val uniqueDocuments = documentFilter.uniqueItems
                    val duplicateCount = uriFilter.duplicateCount + documentFilter.duplicateCount
                    saveUriList(uniqueDocuments.map { it.uri.toUri() })
                    savedStateHandle[KEY_SELECTED_PDF_INDEX] = 0
                    val oversized = uniqueDocuments.filter { !isUploadSizeAllowed(it.sizeBytes) }
                    if (oversized.isNotEmpty()) {
                        val name = oversized.first().fileName
                        _uiState.value = PdfUploadUiState(
                            sessionId = sessionId,
                            loadStatus = PdfLoadStatus.Error(
                                getApplication<Application>().getString(R.string.pdf_too_large_with_name, name)
                            ),
                        )
                        return@onSuccess
                    }
                    val group = DocumentGroup(documents = uniqueDocuments, selectedPdfIndex = 0)
                    _uiState.value = PdfUploadUiState(
                        sessionId = sessionId,
                        documentGroup = group,
                        selectedPageIndex = 0,
                        loadStatus = PdfLoadStatus.Ready,
                        noticeMessage = duplicateNoticeMessage(duplicateCount),
                    )
                    requestPage(PdfRenderKey(0, 0, PdfRenderKind.Preview), previewWidthPx)
                }
                .onFailure {
                    _uiState.value = PdfUploadUiState(
                        sessionId = sessionId,
                        loadStatus = PdfLoadStatus.Error(
                            getApplication<Application>().getString(R.string.pdf_corrupt_generic)
                        ),
                    )
                }
        }
    }

    fun appendDocuments(uris: List<Uri>) {
        val currentCount = _uiState.value.documentGroup?.documents?.size ?: 0
        val existingUriKeys = _uiState.value.documentGroup?.documents?.map { it.uri }?.toSet().orEmpty()
        val uriFilter = filterDuplicateDocuments(uris, existingKeys = existingUriKeys, keyOf = { it.toString() })
        val uniqueUris = uriFilter.uniqueItems
        if (uniqueUris.isEmpty()) {
            _uiState.update { state ->
                state.copy(
                    loadStatus = if (state.documentGroup?.documents?.isNotEmpty() == true) PdfLoadStatus.Ready else state.loadStatus,
                    noticeMessage = duplicateNoticeMessage(uriFilter.duplicateCount),
                )
            }
            return
        }
        if (currentCount + uniqueUris.size > MaxPdfDocumentCount) {
            _uiState.update { state ->
                state.copy(
                    loadStatus = if (state.documentGroup?.documents?.isNotEmpty() == true) PdfLoadStatus.Ready else state.loadStatus,
                    noticeMessage = getApplication<Application>().getString(R.string.too_many_pdfs, MaxPdfDocumentCount),
                )
            }
            return
        }
        val loadGeneration = ++documentLoadGeneration
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.loadDocuments(uniqueUris) }
            if (loadGeneration != documentLoadGeneration) return@launch
            result
                .onSuccess { newDocs ->
                    val oversized = newDocs.filter { !isUploadSizeAllowed(it.sizeBytes) }
                    if (oversized.isNotEmpty()) {
                        val name = oversized.first().fileName
                        _uiState.update { state ->
                            state.copy(loadStatus = PdfLoadStatus.Error(
                                getApplication<Application>().getString(R.string.pdf_too_large_with_name, name)
                            ))
                        }
                        return@onSuccess
                    }
                    _uiState.update { state ->
                        val group = state.documentGroup ?: return@update state
                        val documentFilter = filterDuplicateDocuments(
                            candidates = newDocs,
                            existingKeys = group.documents.map { it.identityKey() }.toSet(),
                            keyOf = PdfDocumentUiModel::identityKey,
                        )
                        val uniqueDocs = documentFilter.uniqueItems
                        val duplicateCount = uriFilter.duplicateCount + documentFilter.duplicateCount
                        if (uniqueDocs.isEmpty()) {
                            state.copy(loadStatus = PdfLoadStatus.Ready, noticeMessage = duplicateNoticeMessage(duplicateCount))
                        } else {
                            val updated = group.copy(documents = group.documents + uniqueDocs)
                            state.copy(
                                documentGroup = updated,
                                loadStatus = PdfLoadStatus.Ready,
                                noticeMessage = duplicateNoticeMessage(duplicateCount),
                            )
                        }
                    }
                    updateSavedUriList()
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(loadStatus = PdfLoadStatus.Error(
                            getApplication<Application>().getString(R.string.pdf_corrupt_generic)
                        ))
                    }
                }
        }
    }

    fun removeDocument(index: Int) {
        val group = _uiState.value.documentGroup ?: return
        if (index !in group.documents.indices) return
        documentLoadGeneration++
        pageCache.removeIf { it.documentIndex == index }
        val newDocs = group.documents.toMutableList().apply { removeAt(index) }
        val newSelected = when {
            group.selectedPdfIndex == index -> if (newDocs.isEmpty()) 0 else index.coerceAtMost(newDocs.lastIndex)
            group.selectedPdfIndex > index -> group.selectedPdfIndex - 1
            else -> group.selectedPdfIndex
        }
        _uiState.update { state ->
            state.copy(
                documentGroup = DocumentGroup(documents = newDocs, selectedPdfIndex = newSelected),
                renderedPages = state.renderedPages.filterKeys { it.documentIndex != index },
                selectedPageIndex = 0,
                loadStatus = if (newDocs.isNotEmpty()) PdfLoadStatus.Ready else PdfLoadStatus.Idle,
                noticeMessage = null,
            )
        }
        updateSavedUriList()
        savedStateHandle[KEY_SELECTED_PDF_INDEX] = newSelected
    }

    fun selectPdf(documentIndex: Int) {
        val group = _uiState.value.documentGroup ?: return
        if (documentIndex !in group.documents.indices) return
        _uiState.update { state ->
            state.copy(documentGroup = group.copy(selectedPdfIndex = documentIndex), selectedPageIndex = 0)
        }
        savedStateHandle[KEY_SELECTED_PDF_INDEX] = documentIndex
        requestPage(PdfRenderKey(documentIndex, 0, PdfRenderKind.Preview), previewWidthPx)
    }

    fun selectPage(pageIndex: Int) {
        val group = _uiState.value.documentGroup ?: return
        val doc = group.documents.getOrNull(group.selectedPdfIndex) ?: return
        if (pageIndex !in 0 until doc.pageCount) return

        _uiState.update { state ->
            state.copy(selectedPageIndex = pageIndex)
        }
        savedStateHandle[KEY_SELECTED_PAGE] = pageIndex
        requestPage(PdfRenderKey(group.selectedPdfIndex, pageIndex, PdfRenderKind.Preview), previewWidthPx)
    }

    fun requestPage(
        key: PdfRenderKey,
        targetWidthPx: Int,
    ) {
        val group = _uiState.value.documentGroup ?: return
        val doc = group.documents.getOrNull(key.documentIndex) ?: return
        val loadGeneration = documentLoadGeneration
        if (key.pageIndex !in 0 until doc.pageCount) return

        pageCache.get(key)?.let { bitmap ->
            _uiState.update { state ->
                state.copy(
                    renderedPages = state.renderedPages + (key to PdfPageRenderState.Ready(bitmap)),
                )
            }
            return
        }

        if (!loadingPages.add(key)) return
        _uiState.update { state ->
            state.copy(renderedPages = state.renderedPages + (key to PdfPageRenderState.Loading))
        }

        viewModelScope.launch {
            val result = renderSemaphore.withPermit {
                withContext(Dispatchers.IO) {
                    repository.renderPage(
                        uri = doc.uri.toUri(),
                        pageIndex = key.pageIndex,
                        targetWidthPx = targetWidthPx,
                    )
                }
            }

            if (loadGeneration != documentLoadGeneration) return@launch

            loadingPages.remove(key)
            result
                .onSuccess { bitmap ->
                    val evictedKeys = pageCache.put(key, bitmap)
                    _uiState.update { state ->
                        state.copy(
                            renderedPages = (state.renderedPages - evictedKeys.toSet()) +
                                (key to PdfPageRenderState.Ready(bitmap)),
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(
                            renderedPages = state.renderedPages + (
                                key to PdfPageRenderState.Error("Page could not be previewed.")
                            ),
                        )
                    }
                }
        }
    }

    fun documentReferences(): List<String> =
        _uiState.value.documentGroup?.documents?.map { it.uri } ?: emptyList()

    fun beginNewSession() {
        documentLoadGeneration += 1
        sessionId += 1
        savedStateHandle[KEY_SESSION_ID] = sessionId
        savedStateHandle.remove<String>(KEY_DOCUMENT_URI_LIST)
        savedStateHandle.remove<String>(KEY_DOCUMENT_URI)
        savedStateHandle.remove<Int>(KEY_SELECTED_PDF_INDEX)
        savedStateHandle.remove<Int>(KEY_SELECTED_PAGE)
        pageCache.clear()
        loadingPages.clear()
        _uiState.value = PdfUploadUiState(sessionId = sessionId)
    }

    private fun saveUriList(uris: List<Uri>) {
        savedStateHandle[KEY_DOCUMENT_URI_LIST] = uris.joinToString("|") { it.toString() }
        savedStateHandle.remove<String>(KEY_DOCUMENT_URI)
    }

    private fun updateSavedUriList() {
        val uris = _uiState.value.documentGroup?.documents?.map { it.uri.toUri() } ?: return
        savedStateHandle[KEY_DOCUMENT_URI_LIST] = uris.joinToString("|") { it.toString() }
    }

    private fun restoreUriList(): List<Uri>? {
        savedStateHandle.get<String>(KEY_DOCUMENT_URI_LIST)?.let { urisStr ->
            val uris = urisStr.split("|").map { it.toUri() }
            if (uris.isNotEmpty()) return uris
        }
        return savedStateHandle.get<String>(KEY_DOCUMENT_URI)?.let { listOf(it.toUri()) }
    }

    private fun duplicateNoticeMessage(duplicateCount: Int): String? =
        if (duplicateCount > 0) {
            getApplication<Application>().getString(R.string.duplicate_pdfs_skipped)
        } else {
            null
        }

    private companion object {
        const val previewWidthPx = 1400
        const val MaxBitmapCacheBytes = 32L * 1024 * 1024
        const val KEY_SESSION_ID = "pdf_session_id"
        const val KEY_DOCUMENT_URI = "pdf_document_uri"
        const val KEY_DOCUMENT_URI_LIST = "pdf_document_uri_list"
        const val KEY_SELECTED_PDF_INDEX = "pdf_selected_pdf_index"
        const val KEY_SELECTED_PAGE = "pdf_selected_page"
    }
}
