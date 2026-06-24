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
        if (_uiState.value.document != null || _uiState.value.loadStatus != PdfLoadStatus.Idle) return
        savedStateHandle.get<String>(KEY_DOCUMENT_URI)?.let { savedUri ->
            selectDocument(
                uri = savedUri.toUri(),
                requestedPageIndex = savedStateHandle[KEY_SELECTED_PAGE] ?: 0,
            )
        }
    }

    fun selectDocument(uri: Uri, requestedPageIndex: Int = 0) {
        val loadGeneration = ++documentLoadGeneration
        savedStateHandle[KEY_DOCUMENT_URI] = uri.toString()
        savedStateHandle[KEY_SELECTED_PAGE] = requestedPageIndex
        pageCache.clear()
        loadingPages.clear()
        _uiState.value = PdfUploadUiState(sessionId = sessionId, loadStatus = PdfLoadStatus.Loading)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.loadDocument(uri)
            }

            if (loadGeneration != documentLoadGeneration) return@launch

            result
                .onSuccess { document ->
                    val selectedPage = restoredPageIndex(requestedPageIndex, document.pageCount)
                    if (!isUploadSizeAllowed(document.sizeBytes)) {
                        _uiState.value = PdfUploadUiState(
                            sessionId = sessionId,
                            loadStatus = PdfLoadStatus.Error(
                                getApplication<Application>().getString(R.string.pdf_too_large),
                            ),
                        )
                        return@onSuccess
                    }
                    _uiState.value = PdfUploadUiState(
                        sessionId = sessionId,
                        document = document,
                        selectedPageIndex = selectedPage,
                        loadStatus = PdfLoadStatus.Ready,
                    )
                    savedStateHandle[KEY_SELECTED_PAGE] = selectedPage
                    requestPage(PdfRenderKey(selectedPage, PdfRenderKind.Preview), previewWidthPx)
                }
                .onFailure {
                    _uiState.value = PdfUploadUiState(
                        sessionId = sessionId,
                        loadStatus = PdfLoadStatus.Error(
                            "This PDF could not be opened. It may be protected, corrupted, or no longer accessible.",
                        ),
                    )
                }
        }
    }

    fun selectPage(pageIndex: Int) {
        val document = _uiState.value.document ?: return
        if (pageIndex !in 0 until document.pageCount) return

        _uiState.update { state ->
            state.copy(selectedPageIndex = pageIndex)
        }
        savedStateHandle[KEY_SELECTED_PAGE] = pageIndex
        requestPage(PdfRenderKey(pageIndex, PdfRenderKind.Preview), previewWidthPx)
    }

    fun requestPage(
        key: PdfRenderKey,
        targetWidthPx: Int,
    ) {
        val document = _uiState.value.document ?: return
        val loadGeneration = documentLoadGeneration
        if (key.pageIndex !in 0 until document.pageCount) return

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
                        uri = document.uri.toUri(),
                        pageIndex = key.pageIndex,
                        targetWidthPx = targetWidthPx,
                    )
                }
            }

            if (loadGeneration != documentLoadGeneration || _uiState.value.document?.uri != document.uri) {
                return@launch
            }

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

    fun documentReference(): String? = _uiState.value.document?.uri

    fun beginNewSession() {
        documentLoadGeneration += 1
        sessionId += 1
        savedStateHandle[KEY_SESSION_ID] = sessionId
        savedStateHandle.remove<String>(KEY_DOCUMENT_URI)
        savedStateHandle.remove<Int>(KEY_SELECTED_PAGE)
        pageCache.clear()
        loadingPages.clear()
        _uiState.value = PdfUploadUiState(sessionId = sessionId)
    }

    private companion object {
        const val previewWidthPx = 1400
        const val MaxBitmapCacheBytes = 32L * 1024 * 1024
        const val KEY_SESSION_ID = "pdf_session_id"
        const val KEY_DOCUMENT_URI = "pdf_document_uri"
        const val KEY_SELECTED_PAGE = "pdf_selected_page"
    }
}
